package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.Binding
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.discord.NameLayerPermService
import io.github.grepsedawk.civdiscord.velocity.discord.PermCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID

class ChatRelayTest {
    private data class ChatSend(
        val channelId: Long,
        val displayName: String,
        val avatarUrl: String,
        val content: String,
    )

    private fun stubRelay(channelId: Long, chatFormat: String? = null) = Relay(
        guildId = 100L,
        namelayerGroup = "townhall",
        discordChannelId = channelId,
        isWriter = true,
        showSnitches = false,
        chatFormat = chatFormat,
        snitchPing = null,
        createdBy = 1L,
        createdAt = 0L,
    )

    private val testUuid: UUID = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")

    private fun permissiveBindings(): BindingDao {
        val b = mockk<BindingDao>(relaxed = true)
        every { b.findByDiscordId(any()) } answers {
            Binding(discordId = firstArg(), mcUuid = testUuid, mcName = "mc", linkedAt = 0L)
        }
        return b
    }

    private fun bindingsReturning(map: Map<Long, Binding?>): BindingDao {
        val b = mockk<BindingDao>(relaxed = true)
        every { b.findByDiscordId(any()) } answers { map[firstArg()] }
        return b
    }

    private class PermissivePermService : NameLayerPermService(lookup = { _, _, _ -> PermCheck.DENIED }) {
        override fun check(mcUuid: UUID, group: String, perm: String): PermCheck = PermCheck.ALLOWED
    }

    private class FakePermService(
        private val readChat: Map<Pair<UUID, String>, Boolean> = emptyMap(),
    ) : NameLayerPermService(lookup = { _, _, _ -> PermCheck.DENIED }) {
        override fun check(mcUuid: UUID, group: String, perm: String): PermCheck = if (perm == NameLayerPermService.READ_CHAT && (readChat[mcUuid to group] ?: false)) PermCheck.ALLOWED else PermCheck.DENIED
    }

    private class UnknownPermService : NameLayerPermService(lookup = { _, _, _ -> PermCheck.UNKNOWN }) {
        override fun check(mcUuid: UUID, group: String, perm: String): PermCheck = PermCheck.UNKNOWN
    }

    private fun fixture(): Pair<ChatRelay, MutableList<ChatSend>> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        val relays = RelayDao(db)
        val sent = mutableListOf<ChatSend>()
        val relay =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
                sendToMc = { _ -> },
                unbind = { _, _ -> },
            )
        return relay to sent
    }

    private fun renderRelay(sent: MutableList<ChatSend>): ChatRelay = ChatRelay(
        relays = RelayDao(CivDiscordDb.inMemory()),
        bindings = permissiveBindings(),
        permService = PermissivePermService(),
        sendToDiscord = { _, _ -> },
        sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
        sendToMc = { _ -> },
        unbind = { _, _ -> },
    )

    @Test
    fun `MC chat fans out to every relay subscribed to that NameLayer group`() {
        val (relay, sent) = fixture()
        relay.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1001L), stubRelay(2001L)),
        )
        sent.map { it.channelId } shouldBe listOf(1001L, 2001L)
        sent.all { it.displayName.contains("alice") } shouldBe true
        sent.all { it.content == "hello" } shouldBe true
    }

    @Test
    fun `Discord chat fans out to ChatToMc payloads with the right group`() {
        val (_, _) = fixture()
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
                unbind = { _, _ -> },
            )
        r.fromDiscord(
            channelId = 1001L,
            fromDisplay = "@alice",
            fromUuid = "00000000-0000-0000-0000-000000000001",
            text = "hi",
            preComputedGroup = "townhall",
        )
        mcSent.size shouldBe 1
        mcSent[0].namelayerGroup shouldBe "townhall"
        mcSent[0].text shouldBe "hi"
        mcSent[0].fromUuid shouldBe "00000000-0000-0000-0000-000000000001"
    }

    @Test
    fun `fromDiscord on unbound channel is a no-op`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
                unbind = { _, _ -> },
            )
        r.fromDiscord(channelId = 9999L, fromDisplay = "alice", fromUuid = null, text = "hi")
        mcSent.size shouldBe 0
    }

    @Test
    fun `MC chat for unrouted group drops the message`() {
        val (relay, sent) = fixture()
        relay.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = emptyList(),
        )
        sent.isEmpty() shouldBe true
    }

    @Test
    fun `dispatch escapes Discord mentions in fromName and text`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "@everyone",
                "townhall",
                "ping me <@123>",
            ),
            preComputedRouting = listOf(stubRelay(1L)),
        )
        sent.size shouldBe 1
        sent[0].displayName.contains("@everyone") shouldBe false
        sent[0].content.contains("<@123>") shouldBe false
    }

    @Test
    fun `dispatch escapes markdown in fromName`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "*bold*name",
                "townhall",
                "hi",
            ),
            preComputedRouting = listOf(stubRelay(1L)),
        )
        sent[0].displayName.contains("\\*bold\\*") shouldBe true
    }

    @Test
    fun `dispatch derives avatar URL from fromUuid`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1L)),
        )
        sent[0].avatarUrl shouldBe "https://mc-heads.net/avatar/00000000-0000-0000-0000-000000000001/128"
    }

    @Test
    fun `dispatch uses default routing when no preComputedRouting is provided`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val sent = mutableListOf<ChatSend>()
        val r =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
                sendToMc = { _ -> },
                unbind = { _, _ -> },
            )
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
        )
        sent.size shouldBe 1
        sent[0].channelId shouldBe 1001L
        sent[0].content shouldBe "hello"
    }

    @Test
    fun `dispatch renders custom chatFormat with name server text group placeholders`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1L, chatFormat = "[{server}/{group}] <{name}> {text}")),
        )
        sent.size shouldBe 1
        sent[0].content shouldBe "[citadel/townhall] <alice> hello"
    }

    @Test
    fun `dispatch leaves unknown placeholders untouched`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1L, chatFormat = "{name}: {text} {bogus}")),
        )
        sent[0].content shouldBe "alice: hello {bogus}"
    }

    @Test
    fun `fromDiscord truncates text past maxTextLength`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
                unbind = { _, _ -> },
                maxTextLength = 8,
            )
        r.fromDiscord(
            channelId = 1L,
            fromDisplay = "alice",
            fromUuid = null,
            text = "0123456789ABCDEF",
            preComputedGroup = "townhall",
        )
        mcSent[0].text shouldBe "01234567"
    }

    @Test
    fun `fromDiscord collapses control chars to spaces`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
                unbind = { _, _ -> },
            )
        r.fromDiscord(
            channelId = 1L,
            fromDisplay = "alice",
            fromUuid = null,
            text = "hi\ntherefriend",
            preComputedGroup = "townhall",
        )
        mcSent[0].text shouldBe "hi there friend "
    }

    @Test
    fun `fromDiscord drops silently when rate-limited`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val limiter = ChatRateLimiter(capacity = 2.0, tokensPerSecond = 0.0, clock = { 0L })
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
                unbind = { _, _ -> },
                rateLimiter = limiter,
            )
        repeat(5) {
            r.fromDiscord(
                channelId = 1L,
                fromDisplay = "alice",
                fromUuid = null,
                text = "spam",
                preComputedGroup = "townhall",
                discordId = 42L,
            )
        }
        mcSent.size shouldBe 2
    }

    @Test
    fun `rate limit is per discordId not per channel`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val limiter = ChatRateLimiter(capacity = 1.0, tokensPerSecond = 0.0, clock = { 0L })
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        GuildDao(db).ensure(200L)
        val relays = RelayDao(db)
        relays.bind(100L, 1L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(200L, 2L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r =
            ChatRelay(
                relays = relays,
                bindings = permissiveBindings(),
                permService = PermissivePermService(),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
                unbind = { _, _ -> },
                rateLimiter = limiter,
            )
        r.fromDiscord(1L, "alice", null, "msg", "townhall", discordId = 42L)
        r.fromDiscord(2L, "alice", null, "msg", "townhall", discordId = 42L)
        mcSent.size shouldBe 1
    }

    @Test
    fun `unknownPlaceholders flags non-allowed names and ignores allowed ones`() {
        ChatRelay.unknownPlaceholders("<{name}> {text}") shouldBe emptyList()
        ChatRelay.unknownPlaceholders("{bogus}") shouldBe listOf("bogus")
        ChatRelay.unknownPlaceholders("{name} {bogus} {also}") shouldBe listOf("bogus", "also")
    }

    @Test
    fun `dispatch strips backticks from group name in displayName`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "weird`group",
                "hi",
            ),
            preComputedRouting = listOf(stubRelay(1L)),
        )
        sent[0].displayName.contains("`") shouldBe false
    }

    @Test
    fun `dispatch unbinds and skips when binder is not a group member`() {
        val sent = mutableListOf<ChatSend>()
        val unbound = mutableListOf<Long>()
        val r = ChatRelay(
            relays = RelayDao(CivDiscordDb.inMemory()),
            bindings = permissiveBindings(),
            permService = FakePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
            sendToMc = { _ -> },
            unbind = { ch, _ -> unbound.add(ch) },
        )
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1001L)),
        )
        sent.isEmpty() shouldBe true
        unbound shouldBe listOf(1001L)
    }

    @Test
    fun `dispatch unbinds and skips when binder has no MC binding`() {
        val sent = mutableListOf<ChatSend>()
        val unbound = mutableListOf<Long>()
        val r = ChatRelay(
            relays = RelayDao(CivDiscordDb.inMemory()),
            bindings = bindingsReturning(emptyMap()),
            permService = PermissivePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
            sendToMc = { _ -> },
            unbind = { ch, _ -> unbound.add(ch) },
        )
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1001L)),
        )
        sent.isEmpty() shouldBe true
        unbound shouldBe listOf(1001L)
    }

    @Test
    fun `dispatch keeps the binding and skips when the perm check is UNKNOWN`() {
        val sent = mutableListOf<ChatSend>()
        val unbound = mutableListOf<Long>()
        val r = ChatRelay(
            relays = RelayDao(CivDiscordDb.inMemory()),
            bindings = permissiveBindings(),
            permService = UnknownPermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
            sendToMc = { _ -> },
            unbind = { ch, _ -> unbound.add(ch) },
        )
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hello",
            ),
            preComputedRouting = listOf(stubRelay(1001L)),
        )
        sent.isEmpty() shouldBe true
        unbound.isEmpty() shouldBe true
    }

    @Test
    fun `fromDiscord keeps the binding and drops the message when the perm check is UNKNOWN`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val unbound = mutableListOf<Long>()
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = UnknownPermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { _, _, _, _ -> },
            sendToMc = { mcSent.add(it) },
            unbind = { ch, _ -> unbound.add(ch) },
        )
        r.fromDiscord(
            channelId = 1001L,
            fromDisplay = "alice",
            fromUuid = null,
            text = "hi",
            preComputedGroup = "townhall",
        )
        mcSent.isEmpty() shouldBe true
        unbound.isEmpty() shouldBe true
    }

    @Test
    fun `fromDiscord unbinds and skips when binder is not a group member`() {
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val unbound = mutableListOf<Long>()
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = FakePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { _, _, _, _ -> },
            sendToMc = { mcSent.add(it) },
            unbind = { ch, _ -> unbound.add(ch) },
        )
        r.fromDiscord(
            channelId = 1001L,
            fromDisplay = "alice",
            fromUuid = null,
            text = "hi",
            preComputedGroup = "townhall",
        )
        mcSent.isEmpty() shouldBe true
        unbound shouldBe listOf(1001L)
    }

    @Test
    fun `fromDiscord rejects when no writer and calls onWriterless`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = false, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 1001L, "tavern", isWriter = false, showSnitches = false, createdBy = 5L)
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val warned = mutableListOf<Pair<Long, List<String>>>()
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = PermissivePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { _, _, _, _ -> },
            sendToMc = { mcSent += it },
            unbind = { _, _ -> },
            onWriterless = { ch, bound -> warned += ch to bound },
        )
        r.fromDiscord(channelId = 1001L, fromDisplay = "alex", fromUuid = null, text = "hi")
        mcSent shouldBe emptyList()
        warned.map { it.first } shouldBe listOf(1001L)
        warned[0].second.toSet() shouldBe setOf("townhall", "tavern")
    }

    @Test
    fun `fromDiscord delivers via writer when present`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 1001L, "tavern", isWriter = false, showSnitches = false, createdBy = 5L)
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = PermissivePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { _, _, _, _ -> },
            sendToMc = { mcSent += it },
            unbind = { _, _ -> },
        )
        r.fromDiscord(channelId = 1001L, fromDisplay = "alex", fromUuid = null, text = "hi")
        mcSent.map { it.namelayerGroup } shouldBe listOf("townhall")
    }

    @Test
    fun `MC to Discord fans out across channels bound to that group`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 2001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 2001L, "tavern", isWriter = false, showSnitches = false, createdBy = 5L)
        val sentByChannel = mutableListOf<Long>()
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = PermissivePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { ch, _, _, _ -> sentByChannel += ch },
            sendToMc = { _ -> },
            unbind = { _, _ -> },
        )
        r.dispatch(Payload.ChatToDiscord("citadel", "00000000-0000-0000-0000-000000000001", "alice", "townhall", "hello"))
        sentByChannel.sorted() shouldBe listOf(1001L, 2001L)
    }

    @Test
    fun `Discord input fans out to other channels bound to the writer group`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 2001L, "townhall", isWriter = false, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 2002L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val sentByChannel = mutableListOf<Long>()
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = PermissivePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { ch, _, _, _ -> sentByChannel += ch },
            sendToMc = { mcSent += it },
            unbind = { _, _ -> },
        )
        r.fromDiscord(channelId = 1001L, fromDisplay = "alex", fromUuid = "00000000-0000-0000-0000-000000000001", text = "hi")
        // MC got the message once.
        mcSent.map { it.namelayerGroup } shouldBe listOf("townhall")
        // The other two channels bound to townhall received the fan-out; the originator did not.
        sentByChannel.sorted() shouldBe listOf(2001L, 2002L)
    }

    @Test
    fun `Discord input does NOT fan out when rate limiter rejects`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 2001L, "townhall", isWriter = false, showSnitches = false, createdBy = 5L)
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val sentByChannel = mutableListOf<Long>()
        val limiter = ChatRateLimiter(capacity = 0.0, tokensPerSecond = 0.0)
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = PermissivePermService(),
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { ch, _, _, _ -> sentByChannel += ch },
            sendToMc = { mcSent += it },
            unbind = { _, _ -> },
            rateLimiter = limiter,
        )
        r.fromDiscord(channelId = 1001L, fromDisplay = "alex", fromUuid = null, text = "hi", discordId = 42L)
        mcSent shouldBe emptyList()
        sentByChannel shouldBe emptyList()
    }

    @Test
    fun `binding without READ_CHAT is unbound by (channel, group), not whole channel`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 1001L, "tavern", isWriter = false, showSnitches = false, createdBy = 6L)
        val unbinds = mutableListOf<Pair<Long, String>>()
        val perms = FakePermService(readChat = mapOf(testUuid to "townhall" to true))
        val r = ChatRelay(
            relays = relays,
            bindings = permissiveBindings(),
            permService = perms,
            sendToDiscord = { _, _ -> },
            sendChatToDiscord = { _, _, _, _ -> },
            sendToMc = { _ -> },
            unbind = { ch, g -> unbinds += ch to g },
        )
        r.dispatch(Payload.ChatToDiscord("citadel", "00000000-0000-0000-0000-000000000001", "alice", "tavern", "hi"))
        unbinds shouldBe listOf(1001L to "tavern")
    }
}
