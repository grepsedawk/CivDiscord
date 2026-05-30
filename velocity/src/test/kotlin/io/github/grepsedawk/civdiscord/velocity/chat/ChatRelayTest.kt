package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChatRelayTest {
    private data class ChatSend(
        val channelId: Long,
        val displayName: String,
        val avatarUrl: String,
        val content: String,
    )

    private fun stubRelay(channelId: Long, chatFormat: String? = null) = Relay(
        id = channelId,
        guildId = 100L,
        namelayerGroup = "townhall",
        discordChannelId = channelId,
        showSnitches = false,
        chatFormat = chatFormat,
        createdBy = 1L,
        createdAt = 0L,
    )

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
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
                sendToMc = { _ -> },
            )
        return relay to sent
    }

    private fun renderRelay(sent: MutableList<ChatSend>): ChatRelay = ChatRelay(
        relays = RelayDao(CivDiscordDb.inMemory()),
        sendToDiscord = { _, _ -> },
        sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
        sendToMc = { _ -> },
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
        sent.all { it.content == "**alice** [`citadel`]: hello" } shouldBe true
    }

    @Test
    fun `Discord chat fans out to ChatToMc payloads with the right group`() {
        val (_, _) = fixture()
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
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
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
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
        sent[0].avatarUrl shouldBe "https://mc-heads.net/head/00000000-0000-0000-0000-000000000001/128"
    }

    @Test
    fun `dispatch uses default routing when no preComputedRouting is provided`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", 5L)
        val sent = mutableListOf<ChatSend>()
        val r =
            ChatRelay(
                relays = relays,
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { ch, name, avatar, content -> sent.add(ChatSend(ch, name, avatar, content)) },
                sendToMc = { _ -> },
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
        sent[0].content shouldBe "**alice** [`citadel`]: hello"
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
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
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
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
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
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
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
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                sendToDiscord = { _, _ -> },
                sendChatToDiscord = { _, _, _, _ -> },
                sendToMc = { mcSent.add(it) },
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
}
