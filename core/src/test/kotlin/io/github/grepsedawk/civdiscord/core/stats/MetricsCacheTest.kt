package io.github.grepsedawk.civdiscord.core.stats

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MetricsCacheTest {
    @Test
    fun `read returns null tps before anything is put`() {
        val cache = MetricsCache(now = { 100 })
        cache.read(staleAfterSeconds = 90).tps shouldBe null
    }

    @Test
    fun `fresh reading is returned`() {
        var t = 100L
        val cache = MetricsCache(now = { t })
        cache.put(server = "citadel", tps = 19.8, online = 5, backendUptimeSeconds = 60)
        t = 150
        val r = cache.read(staleAfterSeconds = 90)
        r.tps shouldBe 19.8
        r.ageSeconds shouldBe 50
        r.backendUptimeSeconds shouldBe 60
    }

    @Test
    fun `stale reading reports null tps and null uptime but keeps age`() {
        var t = 100L
        val cache = MetricsCache(now = { t })
        cache.put(server = "citadel", tps = 19.8, online = 5, backendUptimeSeconds = 60)
        t = 300
        val r = cache.read(staleAfterSeconds = 90)
        r.tps shouldBe null
        r.ageSeconds shouldBe 200
        r.backendUptimeSeconds shouldBe null
    }

    @Test
    fun `reports the busiest backend's tps and uptime together, not the last writer`() {
        val cache = MetricsCache(now = { 100 })
        cache.put(server = "events", tps = 12.0, online = 2, backendUptimeSeconds = 30)
        cache.put(server = "citadel", tps = 19.9, online = 40, backendUptimeSeconds = 600)
        val r = cache.read(staleAfterSeconds = 90)
        r.tps shouldBe 19.9 // citadel has the most players
        r.backendUptimeSeconds shouldBe 600 // and its own uptime, consistent with its tps
    }

    @Test
    fun `age describes the busiest backend, not the freshest`() {
        var t = 100L
        val cache = MetricsCache(now = { t })
        cache.put(server = "citadel", tps = 19.9, online = 40, backendUptimeSeconds = 600) // busy, at t=100
        t = 150
        cache.put(server = "events", tps = 20.0, online = 1, backendUptimeSeconds = 30) // idle, fresher, at t=150
        t = 160
        val r = cache.read(staleAfterSeconds = 90)
        r.tps shouldBe 19.9 // citadel (most players)
        r.ageSeconds shouldBe 60 // citadel's age (160-100), not events' 10
    }

    @Test
    fun `ties on player count resolve deterministically to the lower tps`() {
        val cache = MetricsCache(now = { 100 })
        cache.put(server = "a", tps = 19.9, online = 20, backendUptimeSeconds = 100)
        cache.put(server = "b", tps = 14.0, online = 20, backendUptimeSeconds = 200)
        cache.read(staleAfterSeconds = 90).tps shouldBe 14.0 // tie -> surface the laggier backend
    }

    @Test
    fun `an emptied backend's stale lag reading does not drag tps down`() {
        var t = 100L
        val cache = MetricsCache(now = { t })
        cache.put(server = "events", tps = 5.0, online = 1, backendUptimeSeconds = 30) // lag spike, then emptied
        t = 300 // events stopped publishing -> its reading is now stale
        cache.put(server = "citadel", tps = 19.9, online = 40, backendUptimeSeconds = 600)
        cache.read(staleAfterSeconds = 90).tps shouldBe 19.9
    }
}
