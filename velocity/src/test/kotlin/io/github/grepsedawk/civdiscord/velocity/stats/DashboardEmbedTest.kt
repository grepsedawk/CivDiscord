package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.awt.Color

class DashboardEmbedTest {
    private fun snap(tps: Double?, online: Int = 42) = StatsSnapshot(
        playersOnline = online, maxPlayers = 150, tps = tps, tpsAgeSeconds = 0,
        backendUptimeSeconds = 11_520, proxyUptimeSeconds = 11_520,
        peakToday = 88, peakAllTime = 203, uniquePlayersEver = 1204, newPlayersToday = 7, takenAtEpoch = 1_717_000_000,
    )

    @Test
    fun `healthy snapshot is green and shows the headline numbers`() {
        val e = DashboardEmbed.render(snap(19.8))
        e.color shouldBe Color(0x2ECC71)
        val all = e.fields.joinToString(" ") { "${it.name} ${it.value}" }
        all shouldContain "42/150"
        all shouldContain "19.8"
        all shouldContain "3h 12m"
        all shouldContain "88"
        all shouldContain "203"
    }

    @Test
    fun `unknown tps is grey and shows a dash`() {
        val e = DashboardEmbed.render(snap(null))
        e.color shouldBe Color(0x95A5A6)
        e.fields.joinToString(" ") { it.value ?: "" } shouldContain "—"
    }

    @Test
    fun `description carries an auto-ticking relative timestamp`() {
        DashboardEmbed.render(snap(20.0)).description!! shouldContain "<t:1717000000:R>"
    }
}
