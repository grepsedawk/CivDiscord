package io.github.grepsedawk.civdiscord.paper.namelayer

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class NameLayerQueryHandlerTest {

    @Test
    fun `reply lists every group the resolver returns`() {
        val sent = mutableListOf<Payload>()
        val handler = NameLayerQueryHandler(
            resolver = { _ -> listOf("townhall", "ironsworn") },
            send = sent::add,
        )
        handler.handle(Payload.NameLayerQuery("req-1", UUID.randomUUID().toString()))
        (sent[0] as Payload.NameLayerReply).linkedGroups shouldBe listOf("townhall", "ironsworn")
    }

    @Test
    fun `reply is empty when the player is in no groups`() {
        val sent = mutableListOf<Payload>()
        val handler = NameLayerQueryHandler(resolver = { emptyList() }, send = sent::add)
        handler.handle(Payload.NameLayerQuery("req-2", UUID.randomUUID().toString()))
        (sent[0] as Payload.NameLayerReply).linkedGroups shouldBe emptyList()
    }
}
