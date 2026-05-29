package io.github.grepsedawk.civdiscord.velocity.auth

import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test

class RoleGranterTest {
    @Test
    fun `grantAllForLinkedUser hits each guild with an auth_role and a member entry`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.setAuthRole(100L, 111L)
        guilds.ensure(200L)
        guilds.setAuthRole(200L, 222L)
        guilds.ensure(300L) // no auth role set
        val granted = mutableListOf<Triple<Long, Long, Long>>() // discordId, guildId, roleId
        val granter =
            RoleGranter(
                guilds = guilds,
                // user in 100,200 but not 300
                isMemberOf = { guildId, _ -> guildId in setOf(100L, 200L) },
                grant = { gid, did, rid -> granted.add(Triple(did, gid, rid)) },
            )

        granter.grantAllForLinkedUser(discordId = 42L)

        granted shouldContainExactlyInAnyOrder
            listOf(
                Triple(42L, 100L, 111L),
                Triple(42L, 200L, 222L),
            )
    }

    @Test
    fun `grantForGuild with unknown guildId is a no-op`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        val granted = mutableListOf<Triple<Long, Long, Long>>()
        val granter =
            RoleGranter(
                guilds = guilds,
                isMemberOf = { _, _ -> true },
                grant = { gid, did, rid -> granted.add(Triple(did, gid, rid)) },
            )

        granter.grantForGuild(guildId = 999L, discordId = 42L)

        granted.shouldBeEmpty()
    }

    @Test
    fun `grantForGuild when guild has no authRoleId is a no-op`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(300L)
        val granted = mutableListOf<Triple<Long, Long, Long>>()
        val granter =
            RoleGranter(
                guilds = guilds,
                isMemberOf = { _, _ -> true },
                grant = { gid, did, rid -> granted.add(Triple(did, gid, rid)) },
            )

        granter.grantForGuild(guildId = 300L, discordId = 42L)

        granted.shouldBeEmpty()
    }

    @Test
    fun `grantAllForLinkedUser when user is in zero guilds grants nothing`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.setAuthRole(100L, 111L)
        guilds.ensure(200L)
        guilds.setAuthRole(200L, 222L)
        val granted = mutableListOf<Triple<Long, Long, Long>>()
        val granter =
            RoleGranter(
                guilds = guilds,
                isMemberOf = { _, _ -> false },
                grant = { gid, did, rid -> granted.add(Triple(did, gid, rid)) },
            )

        granter.grantAllForLinkedUser(discordId = 42L)

        granted.shouldBeEmpty()
    }

    @Test
    fun `grantAllForLinkedUser when guilds all is empty grants nothing`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        val granted = mutableListOf<Triple<Long, Long, Long>>()
        val granter =
            RoleGranter(
                guilds = guilds,
                isMemberOf = { _, _ -> true },
                grant = { gid, did, rid -> granted.add(Triple(did, gid, rid)) },
            )

        granter.grantAllForLinkedUser(discordId = 42L)

        granted.shouldBeEmpty()
    }

    /**
     * Pins the (guildId, discordId, roleId) positional contract of RoleGranter.grant.
     * Regression guard: an earlier bug in CivDiscordVelocityPlugin declared the addRole lambda
     * as (discordId, guildId, roleId), so getGuildById(<discordId>) returned null and the auth
     * role grant on link silently no-op'd.
     */
    @Test
    fun `grantAllForLinkedUser invokes grant with positional tuple guildId discordId roleId`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.setAuthRole(100L, 111L)
        val captured = mutableListOf<Triple<Long, Long, Long>>()
        val granter =
            RoleGranter(
                guilds = guilds,
                isMemberOf = { _, _ -> true },
                grant = { a, b, c -> captured.add(Triple(a, b, c)) },
            )

        granter.grantAllForLinkedUser(discordId = 42L)

        captured shouldContainExactlyInAnyOrder listOf(Triple(100L, 42L, 111L))
    }

    @Test
    fun `grantForGuild invokes grant with positional tuple guildId discordId roleId`() {
        val db = CivDiscordDb.inMemory()
        val guilds = GuildDao(db)
        guilds.ensure(100L)
        guilds.setAuthRole(100L, 111L)
        val captured = mutableListOf<Triple<Long, Long, Long>>()
        val granter =
            RoleGranter(
                guilds = guilds,
                isMemberOf = { _, _ -> true },
                grant = { a, b, c -> captured.add(Triple(a, b, c)) },
            )

        granter.grantForGuild(guildId = 100L, discordId = 42L)

        captured shouldContainExactlyInAnyOrder listOf(Triple(100L, 42L, 111L))
    }
}
