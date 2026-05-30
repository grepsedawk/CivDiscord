package io.github.grepsedawk.civdiscord.paper

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.PendingReplies
import io.github.grepsedawk.civdiscord.core.util.tryLockdown
import io.github.grepsedawk.civdiscord.paper.bridge.BridgeClient
import io.github.grepsedawk.civdiscord.paper.bridge.BridgeOutboundQueue
import io.github.grepsedawk.civdiscord.paper.chat.GroupChatProducer
import io.github.grepsedawk.civdiscord.paper.chat.RemoteGroupDelivery
import io.github.grepsedawk.civdiscord.paper.config.Config
import io.github.grepsedawk.civdiscord.paper.config.ConfigLoader
import io.github.grepsedawk.civdiscord.paper.console.ConsoleExecutor
import io.github.grepsedawk.civdiscord.paper.jukealert.SnitchListener
import io.github.grepsedawk.civdiscord.paper.linker.DiscordCommand
import io.github.grepsedawk.civdiscord.paper.linker.buildLinkMessage
import io.github.grepsedawk.civdiscord.paper.namelayer.NameLayerQueryHandler
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import vg.civcraft.mc.civchat2.CivChat2
import vg.civcraft.mc.namelayer.GroupManager
import vg.civcraft.mc.namelayer.NameLayerPlugin
import vg.civcraft.mc.namelayer.group.Group
import java.util.UUID
import java.util.concurrent.TimeUnit

class CivDiscordPaperPlugin :
    JavaPlugin(),
    PluginMessageListener,
    Listener {

    lateinit var serverConfig: Config
        private set
    lateinit var bridge: BridgeClient
        private set

    private val outboundQueue = BridgeOutboundQueue(log = org.slf4j.LoggerFactory.getLogger(BridgeOutboundQueue::class.java))

    override fun onEnable() {
        dataFolder.mkdirs()
        saveDefaultConfig()
        tryLockdown(dataFolder.resolve("config.yml").toPath())
        this.serverConfig = ConfigLoader.load(dataFolder)

        server.messenger.registerOutgoingPluginChannel(this, BridgeChannel.NAME)

        val signer = maybeLoadSigner()
        // Bukkit cannot tell proxy-injected frames from ones a connected client registered on
        // `civdiscord:bridge`. Without a signer, any handler we wired would be reachable from
        // unauthenticated input, so refuse to listen at all and only emit (unsigned) outbound.
        if (signer != null) {
            server.messenger.registerIncomingPluginChannel(this, BridgeChannel.NAME, this)
        } else {
            logger.severe(
                "Bridge incoming channel NOT registered — HMAC verification unavailable. " +
                    "Frames from Velocity will be dropped until secret.key is in place.",
            )
        }
        val console = ConsoleExecutor(server, this.serverConfig.serverName)

        val pending = PendingReplies<UUID>(
            ttlMillis = LINK_TTL_MS,
            onExpire = { uuid -> notifyBridgeTimeout(uuid, "link") },
        )
        val pendingStatus = PendingReplies<UUID>(
            ttlMillis = STATUS_TTL_MS,
            onExpire = { uuid -> notifyBridgeTimeout(uuid, "status") },
        )

        val delivery = RemoteGroupDelivery(
            groupLookup = { name -> GroupManager.getGroup(name) },
            isDisciplined = { group -> (group as Group).isDisciplined },
            sendRemote = { senderId, senderName, senderDisplayName, groupName, message ->
                CivChat2.getInstance().civChat2Manager.sendRemoteGroupMsg(
                    senderId,
                    senderName,
                    senderDisplayName,
                    groupName,
                    message,
                )
            },
            onMainThread = { runnable -> server.scheduler.runTask(this, runnable) },
            // Prefer the UUID Velocity already resolved from the linked Binding. Fall back to
            // the main-thread usercache/Mojang lookup only for legacy frames that omit fromUuid.
            senderUuidFor = { msg ->
                msg.fromUuid?.let { java.util.UUID.fromString(it) }
                    ?: server.getOfflinePlayer(msg.from).uniqueId
            },
        )

        this.bridge = BridgeClient(
            send = ::sendViaAnyPlayer,
            signer = signer,
            handlersFactory = { b ->
                val nlHandler = NameLayerQueryHandler(
                    resolver = { uuid -> NameLayerPlugin.getGroupManagerDao().getGroupNames(uuid) },
                    send = { b.send(it) },
                )
                io.github.grepsedawk.civdiscord.paper.bridge.ClientInboundHandlers(
                    onConsoleRequest = { req ->
                        // Bukkit command dispatch is not async-safe — hop to the main thread.
                        server.scheduler.runTask(
                            this,
                            Runnable {
                                val reply = console.run(req)
                                b.send(reply)
                            },
                        )
                    },
                    onChatToMc = { msg -> delivery.handle(msg) },
                    onNameLayerQuery = { nlHandler.handle(it) },
                    onLinkReply = { reply ->
                        val playerUuid = pending.resolve(reply.id)
                        server.scheduler.runTask(
                            this,
                            Runnable {
                                val p = playerUuid?.let { server.getPlayer(it) } ?: return@Runnable
                                val token = reply.token
                                if (token != null) {
                                    p.sendMessage(buildLinkMessage(token))
                                } else {
                                    p.sendMessage("Could not start link: ${reply.error ?: "unknown error"}")
                                }
                            },
                        )
                    },
                    onStatusReply = { reply ->
                        val playerUuid = pendingStatus.resolve(reply.id)
                        server.scheduler.runTask(
                            this,
                            Runnable {
                                val p = playerUuid?.let { server.getPlayer(it) } ?: return@Runnable
                                val message = reply.discordId?.let { id ->
                                    "§7Linked to Discord user §a$id§7 (mc: §a${reply.mcName}§7)."
                                } ?: "§7No Discord account linked. Run §b/discord link§7 to start."
                                p.sendMessage(message)
                            },
                        )
                    },
                )
            },
        )

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(
            SnitchListener(
                serverName = this.serverConfig.serverName,
                send = { bridge.send(it) },
                scheduleLogin = { task -> server.scheduler.runTaskLater(this, task, LOGIN_DISPATCH_DELAY_TICKS) },
            ),
            this,
        )

        val discordCmd = DiscordCommand(
            send = { bridge.send(it) },
            pending = pending,
            pendingStatus = pendingStatus,
        )
        getCommand("discord")?.setExecutor(discordCmd)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { pending.sweep() }, SWEEP_TICKS, SWEEP_TICKS)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { pendingStatus.sweep() }, SWEEP_TICKS, SWEEP_TICKS)

        val producer = GroupChatProducer(
            serverName = this.serverConfig.serverName,
            emit = { payload -> bridge.send(payload) },
        )
        server.pluginManager.registerEvents(producer, this)

        logger.info("CivDiscord-Paper loaded for server '${this.serverConfig.serverName}'")
    }

    private fun maybeLoadSigner(): BridgeSigner? {
        if (!serverConfig.bridge.hmacEnabled) {
            logger.severe(
                "Bridge HMAC explicitly disabled in config.yml — bridge will refuse all incoming " +
                    "frames. Without HMAC any connected player can inject ConsoleRequest and run " +
                    "commands as the server console. Set bridge.hmac_enabled: true and copy " +
                    "Velocity's plugins/civdiscord/secret.key to this backend's " +
                    "${dataFolder.resolve("secret.key").absolutePath} (note the case difference).",
            )
            return null
        }
        val secretFile = dataFolder.resolve("secret.key")
        if (!secretFile.exists()) {
            logger.severe(
                "bridge.hmac_enabled: true but ${secretFile.absolutePath} missing. " +
                    "From the Velocity host, run e.g.: " +
                    "scp <velocity-host>:/path/to/plugins/civdiscord/secret.key ${secretFile.absolutePath} " +
                    "(Velocity stores it under plugins/civdiscord/ lowercase; Paper expects " +
                    "plugins/CivDiscord/ capital — case matters on Linux.) " +
                    "Bridge incoming channel will NOT be registered until this file is present.",
            )
            return null
        }
        tryLockdown(secretFile.toPath())
        return BridgeSigner(secretFile.readBytes())
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel == BridgeChannel.NAME) bridge.handleIncoming(message)
    }

    override fun onDisable() {
        server.messenger.unregisterIncomingPluginChannel(this)
        server.messenger.unregisterOutgoingPluginChannel(this)
    }

    // Plugin messaging requires a Player as the carrier; queue when the server is empty so
    // the next join can flush anything still inside the TTL.
    private fun sendViaAnyPlayer(payloadType: String, bytes: ByteArray) {
        val carrier = server.onlinePlayers.firstOrNull()
        if (carrier != null) {
            carrier.sendPluginMessage(this, BridgeChannel.NAME, bytes)
        } else {
            outboundQueue.enqueue(BridgeChannel.NAME, payloadType, bytes)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        drainQueueWithCap(event.player)
    }

    // Sweep runs on an async pool; Bukkit player lookup and sendMessage must hop to main.
    private fun notifyBridgeTimeout(uuid: UUID, kind: String) {
        server.scheduler.runTask(
            this,
            Runnable {
                val p = server.getPlayer(uuid) ?: return@Runnable
                logger.warning("Bridge $kind request timed out for player ${p.name} (${p.uniqueId})")
                p.sendMessage(
                    "§cDiscord $kind request timed out — try again, or contact staff if it keeps failing.",
                )
            },
        )
    }

    private fun drainQueueWithCap(carrier: Player) {
        outboundQueue.drain(maxFrames = DRAIN_FRAMES_PER_TICK) { frame ->
            carrier.sendPluginMessage(this, frame.channel, frame.bytes)
        }
        if (!outboundQueue.isEmpty()) {
            server.scheduler.runTaskLater(
                this,
                Runnable {
                    val next = server.onlinePlayers.firstOrNull() ?: return@Runnable
                    drainQueueWithCap(next)
                },
                1L,
            )
        }
    }

    companion object {
        const val DRAIN_FRAMES_PER_TICK = 50

        // PlayerLoginSnitchEvent fires inside JukeAlert's PlayerJoinEvent MONITOR pass;
        // sending the bridge frame immediately races the joining player's plugin-message
        // pipeline through Velocity. 1s is well past the backend handshake and well under
        // the BridgeOutboundQueue TTL.
        private const val LOGIN_DISPATCH_DELAY_TICKS = 20L

        // Sweep every 5s so timeout messages reach the player within a few seconds of the
        // ack deadline. LINK_TTL is the ack deadline — link replies arrive in <1s on a healthy
        // bridge, so 20s is generous; past that, telling the player "broken, try again" is
        // more useful than holding the entry for 5 minutes.
        private const val SWEEP_TICKS = 20L * 5
        private val LINK_TTL_MS = TimeUnit.SECONDS.toMillis(20)
        private val STATUS_TTL_MS = TimeUnit.SECONDS.toMillis(20)
    }
}
