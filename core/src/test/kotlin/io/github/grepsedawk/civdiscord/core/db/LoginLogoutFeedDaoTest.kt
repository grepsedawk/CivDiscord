package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LoginLogoutFeedDaoTest {
    private fun setup(): LoginLogoutFeedDao {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        return LoginLogoutFeedDao(db)
    }

    @Test
    fun `bind then get returns the binding`() {
        val dao = setup()
        dao.bind(100L, 1001L, 5L) shouldBe LoginLogoutFeedDao.BindOutcome.Inserted
        val feed = dao.get()
        feed.shouldNotBeNull()
        feed.guildId shouldBe 100L
        feed.channelId shouldBe 1001L
    }

    @Test
    fun `second bind is rejected as already bound and does not change the row`() {
        val dao = setup()
        dao.bind(100L, 1001L, 5L)
        dao.bind(200L, 2002L, 6L) shouldBe LoginLogoutFeedDao.BindOutcome.AlreadyBound
        dao.get()!!.channelId shouldBe 1001L
    }

    @Test
    fun `unbind removes the binding`() {
        val dao = setup()
        dao.bind(100L, 1001L, 5L)
        dao.unbind().shouldBeTrue()
        dao.get().shouldBeNull()
    }

    @Test
    fun `unbind on empty returns false`() {
        val dao = setup()
        dao.unbind().shouldBeFalse()
    }

    @Test
    fun `rebind after unbind works`() {
        val dao = setup()
        dao.bind(100L, 1001L, 5L)
        dao.unbind()
        dao.bind(200L, 2002L, 6L) shouldBe LoginLogoutFeedDao.BindOutcome.Inserted
        dao.get()!!.channelId shouldBe 2002L
    }
}
