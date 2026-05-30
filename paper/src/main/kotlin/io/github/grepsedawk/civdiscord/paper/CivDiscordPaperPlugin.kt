package io.github.grepsedawk.civdiscord.paper

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.paper.bridge.BridgeClient
import io.github.grepsedawk.civdiscord.paper.bridge.BridgeOutboundQueue
import io.github.grepsedawk.civdiscord.paper.chat.GroupChatProducer
import io.github.grepsedawk.civdiscord.paper.chat.RemoteGroupDelivery
import io.github.grepsedawk.civdiscord.paper.config.Config
import io.github.grepsedawk.civdiscord.paper.config.ConfigLoader
import io.github.grepsedawk.civdiscord.paper.console.ConsoleExecutor
import io.github.grepsedawk.civdiscord.paper.jukealert.SnitchListener
import io.github.grepsedawk.civdiscord.paper.linker.DiscordCommand
import io.github.grepsedawk.civdiscord.paper.linker.PendingLinkReplies
import io.github.grepsedawk.civdiscord.paper.linker.PendingStatusReplies
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
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class CivDiscordPaperPlugin : JavaPlugin(), PluginMessageListener, Listener {

    lateinit var serverConfig: Config
        private set
    lateinit var bridge: BridgeClient
        private set

    private val outboundQueue = BridgeOutboundQueue()

    override fun onEnable() {
        dataFolder.mkdirs()
        saveDefaultConfig()
        this.serverConfig = ConfigLoader.load(dataFolder)

        server.messenger.registerOutgoingPluginChannel(this, BridgeChannel.NAME)
        server.messenger.registerIncomingPluginChannel(this, BridgeChannel.NAME, this)

        val signer = maybeLoadSigner()
        this.bridge = BridgeClient(send = ::sendViaAnyPlayer, signer = signer)
        val console = ConsoleExecutor(server, this.serverConfig.serverName)
        bridge.onConsoleRequest = { req ->
            // Bukkit command dispatch is not async-safe — hop to the main thread.
            server.scheduler.runTask(
                this,
                Runnable {
                    val reply = console.run(req)
                    bridge.send(reply)
                },
            )
        }
        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(
            SnitchListener(
                serverName = this.serverConfig.serverName,
                send = { bridge.send(it) },
            ),
            this,
        )
        val nlHandler = NameLayerQueryHandler(
            resolver = { uuid -> NameLayerPlugin.getGroupCache().getGroupNames(uuid) },
            send = { bridge.send(it) },
        )
        bridge.onNameLayerQuery = { nlHandler.handle(it) }

        val pending = PendingLinkReplies()
        val pendingStatus = PendingStatusReplies()
        val discordCmd = DiscordCommand(
            send = { bridge.send(it) },
            pending = pending,
            pendingStatus = pendingStatus,
        )
        getCommand("discord")?.setExecutor(discordCmd)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { pending.sweep() }, SWEEP_TICKS, SWEEP_TICKS)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { pendingStatus.sweep() }, SWEEP_TICKS, SWEEP_TICKS)

        bridge.onLinkReply = { reply ->
            val playerUuid = pending.resolve(reply)
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
        }

        bridge.onStatusReply = { reply ->
            val playerUuid = pendingStatus.resolve(reply)
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
        }

        val producer = GroupChatProducer(
            serverName = this.serverConfig.serverName,
            emit = { payload -> bridge.send(payload) },
        )
        server.pluginManager.registerEvents(producer, this)

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
            senderUuidFor = { msg -> server.getOfflinePlayer(msg.from).uniqueId },
        )
        bridge.onChatToMc = { msg -> delivery.handle(msg) }

        logger.info("CivDiscord-Paper loaded for server '${this.serverConfig.serverName}'")
    }

    private fun maybeLoadSigner(): BridgeSigner? {
        if (!serverConfig.bridge.hmacEnabled) {
            logger.warning(
                "Bridge HMAC disabled — plugin-message frames are unauthenticated. " +
                    "Set bridge.hmac_enabled: true (and copy plugins/civdiscord/secret.key from Velocity) to enable.",
            )
            return null
        }
        val secretFile = dataFolder.resolve("secret.key")
        if (!secretFile.exists()) {
            logger.severe(
                "bridge.hmac_enabled: true but ${secretFile.absolutePath} missing — " +
                    "copy it from Velocity's plugins/civdiscord/secret.key. Bridge will reject all frames until present.",
            )
            return null
        }
        try {
            Files.setPosixFilePermissions(secretFile.toPath(), PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
        }
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
    private fun sendViaAnyPlayer(bytes: ByteArray) {
        val carrier = server.onlinePlayers.firstOrNull()
        if (carrier != null) {
            carrier.sendPluginMessage(this, BridgeChannel.NAME, bytes)
        } else {
            outboundQueue.enqueue(BridgeChannel.NAME, bytes)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        drainQueueWithCap(event.player)
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
        private const val SWEEP_TICKS = 20L * 60
    }
}
