package io.github.grepsedawk.civdiscord.core.bridge

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RateLimitedLoggerTest {

    private class CapturingLogger : Logger by LoggerFactory.getLogger("capture") {
        val warns = mutableListOf<String>()
        override fun warn(msg: String) {
            warns += msg
        }
    }

    @Test
    fun `first warn emits, subsequent within interval suppress`() {
        var now = 0L
        val log = CapturingLogger()
        val rl = RateLimitedLogger(log, intervalNanos = 1_000_000L, nanoClock = { now })

        rl.warn("a")
        rl.warn("a")
        rl.warn("a")

        log.warns.size shouldBe 1
        rl.count() shouldBe 3L
    }

    @Test
    fun `next warn after interval emits with suppressed suffix`() {
        var now = 0L
        val log = CapturingLogger()
        val rl = RateLimitedLogger(log, intervalNanos = 1_000L, nanoClock = { now })

        rl.warn("x")
        rl.warn("x")
        rl.warn("x")
        now = 5_000L
        rl.warn("x")

        log.warns.size shouldBe 2
        log.warns[1] shouldContain "suppressed 2 since last"
    }
}
