package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.db.SeenPlayersDao
import io.github.grepsedawk.civdiscord.core.db.StatsCountersDao
import io.github.grepsedawk.civdiscord.core.stats.MetricsCache
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Composes a StatsSnapshot from proxy reads + the bridged metrics cache + the counter DAOs.
 *  snapshot() is a pure read; recordSample() additionally records a peak sample and is the only
 *  writer — call it from the (single-threaded) tick, never from command threads. */
class StatsService(
    private val playerCount: () -> Int,
    private val maxPlayers: Int,
    private val proxyBootEpoch: Long,
    private val metricsCache: MetricsCache,
    private val counters: StatsCountersDao,
    private val seen: SeenPlayersDao,
    private val metricsStaleSeconds: Long,
    private val now: () -> Long = { Instant.now().epochSecond },
    private val today: () -> String = { LocalDate.now(ZoneOffset.UTC).toString() },
    private val startOfTodayEpoch: () -> Long = { LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toEpochSecond() },
) {
    @Volatile
    private var latest: StatsSnapshot? = null

    /** Pure read — no DB writes — so it is safe to call from the JDA command thread. */
    fun snapshot(): StatsSnapshot {
        val online = playerCount()
        val nowEpoch = now()
        val resolved = metricsCache.read(metricsStaleSeconds)
        val c = counters.get()
        // The date-rollover reset lives in bumpPeak (tick-only); apply it on read too so a snapshot
        // taken after UTC midnight — or any read while ticks are disabled — doesn't show a stale day.
        val peakToday = when {
            c == null -> online
            c.peakTodayDate != today() -> online
            else -> c.peakToday
        }
        val snap = StatsSnapshot(
            playersOnline = online,
            maxPlayers = maxPlayers,
            tps = resolved.tps,
            tpsAgeSeconds = resolved.ageSeconds,
            backendUptimeSeconds = resolved.backendUptimeSeconds,
            proxyUptimeSeconds = nowEpoch - proxyBootEpoch,
            peakToday = peakToday,
            peakAllTime = maxOf(c?.peakAllTime ?: online, online),
            uniquePlayersEver = seen.uniqueCount(),
            newPlayersToday = seen.countSeenOnOrAfter(startOfTodayEpoch()),
            takenAtEpoch = nowEpoch,
        )
        latest = snap
        return snap
    }

    /** Tick-only: record a peak sample, then return a fresh snapshot reflecting it. */
    fun recordSample(): StatsSnapshot {
        counters.bumpPeak(playerCount(), today())
        return snapshot()
    }

    /** Cheap read-only reply for /status: the last tick's snapshot, recomputed only if it's older
     *  than [maxAgeSeconds] (so it never goes stale-forever when the stats ticks are disabled). */
    fun cached(maxAgeSeconds: Long): StatsSnapshot {
        val last = latest
        if (last != null && now() - last.takenAtEpoch < maxAgeSeconds) return last
        return snapshot()
    }
}
