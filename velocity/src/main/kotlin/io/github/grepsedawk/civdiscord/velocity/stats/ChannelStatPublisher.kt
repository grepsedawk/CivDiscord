package io.github.grepsedawk.civdiscord.velocity.stats

import io.github.grepsedawk.civdiscord.core.db.StatsBinding
import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import java.util.concurrent.ConcurrentHashMap

/** Updates the opt-in voice-channel names + topic on the slow tick. Edits only when the meaningful
 *  stats changed — the topic's climbing uptime and clock are excluded from the dedup key, so an
 *  idle server isn't re-edited every tick — and a value is cached only once the JDA edit succeeds,
 *  so a failed/rate-limited edit is retried next tick. The dedup cache is keyed per surface, not
 *  just per channel, so the same channel bound to two surfaces doesn't clobber its own entries.
 *  Channels no longer bound are forgotten, so a later re-bind edits fresh. JDA calls are injected. */
class ChannelStatPublisher(
    private val setName: (channelId: Long, name: String, onSuccess: () -> Unit) -> Unit,
    private val setTopic: (channelId: Long, topic: String, onSuccess: () -> Unit) -> Unit,
    private val nowUtc: () -> String,
) {
    private val lastBySurface = ConcurrentHashMap<String, String>()

    fun update(snapshot: StatsSnapshot, binding: StatsBinding?, topicChannelIds: List<Long>) {
        val playersChannel = binding?.voicePlayersChannelId
        // One channel can only show one name; if it's bound to both voice surfaces, players wins
        // (otherwise the two would fight over the name every tick and burn the rename budget).
        val tpsChannel = binding?.voiceTpsChannelId?.takeIf { it != playersChannel }
        val active = buildSet {
            playersChannel?.let { add(surfaceKey(PLAYERS, it)) }
            tpsChannel?.let { add(surfaceKey(TPS, it)) }
            topicChannelIds.forEach { add(surfaceKey(TOPIC, it)) }
        }
        lastBySurface.keys.retainAll(active)

        playersChannel?.let { editName(PLAYERS, it, StatLabels.players(snapshot)) }
        tpsChannel?.let { editName(TPS, it, StatLabels.tps(snapshot)) }
        if (topicChannelIds.isNotEmpty()) {
            val dedupKey = StatLabels.topicKey(snapshot)
            val topic = StatLabels.topic(snapshot, nowUtc())
            topicChannelIds.forEach { editTopic(it, dedupKey, topic) }
        }
    }

    private fun editName(surface: String, id: Long, value: String) {
        val key = surfaceKey(surface, id)
        if (lastBySurface[key] == value) return
        setName(id, value) { lastBySurface[key] = value }
    }

    private fun editTopic(id: Long, dedupKey: String, value: String) {
        val key = surfaceKey(TOPIC, id)
        if (lastBySurface[key] == dedupKey) return
        setTopic(id, value) { lastBySurface[key] = dedupKey }
    }

    private fun surfaceKey(surface: String, id: Long) = "$surface:$id"

    private companion object {
        const val PLAYERS = "players"
        const val TPS = "tps"
        const val TOPIC = "topic"
    }
}
