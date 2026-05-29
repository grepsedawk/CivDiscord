package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.junit.jupiter.api.Test
import java.util.UUID

class ChatProducerTest {

    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun mockPlayer(name: String = "alice"): Player =
        mockk<Player>().also {
            every { it.uniqueId } returns playerUuid
            every { it.name } returns name
        }

    private fun mockEvent(player: Player, message: String): AsyncPlayerChatEvent =
        mockk<AsyncPlayerChatEvent>(relaxed = true).also {
            every { it.player } returns player
            every { it.message } returns message
        }

    @Test
    fun `emits ChatToDiscord with all fields populated when player has an active group`() {
        val sent = mutableListOf<Payload.ChatToDiscord>()
        val producer = ChatProducer(
            serverName = "citadel",
            groupFor = { "townhall" },
            emit = { sent.add(it) },
        )

        producer.on(mockEvent(mockPlayer(), "hello world"))

        sent.size shouldBe 1
        sent[0].server shouldBe "citadel"
        sent[0].fromUuid shouldBe playerUuid.toString()
        sent[0].fromName shouldBe "alice"
        sent[0].namelayerGroup shouldBe "townhall"
        sent[0].text shouldBe "hello world"
    }

    @Test
    fun `drops the message when player has no active group`() {
        val sent = mutableListOf<Payload.ChatToDiscord>()
        val producer = ChatProducer(
            serverName = "citadel",
            groupFor = { null },
            emit = { sent.add(it) },
        )

        producer.on(mockEvent(mockPlayer(), "hi"))

        sent shouldBe emptyList()
    }

    @Test
    fun `on is annotated EventHandler at MONITOR priority and ignores cancelled events`() {
        val method = ChatProducer::class.java.getDeclaredMethod("on", AsyncPlayerChatEvent::class.java)
        val ann = method.getAnnotation(EventHandler::class.java)
        (ann != null) shouldBe true
        ann.priority shouldBe EventPriority.MONITOR
        ann.ignoreCancelled shouldBe true
    }
}
