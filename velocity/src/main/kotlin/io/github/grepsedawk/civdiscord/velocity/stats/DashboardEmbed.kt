package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.stats.Health
import io.github.grepsedawk.civdiscord.core.stats.StatsFormat
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color

object DashboardEmbed {
    private val GREEN = Color(0x2ECC71)
    private val YELLOW = Color(0xF1C40F)
    private val RED = Color(0xE74C3C)
    private val GREY = Color(0x95A5A6)

    fun render(s: StatsSnapshot): MessageEmbed {
        val players = if (s.maxPlayers > 0) "${s.playersOnline}/${s.maxPlayers}" else "${s.playersOnline}"
        return EmbedBuilder()
            .setTitle("Live server stats")
            .setColor(colorFor(s.health()))
            .addField("Players", players, true)
            .addField("TPS", StatsFormat.tps(s.tps), true)
            .addField("Uptime", StatsFormat.uptime(s.uptimeSeconds()), true)
            .addField("Peak today", s.peakToday.toString(), true)
            .addField("Peak all-time", s.peakAllTime.toString(), true)
            .addField("Players seen", "${s.uniquePlayersEver} (+${s.newPlayersToday} today)", true)
            .setDescription("updated <t:${s.takenAtEpoch}:R>")
            .build()
    }

    private fun colorFor(h: Health): Color = when (h) {
        Health.HEALTHY -> GREEN
        Health.DEGRADED -> YELLOW
        Health.UNHEALTHY -> RED
        Health.UNKNOWN -> GREY
    }
}
