package io.github.grepsedawk.civdiscord.core.stats

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Latest TPS/online/uptime each Paper backend reported over the bridge, keyed by server name.
 *  Written from the bridge thread, read from the stats tick thread — a ConcurrentHashMap carries
 *  the cross-thread visibility. read() reports the TPS + uptime of the backend the most players are
 *  on, so an idle or just-restarted secondary backend can't skew the headline number, and so the
 *  TPS and uptime always describe the same backend. */
class MetricsCache(private val now: () -> Long = { Instant.now().epochSecond }) {
    private data class Reading(val tps: Double, val online: Int, val backendUptimeSeconds: Long, val atEpoch: Long)

    private val byServer = ConcurrentHashMap<String, Reading>()

    data class Resolved(val tps: Double?, val ageSeconds: Long?, val backendUptimeSeconds: Long?)

    fun put(server: String, tps: Double, online: Int, backendUptimeSeconds: Long) {
        byServer[server] = Reading(tps, online, backendUptimeSeconds, now())
    }

    fun read(staleAfterSeconds: Long): Resolved {
        val nowEpoch = now()
        // Bound growth if backend names ever churn (renames/ephemeral servers): drop long-dead readings.
        byServer.values.removeIf { nowEpoch - it.atEpoch > staleAfterSeconds * EVICT_FACTOR }
        val readings = byServer.values.toList()
        if (readings.isEmpty()) return Resolved(null, null, null)
        val minAge = readings.minOf { nowEpoch - it.atEpoch }
        val fresh = readings.filter { nowEpoch - it.atEpoch <= staleAfterSeconds }
        // Busiest backend; ties broken toward lower TPS then higher uptime, so the pick is fully deterministic.
        val primary = fresh.maxWithOrNull(compareBy({ it.online }, { -it.tps }, { it.backendUptimeSeconds }))
            ?: return Resolved(null, minAge, null)
        return Resolved(tps = primary.tps, ageSeconds = nowEpoch - primary.atEpoch, backendUptimeSeconds = primary.backendUptimeSeconds)
    }

    private companion object {
        const val EVICT_FACTOR = 10
    }
}
