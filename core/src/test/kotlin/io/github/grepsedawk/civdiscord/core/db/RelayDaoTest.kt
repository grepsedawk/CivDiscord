package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class RelayDaoTest {

    private fun setup(): Pair<RelayDao, GuildDao> {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.ensure(200L)
        return RelayDao(db) to guilds
    }

    @Test
    fun `bind creates a relay row`() {
        val (relays, _) = setup()
        val outcome = relays.bind(guildId = 100L, channelId = 1001L, group = "townhall", createdBy = 5L)
        outcome.shouldBeInstanceOf<RelayDao.BindOutcome.Inserted>()
        val r = relays.findByChannel(1001L)
        r.shouldNotBeNull()
        r.id shouldBe outcome.id
        r.namelayerGroup shouldBe "townhall"
        r.showSnitches shouldBe false
        r.chatFormat.shouldBeNull()
    }

    @Test
    fun `bind on already-bound channel returns AlreadyBound without throwing`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", 5L)
        relays.bind(100L, 1001L, "second", 5L) shouldBe RelayDao.BindOutcome.AlreadyBound
        relays.findByChannel(1001L)!!.namelayerGroup shouldBe "townhall"
    }

    @Test
    fun `setShowSnitches on unbound channel returns 0`() {
        val (relays, _) = setup()
        relays.setShowSnitches(9999L, true) shouldBe 0
    }

    @Test
    fun `setChatFormat on unbound channel returns 0`() {
        val (relays, _) = setup()
        relays.setChatFormat(9999L, "{name}: {msg}") shouldBe 0
    }

    @Test
    fun `same group name allowed in different guilds`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "shared", 5L)
        relays.bind(200L, 2001L, "shared", 5L)
        relays.listForGuild(100L) shouldHaveSize 1
        relays.listForGuild(200L) shouldHaveSize 1
    }

    @Test
    fun `unbind removes the row`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", 5L)
        relays.unbind(1001L)
        relays.findByChannel(1001L).shouldBeNull()
    }

    @Test
    fun `setSnitches toggles the boolean`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", 5L)
        relays.setShowSnitches(1001L, true)
        relays.findByChannel(1001L)!!.showSnitches shouldBe true
    }

    @Test
    fun `setChatFormat updates the template`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "townhall", 5L)
        relays.setChatFormat(1001L, "{name}: {msg}")
        relays.findByChannel(1001L)!!.chatFormat shouldBe "{name}: {msg}"
    }

    @Test
    fun `deleting a guild cascades to its relays`() {
        val (relays, guilds) = setup()
        relays.bind(100L, 1001L, "townhall", 5L)
        guilds.delete(100L)
        relays.findByChannel(1001L).shouldBeNull()
    }

    @Test
    fun `findRelaysForGroup returns every relay subscribed to that NameLayer group`() {
        val (relays, _) = setup()
        relays.bind(100L, 1001L, "shared", 5L)
        relays.bind(200L, 2001L, "shared", 5L)
        relays.bind(200L, 2002L, "other", 5L)
        relays.findRelaysForGroup("shared") shouldHaveSize 2
    }
}
