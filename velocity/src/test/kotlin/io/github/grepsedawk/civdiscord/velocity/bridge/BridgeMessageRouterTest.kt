package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeChannel
import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BridgeMessageRouterTest {

    @Test
    fun `route returns false for unrelated channel and does not call bridge`() {
        var handled = false
        val bridge = BridgeServer(
            broadcast = { _, _ -> },
            handlersFactory = { ServerInboundHandlers.noop().copy(onSnitchHit = { handled = true }) },
        )
        val router = BridgeMessageRouter(bridge)

        val bytes = BridgeCodec.encode(
            Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
        )

        router.route("velocity:unknown", bytes, fromBackend = true) shouldBe false
        handled shouldBe false
    }

    @Test
    fun `route returns true and delegates to bridge for civdiscord channel`() {
        var got: Payload.SnitchHit? = null
        val bridge = BridgeServer(
            broadcast = { _, _ -> },
            handlersFactory = { ServerInboundHandlers.noop().copy(onSnitchHit = { got = it }) },
        )
        val router = BridgeMessageRouter(bridge)

        val bytes = BridgeCodec.encode(
            Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
        )

        router.route(BridgeChannel.NAME, bytes, fromBackend = true) shouldBe true
        got!!.snitchName shouldBe "S"
    }

    @Test
    fun `route returns false for player-sourced frames even on the bridge channel`() {
        var handled = false
        val bridge = BridgeServer(
            broadcast = { _, _ -> },
            handlersFactory = { ServerInboundHandlers.noop().copy(onSnitchHit = { handled = true }) },
        )
        val router = BridgeMessageRouter(bridge)

        val bytes = BridgeCodec.encode(
            Payload.SnitchHit("citadel", "u1", "u2", 0, 0, 0, "S", "g", "ENTER"),
        )

        router.route(BridgeChannel.NAME, bytes, fromBackend = false) shouldBe false
        handled shouldBe false
    }
}
