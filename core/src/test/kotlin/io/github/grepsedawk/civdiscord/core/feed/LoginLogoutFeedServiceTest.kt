package io.github.grepsedawk.civdiscord.core.feed

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.Feed
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.LoginLogoutFeedDao
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class LoginLogoutFeedServiceTest {
    private fun service(): LoginLogoutFeedService {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        return LoginLogoutFeedService(LoginLogoutFeedDao(db))
    }

    @Test
    fun `bind on empty returns Bound`() {
        service().bind(100L, 1001L, 5L) shouldBe LoginLogoutFeedService.BindResult.Bound
    }

    @Test
    fun `bind when already bound returns AlreadyBound with the existing channel`() {
        val svc = service()
        svc.bind(100L, 1001L, 5L)
        val result = svc.bind(200L, 2002L, 6L)
        result.shouldBeInstanceOf<LoginLogoutFeedService.BindResult.AlreadyBound>()
        result.channelId shouldBe 1001L
    }

    @Test
    fun `unbind returns Unbound when bound`() {
        val svc = service()
        svc.bind(100L, 1001L, 5L)
        svc.unbind() shouldBe LoginLogoutFeedService.UnbindResult.Unbound
    }

    @Test
    fun `unbind returns NotBound when empty`() {
        service().unbind() shouldBe LoginLogoutFeedService.UnbindResult.NotBound
    }

    @Test
    fun `channelId reflects the current binding`() {
        val svc = service()
        svc.channelId() shouldBe null
        svc.bind(100L, 1001L, 5L)
        svc.channelId() shouldBe 1001L
    }

    @Test
    fun `channelId is cleared after unbind`() {
        val svc = service()
        svc.bind(100L, 1001L, 5L)
        svc.unbind()
        svc.channelId() shouldBe null
    }

    @Test
    fun `channelId is initialized from an existing binding at construction`() {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        val dao = LoginLogoutFeedDao(db)
        dao.bind(100L, 1001L, 5L)
        LoginLogoutFeedService(dao).channelId() shouldBe 1001L
    }

    @Test
    fun `channelId is served from cache without re-reading the dao`() {
        val dao = mockk<LoginLogoutFeedDao>()
        every { dao.get() } returns Feed(100L, 1001L)
        val svc = LoginLogoutFeedService(dao)
        repeat(5) { svc.channelId() shouldBe 1001L }
        verify(exactly = 1) { dao.get() }
    }
}
