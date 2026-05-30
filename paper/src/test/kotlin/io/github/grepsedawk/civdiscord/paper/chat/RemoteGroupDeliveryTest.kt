package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Test
import java.util.UUID

class RemoteGroupDeliveryTest {

    private val senderUuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")

    private fun delivery(
        lookup: (String) -> Any?,
        disciplined: (Any) -> Boolean = { false },
        sendRemote: ((UUID, String, Component, String, Component) -> Unit) =
            { _, _, _, _, _ -> },
        senderUuidFor: (Payload.ChatToMc) -> UUID = { senderUuid },
    ): RemoteGroupDelivery = RemoteGroupDelivery(
        groupLookup = lookup,
        isDisciplined = disciplined,
        sendRemote = { a, b, c, d, e -> sendRemote(a, b, c, d, e) },
        onMainThread = { it.run() },
        senderUuidFor = senderUuidFor,
    )

    private fun msg(
        group: String = "grepsedawk",
        text: String = "hi",
        from: String = "grepsedawk",
        fromUuid: String? = null,
    ) = Payload.ChatToMc(
        server = "*",
        namelayerGroup = group,
        from = from,
        text = text,
        fromUuid = fromUuid,
    )

    @Test
    fun `unknown group is dropped`() {
        var called = false
        val d = delivery(lookup = { null }, sendRemote = { _, _, _, _, _ -> called = true })
        d.handle(msg())
        called shouldBe false
    }

    @Test
    fun `disciplined group is dropped`() {
        var called = false
        val g = Any()
        val d = delivery(
            lookup = { g },
            disciplined = { true },
            sendRemote = { _, _, _, _, _ -> called = true },
        )
        d.handle(msg())
        called shouldBe false
    }

    @Test
    fun `known group sends with correct arg order`() {
        // args: (senderId, senderName, senderDisplayName, groupName, message)
        val captured = mutableListOf<List<Any>>()
        val g = Any()
        val d = delivery(
            lookup = { g },
            sendRemote = { id, name, displayName, group, message ->
                captured += listOf(id, name, displayName, group, message)
            },
        )
        d.handle(msg(group = "grepsedawk", text = "hello", from = "grepsedawk"))
        captured.size shouldBe 1
        captured[0][0] shouldBe senderUuid // senderId
        captured[0][1] shouldBe "grepsedawk" // senderName
        captured[0][2] shouldBe Component.text("[Discord] ").append(Component.text("grepsedawk")) // senderDisplayName
        captured[0][3] shouldBe "grepsedawk" // groupName
        captured[0][4] shouldBe Component.text("hello") // message
    }

    @Test
    fun `senderUuidFor receives the ChatToMc so it can prefer fromUuid`() {
        val wireUuid = "11111111-1111-1111-1111-111111111111"
        val seen = mutableListOf<String?>()
        val g = Any()
        val d = delivery(
            lookup = { g },
            senderUuidFor = { msg ->
                seen += msg.fromUuid
                UUID.fromString(msg.fromUuid)
            },
        )
        d.handle(msg(fromUuid = wireUuid))
        seen shouldBe listOf(wireUuid)
    }
}
