package io.github.grepsedawk.civdiscord.paper.metrics

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MetricsPublisherTest {
    @Test
    fun `builds ServerMetrics from suppliers`() {
        val sent = mutableListOf<Payload>()
        val pub = MetricsPublisher(
            serverName = "citadel",
            tps = { doubleArrayOf(19.84, 19.9, 20.0) },
            onlineCount = { 42 },
            uptimeSeconds = { 11_520 },
            send = { sent += it },
        )
        pub.tick()
        val m = sent.single() as Payload.ServerMetrics
        m.server shouldBe "citadel"
        m.tps1m shouldBe 19.84
        m.onlineOnBackend shouldBe 42
        m.backendUptimeSeconds shouldBe 11_520
    }

    @Test
    fun `skips send when the server is empty (no carrier player)`() {
        val sent = mutableListOf<Payload>()
        val pub = MetricsPublisher(
            serverName = "citadel",
            tps = { doubleArrayOf(20.0, 20.0, 20.0) },
            onlineCount = { 0 },
            uptimeSeconds = { 1 },
            send = { sent += it },
        )
        pub.tick()
        sent.firstOrNull().shouldBeNull()
    }

    @Test
    fun `skips send when no tps reading is available`() {
        val sent = mutableListOf<Payload>()
        val pub = MetricsPublisher(
            serverName = "citadel",
            tps = { doubleArrayOf() },
            onlineCount = { 5 },
            uptimeSeconds = { 100 },
            send = { sent += it },
        )
        pub.tick()
        sent.firstOrNull().shouldBeNull()
    }
}
