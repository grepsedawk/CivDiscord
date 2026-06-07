package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsCountersDaoTest {
    private fun dao() = StatsCountersDao(CivDiscordDb.inMemory())

    @Test
    fun `first bump seeds both peaks`() {
        val c = dao().bumpPeak(current = 42, today = "2026-06-03")
        c.peakAllTime shouldBe 42
        c.peakToday shouldBe 42
    }

    @Test
    fun `higher current raises both peaks`() {
        val dao = dao()
        dao.bumpPeak(42, "2026-06-03")
        val c = dao.bumpPeak(50, "2026-06-03")
        c.peakAllTime shouldBe 50
        c.peakToday shouldBe 50
    }

    @Test
    fun `lower current keeps peaks`() {
        val dao = dao()
        dao.bumpPeak(50, "2026-06-03")
        val c = dao.bumpPeak(10, "2026-06-03")
        c.peakAllTime shouldBe 50
        c.peakToday shouldBe 50
    }

    @Test
    fun `new day resets peak today but keeps all time`() {
        val dao = dao()
        dao.bumpPeak(50, "2026-06-03")
        val c = dao.bumpPeak(12, "2026-06-04")
        c.peakAllTime shouldBe 50
        c.peakToday shouldBe 12
    }
}
