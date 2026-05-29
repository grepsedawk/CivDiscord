package io.github.grepsedawk.civdiscord.paper.linker

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class PendingLinkRepliesTest {

    private fun reply(id: String) = Payload.LinkReply(id = id, token = null, error = null)

    @Test
    fun `consume immediately returns the remembered player`() {
        val pending = PendingLinkReplies()
        val player = UUID.randomUUID()
        pending.remember("req-1", player)

        pending.resolve(reply("req-1")) shouldBe player
    }

    @Test
    fun `consume after TTL returns null`() {
        var now = 1_000L
        val ttl = TimeUnit.MINUTES.toMillis(5)
        val pending = PendingLinkReplies(ttlMillis = ttl, clock = { now })
        val player = UUID.randomUUID()
        pending.remember("req-1", player)

        now += ttl + 1

        pending.resolve(reply("req-1")) shouldBe null
    }

    @Test
    fun `sweep removes expired entries and keeps fresh ones`() {
        var now = 1_000L
        val ttl = TimeUnit.MINUTES.toMillis(5)
        val pending = PendingLinkReplies(ttlMillis = ttl, clock = { now })
        val stale = UUID.randomUUID()
        val fresh = UUID.randomUUID()
        pending.remember("stale", stale)

        now += ttl + 1
        pending.remember("fresh", fresh)

        pending.sweep()

        pending.resolve(reply("stale")) shouldBe null
        pending.resolve(reply("fresh")) shouldBe fresh
    }

    @Test
    fun `unknown id resolves to null`() {
        PendingLinkReplies().resolve(reply("never-registered")) shouldBe null
    }
}
