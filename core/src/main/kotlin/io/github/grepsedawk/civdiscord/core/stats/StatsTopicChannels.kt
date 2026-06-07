package io.github.grepsedawk.civdiscord.core.stats

import io.github.grepsedawk.civdiscord.core.db.StatsTopicChannelsDao

/** Cached set of channels whose topic carries the stats line. Read on the stats-tick thread,
 *  mutated from the JDA command thread; @Synchronized writes keep the cache atomic and @Volatile
 *  publishes it to readers. */
class StatsTopicChannels(private val dao: StatsTopicChannelsDao) {
    @Volatile
    private var cached: List<Long> = dao.list()

    fun channels(): List<Long> = cached

    @Synchronized
    fun add(guildId: Long, channelId: Long, by: Long): Boolean {
        val added = dao.add(guildId, channelId, by)
        cached = dao.list()
        return added
    }

    @Synchronized
    fun remove(channelId: Long): Boolean {
        val removed = dao.remove(channelId)
        cached = dao.list()
        return removed
    }

    @Synchronized
    fun clear(): Int {
        val removed = dao.clear()
        cached = emptyList()
        return removed
    }
}
