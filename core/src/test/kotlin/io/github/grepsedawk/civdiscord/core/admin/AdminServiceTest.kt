package io.github.grepsedawk.civdiscord.core.admin

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class AdminServiceTest {

    @Test
    fun `forceUnlink removes existing binding and returns Unlinked`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        val svc = AdminService(bindings)
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        svc.forceUnlink(42L) shouldBe AdminService.UnlinkResult.Unlinked
        bindings.findByDiscordId(42L).shouldBeNull()
    }

    @Test
    fun `forceUnlink on absent returns NotLinked`() {
        val db = CivDiscordDb.inMemory()
        val svc = AdminService(BindingDao(db))
        svc.forceUnlink(42L) shouldBe AdminService.UnlinkResult.NotLinked
    }

    @Test
    fun `viewBinding returns the row when present`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        bindings.upsert(42L, UUID.randomUUID(), "alice")
        AdminService(bindings).viewBinding(42L).shouldNotBeNull()
    }
}
