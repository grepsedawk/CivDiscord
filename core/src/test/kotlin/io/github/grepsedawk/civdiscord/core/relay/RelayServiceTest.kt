package io.github.grepsedawk.civdiscord.core.relay

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RelayServiceTest {

    private fun setup(): RelayService {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        return RelayService(RelayDao(db))
    }

    @Test
    fun `bind a fresh channel returns Bound`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", 5L) shouldBe RelayService.BindResult.Bound
    }

    @Test
    fun `bind the same channel twice returns ChannelAlreadyBound`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.bind(100L, 1001L, "other", 5L) shouldBe RelayService.BindResult.ChannelAlreadyBound
    }

    @Test
    fun `unbind missing channel returns NotBound`() {
        val svc = setup()
        svc.unbind(1001L) shouldBe RelayService.UnbindResult.NotBound
    }

    @Test
    fun `unbind existing returns Unbound`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.unbind(1001L) shouldBe RelayService.UnbindResult.Unbound
    }

    @Test
    fun `second bind on already-bound channel returns ChannelAlreadyBound without throwing`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", 5L) shouldBe RelayService.BindResult.Bound
        svc.bind(100L, 1001L, "other", 5L) shouldBe RelayService.BindResult.ChannelAlreadyBound
    }

    @Test
    fun `setShowSnitches on bound channel returns Updated`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.setShowSnitches(1001L, true) shouldBe RelayService.SetResult.Updated
    }

    @Test
    fun `setShowSnitches on unbound channel returns NotBound`() {
        val svc = setup()
        svc.setShowSnitches(9999L, true) shouldBe RelayService.SetResult.NotBound
    }

    @Test
    fun `setChatFormat on bound channel returns Updated`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", 5L)
        svc.setChatFormat(1001L, "{name}: {msg}") shouldBe RelayService.SetResult.Updated
    }

    @Test
    fun `setChatFormat on unbound channel returns NotBound`() {
        val svc = setup()
        svc.setChatFormat(9999L, "{name}: {msg}") shouldBe RelayService.SetResult.NotBound
    }
}
