package io.github.grepsedawk.civdiscord.core.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LinkTokenStoreTest {
    @Test
    fun `mint returns a 12-char Crockford base32 code`() {
        val s = LinkTokenStore(ttlMs = 60_000, clock = { 0 })
        val t = s.mint(UUID.randomUUID(), "alice")
        t.code.matches(Regex("[0-9A-HJKMNP-TV-Z]{12}")) shouldBe true
    }

    @Test
    fun `consume returns the token then it is gone (single-use)`() {
        val now = 1000L
        val s = LinkTokenStore(ttlMs = 60_000, clock = { now })
        val uuid = UUID.randomUUID()
        val t = s.mint(uuid, "alice")
        s.consume(t.code)?.mcUuid shouldBe uuid
        s.consume(t.code).shouldBeNull()
    }

    @Test
    fun `consume after TTL returns null`() {
        var now = 1000L
        val s = LinkTokenStore(ttlMs = 60_000, clock = { now })
        val t = s.mint(UUID.randomUUID(), "alice")
        now = 1000L + 60_000L + 1L
        s.consume(t.code).shouldBeNull()
    }

    @Test
    fun `mint again replaces any previous outstanding token for the same player`() {
        val s = LinkTokenStore(ttlMs = 60_000, clock = { 0 })
        val uuid = UUID.randomUUID()
        val first = s.mint(uuid, "alice")
        val second = s.mint(uuid, "alice")
        s.consume(first.code).shouldBeNull()
        s.consume(second.code).shouldNotBeNull()
    }

    @Test
    fun `concurrent consume of the same code yields exactly one winner`() {
        repeat(50) {
            val s = LinkTokenStore(ttlMs = 60_000, clock = { 1000L })
            val token = s.mint(UUID.randomUUID(), "alice")
            val n = 8
            val barrier = CyclicBarrier(n)
            val pool = Executors.newFixedThreadPool(n)
            try {
                val futures =
                    (1..n).map {
                        CompletableFuture.supplyAsync({
                            barrier.await()
                            s.consume(token.code)
                        }, pool)
                    }
                val winners = futures.count { it.get(5, TimeUnit.SECONDS) != null }
                winners shouldBe 1
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
