package io.github.grepsedawk.civdiscord.core.stats

enum class Health { HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN }

data class StatsSnapshot(
    val playersOnline: Int,
    val maxPlayers: Int,
    val tps: Double?,
    val tpsAgeSeconds: Long?,
    val backendUptimeSeconds: Long?,
    val proxyUptimeSeconds: Long,
    val peakToday: Int,
    val peakAllTime: Int,
    val uniquePlayersEver: Long,
    val newPlayersToday: Int,
    val takenAtEpoch: Long,
) {
    fun health(): Health = when {
        tps == null -> Health.UNKNOWN
        tps >= 19.5 -> Health.HEALTHY
        tps >= 18.0 -> Health.DEGRADED
        else -> Health.UNHEALTHY
    }

    fun uptimeSeconds(): Long = backendUptimeSeconds ?: proxyUptimeSeconds
}
