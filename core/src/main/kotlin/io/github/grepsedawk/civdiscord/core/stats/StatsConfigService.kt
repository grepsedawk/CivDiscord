package io.github.grepsedawk.civdiscord.core.stats

import io.github.grepsedawk.civdiscord.core.db.StatsBinding
import io.github.grepsedawk.civdiscord.core.db.StatsConfigDao

/** Caches the single stats-config row. binding() is read on the stats-tick thread; the mutators
 *  run from BOTH the JDA command thread (/stats) and the stats-tick thread (setDashboardMessageId
 *  via the dashboard publisher), so each is @Synchronized to keep its read-modify-write of [cached]
 *  atomic, and @Volatile carries the value to readers. */
class StatsConfigService(private val dao: StatsConfigDao) {
    @Volatile
    private var cached: StatsBinding? = dao.get()

    fun binding(): StatsBinding? = cached

    /** Returns the old (channelId, messageId) to delete when the dashboard moved channels, else null. */
    @Synchronized
    fun bindDashboardChannel(guildId: Long, channelId: Long, by: Long): Pair<Long, Long>? {
        val orphan = dao.bindDashboardChannel(guildId, channelId, by)
        cached = dao.get()
        return orphan
    }

    @Synchronized
    fun setDashboardMessageId(messageId: Long?) {
        dao.setDashboardMessageId(messageId)
        cached = dao.get()
    }

    /** Attach the message id only if the dashboard still points at [channelId]; returns whether it did. */
    @Synchronized
    fun attachDashboardMessage(channelId: Long, messageId: Long): Boolean {
        val saved = dao.attachDashboardMessage(channelId, messageId)
        cached = dao.get()
        return saved
    }

    @Synchronized
    fun setVoicePlayersChannel(guildId: Long, channelId: Long?, by: Long) {
        dao.setVoicePlayersChannel(guildId, channelId, by)
        cached = dao.get()
    }

    @Synchronized
    fun setVoiceTpsChannel(guildId: Long, channelId: Long?, by: Long) {
        dao.setVoiceTpsChannel(guildId, channelId, by)
        cached = dao.get()
    }

    /** Returns the (channel, message) that was removed, to delete, or null if nothing was posted. */
    @Synchronized
    fun clearDashboard(): Pair<Long, Long>? {
        val removed = dao.clearDashboard()
        cached = dao.get()
        return removed
    }

    @Synchronized
    fun clearAll() {
        dao.clearAll()
        cached = null
    }
}
