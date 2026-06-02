package io.github.grepsedawk.civdiscord.core.bridge

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class PendingRepliesTest {

    @Test
    fun `resolve immediately returns the remembered carrier`() {
        val pending = PendingReplies<UUID>(ttlMillis = 60_000)
        val player = UUID.randomUUID()
        pending.remember("req-1", player)

        pending.resolve("req-1") shouldBe player
    }

    @Test
    fun `resolve after TTL returns null`() {
        var now = 1_000L
        val ttl = TimeUnit.MINUTES.toMillis(5)
        val pending = PendingReplies<UUID>(ttlMillis = ttl, clock = { now })
        val player = UUID.randomUUID()
        pending.remember("req-1", player)

        now += ttl + 1

        pending.resolve("req-1") shouldBe null
    }

    @Test
    fun `unknown id resolves to null`() {
        PendingReplies<UUID>(ttlMillis = 60_000).resolve("never-registered") shouldBe null
    }

    @Test
    fun `sweep removes expired entries and keeps fresh ones`() {
        var now = 1_000L
        val ttl = TimeUnit.SECONDS.toMillis(30)
        val pending = PendingReplies<UUID>(ttlMillis = ttl, clock = { now })
        val stale = UUID.randomUUID()
        val fresh = UUID.randomUUID()
        pending.remember("stale", stale)

        now += ttl + 1
        pending.remember("fresh", fresh)

        pending.sweep()

        pending.resolve("stale") shouldBe null
        pending.resolve("fresh") shouldBe fresh
    }

    @Test
    fun `sweep invokes onExpire for each dropped entry`() {
        var now = 1_000L
        val ttl = TimeUnit.SECONDS.toMillis(30)
        val expired = mutableListOf<String>()
        val pending = PendingReplies<String>(ttlMillis = ttl, clock = { now }, onExpire = { expired.add(it) })
        pending.remember("a", "carrier-a")
        pending.remember("b", "carrier-b")

        now += ttl + 1
        pending.sweep()

        expired.toSet() shouldBe setOf("carrier-a", "carrier-b")
    }

    @Test
    fun `resolve does NOT invoke onExpire`() {
        val expired = mutableListOf<String>()
        val pending = PendingReplies<String>(ttlMillis = 60_000, onExpire = { expired.add(it) })
        pending.remember("a", "carrier-a")

        pending.resolve("a") shouldBe "carrier-a"

        expired.size shouldBe 0
    }

    @Test
    fun `remember silently drops new entries when at max capacity`() {
        val pending = PendingReplies<String>(ttlMillis = 60_000, maxEntries = 2)
        pending.remember("a", "x")
        pending.remember("b", "y")
        pending.remember("c", "z")

        pending.size() shouldBe 2
        pending.resolve("c") shouldBe null
        pending.resolve("a") shouldBe "x"
        pending.resolve("b") shouldBe "y"
    }

    @Test
    fun `onExpire failure does not abort sweep`() {
        var now = 1_000L
        val ttl = 100L
        val survived = mutableListOf<String>()
        val pending = PendingReplies<String>(
            ttlMillis = ttl,
            clock = { now },
            onExpire = { c ->
                if (c == "boom") throw RuntimeException("kaboom")
                survived.add(c)
            },
        )
        pending.remember("a", "boom")
        pending.remember("b", "ok")

        now += ttl + 1
        pending.sweep()

        survived shouldBe listOf("ok")
    }
}
