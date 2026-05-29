package io.github.grepsedawk.civdiscord.velocity.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChatRelayTest {
    private fun fixture(): Pair<ChatRelay, MutableList<Pair<Long, String>>> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        val relays = RelayDao(db)
        val sent = mutableListOf<Pair<Long, String>>()
        val relay =
            ChatRelay(
                relays = relays,
                sendToDiscord = { ch, txt -> sent.add(ch to txt) },
                sendToMc = { _ -> },
            )
        return relay to sent
    }

    private fun renderRelay(sent: MutableList<Pair<Long, String>>): ChatRelay =
        ChatRelay(
            relays = RelayDao(CivDiscordDb.inMemory()),
            sendToDiscord = { ch, txt -> sent.add(ch to txt) },
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
        sent.map { it.first } shouldBe listOf(1001L, 2001L)
        sent.all { it.second.contains("alice") } shouldBe true
    }

    @Test
    fun `Discord chat fans out to ChatToMc payloads with the right group`() {
        val (_, _) = fixture()
        val mcSent = mutableListOf<Payload.ChatToMc>()
        val r =
            ChatRelay(
                relays = RelayDao(CivDiscordDb.inMemory()),
                sendToDiscord = { _, _ -> },
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
    fun `render escapes Discord mentions in fromName and text`() {
        val sent = mutableListOf<Pair<Long, String>>()
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
        val rendered = sent[0].second
        rendered.contains("@everyone") shouldBe false
        rendered.contains("<@123>") shouldBe false
    }

    @Test
    fun `render escapes markdown in fromName`() {
        val sent = mutableListOf<Pair<Long, String>>()
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
        val rendered = sent[0].second
        rendered.contains("\\*bold\\*") shouldBe true
        rendered.contains("**bold*") shouldBe false
    }

    @Test
    fun `chatFormat null falls back to default format`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", 5L)
        val sent = mutableListOf<Pair<Long, String>>()
        val r =
            ChatRelay(
                relays = relays,
                sendToDiscord = { ch, txt -> sent.add(ch to txt) },
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
        sent[0].second shouldBe "**alice** [`citadel`]: hello"
    }

    @Test
    fun `chatFormat custom template renders with sanitized placeholders`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", 5L)
        relays.setChatFormat(1001L, "<{name}> {text}")
        val sent = mutableListOf<Pair<Long, String>>()
        val r =
            ChatRelay(
                relays = relays,
                sendToDiscord = { ch, txt -> sent.add(ch to txt) },
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
        sent[0].second shouldBe "<alice> hello"
    }

    @Test
    fun `chatFormat with backtick payload values still strips backticks`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val relays = RelayDao(db)
        relays.bind(100L, 1001L, "townhall", 5L)
        relays.setChatFormat(1001L, "<{name}>: {text}")
        val sent = mutableListOf<Pair<Long, String>>()
        val r =
            ChatRelay(
                relays = relays,
                sendToDiscord = { ch, txt -> sent.add(ch to txt) },
                sendToMc = { _ -> },
            )
        r.dispatch(
            Payload.ChatToDiscord(
                "citadel",
                "00000000-0000-0000-0000-000000000001",
                "weird`name",
                "townhall",
                "x`y",
            ),
        )
        sent.size shouldBe 1
        sent[0].second.contains("`") shouldBe false
    }

    @Test
    fun `unknownPlaceholders flags non-allowed names and ignores allowed ones`() {
        ChatRelay.unknownPlaceholders("<{name}> {text}") shouldBe emptyList()
        ChatRelay.unknownPlaceholders("{bogus}") shouldBe listOf("bogus")
        ChatRelay.unknownPlaceholders("{name} {bogus} {also}") shouldBe listOf("bogus", "also")
    }

    @Test
    fun `render strips backticks from server name`() {
        val sent = mutableListOf<Pair<Long, String>>()
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
        val rendered = sent[0].second
        val serverPortion = rendered.substringAfter("[`").substringBefore("`]")
        serverPortion.contains("`") shouldBe false
    }
}
