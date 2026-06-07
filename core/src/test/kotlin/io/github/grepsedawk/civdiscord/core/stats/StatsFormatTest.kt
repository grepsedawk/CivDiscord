package io.github.grepsedawk.civdiscord.core.stats

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsFormatTest {
    @Test fun `uptime under an hour shows minutes`() {
        StatsFormat.uptime(125) shouldBe "2m"
    }

    @Test fun `uptime over an hour shows h and m`() {
        StatsFormat.uptime(11_520) shouldBe "3h 12m"
    }

    @Test fun `uptime over a day shows d h m`() {
        StatsFormat.uptime(90_000) shouldBe "1d 1h 0m"
    }

    @Test fun `tps formats to one decimal`() {
        StatsFormat.tps(19.84) shouldBe "19.8"
    }

    @Test fun `null tps renders em dash`() {
        StatsFormat.tps(null) shouldBe "—"
    }
}
