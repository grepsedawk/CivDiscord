package io.github.grepsedawk.civdiscord.velocity.chat

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChatRateLimiterTest {

    @Test
    fun `burst capacity is honored and exhausted bucket rejects`() {
        val limiter = ChatRateLimiter(capacity = 5.0, tokensPerSecond = 1.0, clock = { 0L })
        repeat(5) { limiter.tryAcquire(42L) shouldBe true }
        limiter.tryAcquire(42L) shouldBe false
    }

    @Test
    fun `bucket refills over time`() {
        var now = 0L
        val limiter = ChatRateLimiter(capacity = 5.0, tokensPerSecond = 1.0, clock = { now })
        repeat(5) { limiter.tryAcquire(42L) }
        limiter.tryAcquire(42L) shouldBe false
        now = 2_000L
        limiter.tryAcquire(42L) shouldBe true
        limiter.tryAcquire(42L) shouldBe true
        limiter.tryAcquire(42L) shouldBe false
    }

    @Test
    fun `buckets are per-user`() {
        val limiter = ChatRateLimiter(capacity = 1.0, tokensPerSecond = 1.0, clock = { 0L })
        limiter.tryAcquire(1L) shouldBe true
        limiter.tryAcquire(1L) shouldBe false
        limiter.tryAcquire(2L) shouldBe true
    }

    @Test
    fun `refill caps at capacity`() {
        var now = 0L
        val limiter = ChatRateLimiter(capacity = 3.0, tokensPerSecond = 1.0, clock = { now })
        limiter.tryAcquire(42L)
        now = 1_000_000L
        repeat(3) { limiter.tryAcquire(42L) shouldBe true }
        limiter.tryAcquire(42L) shouldBe false
    }
}
