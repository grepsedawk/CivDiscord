package io.github.grepsedawk.civdiscord.velocity.stats

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class PresencePublisherTest {
    @Test
    fun `sets the players-online string and skips no-op updates`() {
        val sent = mutableListOf<String>()
        val pub = PresencePublisher(setActivity = {
            sent += it
            true
        })
        pub.update(42)
        pub.update(42) // unchanged -> skipped
        pub.update(43)
        sent.shouldContainExactly("42 players online", "43 players online")
    }

    @Test
    fun `retries the same count after a failed apply`() {
        val sent = mutableListOf<String>()
        var applied = false // first apply fails (e.g. JDA not ready yet), then succeeds
        val pub = PresencePublisher(setActivity = {
            sent += it
            applied.also { applied = true }
        })
        pub.update(42) // apply returns false -> not cached
        pub.update(42) // same count retried because last was never set
        sent.shouldContainExactly("42 players online", "42 players online")
    }
}
