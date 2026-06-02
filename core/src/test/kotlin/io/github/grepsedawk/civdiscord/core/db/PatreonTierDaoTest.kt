package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PatreonTierDaoTest {
    @Test
    fun `set then get round-trips`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(discordId = 1L, tier = "gold")
        dao.get(1L) shouldBe "gold"
    }

    @Test
    fun `set with null clears the tier`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(1L, "gold")
        dao.set(1L, null)
        dao.get(1L).shouldBeNull()
    }

    @Test
    fun `findAll returns every stored row`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(1L, "gold")
        dao.set(2L, "silver")
        dao.findAll() shouldContainExactly mapOf<Long, String?>(1L to "gold", 2L to "silver")
    }

    @Test
    fun `deleteByDiscordId removes the row`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(1L, "gold")
        dao.deleteByDiscordId(1L) shouldBe true
        dao.get(1L).shouldBeNull()
    }

    @Test
    fun `replaceSnapshot upserts present users and deletes the removed set`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(1L, "gold")
        dao.set(2L, "silver")

        dao.replaceSnapshot(
            snapshot = mapOf(1L to "silver", 3L to "gold"),
            toRemove = setOf(2L),
        )

        dao.findAll() shouldContainExactly mapOf<Long, String?>(1L to "silver", 3L to "gold")
    }
}
