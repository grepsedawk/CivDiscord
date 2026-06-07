package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.db.StatsBinding
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DashboardPublisherTest {
    private val snap = StatsSnapshot(1, 150, 20.0, 0, 1, 1, 1, 1, 1, 0, 0)

    private class Fakes {
        var channelId: Long? = 10L
        var messageId: Long? = null
        val posts = mutableListOf<Long>()
        val edits = mutableListOf<Long>()
        val deletes = mutableListOf<Pair<Long, Long>>()
        var postSucceeds = true
        var editOutcome: DashboardPublisher.Result = DashboardPublisher.Result.Edited
        var nowT = 1000L

        fun publisher() = DashboardPublisher(
            binding = { channelId?.let { StatsBinding(1L, it, messageId, null, null) } },
            clearMessageId = { messageId = null },
            attachMessage = { ch, id ->
                if (channelId == ch) {
                    messageId = id
                    true
                } else {
                    false
                }
            },
            post = { _, _, onResult ->
                posts += 1
                onResult(if (postSucceeds) DashboardPublisher.Result.Posted(555L) else DashboardPublisher.Result.Failed)
            },
            edit = { _, id, _, onResult ->
                edits += id
                onResult(editOutcome)
            },
            deleteMessage = { ch, mid -> deletes += ch to mid },
            now = { nowT },
            retryCooldownSeconds = 600,
        )
    }

    @Test
    fun `first update posts and stores the id`() {
        val f = Fakes()
        f.publisher().update(snap)
        f.posts.size shouldBe 1
        f.messageId shouldBe 555L
    }

    @Test
    fun `subsequent update edits the stored message`() {
        val f = Fakes()
        f.messageId = 555L
        f.publisher().update(snap)
        f.edits shouldBe mutableListOf(555L)
        f.posts.size shouldBe 0
    }

    @Test
    fun `a deleted message is dropped, then reposted on the next tick`() {
        val f = Fakes()
        f.messageId = 555L
        f.editOutcome = DashboardPublisher.Result.Missing
        val pub = f.publisher()
        pub.update(snap) // edit -> Missing -> id dropped, no repost this tick
        f.messageId shouldBe null
        f.posts.size shouldBe 0
        pub.update(snap) // mid == null now -> post
        f.posts.size shouldBe 1
        f.messageId shouldBe 555L
    }

    @Test
    fun `no channel bound is a no-op`() {
        val f = Fakes()
        f.channelId = null
        f.publisher().update(snap)
        f.posts.size shouldBe 0
        f.edits.size shouldBe 0
    }

    @Test
    fun `a failed post backs off, then retries after the cooldown`() {
        val f = Fakes()
        f.postSucceeds = false
        val pub = f.publisher()
        pub.update(snap) // posts, fails -> backoff until now+600
        pub.update(snap) // within cooldown -> suppressed
        f.posts.size shouldBe 1
        f.nowT = 1000 + 600
        pub.update(snap) // cooldown elapsed -> retries
        f.posts.size shouldBe 2
    }

    @Test
    fun `re-bind clears the backoff and retries immediately`() {
        val f = Fakes()
        f.postSucceeds = false
        val pub = f.publisher()
        pub.update(snap) // fails -> backoff
        pub.update(snap) // suppressed
        f.posts.size shouldBe 1
        pub.retryAfterRebind()
        pub.update(snap) // backoff cleared -> retries even within cooldown
        f.posts.size shouldBe 2
    }

    @Test
    fun `a failed edit also backs off, then recovers`() {
        val f = Fakes()
        f.messageId = 555L
        f.editOutcome = DashboardPublisher.Result.Failed
        val pub = f.publisher()
        pub.update(snap) // edit fails -> backoff
        pub.update(snap) // suppressed
        f.edits.size shouldBe 1
        f.nowT = 1000 + 600
        pub.update(snap) // cooldown elapsed -> retries
        f.edits.size shouldBe 2
    }

    @Test
    fun `a post that lands after the channel changed deletes the orphan`() {
        var channel: Long? = 10L
        val posts = mutableListOf<Long>()
        val deletes = mutableListOf<Pair<Long, Long>>()
        var pending: ((DashboardPublisher.Result) -> Unit)? = null
        val pub = DashboardPublisher(
            binding = { channel?.let { StatsBinding(1L, it, null, null, null) } },
            clearMessageId = { },
            attachMessage = { ch, _ -> channel == ch }, // attach only if still pointing at ch
            post = { _, _, onResult ->
                posts += 1
                pending = onResult
            },
            edit = { _, _, _, _ -> },
            deleteMessage = { ch, mid -> deletes += ch to mid },
            now = { 0L },
        )
        pub.update(snap) // posts to channel 10, in flight
        channel = 20L // operator re-channels while the post is in flight
        pending!!(DashboardPublisher.Result.Posted(555L)) // post to 10 lands; binding now points at 20
        deletes shouldBe mutableListOf(10L to 555L)
    }

    @Test
    fun `an in-flight op suppresses a second post until the watchdog timeout`() {
        var nowT = 1000L
        val posts = mutableListOf<Long>()
        val pub = DashboardPublisher(
            binding = { StatsBinding(1L, 10L, null, null, null) },
            clearMessageId = { },
            attachMessage = { _, _ -> true },
            post = { _, _, _ -> posts += 1 }, // callback never fires: the op is lost in flight
            edit = { _, _, _, _ -> },
            deleteMessage = { _, _ -> },
            now = { nowT },
        )
        pub.update(snap) // posts, in flight, callback never arrives
        nowT = 1000 + 200 // within the 300s watchdog -> still suppressed
        pub.update(snap)
        posts.size shouldBe 1
        nowT = 1000 + 301 // watchdog elapsed -> a truly stuck op is retried
        pub.update(snap)
        posts.size shouldBe 2
    }
}
