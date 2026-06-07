package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class StatsTopicChannelsDao(private val db: Database) {
    /** Returns false if the channel was already bound (PK violation swallowed). */
    fun add(guildId: Long, channelId: Long, by: Long, now: Long = System.currentTimeMillis()): Boolean = transaction(db) {
        try {
            StatsTopicChannelsTable.insert {
                it[StatsTopicChannelsTable.channelId] = channelId
                it[StatsTopicChannelsTable.guildId] = guildId
                it[createdBy] = by
                it[createdAt] = now
            }
            true
        } catch (e: ExposedSQLException) {
            if (!isSqliteUniqueViolation(e)) throw e
            false
        }
    }

    fun remove(channelId: Long): Boolean = transaction(db) {
        StatsTopicChannelsTable.deleteWhere { StatsTopicChannelsTable.channelId eq channelId } > 0
    }

    fun clear(): Int = transaction(db) { StatsTopicChannelsTable.deleteAll() }

    fun list(): List<Long> = transaction(db) {
        StatsTopicChannelsTable.selectAll().map { it[StatsTopicChannelsTable.channelId] }
    }
}
