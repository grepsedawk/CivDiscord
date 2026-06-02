package io.github.grepsedawk.civdiscord.core.auth

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LinkAttemptLimiterTest {
    @Test
    fun `single failure does not lock out`() {
        val limiter = LinkAttemptLimiter(clock = { 0L })
        limiter.recordFailure(42L) shouldBe false
        limiter.isLockedOut(42L) shouldBe false
    }

    @Test
    fun `five failures within window triggers cooldown`() {
        val limiter = LinkAttemptLimiter(clock = { 0L })
        repeat(4) { limiter.recordFailure(42L) shouldBe false }
        limiter.recordFailure(42L) shouldBe true
        limiter.isLockedOut(42L) shouldBe true
    }

    @Test
    fun `cooldown ends after cooldownMillis`() {
        var now = 0L
        val limiter = LinkAttemptLimiter(clock = { now })
        repeat(5) { limiter.recordFailure(42L) }
        limiter.isLockedOut(42L) shouldBe true
        now = 10 * 60 * 1000L + 1L
        limiter.isLockedOut(42L) shouldBe false
    }

    @Test
    fun `failures outside window do not accumulate`() {
        var now = 0L
        val limiter = LinkAttemptLimiter(clock = { now })
        repeat(4) { limiter.recordFailure(42L) }
        now = 5 * 60 * 1000L + 1L
        limiter.recordFailure(42L) shouldBe false
        limiter.isLockedOut(42L) shouldBe false
    }

    @Test
    fun `success clears failure counter`() {
        val limiter = LinkAttemptLimiter(clock = { 0L })
        repeat(4) { limiter.recordFailure(42L) }
        limiter.recordSuccess(42L)
        limiter.recordFailure(42L) shouldBe false
        limiter.isLockedOut(42L) shouldBe false
    }

    @Test
    fun `per-user isolation`() {
        val limiter = LinkAttemptLimiter(clock = { 0L })
        repeat(5) { limiter.recordFailure(42L) }
        limiter.isLockedOut(42L) shouldBe true
        limiter.isLockedOut(99L) shouldBe false
    }
}
