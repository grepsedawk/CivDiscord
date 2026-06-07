package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class StatLabelsTest {
    private fun snap(tps: Double?) = StatsSnapshot(42, 150, tps, 0, 11_520, 11_520, 88, 203, 1204, 7, 0)

    @Test fun `players label`() {
        StatLabels.players(snap(20.0)) shouldBe "🟢 42/150 online"
    }

    @Test fun `tps label`() {
        StatLabels.tps(snap(19.84)) shouldBe "⚡ TPS 19.8"
    }

    @Test fun `tps label stale`() {
        StatLabels.tps(snap(null)) shouldBe "⚡ TPS —"
    }

    @Test
    fun `topic line packs the stats with a literal UTC stamp`() {
        StatLabels.topic(snap(19.8), nowUtc = "02:31") shouldBe
            "🟢 42/150 · ⚡ 19.8 TPS · 🏔 peak 88 · ⏱ up 3h 12m · updated 02:31 UTC"
    }

    @Test
    fun `topic dedup key excludes the climbing uptime`() {
        val a = StatsSnapshot(42, 150, 19.8, 0, 11_520, 11_520, 88, 203, 1204, 7, 0)
        val b = StatsSnapshot(42, 150, 19.8, 0, 99_999, 99_999, 88, 203, 1204, 7, 0)
        StatLabels.topicKey(a) shouldBe StatLabels.topicKey(b)
    }

    @Test
    fun `topic dedup key changes when players change`() {
        val a = StatsSnapshot(42, 150, 19.8, 0, 11_520, 11_520, 88, 203, 1204, 7, 0)
        val b = StatsSnapshot(43, 150, 19.8, 0, 11_520, 11_520, 88, 203, 1204, 7, 0)
        StatLabels.topicKey(a) shouldNotBe StatLabels.topicKey(b)
    }
}
