package io.github.grepsedawk.civdiscord.velocity.patreon

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.PatreonTierDao
import io.github.grepsedawk.civdiscord.core.patreon.PatreonClient
import io.github.grepsedawk.civdiscord.core.patreon.PatreonSync
import io.github.grepsedawk.civdiscord.core.patreon.TierRoleMap
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PatreonSyncJobTest {
    @Test
    fun `tick applies every delta via the granter`() {
        val db = CivDiscordDb.inMemory()
        val client =
            object : PatreonClient {
                override fun fetchCurrentTiers(): Map<Long, String> = mapOf(1L to "gold")
            }
        val sync =
            PatreonSync(
                client,
                PatreonTierDao(db),
                TierRoleMap(mapOf("gold" to 11L, "silver" to 22L)),
            )
        val added = mutableListOf<Triple<Long, Long, Long>>()
        val removed = mutableListOf<Triple<Long, Long, Long>>()
        val job =
            PatreonSyncJob(
                sync = sync,
                homeGuildId = 100L,
                addRole = { guildId, discordId, roleId -> added.add(Triple(guildId, discordId, roleId)) },
                removeRole = { guildId, discordId, roleId -> removed.add(Triple(guildId, discordId, roleId)) },
            )

        job.tick()

        added.size shouldBe 1
        added[0].first shouldBe 100L
        added[0].second shouldBe 1L
        added[0].third shouldBe 11L
        removed.any { it.third == 22L } shouldBe true
    }
}
