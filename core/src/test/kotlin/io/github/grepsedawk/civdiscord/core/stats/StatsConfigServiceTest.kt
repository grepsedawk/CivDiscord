package io.github.grepsedawk.civdiscord.core.stats

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.StatsConfigDao
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsConfigServiceTest {
    private fun service(): StatsConfigService {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        return StatsConfigService(StatsConfigDao(db))
    }

    @Test fun `binding is null initially`() {
        service().binding().shouldBeNull()
    }

    @Test
    fun `setDashboardChannel updates the cache without a db reread`() {
        val s = service()
        s.bindDashboardChannel(100L, 555L, 7L)
        s.binding()!!.dashboardChannelId shouldBe 555L
    }

    @Test
    fun `setDashboardMessageId updates cache`() {
        val s = service()
        s.bindDashboardChannel(100L, 555L, 7L)
        s.setDashboardMessageId(999L)
        s.binding()!!.dashboardMessageId shouldBe 999L
    }

    @Test
    fun `clearAll empties the cache`() {
        val s = service()
        s.bindDashboardChannel(100L, 555L, 7L)
        s.clearAll()
        s.binding().shouldBeNull()
    }

    @Test
    fun `clearDashboard nulls the dashboard but keeps the row`() {
        val s = service()
        s.bindDashboardChannel(100L, 555L, 7L)
        s.setDashboardMessageId(999L)
        s.clearDashboard() shouldBe (555L to 999L)
        s.binding()!!.dashboardChannelId shouldBe null
        s.binding()!!.dashboardMessageId shouldBe null
    }
}
