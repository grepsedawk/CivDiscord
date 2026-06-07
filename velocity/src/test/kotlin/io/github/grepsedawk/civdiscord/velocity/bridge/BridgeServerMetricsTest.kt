package io.github.grepsedawk.civdiscord.velocity.bridge

import io.github.grepsedawk.civdiscord.core.bridge.BridgeCodec
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BridgeServerMetricsTest {
    @Test
    fun `incoming ServerMetrics reaches the handler`() {
        var received: Payload.ServerMetrics? = null
        val server = BridgeServer(
            handlersFactory = {
                ServerInboundHandlers.noop().copy(onServerMetrics = { received = it })
            },
        )
        val frame = BridgeCodec.encode(
            Payload.ServerMetrics("citadel", 19.8, 42, 600),
        )
        server.handleIncoming(frame)
        received!!.onlineOnBackend shouldBe 42
    }
}
