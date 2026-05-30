package io.github.grepsedawk.civdiscord.paper.linker

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.entity.PlayerMock
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.github.grepsedawk.civdiscord.core.bridge.PendingReplies
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class DiscordCommandTest {

    private fun linkReplies() = PendingReplies<UUID>(ttlMillis = TimeUnit.MINUTES.toMillis(5))
    private fun statusReplies() = PendingReplies<UUID>(ttlMillis = TimeUnit.SECONDS.toMillis(30))

    @BeforeEach fun setup() {
        MockBukkit.mock()
    }

    @AfterEach fun teardown() {
        MockBukkit.unmock()
    }

    @Test
    fun `bare slash command prints help`() {
        val sent = mutableListOf<Payload>()
        val cmd = DiscordCommand(send = { sent.add(it) }, pending = linkReplies(), pendingStatus = statusReplies())
        val p: PlayerMock = MockBukkit.getMock()!!.addPlayer()

        cmd.onCommand(p, "discord", emptyArray())

        p.nextMessage() shouldBe "/discord link – start linking your Discord account"
        p.nextMessage() shouldBe "/discord status – show your current Discord link"
    }

    @Test
    fun `discord link sends a LinkRequest to the bridge`() {
        val sent = mutableListOf<Payload>()
        val cmd = DiscordCommand(send = { sent.add(it) }, pending = linkReplies(), pendingStatus = statusReplies())
        val p = MockBukkit.getMock()!!.addPlayer("alice")

        cmd.onCommand(p, "discord", arrayOf("link"))

        sent.size shouldBe 1
        (sent[0] as Payload.LinkRequest).mcName shouldBe "alice"
    }

    @Test
    fun `link request sweep past ack deadline fires onExpire with the requester uuid`() {
        var now = 1_000L
        val expired = mutableListOf<UUID>()
        val ackDeadline = TimeUnit.SECONDS.toMillis(20)
        val pending = PendingReplies<UUID>(
            ttlMillis = ackDeadline,
            clock = { now },
            onExpire = { expired.add(it) },
        )
        val cmd = DiscordCommand(send = { }, pending = pending, pendingStatus = statusReplies())
        val p = MockBukkit.getMock()!!.addPlayer("alice")

        cmd.onCommand(p, "discord", arrayOf("link"))
        now += ackDeadline + 1
        pending.sweep()

        expired shouldBe listOf(p.uniqueId)
    }

    @Test
    fun `discord status sends a StatusRequest and remembers the requester`() {
        val sent = mutableListOf<Payload>()
        val pendingStatus = statusReplies()
        val cmd = DiscordCommand(
            send = { sent.add(it) },
            pending = linkReplies(),
            pendingStatus = pendingStatus,
        )
        val p = MockBukkit.getMock()!!.addPlayer("alice")

        cmd.onCommand(p, "discord", arrayOf("status"))

        sent.size shouldBe 1
        val req = sent[0] as Payload.StatusRequest
        req.mcUuid shouldBe p.uniqueId.toString()
        pendingStatus.resolve(req.id) shouldBe p.uniqueId
        p.nextMessage() shouldBe "§7Looking up your Discord link…"
    }
}
