package io.github.grepsedawk.civdiscord.paper.linker

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.entity.PlayerMock
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiscordCommandTest {

    @BeforeEach fun setup() {
        MockBukkit.mock()
    }

    @AfterEach fun teardown() {
        MockBukkit.unmock()
    }

    @Test
    fun `bare slash command prints help`() {
        val sent = mutableListOf<Payload>()
        val cmd = DiscordCommand(send = { sent.add(it) }, pending = PendingLinkReplies())
        val p: PlayerMock = MockBukkit.getMock()!!.addPlayer()

        cmd.onCommand(p, "discord", emptyArray())

        p.nextMessage() shouldBe "/discord link – start linking your Discord account"
        p.nextMessage() shouldBe "/discord status – show your current Discord link"
    }

    @Test
    fun `discord link sends a LinkRequest to the bridge`() {
        val sent = mutableListOf<Payload>()
        val cmd = DiscordCommand(send = { sent.add(it) }, pending = PendingLinkReplies())
        val p = MockBukkit.getMock()!!.addPlayer("alice")

        cmd.onCommand(p, "discord", arrayOf("link"))

        sent.size shouldBe 1
        (sent[0] as Payload.LinkRequest).mcName shouldBe "alice"
    }

    @Test
    fun `discord status sends a StatusRequest and remembers the requester`() {
        val sent = mutableListOf<Payload>()
        val pendingStatus = PendingStatusReplies()
        val cmd = DiscordCommand(
            send = { sent.add(it) },
            pending = PendingLinkReplies(),
            pendingStatus = pendingStatus,
        )
        val p = MockBukkit.getMock()!!.addPlayer("alice")

        cmd.onCommand(p, "discord", arrayOf("status"))

        sent.size shouldBe 1
        val req = sent[0] as Payload.StatusRequest
        req.mcUuid shouldBe p.uniqueId.toString()
        pendingStatus.resolve(
            Payload.StatusReply(req.id, discordId = null, mcName = null, linkedAt = null),
        ) shouldBe p.uniqueId
        p.nextMessage() shouldBe "§7Looking up your Discord link…"
    }
}
