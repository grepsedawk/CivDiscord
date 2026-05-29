package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GuildDaoTest {
    @Test
    fun `upsert inserts a new guild`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(guildId = 222L)
        dao.find(222L).shouldNotBeNull()
    }

    @Test
    fun `setAuthRole updates the auth_role_id`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(222L)
        dao.setAuthRole(222L, 999L)
        dao.find(222L)!!.authRoleId shouldBe 999L
    }

    @Test
    fun `setAuthRole(null) clears the auth role`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(222L)
        dao.setAuthRole(222L, 999L)
        dao.setAuthRole(222L, null)
        dao.find(222L)!!.authRoleId.shouldBeNull()
    }

    @Test
    fun `delete removes the guild`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(222L)
        dao.delete(222L)
        dao.find(222L).shouldBeNull()
    }

    @Test
    fun `markDeleted hides the row from find`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(222L)
        dao.markDeleted(222L, now = 12345L)
        dao.find(222L).shouldBeNull()
    }

    @Test
    fun `markDeleted hides the row from all`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(111L)
        dao.ensure(222L)
        dao.markDeleted(222L, now = 12345L)
        dao.all().map { it.guildId } shouldBe listOf(111L)
    }

    @Test
    fun `markDeleted of the only row leaves all empty`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(222L)
        dao.markDeleted(222L, now = 12345L)
        dao.all().shouldBeEmpty()
    }

    @Test
    fun `ensure on a soft-deleted row restores it`() {
        val db = CivDiscordDb.inMemory()
        val dao = GuildDao(db)
        dao.ensure(222L)
        dao.setAuthRole(222L, 999L)
        dao.markDeleted(222L, now = 12345L)
        dao.find(222L).shouldBeNull()

        dao.ensure(222L)
        val restored = dao.find(222L)
        restored.shouldNotBeNull()
        restored.authRoleId shouldBe 999L
    }
}
