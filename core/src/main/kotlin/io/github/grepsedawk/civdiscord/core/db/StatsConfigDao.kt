package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class StatsBinding(
    val guildId: Long,
    val dashboardChannelId: Long?,
    val dashboardMessageId: Long?,
    val voicePlayersChannelId: Long?,
    val voiceTpsChannelId: Long?,
)

class StatsConfigDao(private val db: Database) {
    fun get(): StatsBinding? = transaction(db) {
        StatsConfigTable.selectAll().firstOrNull()?.let(::toBinding)
    }

    /** Bind/re-bind the dashboard channel. When the channel changes, the stored message id (which
     *  lives in the OLD channel) is cleared in the same transaction so the publisher posts a fresh
     *  panel in the new channel. Returns the old (channelId, messageId) to delete, or null. */
    fun bindDashboardChannel(guildId: Long, channelId: Long, by: Long, now: Long = System.currentTimeMillis()): Pair<Long, Long>? = transaction(db) {
        val existing = StatsConfigTable.selectAll().firstOrNull()
        if (existing == null) {
            StatsConfigTable.insert {
                it[id] = 1
                it[StatsConfigTable.guildId] = guildId
                it[createdBy] = by
                it[createdAt] = now
                it[dashboardChannelId] = channelId
            }
            null
        } else {
            val prevChannel = existing[StatsConfigTable.dashboardChannelId]
            val prevMessage = existing[StatsConfigTable.dashboardMessageId]
            val changing = prevChannel != null && prevChannel != channelId
            StatsConfigTable.update({ StatsConfigTable.id eq 1 }) {
                it[dashboardChannelId] = channelId
                if (changing) it[dashboardMessageId] = null
            }
            if (changing && prevChannel != null && prevMessage != null) prevChannel to prevMessage else null
        }
    }

    fun setDashboardMessageId(messageId: Long?) = transaction(db) {
        StatsConfigTable.update({ StatsConfigTable.id eq 1 }) { it[StatsConfigTable.dashboardMessageId] = messageId }
        Unit
    }

    /** Save the dashboard message id only if the row still points at [channelId]; returns whether it
     *  did. Guards a post that lands after a clear/re-channel from attaching to the wrong row. */
    fun attachDashboardMessage(channelId: Long, messageId: Long): Boolean = transaction(db) {
        StatsConfigTable.update({ (StatsConfigTable.id eq 1) and (StatsConfigTable.dashboardChannelId eq channelId) }) {
            it[dashboardMessageId] = messageId
        } > 0
    }

    /** Unbind the dashboard: drop both channel and message in one transaction so a concurrent tick
     *  never sees a channel without its message. Returns the (channel, message) that was set, to
     *  delete — read inside the same transaction so a racing repost can't leave a stray panel. */
    fun clearDashboard(): Pair<Long, Long>? = transaction(db) {
        val existing = StatsConfigTable.selectAll().firstOrNull()
        val ch = existing?.get(StatsConfigTable.dashboardChannelId)
        val mid = existing?.get(StatsConfigTable.dashboardMessageId)
        StatsConfigTable.update({ StatsConfigTable.id eq 1 }) {
            it[dashboardChannelId] = null
            it[dashboardMessageId] = null
        }
        if (ch != null && mid != null) ch to mid else null
    }

    fun setVoicePlayersChannel(guildId: Long, channelId: Long?, by: Long, now: Long = System.currentTimeMillis()) {
        if (channelId == null) {
            clearColumn { it[StatsConfigTable.voicePlayersChannelId] = null }
        } else {
            upsert(guildId, by, now) { it[StatsConfigTable.voicePlayersChannelId] = channelId }
        }
    }

    fun setVoiceTpsChannel(guildId: Long, channelId: Long?, by: Long, now: Long = System.currentTimeMillis()) {
        if (channelId == null) {
            clearColumn { it[StatsConfigTable.voiceTpsChannelId] = null }
        } else {
            upsert(guildId, by, now) { it[StatsConfigTable.voiceTpsChannelId] = channelId }
        }
    }

    fun clearAll(): Boolean = transaction(db) { StatsConfigTable.deleteAll() > 0 }

    // Clear a column without materializing the row — clearing a surface that was never bound is a no-op.
    private fun clearColumn(mutate: (UpdateBuilder<*>) -> Unit) = transaction(db) {
        StatsConfigTable.update({ StatsConfigTable.id eq 1 }) { mutate(it) }
        Unit
    }

    // Ensure the single row exists, then apply the field mutation. created_by/at are set on first
    // insert only; subsequent calls just update the named column.
    private fun upsert(guildId: Long, by: Long, now: Long, mutate: (UpdateBuilder<*>) -> Unit) = transaction(db) {
        val exists = StatsConfigTable.selectAll().firstOrNull() != null
        if (!exists) {
            StatsConfigTable.insert {
                it[id] = 1
                it[StatsConfigTable.guildId] = guildId
                it[createdBy] = by
                it[createdAt] = now
                mutate(it)
            }
        } else {
            StatsConfigTable.update({ StatsConfigTable.id eq 1 }) { mutate(it) }
        }
        Unit
    }

    private fun toBinding(row: ResultRow) = StatsBinding(
        guildId = row[StatsConfigTable.guildId],
        dashboardChannelId = row[StatsConfigTable.dashboardChannelId],
        dashboardMessageId = row[StatsConfigTable.dashboardMessageId],
        voicePlayersChannelId = row[StatsConfigTable.voicePlayersChannelId],
        voiceTpsChannelId = row[StatsConfigTable.voiceTpsChannelId],
    )
}
