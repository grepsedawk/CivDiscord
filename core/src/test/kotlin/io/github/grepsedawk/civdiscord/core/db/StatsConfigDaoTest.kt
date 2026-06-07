package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsConfigDaoTest {
    private fun setup(): StatsConfigDao {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        return StatsConfigDao(db)
    }

    @Test fun `get is null before any set`() {
        setup().get().shouldBeNull()
    }

    @Test
    fun `setting dashboard channel creates the row`() {
        val dao = setup()
        dao.bindDashboardChannel(guildId = 100L, channelId = 555L, by = 7L)
        val c = dao.get()
        c.shouldNotBeNull()
        c.dashboardChannelId shouldBe 555L
        c.dashboardMessageId shouldBe null
    }

    @Test
    fun `message id persists and updates independently`() {
        val dao = setup()
        dao.bindDashboardChannel(100L, 555L, 7L)
        dao.setDashboardMessageId(999L)
        dao.get()!!.dashboardMessageId shouldBe 999L
        dao.setDashboardMessageId(null)
        dao.get()!!.dashboardMessageId shouldBe null
    }

    @Test
    fun `voice channels set independently without clobbering dashboard`() {
        val dao = setup()
        dao.bindDashboardChannel(100L, 555L, 7L)
        dao.setVoicePlayersChannel(100L, 1L, 7L)
        dao.setVoiceTpsChannel(100L, 2L, 7L)
        val c = dao.get()!!
        c.dashboardChannelId shouldBe 555L
        c.voicePlayersChannelId shouldBe 1L
        c.voiceTpsChannelId shouldBe 2L
    }

    @Test
    fun `clearAll removes the row`() {
        val dao = setup()
        dao.bindDashboardChannel(100L, 555L, 7L)
        dao.clearAll()
        dao.get().shouldBeNull()
    }

    @Test
    fun `clearing a voice channel with no config does not create a row`() {
        val dao = setup()
        dao.setVoicePlayersChannel(100L, null, 7L)
        dao.get().shouldBeNull()
    }

    @Test
    fun `clearDashboard nulls channel and message but keeps voice bindings`() {
        val dao = setup()
        dao.bindDashboardChannel(100L, 555L, 7L)
        dao.setDashboardMessageId(999L)
        dao.setVoicePlayersChannel(100L, 1L, 7L)
        dao.clearDashboard() shouldBe (555L to 999L)
        val c = dao.get()!!
        c.dashboardChannelId shouldBe null
        c.dashboardMessageId shouldBe null
        c.voicePlayersChannelId shouldBe 1L
    }
}
