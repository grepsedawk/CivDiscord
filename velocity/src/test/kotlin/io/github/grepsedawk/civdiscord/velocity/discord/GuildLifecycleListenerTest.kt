package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import org.junit.jupiter.api.Test

class GuildLifecycleListenerTest {

    private fun mockGuild(id: Long): Guild = mockk<Guild>(relaxed = true).also { every { it.idLong } returns id }

    @Test
    fun `GuildReady ensures the row`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        val listener = GuildLifecycleListener(dao)
        listener.onGuildReady(GuildReadyEvent(mockk(relaxed = true), 0, mockGuild(111L)))
        dao.find(111L).shouldNotBeNull()
    }

    @Test
    fun `GuildJoin ensures the row`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        GuildLifecycleListener(dao).onGuildJoin(GuildJoinEvent(mockk(relaxed = true), 0, mockGuild(222L)))
        dao.find(222L).shouldNotBeNull()
    }

    @Test
    fun `GuildLeave soft-deletes the row (preserves it for re-join)`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(333L)
        dao.setAuthRole(333L, 444L)
        GuildLifecycleListener(dao).onGuildLeave(GuildLeaveEvent(mockk(relaxed = true), 0, mockGuild(333L)))
        dao.find(333L).shouldBeNull()
    }

    @Test
    fun `GuildReady after GuildLeave restores the row with prior auth role intact`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        val listener = GuildLifecycleListener(dao)
        dao.ensure(333L)
        dao.setAuthRole(333L, 444L)
        listener.onGuildLeave(GuildLeaveEvent(mockk(relaxed = true), 0, mockGuild(333L)))
        listener.onGuildReady(GuildReadyEvent(mockk(relaxed = true), 0, mockGuild(333L)))

        val restored = dao.find(333L)
        restored.shouldNotBeNull()
        restored.authRoleId shouldBe 444L
    }
}
