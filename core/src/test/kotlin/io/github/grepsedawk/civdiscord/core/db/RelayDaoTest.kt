package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RelayDaoTest {

    private fun setup(): Pair<RelayDao, GuildDao> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(10L)
        guilds.ensure(100L)
        guilds.ensure(200L)
        return RelayDao(db) to guilds
    }

    @Test
    fun `bind creates a relay row`() {
        val (relays, _) = setup()
        val outcome = relays.bind(
            guildId = 100L,
            channelId = 1001L,
            group = "townhall",
            isWriter = true,
            showSnitches = false,
            createdBy = 5L,
        )
        outcome shouldBe RelayDao.BindOutcome.Inserted
        val r = relays.findByChannelAndGroup(1001L, "townhall")
        r.shouldNotBeNull()
        r.namelayerGroup shouldBe "townhall"
        r.showSnitches shouldBe false
        r.chatFormat.shouldBeNull()
    }

    @Test
    fun `bind on already-bound channel returns AlreadyBound without throwing`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(100L, 1001L, "townhall", isWriter = false, showSnitches = false, createdBy = 5L) shouldBe RelayDao.BindOutcome.AlreadyBound
        relays.findByChannelAndGroup(1001L, "townhall")!!.namelayerGroup shouldBe "townhall"
    }

    @Test
    fun `setShowSnitches on unbound channel returns 0`() {
        val (relays, _) = setup()
        relays.setShowSnitches(9999L, "townhall", true) shouldBe 0
    }

    @Test
    fun `setChatFormat on unbound channel returns 0`() {
        val (relays, _) = setup()
        relays.setChatFormat(9999L, "townhall", "{name}: {msg}") shouldBe 0
    }

    @Test
    fun `same group name allowed in different guilds`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "shared", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(200L, 2001L, "shared", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.listForGuild(100L) shouldHaveSize 1
        relays.listForGuild(200L) shouldHaveSize 1
    }

    @Test
    fun `unbind removes the row`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.unbind(1001L, "townhall")
        relays.findByChannelAndGroup(1001L, "townhall").shouldBeNull()
    }

    @Test
    fun `setSnitches toggles the boolean`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.setShowSnitches(1001L, "townhall", true)
        relays.findByChannelAndGroup(1001L, "townhall")!!.showSnitches shouldBe true
    }

    @Test
    fun `setChatFormat updates the template`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.setChatFormat(1001L, "townhall", "{name}: {msg}")
        relays.findByChannelAndGroup(1001L, "townhall")!!.chatFormat shouldBe "{name}: {msg}"
    }

    @Test
    fun `deleting a guild cascades to its relays`() {
        val (relays, guilds) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        guilds.delete(100L)
        relays.findByChannelAndGroup(1001L, "townhall").shouldBeNull()
    }

    @Test
    fun `findRelaysForGroup returns every relay subscribed to that NameLayer group`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "shared", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(200L, 2001L, "shared", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.bind(200L, 2002L, "other", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.findRelaysForGroup("shared") shouldHaveSize 2
    }

    @Test
    fun `bind defaults snitchPing to null`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.findByChannelAndGroup(1001L, "townhall")!!.snitchPing.shouldBeNull()
    }

    @Test
    fun `setSnitchPing on bound channel writes the value`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.setSnitchPing(1001L, "townhall", "<@&123>") shouldBe 1
        relays.findByChannelAndGroup(1001L, "townhall")!!.snitchPing shouldBe "<@&123>"
    }

    @Test
    fun `setSnitchPing on unbound channel returns 0`() {
        val (relays, _) = setup()
        relays.setSnitchPing(9999L, "townhall", "<@&123>") shouldBe 0
    }

    @Test
    fun `setSnitchPing to null clears the value`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", isWriter = true, showSnitches = false, createdBy = 5L)
        relays.setSnitchPing(1001L, "townhall", "<@&123>")
        relays.setSnitchPing(1001L, "townhall", null)
        relays.findByChannelAndGroup(1001L, "townhall")!!.snitchPing.shouldBeNull()
    }

    @Test
    fun `bind first as writer then bind second on same channel as reader`() {
        val (dao, _) = setup()
        val a = dao.bind(
            guildId = 10,
            channelId = 99,
            group = "a",
            isWriter = true,
            showSnitches = false,
            createdBy = 5,
            now = 1000,
        )
        val b = dao.bind(
            guildId = 10,
            channelId = 99,
            group = "b",
            isWriter = false,
            showSnitches = true,
            createdBy = 5,
            now = 1001,
        )
        a shouldBe RelayDao.BindOutcome.Inserted
        b shouldBe RelayDao.BindOutcome.Inserted
        val rows = dao.listForChannel(99)
        rows.map { it.namelayerGroup to it.isWriter } shouldBe listOf("a" to true, "b" to false)
        rows.first { it.namelayerGroup == "b" }.showSnitches shouldBe true
    }

    @Test
    fun `bind same channel and group twice returns AlreadyBound`() {
        val (dao, _) = setup()
        dao.bind(10, 99, "a", isWriter = true, showSnitches = false, createdBy = 5, now = 1)
        dao.bind(10, 99, "a", isWriter = false, showSnitches = false, createdBy = 5, now = 2) shouldBe
            RelayDao.BindOutcome.AlreadyBound
    }

    @Test
    fun `findWriterForChannel returns the writer row or null`() {
        val (dao, _) = setup()
        dao.findWriterForChannel(99) shouldBe null
        dao.bind(10, 99, "a", isWriter = true, showSnitches = false, createdBy = 5, now = 1)
        dao.bind(10, 99, "b", isWriter = false, showSnitches = false, createdBy = 5, now = 2)
        dao.findWriterForChannel(99)?.namelayerGroup shouldBe "a"
    }

    @Test
    fun `findByChannelAndGroup returns matching row`() {
        val (dao, _) = setup()
        dao.bind(10, 99, "b", isWriter = false, showSnitches = false, createdBy = 5, now = 1)
        dao.findByChannelAndGroup(99, "b")?.namelayerGroup shouldBe "b"
        dao.findByChannelAndGroup(99, "nope") shouldBe null
    }

    @Test
    fun `unbind single binding removes it and unbind unknown returns false`() {
        val (dao, _) = setup()
        dao.bind(10, 99, "a", isWriter = true, showSnitches = false, createdBy = 5, now = 1)
        dao.unbind(99, "a") shouldBe true
        dao.listForChannel(99) shouldBe emptyList()
        dao.unbind(99, "nope") shouldBe false
    }

    @Test
    fun `promoteToWriter demotes previous writer and promotes target`() {
        val (dao, _) = setup()
        dao.bind(10, 99, "a", isWriter = true, showSnitches = false, createdBy = 5, now = 1)
        dao.bind(10, 99, "b", isWriter = false, showSnitches = false, createdBy = 5, now = 2)
        dao.promoteToWriter(99, "b") shouldBe true
        dao.findWriterForChannel(99)?.namelayerGroup shouldBe "b"
        dao.findByChannelAndGroup(99, "a")?.isWriter shouldBe false
    }

    @Test
    fun `promoteToWriter on unbound group returns false and preserves existing writer`() {
        val (dao, _) = setup()
        dao.bind(10, 99, "a", isWriter = true, showSnitches = false, createdBy = 5, now = 1)
        dao.promoteToWriter(99, "nope") shouldBe false
        dao.findWriterForChannel(99)?.namelayerGroup shouldBe "a"
    }

    @Test
    fun `setShowSnitches scopes to one binding`() {
        val (dao, _) = setup()
        dao.bind(10, 99, "a", isWriter = true, showSnitches = false, createdBy = 5, now = 1)
        dao.bind(10, 99, "b", isWriter = false, showSnitches = false, createdBy = 5, now = 2)
        dao.setShowSnitches(99, "b", true) shouldBe 1
        dao.findByChannelAndGroup(99, "a")?.showSnitches shouldBe false
        dao.findByChannelAndGroup(99, "b")?.showSnitches shouldBe true
    }
}
