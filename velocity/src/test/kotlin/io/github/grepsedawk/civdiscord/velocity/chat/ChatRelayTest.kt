package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
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

    private fun renderRelay(sent: MutableList<ChatSend>): ChatRelay =
        ChatRelay(
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
            preComputedRouting = listOf(1001L, 2001L),
        )
        sent.map { it.channelId } shouldBe listOf(1001L, 2001L)
        sent.all { it.displayName.contains("alice") } shouldBe true
        sent.all { it.content == "hello" } shouldBe true
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
            text = "hi",
            preComputedGroup = "townhall",
        )
        mcSent.size shouldBe 1
        mcSent[0].namelayerGroup shouldBe "townhall"
        mcSent[0].text shouldBe "hi"
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
        r.fromDiscord(channelId = 9999L, fromDisplay = "alice", text = "hi")
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
            preComputedRouting = listOf(1L),
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
            preComputedRouting = listOf(1L),
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
            preComputedRouting = listOf(1L),
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
        sent[0].content shouldBe "hello"
    }

    @Test
    fun `unknownPlaceholders flags non-allowed names and ignores allowed ones`() {
        ChatRelay.unknownPlaceholders("<{name}> {text}") shouldBe emptyList()
        ChatRelay.unknownPlaceholders("{bogus}") shouldBe listOf("bogus")
        ChatRelay.unknownPlaceholders("{name} {bogus} {also}") shouldBe listOf("bogus", "also")
    }

    @Test
    fun `dispatch strips backticks from server name in displayName`() {
        val sent = mutableListOf<ChatSend>()
        val r = renderRelay(sent)
        r.dispatch(
            Payload.ChatToDiscord(
                "weird`name",
                "00000000-0000-0000-0000-000000000001",
                "alice",
                "townhall",
                "hi",
            ),
            preComputedRouting = listOf(1L),
        )
        sent[0].displayName.contains("`") shouldBe false
    }
}
