package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.SeenPlayersDao
import io.github.grepsedawk.civdiscord.core.db.StatsCountersDao
import io.github.grepsedawk.civdiscord.core.stats.MetricsCache
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsServiceTest {
    private fun service(playerCount: () -> Int, cache: MetricsCache, now: () -> Long = { 2000 }): StatsService {
        val db = CivDiscordDb.inMemory()
        val seen = SeenPlayersDao(db)
        seen.recordSeen("a", 500)
        seen.recordSeen("b", 1500)
        return StatsService(
            playerCount = playerCount,
            maxPlayers = 150,
            proxyBootEpoch = 0,
            metricsCache = cache,
            counters = StatsCountersDao(db),
            seen = seen,
            metricsStaleSeconds = 90,
            now = now,
            today = { "2026-06-03" },
            startOfTodayEpoch = { 1000 },
        )
    }

    @Test
    fun `recordSample composes proxy count, cached tps, and records peaks`() {
        val cache = MetricsCache(now = { 2000 })
        cache.put(server = "citadel", tps = 19.8, online = 42, backendUptimeSeconds = 600)
        val s = service({ 42 }, cache).recordSample()
        s.playersOnline shouldBe 42
        s.maxPlayers shouldBe 150
        s.tps shouldBe 19.8
        s.backendUptimeSeconds shouldBe 600
        s.proxyUptimeSeconds shouldBe 2000
        s.peakToday shouldBe 42
        s.peakAllTime shouldBe 42
        s.uniquePlayersEver shouldBe 2L
        s.newPlayersToday shouldBe 1 // only "b" at 1500 >= 1000
    }

    @Test
    fun `snapshot does not record a peak`() {
        var online = 42
        val svc = service({ online }, MetricsCache(now = { 2000 }))
        svc.snapshot() // reads 42 but must not persist it as a peak
        online = 10
        svc.recordSample().peakAllTime shouldBe 10
    }

    @Test
    fun `stale cache yields null tps`() {
        service({ 0 }, MetricsCache(now = { 2000 })).snapshot().tps shouldBe null
    }

    @Test
    fun `cached returns the last snapshot within maxAge`() {
        val cache = MetricsCache(now = { 2000 })
        cache.put(server = "citadel", tps = 19.8, online = 42, backendUptimeSeconds = 600)
        val svc = service({ 42 }, cache)
        svc.recordSample()
        svc.cached(60).tps shouldBe 19.8
    }

    @Test
    fun `cached recomputes once the snapshot is older than maxAge`() {
        var nowT = 2000L
        var online = 5
        val svc = service({ online }, MetricsCache(now = { nowT }), now = { nowT })
        svc.snapshot() // taken at 2000
        nowT = 2100
        online = 9
        svc.cached(60).playersOnline shouldBe 9 // 100s elapsed > 60s maxAge -> recomputed
    }

    @Test
    fun `peakToday resets on a read once the day has rolled`() {
        val db = CivDiscordDb.inMemory()
        val counters = StatsCountersDao(db)
        counters.bumpPeak(80, "2026-06-02") // yesterday's peak
        val svc = StatsService(
            playerCount = { 5 },
            maxPlayers = 150,
            proxyBootEpoch = 0,
            metricsCache = MetricsCache(now = { 2000 }),
            counters = counters,
            seen = SeenPlayersDao(db),
            metricsStaleSeconds = 90,
            now = { 2000 },
            today = { "2026-06-03" },
            startOfTodayEpoch = { 1000 },
        )
        val s = svc.snapshot()
        s.peakToday shouldBe 5 // yesterday's 80 must not carry into today
        s.peakAllTime shouldBe 80 // all-time still holds
    }
}
