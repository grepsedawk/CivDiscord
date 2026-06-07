package io.github.grepsedawk.civdiscord.core.stats

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsSnapshotTest {
    private fun snap(tps: Double?, citadelUptime: Long? = null) = StatsSnapshot(
        playersOnline = 1, maxPlayers = 150, tps = tps, tpsAgeSeconds = 0,
        backendUptimeSeconds = citadelUptime, proxyUptimeSeconds = 99,
        peakToday = 1, peakAllTime = 1, uniquePlayersEver = 1, newPlayersToday = 0, takenAtEpoch = 0,
    )

    @Test fun `null tps is UNKNOWN`() {
        snap(null).health() shouldBe Health.UNKNOWN
    }

    @Test fun `20 tps is HEALTHY`() {
        snap(20.0).health() shouldBe Health.HEALTHY
    }

    @Test fun `19_5 tps is HEALTHY`() {
        snap(19.5).health() shouldBe Health.HEALTHY
    }

    @Test fun `18_5 tps is DEGRADED`() {
        snap(18.5).health() shouldBe Health.DEGRADED
    }

    @Test fun `17 tps is UNHEALTHY`() {
        snap(17.0).health() shouldBe Health.UNHEALTHY
    }

    @Test fun `uptime prefers citadel over proxy`() {
        snap(20.0, citadelUptime = 5).uptimeSeconds() shouldBe 5
    }

    @Test fun `uptime falls back to proxy when citadel null`() {
        snap(20.0, null).uptimeSeconds() shouldBe 99
    }
}
