package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import vg.civcraft.mc.civchat2.event.GroupChatEvent
import java.util.UUID

class GroupChatProducerTest {

    @Test
    fun `emits ChatToDiscord with right fields`() {
        val uuid = UUID.randomUUID()
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns uuid
        every { player.name } returns "grepsedawk"
        // GroupChatEvent.getGroup() returns the group NAME as a String, not a Group object.
        // Verified via javap on the vendored civchat2.jar; constructor signature is
        // GroupChatEvent(Player, String, String).
        val event = mockk<GroupChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.group } returns "grepsedawk"
        every { event.message } returns "hello"
        every { event.isCancelled } returns false

        val emitted = mutableListOf<Payload.ChatToDiscord>()
        GroupChatProducer(serverName = "citadel", emit = emitted::add).on(event)

        emitted shouldBe listOf(
            Payload.ChatToDiscord(
                server = "citadel",
                fromUuid = uuid.toString(),
                fromName = "grepsedawk",
                namelayerGroup = "grepsedawk",
                text = "hello",
            ),
        )
    }
}
