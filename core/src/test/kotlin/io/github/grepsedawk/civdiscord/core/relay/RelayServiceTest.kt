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
    fun `first bind on a channel returns Writer`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L) shouldBe RelayService.BindResult.Writer
    }

    @Test
    fun `second bind on same channel with new group returns Reader`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1001L, "other", showSnitches = true, createdBy = 5L) shouldBe RelayService.BindResult.Reader
    }

    @Test
    fun `bind same channel and group twice returns ChannelAlreadyBound`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L) shouldBe RelayService.BindResult.ChannelAlreadyBound
    }

    @Test
    fun `unbind missing returns NotBound`() {
        val svc = setup()
        svc.unbind(1001L, "townhall") shouldBe RelayService.UnbindResult.NotBound
    }

    @Test
    fun `unbind existing returns Unbound`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.unbind(1001L, "townhall") shouldBe RelayService.UnbindResult.Unbound
    }

    @Test
    fun `unbind specific group leaves the rest`() {
        val svc = setup()
        svc.bind(100L, 1001L, "a", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1001L, "b", showSnitches = false, createdBy = 5L)
        svc.unbind(1001L, "b") shouldBe RelayService.UnbindResult.Unbound
        svc.listForChannel(1001L).map { it.namelayerGroup } shouldBe listOf("a")
    }

    @Test
    fun `unbind of writer leaves channel writerless but with readers`() {
        val svc = setup()
        svc.bind(100L, 1001L, "a", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1001L, "b", showSnitches = false, createdBy = 5L)
        svc.unbind(1001L, "a") shouldBe RelayService.UnbindResult.Unbound
        svc.findWriterForChannel(1001L) shouldBe null
        svc.listForChannel(1001L).map { it.namelayerGroup } shouldBe listOf("b")
    }

    @Test
    fun `promoteWriter swaps writer and reports Promoted`() {
        val svc = setup()
        svc.bind(100L, 1001L, "a", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1001L, "b", showSnitches = false, createdBy = 5L)
        svc.promoteWriter(1001L, "b") shouldBe RelayService.PromoteWriterResult.Promoted
        svc.findWriterForChannel(1001L)?.namelayerGroup shouldBe "b"
    }

    @Test
    fun `promoteWriter on unbound group reports NotBound and preserves the writer`() {
        val svc = setup()
        svc.bind(100L, 1001L, "a", showSnitches = false, createdBy = 5L)
        svc.promoteWriter(1001L, "nope") shouldBe RelayService.PromoteWriterResult.NotBound
        svc.findWriterForChannel(1001L)?.namelayerGroup shouldBe "a"
    }

    @Test
    fun `setShowSnitches on bound channel returns Updated`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setShowSnitches(1001L, "townhall", true) shouldBe RelayService.SetResult.Updated
    }

    @Test
    fun `setShowSnitches on unbound channel returns NotBound`() {
        val svc = setup()
        svc.setShowSnitches(9999L, "townhall", true) shouldBe RelayService.SetResult.NotBound
    }

    @Test
    fun `setShowSnitches scopes updates to the named group`() {
        val svc = setup()
        svc.bind(100L, 1001L, "a", showSnitches = false, createdBy = 5L)
        svc.bind(100L, 1001L, "b", showSnitches = false, createdBy = 5L)
        svc.setShowSnitches(1001L, "b", true) shouldBe RelayService.SetResult.Updated
        svc.findByChannelAndGroup(1001L, "a")?.showSnitches shouldBe false
        svc.findByChannelAndGroup(1001L, "b")?.showSnitches shouldBe true
        svc.setShowSnitches(1001L, "nope", true) shouldBe RelayService.SetResult.NotBound
    }

    @Test
    fun `setChatFormat on bound channel returns Updated`() {
        val svc = setup()
        svc.bind(100L, 1001L, "townhall", showSnitches = false, createdBy = 5L)
        svc.setChatFormat(1001L, "townhall", "{name}: {msg}") shouldBe RelayService.SetResult.Updated
    }

    @Test
    fun `setChatFormat on unbound channel returns NotBound`() {
        val svc = setup()
        svc.setChatFormat(9999L, "townhall", "{name}: {msg}") shouldBe RelayService.SetResult.NotBound
    }
}
