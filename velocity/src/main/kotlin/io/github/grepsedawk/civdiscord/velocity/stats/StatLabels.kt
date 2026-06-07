package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.stats.StatsFormat
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot

/** Plain-text labels for the rate-limited channel surfaces. Topics cannot render <t:..> tokens
 *  (only messages/embeds can), so the topic carries a literal UTC time instead. */
object StatLabels {
    fun players(s: StatsSnapshot): String {
        val count = if (s.maxPlayers > 0) "${s.playersOnline}/${s.maxPlayers}" else "${s.playersOnline}"
        return "🟢 $count online"
    }

    fun tps(s: StatsSnapshot): String = "⚡ TPS ${StatsFormat.tps(s.tps)}"

    fun topic(s: StatsSnapshot, nowUtc: String): String {
        val count = if (s.maxPlayers > 0) "${s.playersOnline}/${s.maxPlayers}" else "${s.playersOnline}"
        return "🟢 $count · ⚡ ${StatsFormat.tps(s.tps)} TPS · " +
            "🏔 peak ${s.peakToday} · ⏱ up ${StatsFormat.uptime(s.uptimeSeconds())} · updated $nowUtc UTC"
    }

    /** Dedup key for topic edits: only the parts a viewer cares about changing. Excludes the
     *  climbing uptime + clock so an idle server's topic isn't rewritten every slow tick. */
    fun topicKey(s: StatsSnapshot): String {
        val count = if (s.maxPlayers > 0) "${s.playersOnline}/${s.maxPlayers}" else "${s.playersOnline}"
        return "$count|${StatsFormat.tps(s.tps)}|${s.peakToday}"
    }
}
