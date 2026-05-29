package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class DiscordChatDeliveryTest {

    private val u1: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val u2: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `delivers rendered text to every member of the group`() {
        val delivered = mutableListOf<Pair<UUID, String>>()
        val delivery = DiscordChatDelivery(
            memberLookup = { if (it == "townhall") listOf(u1, u2) else null },
            sendTo = { uuid, msg -> delivered.add(uuid to msg) },
        )

        delivery.handle(
            Payload.ChatToMc(server = "*", namelayerGroup = "townhall", from = "alice", text = "hi"),
        )

        delivered.map { it.first }.toSet() shouldBe setOf(u1, u2)
        delivered.all { it.second.contains("alice") } shouldBe true
        delivered.all { it.second.contains("hi") } shouldBe true
        delivered.all { it.second.contains("[Discord]") } shouldBe true
    }

    @Test
    fun `drops the payload when memberLookup returns null (group unknown to this backend)`() {
        val delivered = mutableListOf<Pair<UUID, String>>()
        val delivery = DiscordChatDelivery(
            memberLookup = { null },
            sendTo = { uuid, msg -> delivered.add(uuid to msg) },
        )

        delivery.handle(
            Payload.ChatToMc(server = "*", namelayerGroup = "townhall", from = "alice", text = "hi"),
        )

        delivered shouldBe emptyList()
    }

    @Test
    fun `strips section color codes from from and text to prevent spoofing`() {
        val delivered = mutableListOf<Pair<UUID, String>>()
        val delivery = DiscordChatDelivery(
            memberLookup = { listOf(u1) },
            sendTo = { uuid, msg -> delivered.add(uuid to msg) },
        )

        delivery.handle(
            Payload.ChatToMc(
                server = "*",
                namelayerGroup = "townhall",
                from = "§4admin",
                text = "§clook §estaff",
            ),
        )

        // Strip color codes from the user-controlled fields before asserting — our own
        // render legitimately uses some color codes (BLUE/WHITE/GRAY/etc.) for the
        // [Discord] prefix, so we can't grep the full rendered string for raw §-sequences.
        val rendered = delivered.single().second
        val stripped = org.bukkit.ChatColor.stripColor(rendered)!!
        stripped.contains("admin") shouldBe true
        stripped.contains("look staff") shouldBe true
        // Specifically: the injected §4, §c, §e do not survive into the player-visible text.
        rendered.contains("§4admin") shouldBe false
        rendered.contains("§clook") shouldBe false
        rendered.contains("§estaff") shouldBe false
    }
}
