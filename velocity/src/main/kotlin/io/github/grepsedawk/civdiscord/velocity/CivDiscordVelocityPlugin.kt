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
import io.github.grepsedawk.civdiscord.core.bridge.PendingReplies
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
import io.github.grepsedawk.civdiscord.core.util.tryLockdown
import io.github.grepsedawk.civdiscord.velocity.auth.MemberAddListener
import io.github.grepsedawk.civdiscord.velocity.auth.RoleGranter
import io.github.grepsedawk.civdiscord.velocity.bridge.BridgeMessageRouter
import io.github.grepsedawk.civdiscord.velocity.bridge.BridgeServer
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRateLimiter
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import io.github.grepsedawk.civdiscord.velocity.commands.AdminGuildCommand
import io.github.grepsedawk.civdiscord.velocity.commands.AdminRunCommand
import io.github.grepsedawk.civdiscord.velocity.commands.AdminUserCommand
import io.github.grepsedawk.civdiscord.velocity.commands.CommandRegistrar
import io.github.grepsedawk.civdiscord.velocity.commands.LinkCommand
import io.github.grepsedawk.civdiscord.velocity.commands.MeCommand
import io.github.grepsedawk.civdiscord.velocity.commands.RelayCommand
import io.github.grepsedawk.civdiscord.velocity.commands.SlashCommandDispatcher
import io.github.grepsedawk.civdiscord.velocity.config.Config
import io.github.grepsedawk.civdiscord.velocity.config.ConfigLoader
import io.github.grepsedawk.civdiscord.velocity.discord.GuildLifecycleListener
import io.github.grepsedawk.civdiscord.velocity.discord.JdaFactory
import io.github.grepsedawk.civdiscord.velocity.discord.LinkPrompt
import io.github.grepsedawk.civdiscord.velocity.discord.MessageRelayListener
import io.github.grepsedawk.civdiscord.velocity.discord.WebhookRelay
import io.github.grepsedawk.civdiscord.velocity.patreon.PatreonSyncJob
import io.github.grepsedawk.civdiscord.velocity.snitch.SnitchRelay
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.Logger
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID
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
    private val jdaBootExecutor =
        Executors.newSingleThreadExecutor { r -> Thread(r, "civdiscord-jda-boot").apply { isDaemon = true } }

    @Volatile
    private var jda: JDA? = null
    private lateinit var bridge: BridgeServer
    private lateinit var router: BridgeMessageRouter
    private val restErrorHandler =
        ErrorHandler()
            .ignore(ErrorResponse.UNKNOWN_MEMBER, ErrorResponse.UNKNOWN_ROLE, ErrorResponse.UNKNOWN_MESSAGE)
            .handle({ true }) { t -> logger.warn("Discord REST call failed", t) }

    @Subscribe
    fun onInit(event: ProxyInitializeEvent) {
        dataDir.toFile().mkdirs()
        val cfgFile = dataDir.resolve("config.yml").toFile()
        if (!cfgFile.exists()) {
            cfgFile.writeText(this::class.java.classLoader.getResource("config.yml")!!.readText())
            tryLockdown(cfgFile.toPath())
            logger.warn("Wrote default config.yml to ${cfgFile.absolutePath} — fill it in and restart.")
            return
        }
        tryLockdown(cfgFile.toPath())
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
                val role = g.getRoleById(roleId)
                val member = resolveMember(g, discordId)
                if (role != null && member != null) {
                    g.addRoleToMember(member, role).queue(null, restErrorHandler)
                }
            }
            Unit
        }
        val removeRoleFn = { guildId: Long, discordId: Long, roleId: Long ->
            jda?.getGuildById(guildId)?.let { g ->
                val role = g.getRoleById(roleId)
                val member = resolveMember(g, discordId)
                if (role != null && member != null) {
                    g.removeRoleFromMember(member, role).queue(null, restErrorHandler)
                }
            }
            Unit
        }

        val signer = maybeLoadSigner()
        val channel = MinecraftChannelIdentifier.from(BridgeChannel.NAME)
        server.channelRegistrar.register(channel)
        // BridgeServer construction is deferred until after the handlers' dependencies
        // (chatRelay, snitchRelay, pendingHooks) are ready, so we can inject them as
        // required `val` fields on ServerInboundHandlers — missing handlers become a
        // compile error instead of a silent runtime drop.

        val granter =
            RoleGranter(
                guilds = guildsDao,
                isMemberOf = { gid, did ->
                    jda?.getGuildById(gid)?.let { resolveMember(it, did) } != null
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
        val pendingHooks =
            PendingReplies<InteractionHook>(
                ttlMillis = CONSOLE_TTL_MS,
                onExpire = { hook ->
                    runCatching {
                        hook.editOriginal("Backend did not reply within 14 minutes.")
                            .queue(null, restErrorHandler)
                    }
                },
            )
        val adminRunCmd =
            AdminRunCommand(
                backends = { server.allServers.map { it.serverInfo.name } },
                dispatch = { srv, p -> bridge.sendToServer(srv, p) },
                registerPending = { id, hook -> pendingHooks.remember(id, hook) },
                unregisterPending = { id ->
                    pendingHooks.resolve(id)
                    Unit
                },
            )

        val dispatcher =
            SlashCommandDispatcher(
                homeGuildId = config.discord.homeGuildId,
                restErrorHandler = restErrorHandler,
                link = linkCmd,
                me = meCmd,
                relay = relayCmd,
                adminUser = adminUserCmd,
                adminGuild = adminGuildCmd,
                adminRun = adminRunCmd,
                backends = { server.allServers.map { it.serverInfo.name } },
            )

        val relayWorker =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "civdiscord-relay-worker").apply { isDaemon = true }
            }
        val webhookRelay = WebhookRelay(getChannel = { id -> jda?.getTextChannelById(id) })
        val linkPrompt = LinkPrompt(openPrivateChannel = { userId -> jda?.openPrivateChannelById(userId) })

        val sendPlainToDiscord = { channelId: Long, text: String ->
            jda?.getTextChannelById(channelId)?.let { ch ->
                ch.sendMessage(text)
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .queue(null, restErrorHandler)
            }
            Unit
        }
        val chatLimiter =
            ChatRateLimiter(
                capacity = config.chat.burst.toDouble(),
                tokensPerSecond = config.chat.refillPerSecond,
            )
        val chatRelay =
            ChatRelay(
                relays = relays,
                sendToDiscord = sendPlainToDiscord,
                sendChatToDiscord = { channelId, displayName, avatarUrl, content ->
                    relayWorker.execute { webhookRelay.send(channelId, displayName, avatarUrl, content) }
                },
                sendToMc = { p -> for (srv in server.allServers) bridge.sendToServer(srv.serverInfo.name, p) },
                rateLimiter = chatLimiter,
                maxTextLength = config.chat.maxLength,
            )

        val listeners =
            listOf(
                GuildLifecycleListener(guildsDao),
                memberAddListener,
                MessageRelayListener(
                    relays = relays,
                    bindings = bindings,
                    webhook = webhookRelay,
                    linkPrompt = linkPrompt,
                    chatRelay = chatRelay,
                    worker = relayWorker,
                ),
                dispatcher,
            )
        val discordToken = config.discord.token
        val homeGuildId = config.discord.homeGuildId
        jdaBootExecutor.execute {
            val instance =
                try {
                    JdaFactory.build(discordToken, listeners).also { it.awaitReady() }
                } catch (t: Throwable) {
                    logger.error("JDA never readied — Discord features offline until restart", t)
                    return@execute
                }
            jda = instance
            for (guild in instance.guilds) {
                if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
                    logger.warn(
                        "Bot missing MANAGE_ROLES in guild {} ({}) — auth role grants will fail",
                        guild.name,
                        guild.idLong,
                    )
                }
                for (relay in relays.listForGuild(guild.idLong)) {
                    val ch = guild.getTextChannelById(relay.discordChannelId)
                    if (ch == null) {
                        logger.warn(
                            "Bound relay channel {} not visible in guild {} ({}) — relay will be inert",
                            relay.discordChannelId,
                            guild.name,
                            guild.idLong,
                        )
                        continue
                    }
                    val self = guild.selfMember
                    val missing = REQUIRED_RELAY_PERMS.filter { !self.hasPermission(ch, it) }
                    if (missing.isNotEmpty()) {
                        logger.warn(
                            "Bot missing {} on relay channel #{} ({}) in guild {} ({}) — MC↔Discord relay will degrade until granted",
                            missing.joinToString(", ") { it.name },
                            ch.name,
                            ch.idLong,
                            guild.name,
                            guild.idLong,
                        )
                    }
                }
            }
            CommandRegistrar(instance, homeGuildId).register()
            logger.info("JDA ready — Discord features online")
        }

        val snitchRelay = SnitchRelay(relays = relays, sendToDiscord = sendPlainToDiscord, logger = logger)
        val nameLayerReplyLog = io.github.grepsedawk.civdiscord.core.bridge.RateLimitedLogger(
            logger,
            TimeUnit.MINUTES.toNanos(5),
        )
        bridge = BridgeServer(
            broadcast = { target, bytes ->
                server.getServer(target).ifPresent { it.sendPluginMessage(channel, bytes) }
            },
            signer = signer,
            handlersFactory = { b ->
                io.github.grepsedawk.civdiscord.velocity.bridge.ServerInboundHandlers(
                    onSnitchHit = { hit ->
                        logger.info(
                            "BridgeServer: received SnitchHit from backend server={} group='{}' kind={} snitch='{}'",
                            hit.server,
                            hit.namelayerGroup,
                            hit.kind,
                            hit.snitchName,
                        )
                        runCatching { snitchRelay.dispatch(hit) }
                            .onFailure { logger.warn("onSnitchHit handler failed", it) }
                    },
                    onConsoleReply = { reply ->
                        pendingHooks.resolve(reply.id)?.let { hook ->
                            val text =
                                if (reply.ok) {
                                    "```\n${reply.output.take(1900)}\n```"
                                } else {
                                    "```\nERROR: ${reply.output.take(1900)}\n```"
                                }
                            hook.editOriginal(text).queue(null, restErrorHandler)
                        }
                    },
                    onLinkRequest = { req ->
                        val token = linkTokens.mint(UUID.fromString(req.mcUuid), req.mcName)
                        b.sendBroadcast(
                            Payload.LinkReply(id = req.id, token = token.code, error = null),
                            allServers = server.allServers.map { it.serverInfo.name },
                        )
                    },
                    onChatToDiscord = { chatRelay.dispatch(it) },
                    // No Velocity-side consumer of NameLayerReply yet — Paper still sends them
                    // in response to NameLayerQuery, but the Velocity plugin has never wired
                    // a query path. Surface the drop loudly instead of silently swallowing.
                    onNameLayerReply = { reply ->
                        nameLayerReplyLog.warn(
                            "Received NameLayerReply id=${reply.id} but no consumer is registered " +
                                "on the Velocity side — the namelayer query feature is unimplemented.",
                        )
                    },
                    onStatusRequest = { req ->
                        val binding =
                            runCatching { bindings.findByMcUuid(UUID.fromString(req.mcUuid)) }
                                .onFailure { logger.warn("onStatusRequest: invalid mcUuid {}", req.mcUuid, it) }
                                .getOrNull()
                        b.sendBroadcast(
                            Payload.StatusReply(
                                id = req.id,
                                discordId = binding?.discordId,
                                mcName = binding?.mcName,
                                linkedAt = binding?.linkedAt,
                            ),
                            allServers = server.allServers.map { it.serverInfo.name },
                        )
                    },
                )
            },
        )
        router = BridgeMessageRouter(bridge)

        pendingHookSweepExecutor.scheduleAtFixedRate(
            { pendingHooks.sweep() },
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

    private fun resolveMember(guild: Guild, discordId: Long): Member? = guild.getMemberById(discordId)
        ?: runCatching { guild.retrieveMemberById(discordId).complete() }.getOrNull()

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
            logger.warn(
                "Generated new bridge secret at ${secretFile.absolutePath}. " +
                    "Copy this file to plugins/civdiscord/secret.key on every Paper backend before they reconnect.",
            )
        }
        tryLockdown(secretFile.toPath())
        return BridgeSigner(secretFile.readBytes())
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (!::router.isInitialized) return
        val fromBackend = event.source is ServerConnection
        if (event.identifier.id == BridgeChannel.NAME) {
            logger.info(
                "PluginMessage on civdiscord:bridge: source={} fromBackend={} bytes={}",
                event.source?.javaClass?.simpleName,
                fromBackend,
                event.data.size,
            )
        }
        if (router.route(event.identifier.id, event.data, fromBackend)) {
            logger.debug("Routed plugin message from {} ({} bytes)", event.source, event.data.size)
            event.result = PluginMessageEvent.ForwardResult.handled()
        }
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        jdaBootExecutor.shutdownNow()
        roleGrantExecutor.shutdownNow()
        pendingHookSweepExecutor.shutdownNow()
        patreonExecutor.shutdownNow()
        jda?.shutdown()
    }

    companion object {
        private val CONSOLE_TTL_MS = TimeUnit.MINUTES.toMillis(14)
        private val REQUIRED_RELAY_PERMS =
            listOf(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_MANAGE,
                Permission.MANAGE_WEBHOOKS,
            )
    }
}
