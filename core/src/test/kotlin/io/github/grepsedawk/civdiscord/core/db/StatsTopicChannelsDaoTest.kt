package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StatsTopicChannelsDaoTest {
    private fun dao(): StatsTopicChannelsDao {
        val db = CivDiscordDb.inMemory()
        GuildDao(db).ensure(100L)
        return StatsTopicChannelsDao(db)
    }

    @Test
    fun `add then list returns the channel`() {
        val dao = dao()
        dao.add(100L, 1L, 7L) shouldBe true
        dao.list() shouldContainExactly listOf(1L)
    }

    @Test
    fun `adding the same channel twice is idempotent`() {
        val dao = dao()
        dao.add(100L, 1L, 7L)
        dao.add(100L, 1L, 7L) shouldBe false
        dao.list() shouldContainExactly listOf(1L)
    }

    @Test
    fun `multiple channels are tracked`() {
        val dao = dao()
        dao.add(100L, 1L, 7L)
        dao.add(100L, 2L, 7L)
        dao.list().toSet() shouldBe setOf(1L, 2L)
    }

    @Test
    fun `remove drops one channel`() {
        val dao = dao()
        dao.add(100L, 1L, 7L)
        dao.add(100L, 2L, 7L)
        dao.remove(1L) shouldBe true
        dao.list() shouldContainExactly listOf(2L)
    }

    @Test
    fun `clear removes all`() {
        val dao = dao()
        dao.add(100L, 1L, 7L)
        dao.add(100L, 2L, 7L)
        dao.clear()
        dao.list().shouldBeEmpty()
    }
}
