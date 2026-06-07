package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.db.StatsBinding
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChannelStatPublisherTest {
    private fun snap(online: Int, tps: Double?, peak: Int = online, uptime: Long = 60) = StatsSnapshot(online, 150, tps, 0, uptime, uptime, peak, peak, 1, 0, 0)

    private fun binding(players: Long? = 1L, tps: Long? = 2L) = StatsBinding(1L, null, null, players, tps)

    private class Recorder {
        val names = mutableListOf<Pair<Long, String>>()
        val topics = mutableListOf<Pair<Long, String>>()
        var nameSucceeds = true
        var topicSucceeds = true
        var clock = "02:31"

        fun publisher() = ChannelStatPublisher(
            setName = { id, name, ok ->
                names += id to name
                if (nameSucceeds) ok()
            },
            setTopic = { id, topic, ok ->
                topics += id to topic
                if (topicSucceeds) ok()
            },
            nowUtc = { clock },
        )
    }

    @Test
    fun `sets names and topic, then skips identical follow-up updates`() {
        val r = Recorder()
        val pub = r.publisher()
        val s = snap(42, 19.8)
        pub.update(s, binding(), listOf(3L))
        pub.update(s, binding(), listOf(3L)) // identical -> all skipped
        r.names.shouldContainExactly(1L to "🟢 42/150 online", 2L to "⚡ TPS 19.8")
        r.topics.map { it.first }.shouldContainExactly(3L)
    }

    @Test
    fun `changed value triggers a fresh edit`() {
        val r = Recorder()
        val pub = r.publisher()
        pub.update(snap(42, 19.8), binding(), listOf(3L))
        pub.update(snap(43, 19.8), binding(), listOf(3L))
        r.names.count { it.first == 1L } shouldBe 2
    }

    @Test
    fun `every bound topic channel gets the same line, independent of the voice binding`() {
        val r = Recorder()
        val pub = r.publisher()
        pub.update(snap(42, 19.8), null, listOf(3L, 4L, 5L))
        r.topics.map { it.first }.shouldContainExactly(3L, 4L, 5L)
        r.topics.map { it.second }.toSet().size shouldBe 1
    }

    @Test
    fun `topic is not re-edited when only the clock and uptime advance`() {
        val r = Recorder()
        val pub = r.publisher()
        pub.update(snap(42, 19.8, uptime = 60), null, listOf(3L))
        r.clock = "02:41"
        pub.update(snap(42, 19.8, uptime = 660), null, listOf(3L)) // 10 min later, same stats
        r.topics.map { it.first }.shouldContainExactly(3L) // only the first edit fired
    }

    @Test
    fun `a failed edit is retried next tick (cached only on success)`() {
        val r = Recorder()
        r.nameSucceeds = false
        val pub = r.publisher()
        pub.update(snap(42, 19.8), binding(tps = null), emptyList())
        pub.update(snap(42, 19.8), binding(tps = null), emptyList())
        r.names.count { it.first == 1L } shouldBe 2
    }

    @Test
    fun `an unbound channel is forgotten so a later re-bind edits fresh`() {
        val r = Recorder()
        val pub = r.publisher()
        pub.update(snap(42, 19.8), binding(tps = null), emptyList()) // bind -> edit + cache
        pub.update(snap(42, 19.8), binding(players = null, tps = null), emptyList()) // unbind -> forgotten
        pub.update(snap(42, 19.8), binding(tps = null), emptyList()) // re-bind, same value -> edits again
        r.names.count { it.first == 1L } shouldBe 2
    }

    @Test
    fun `a channel bound to both players and tps shows only the player count`() {
        val r = Recorder()
        val pub = r.publisher()
        val both = StatsBinding(1L, null, null, voicePlayersChannelId = 9L, voiceTpsChannelId = 9L)
        pub.update(snap(42, 19.8), both, emptyList())
        pub.update(snap(43, 20.0), both, emptyList()) // stats changed
        r.names.none { it.first == 9L && it.second.contains("TPS") } shouldBe true
        r.names.count { it.first == 9L } shouldBe 2 // two player-count edits, never a tps edit (players wins)
    }
}
