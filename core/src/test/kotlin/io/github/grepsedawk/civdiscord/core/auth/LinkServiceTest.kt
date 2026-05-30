package io.github.grepsedawk.civdiscord.core.auth

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.CivDiscordDb
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class LinkServiceTest {
    @Test
    fun `redeem writes a binding and returns success`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        val tokens = LinkTokenStore(clock = { 0 })
        val svc = LinkService(tokens, bindings)
        val uuid = UUID.randomUUID()
        val token = tokens.mint(uuid, "alice")

        val result = svc.redeem(discordId = 42L, code = token.code)

        result shouldBe LinkService.Result.Linked(mcUuid = uuid, mcName = "alice", replaced = false)
        bindings.findByDiscordId(42L)!!.mcUuid shouldBe uuid
    }

    @Test
    fun `redeem replacing an existing binding reports replaced = true`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        val tokens = LinkTokenStore(clock = { 0 })
        val svc = LinkService(tokens, bindings)
        bindings.upsert(42L, UUID.randomUUID(), "old")
        val newUuid = UUID.randomUUID()
        val t = tokens.mint(newUuid, "new")

        val r = svc.redeem(42L, t.code) as LinkService.Result.Linked
        r.replaced shouldBe true
    }

    @Test
    fun `redeem with unknown code returns NoSuchCode`() {
        val db = CivDiscordDb.inMemory()
        val svc = LinkService(LinkTokenStore(clock = { 0 }), BindingDao(db))
        svc.redeem(42L, "no-such-code") shouldBe LinkService.Result.NoSuchCode
    }

    @Test
    fun `redeem with expired code returns NoSuchCode (already reaped)`() {
        val db = CivDiscordDb.inMemory()
        var now = 0L
        val tokens = LinkTokenStore(ttlMs = 1000L, clock = { now })
        val svc = LinkService(tokens, BindingDao(db))
        val t = tokens.mint(UUID.randomUUID(), "alice")
        now = 2000L
        svc.redeem(42L, t.code) shouldBe LinkService.Result.NoSuchCode
    }

    @Test
    fun `redeem reports McAlreadyLinked when mc_uuid belongs to a different Discord user`() {
        val db = CivDiscordDb.inMemory()
        val bindings = BindingDao(db)
        val tokens = LinkTokenStore(clock = { 0 })
        val svc = LinkService(tokens, bindings)
        val mcUuid = UUID.randomUUID()
        bindings.upsert(111L, mcUuid, "alice")
        val t = tokens.mint(mcUuid, "alice")

        val result = svc.redeem(discordId = 222L, code = t.code)

        result shouldBe LinkService.Result.McAlreadyLinked(otherDiscordId = 111L)
        bindings.findByDiscordId(222L) shouldBe null
        bindings.findByMcUuid(mcUuid)!!.discordId shouldBe 111L
    }
}
