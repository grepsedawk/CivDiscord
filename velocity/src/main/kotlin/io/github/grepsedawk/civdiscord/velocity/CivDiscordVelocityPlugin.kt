package io.github.grepsedawk.civdiscord.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import io.github.grepsedawk.civdiscord.core.admin.AdminService
import io.github.grepsedawk.civdiscord.core.auth.LinkAttemptLimiter
import io.github.grepsedawk.civdiscord.core.auth.LinkService
import io.github.grepsedawk.civdiscord.core.auth.LinkTokenStore
import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeSigner
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.PatreonTierDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.core.patreon.OkHttpPatreonClient
import io.github.grepsedawk.civdiscord.core.patreon.PatreonRefreshCreds
import io.github.grepsedawk.civdiscord.core.patreon.PatreonSync
import io.github.grepsedawk.civdiscord.core.patreon.TierRoleMap
import io.github.grepsedawk.civdiscord.core.relay.RelayService
import io.github.grepsedawk.civdiscord.velocity.auth.MemberAddListener
import io.github.grepsedawk.civdiscord.velocity.auth.RoleGranter
import io.github.grepsedawk.civdiscord.velocity.bridge.BridgeMessageRouter
import io.github.grepsedawk.civdiscord.velocity.bridge.BridgeServer
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import io.github.grepsedawk.civdiscord.velocity.commands.AdminGuildCommand
import io.github.grepsedawk.civdiscord.velocity.commands.AdminRunCommand
import io.github.grepsedawk.civdiscord.velocity.commands.AdminUserCommand
import io.github.grepsedawk.civdiscord.velocity.commands.CommandRegistrar
import io.github.grepsedawk.civdiscord.velocity.commands.LinkCommand
import io.github.grepsedawk.civdiscord.velocity.commands.MeCommand
import io.github.grepsedawk.civdiscord.velocity.commands.RelayCommand
import io.github.grepsedawk.civdiscord.velocity.config.Config
import io.github.grepsedawk.civdiscord.velocity.config.ConfigLoader
import io.github.grepsedawk.civdiscord.velocity.discord.GuildLifecycleListener
import io.github.grepsedawk.civdiscord.velocity.discord.JdaFactory
import io.github.grepsedawk.civdiscord.velocity.discord.MessageRelayListener
import io.github.grepsedawk.civdiscord.velocity.patreon.PatreonSyncJob
import io.github.grepsedawk.civdiscord.velocity.snitch.SnitchRelay
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Plugin(
    id = "civdiscord",
    name = "CivDiscord",
    version = "0.1.0",
    description = "Discord <-> Minecraft bridge bot (Velocity side)",
    authors = ["grepsedawk"],
)
class CivDiscordVelocityPlugin
@Inject
constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDir: Path,
) {
    private lateinit var config: Config
    private val pendingHookSweepExecutor =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "civdiscord-sweep") }
    private val patreonExecutor =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "civdiscord-patreon") }
    private val roleGrantExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "civdiscord-role-grant") }
    private var jda: JDA? = null
    private lateinit var bridge: BridgeServer
    private lateinit var router: BridgeMessageRouter
    private val restErrorHandler =
        ErrorHandler()
            .ignore(ErrorResponse.UNKNOWN_MEMBER, ErrorResponse.UNKNOWN_ROLE, ErrorResponse.UNKNOWN_MESSAGE)
            .handle({ true }) { t -> logger.warn("Discord REST call failed", t) }

    private data class PendingEntry(val hook: InteractionHook, val registeredAt: Long)

    @Subscribe
    fun onInit(event: ProxyInitializeEvent) {
        dataDir.toFile().mkdirs()
        val cfgFile = dataDir.resolve("config.yml").toFile()
        if (!cfgFile.exists()) {
            cfgFile.writeText(this::class.java.classLoader.getResource("config.yml")!!.readText())
            try {
                Files.setPosixFilePermissions(cfgFile.toPath(), PosixFilePermissions.fromString("rw-------"))
            } catch (_: UnsupportedOperationException) {
            }
            logger.warn("Wrote default config.yml to ${cfgFile.absolutePath} — fill it in and restart.")
            return
        }
        config = ConfigLoader.load(dataDir.toFile())

        val db = CivDiscordDb.connect(dataDir.resolve(config.database.path).toString())
        val bindings = BindingDao(db)
        val guildsDao = GuildDao(db)
        val relays = RelayDao(db)
        val tiers = PatreonTierDao(db)

        val linkTokens = LinkTokenStore()
        val linkSvc = LinkService(linkTokens, bindings)
        val relaySvc = RelayService(relays)
        val adminSvc = AdminService(bindings)
        val linkLimiter = LinkAttemptLimiter()

        val addRoleFn = { guildId: Long, discordId: Long, roleId: Long ->
            jda?.getGuildById(guildId)?.let { g ->
                val role = g.getRoleById(roleId) ?: return@let
                val member =
                    g.getMemberById(discordId) ?: runCatching {
                        g.retrieveMemberById(discordId).complete()
                    }.getOrNull()
                member?.let { m -> g.addRoleToMember(m, role).queue(null, restErrorHandler) }
            }
            Unit
        }
        val removeRoleFn = { guildId: Long, discordId: Long, roleId: Long ->
            jda?.getGuildById(guildId)?.let { g ->
                val role = g.getRoleById(roleId) ?: return@let
                val member =
                    g.getMemberById(discordId) ?: runCatching {
                        g.retrieveMemberById(discordId).complete()
                    }.getOrNull()
                member?.let { m -> g.removeRoleFromMember(m, role).queue(null, restErrorHandler) }
            }
            Unit
        }

        val signer = maybeLoadSigner()
        val channel = MinecraftChannelIdentifier.from(BridgeChannel.NAME)
        server.channelRegistrar.register(channel)
        bridge =
            BridgeServer(
                broadcast = { target, bytes ->
                    server.getServer(target).ifPresent { it.sendPluginMessage(channel, bytes) }
                },
                signer = signer,
            )
        router = BridgeMessageRouter(bridge)

        val granter =
            RoleGranter(
                guilds = guildsDao,
                isMemberOf = { gid, did ->
                    val guild = jda?.getGuildById(gid)
                    val cached = guild?.getMemberById(did)
                    val resolved =
                        cached ?: guild?.let {
                            runCatching { it.retrieveMemberById(did).complete() }.getOrNull()
                        }
                    resolved != null
                },
                grant = addRoleFn,
            )
        val memberAddListener =
            MemberAddListener(
                bindings,
                grant = { gid, did ->
                    roleGrantExecutor.execute { granter.grantForGuild(gid, did) }
                },
            )

        val linkCmd =
            LinkCommand(
                linkSvc,
                linkLimiter,
                onLinked = { did, _, _ ->
                    roleGrantExecutor.execute { granter.grantAllForLinkedUser(did) }
                },
            )
        val meCmd = MeCommand(bindings, tiers, guildsDao, granter, roleGrantExecutor)
        val relayCmd = RelayCommand(relaySvc)
        val adminUserCmd = AdminUserCommand(adminSvc)
        val adminGuildCmd = AdminGuildCommand(guildsDao)
        val pendingHooks = ConcurrentHashMap<String, PendingEntry>()
        val adminRunCmd =
            AdminRunCommand(
                backends = { server.allServers.map { it.serverInfo.name } },
                dispatch = { srv, p -> bridge.sendToServer(srv, p) },
                registerPending = { id, hook -> pendingHooks[id] = PendingEntry(hook, System.currentTimeMillis()) },
                unregisterPending = { id ->
                    pendingHooks.remove(id)
                    Unit
                },
            )

        val dispatcher =
            object : ListenerAdapter() {
                override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
                    when (event.name) {
                        "link" -> linkCmd.handle(event)
                        "me" -> meCmd.handle(event)
                        "relay" -> relayCmd.handle(event)
                        "admin" ->
                            when (event.subcommandGroup) {
                                "user" -> {
                                    if (event.guild?.idLong != config.discord.homeGuildId) {
                                        event.reply("This command is only available in the home guild.")
                                            .setEphemeral(true).queue(null, restErrorHandler)
                                        return
                                    }
                                    adminUserCmd.handle(event)
                                }
                                "guild" -> adminGuildCmd.handle(event)
                                null ->
                                    when (event.subcommandName) {
                                        "run" -> {
                                            if (event.guild?.idLong != config.discord.homeGuildId) {
                                                event.reply("This command is only available in the home guild.")
                                                    .setEphemeral(true).queue(null, restErrorHandler)
                                                return
                                            }
                                            adminRunCmd.handle(event)
                                        }
                                        else ->
                                            event.reply("Unknown subcommand.")
                                                .setEphemeral(true).queue(null, restErrorHandler)
                                    }
                                else ->
                                    event.reply("Unknown subcommand group.")
                                        .setEphemeral(true).queue(null, restErrorHandler)
                            }
                    }
                }

                override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
                    if (event.name == "admin" && event.focusedOption.name == "server") {
                        val matches =
                            server.allServers
                                .map { it.serverInfo.name }
                                .filter { it.startsWith(event.focusedOption.value, ignoreCase = true) }
                                .take(25)
                        event.replyChoiceStrings(matches).queue(null, restErrorHandler)
                    }
                }
            }

        val chatRelay =
            ChatRelay(
                relays = relays,
                sendToDiscord = { channelId, text ->
                    jda?.getTextChannelById(channelId)?.let { ch ->
                        ch.sendMessage(text)
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .queue(null, restErrorHandler)
                    }
                    Unit
                },
                sendToMc = { p -> for (srv in server.allServers) bridge.sendToServer(srv.serverInfo.name, p) },
            )

        try {
            jda =
                JdaFactory.build(
                    config.discord.token,
                    listOf(
                        GuildLifecycleListener(guildsDao),
                        memberAddListener,
                        MessageRelayListener(chatRelay),
                        dispatcher,
                    ),
                )
            jda!!.awaitReady()
        } catch (t: Throwable) {
            logger.error("JDA never readied — aborting plugin init", t)
            return
        }

        for (guild in jda!!.guilds) {
            if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
                logger.warn(
                    "Bot missing MANAGE_ROLES in guild {} ({}) — auth role grants will fail",
                    guild.name,
                    guild.idLong,
                )
            }
        }

        CommandRegistrar(jda!!, config.discord.homeGuildId).register()

        bridge.onConsoleReply = { reply ->
            pendingHooks.remove(reply.id)?.let { entry ->
                val text =
                    if (reply.ok) {
                        "```\n${reply.output.take(1900)}\n```"
                    } else {
                        "```\nERROR: ${reply.output.take(1900)}\n```"
                    }
                entry.hook.editOriginal(text).queue(null, restErrorHandler)
            }
        }

        bridge.onLinkRequest = { req ->
            val token = linkTokens.mint(UUID.fromString(req.mcUuid), req.mcName)
            bridge.sendBroadcast(
                Payload.LinkReply(id = req.id, token = token.code, error = null),
                allServers = server.allServers.map { it.serverInfo.name },
            )
        }

        bridge.onStatusRequest = { req ->
            val binding =
                runCatching { bindings.findByMcUuid(UUID.fromString(req.mcUuid)) }
                    .onFailure { logger.warn("onStatusRequest: invalid mcUuid {}", req.mcUuid, it) }
                    .getOrNull()
            bridge.sendBroadcast(
                Payload.StatusReply(
                    id = req.id,
                    discordId = binding?.discordId,
                    mcName = binding?.mcName,
                    linkedAt = binding?.linkedAt,
                ),
                allServers = server.allServers.map { it.serverInfo.name },
            )
        }

        bridge.onChatToDiscord = { chatRelay.dispatch(it) }

        val snitchRelay =
            SnitchRelay(
                relays = relays,
                sendToDiscord = { channelId, text ->
                    jda?.getTextChannelById(channelId)?.let { ch ->
                        ch.sendMessage(text)
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .queue(null, restErrorHandler)
                    }
                    Unit
                },
            )
        bridge.onSnitchHit = { hit ->
            runCatching { snitchRelay.dispatch(hit) }
                .onFailure { logger.warn("onSnitchHit handler failed", it) }
        }

        pendingHookSweepExecutor.scheduleAtFixedRate(
            {
                val cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(14)
                val expired = pendingHooks.entries.filter { it.value.registeredAt < cutoff }
                for ((id, entry) in expired) {
                    pendingHooks.remove(id)
                    runCatching {
                        entry.hook.editOriginal("Backend did not reply within 14 minutes.")
                            .queue(null, restErrorHandler)
                    }
                }
            },
            1,
            1,
            TimeUnit.MINUTES,
        )

        config.patreon?.let { p ->
            val refreshCreds =
                if (p.refreshToken != null && p.clientId != null && p.clientSecret != null) {
                    PatreonRefreshCreds(
                        clientId = p.clientId,
                        clientSecret = p.clientSecret,
                        initialRefreshToken = p.refreshToken,
                        tokensFile = dataDir.resolve("patreon-tokens.json").toFile(),
                    )
                } else {
                    null
                }
            val client =
                OkHttpPatreonClient(
                    accessToken = p.accessToken,
                    campaignId = p.campaignId,
                    refreshCreds = refreshCreds,
                )
            val sync = PatreonSync(client, tiers, TierRoleMap(p.tierRoles))
            val job =
                PatreonSyncJob(
                    sync = sync,
                    homeGuildId = config.discord.homeGuildId,
                    addRole = addRoleFn,
                    removeRole = removeRoleFn,
                )
            patreonExecutor.scheduleAtFixedRate(
                { runCatching { job.tick() }.onFailure { logger.warn("Patreon sync failed", it) } },
                0,
                p.syncIntervalMinutes.toLong(),
                TimeUnit.MINUTES,
            )
        }

        logger.info("CivDiscord-Velocity ready")
    }

    private fun maybeLoadSigner(): BridgeSigner? {
        if (!config.bridge.hmacEnabled) {
            logger.warn(
                "Bridge HMAC disabled — plugin-message frames are unauthenticated. " +
                    "Set bridge.hmac_enabled: true (and copy plugins/civdiscord/secret.key to every backend) to enable.",
            )
            return null
        }
        val secretFile = dataDir.resolve("secret.key").toFile()
        if (!secretFile.exists()) {
            val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
            secretFile.writeBytes(secret)
            try {
                Files.setPosixFilePermissions(secretFile.toPath(), PosixFilePermissions.fromString("rw-------"))
            } catch (_: UnsupportedOperationException) {
            }
            logger.warn(
                "Generated new bridge secret at ${secretFile.absolutePath}. " +
                    "Copy this file to plugins/civdiscord/secret.key on every Paper backend before they reconnect.",
            )
        }
        return BridgeSigner(secretFile.readBytes())
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (!::router.isInitialized) return
        val fromBackend = event.source is ServerConnection
        if (router.route(event.identifier.id, event.data, fromBackend)) {
            logger.debug("Routed plugin message from {} ({} bytes)", event.source, event.data.size)
            event.result = PluginMessageEvent.ForwardResult.handled()
        }
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        roleGrantExecutor.shutdownNow()
        pendingHookSweepExecutor.shutdownNow()
        patreonExecutor.shutdownNow()
        jda?.shutdown()
    }
}
