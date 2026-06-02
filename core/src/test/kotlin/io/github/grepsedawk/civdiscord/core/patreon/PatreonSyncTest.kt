package io.github.grepsedawk.civdiscord.core.patreon

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.PatreonTierDao
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PatreonSyncTest {
    private fun fixture(tiers: Map<Long, String>): Pair<PatreonSync, PatreonTierDao> {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        val client =
            object : PatreonClient {
                override fun fetchCurrentTiers(): Map<Long, String> = tiers
            }
        val map = TierRoleMap(mapOf("gold" to 11L, "silver" to 22L))
        return PatreonSync(client, dao, map) to dao
    }

    @Test
    fun `sync records each user's tier`() {
        val (sync, dao) = fixture(mapOf(1L to "gold", 2L to "silver"))
        sync.runOnce()
        dao.get(1L) shouldBe "gold"
        dao.get(2L) shouldBe "silver"
    }

    @Test
    fun `sync returns the role-grant deltas to apply`() {
        val (sync, _) = fixture(mapOf(1L to "gold", 2L to "silver"))
        val deltas = sync.runOnce()
        deltas shouldContainExactlyInAnyOrder
            listOf(
                PatreonSync.RoleDelta(discordId = 1L, addRole = 11L, removeRoles = setOf(22L)),
                PatreonSync.RoleDelta(discordId = 2L, addRole = 22L, removeRoles = setOf(11L)),
            )
    }

    @Test
    fun `sync for unmapped tier emits a delta that removes every mapped role`() {
        val (sync, _) = fixture(mapOf(1L to "bronze"))
        val deltas = sync.runOnce()
        deltas shouldContainExactlyInAnyOrder
            listOf(
                PatreonSync.RoleDelta(discordId = 1L, addRole = null, removeRoles = setOf(11L, 22L)),
            )
    }

    @Test
    fun `dropped-off user gets all tier roles removed and their snapshot deleted`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(discordId = 42L, tier = "gold")
        val client =
            object : PatreonClient {
                override fun fetchCurrentTiers(): Map<Long, String> = emptyMap()
            }
        val sync = PatreonSync(client, dao, TierRoleMap(mapOf("gold" to 11L, "silver" to 22L)))

        val deltas = sync.runOnce()

        deltas shouldContainExactlyInAnyOrder
            listOf(
                PatreonSync.RoleDelta(discordId = 42L, addRole = null, removeRoles = setOf(11L, 22L)),
            )
        dao.get(42L).shouldBeNull()
    }

    @Test
    fun `users still pledged are not treated as dropped off`() {
        val db = CivDiscordDb.inMemory()
        val dao = PatreonTierDao(db)
        dao.set(discordId = 1L, tier = "gold")
        dao.set(discordId = 2L, tier = "silver")
        val client =
            object : PatreonClient {
                override fun fetchCurrentTiers(): Map<Long, String> = mapOf(1L to "gold")
            }
        val sync = PatreonSync(client, dao, TierRoleMap(mapOf("gold" to 11L, "silver" to 22L)))

        val deltas = sync.runOnce()

        deltas shouldContainExactlyInAnyOrder
            listOf(
                PatreonSync.RoleDelta(discordId = 1L, addRole = 11L, removeRoles = setOf(22L)),
                PatreonSync.RoleDelta(discordId = 2L, addRole = null, removeRoles = setOf(11L, 22L)),
            )
        dao.get(1L) shouldBe "gold"
        dao.get(2L).shouldBeNull()
    }
}
