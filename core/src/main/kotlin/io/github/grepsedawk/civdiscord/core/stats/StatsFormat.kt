package io.github.grepsedawk.civdiscord.core.stats

import java.util.Locale

object StatsFormat {
    const val NO_VALUE = "—" // em dash

    fun uptime(seconds: Long): String {
        val d = seconds / 86_400
        val h = (seconds % 86_400) / 3_600
        val m = (seconds % 3_600) / 60
        return when {
            d > 0 -> "${d}d ${h}h ${m}m"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun tps(tps: Double?): String = tps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: NO_VALUE
}
