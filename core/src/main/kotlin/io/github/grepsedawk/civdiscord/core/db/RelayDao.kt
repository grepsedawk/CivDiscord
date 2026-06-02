package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException

data class Relay(
    val guildId: Long,
    val namelayerGroup: String,
    val discordChannelId: Long,
    val isWriter: Boolean,
    val showSnitches: Boolean,
    val chatFormat: String?,
    val snitchPing: String?,
    val createdBy: Long,
    val createdAt: Long,
)

class RelayDao(private val db: Database) {
    sealed class BindOutcome {
        data object Inserted : BindOutcome()
        data object AlreadyBound : BindOutcome()
    }

    fun bind(
        guildId: Long,
        channelId: Long,
        group: String,
        isWriter: Boolean,
        showSnitches: Boolean,
        createdBy: Long,
        now: Long = System.currentTimeMillis(),
    ): BindOutcome = transaction(db) {
        try {
            RelaysTable.insert {
                it[RelaysTable.guildId] = guildId
                it[RelaysTable.namelayerGroup] = group
                it[RelaysTable.discordChannelId] = channelId
                it[RelaysTable.isWriter] = isWriter
                it[RelaysTable.showSnitches] = showSnitches
                it[RelaysTable.chatFormat] = null
                it[RelaysTable.snitchPing] = null
                it[RelaysTable.createdBy] = createdBy
                it[RelaysTable.createdAt] = now
            }
            BindOutcome.Inserted
        } catch (e: ExposedSQLException) {
            if (!isSqliteUniqueViolation(e)) throw e
            BindOutcome.AlreadyBound
        }
    }

    fun unbind(channelId: Long, group: String): Boolean = transaction(db) {
        RelaysTable.deleteWhere {
            (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group)
        } > 0
    }

    private fun isSqliteUniqueViolation(e: ExposedSQLException): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            if (cur is SQLException) {
                if (cur.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true) return true
            }
            cur = cur.cause
        }
        return false
    }

    fun listForChannel(channelId: Long): List<Relay> = transaction(db) {
        RelaysTable.selectAll()
            .where { RelaysTable.discordChannelId eq channelId }
            .orderBy(RelaysTable.isWriter, SortOrder.DESC)
            .map(::toRelay)
    }

    fun findByChannelAndGroup(channelId: Long, group: String): Relay? = transaction(db) {
        RelaysTable.selectAll()
            .where { (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group) }
            .firstOrNull()?.let(::toRelay)
    }

    fun findWriterForChannel(channelId: Long): Relay? = transaction(db) {
        RelaysTable.selectAll()
            .where { (RelaysTable.discordChannelId eq channelId) and (RelaysTable.isWriter eq true) }
            .firstOrNull()?.let(::toRelay)
    }

    fun listForGuild(guildId: Long): List<Relay> = transaction(db) {
        RelaysTable.selectAll().where { RelaysTable.guildId eq guildId }.map(::toRelay)
    }

    fun findRelaysForGroup(group: String): List<Relay> = transaction(db) {
        RelaysTable.selectAll().where { RelaysTable.namelayerGroup eq group }.map(::toRelay)
    }

    /**
     * Demote any existing writer for the channel, then promote (channel, group) to writer
     * in the same transaction so the partial unique index never sees two writers. Returns
     * false (without mutating anything) if (channel, group) is not bound.
     */
    fun promoteToWriter(channelId: Long, group: String): Boolean = transaction(db) {
        val targetPresent = RelaysTable.selectAll()
            .where { (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group) }
            .firstOrNull() != null
        if (!targetPresent) return@transaction false
        RelaysTable.update({ (RelaysTable.discordChannelId eq channelId) and (RelaysTable.isWriter eq true) }) {
            it[RelaysTable.isWriter] = false
        }
        RelaysTable.update({ (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group) }) {
            it[RelaysTable.isWriter] = true
        }
        true
    }

    fun setShowSnitches(channelId: Long, group: String, value: Boolean): Int = transaction(db) {
        RelaysTable.update({
            (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group)
        }) {
            it[RelaysTable.showSnitches] = value
        }
    }

    fun setChatFormat(channelId: Long, group: String, value: String?): Int = transaction(db) {
        RelaysTable.update({
            (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group)
        }) {
            it[RelaysTable.chatFormat] = value
        }
    }

    fun setSnitchPing(channelId: Long, group: String, value: String?): Int = transaction(db) {
        RelaysTable.update({
            (RelaysTable.discordChannelId eq channelId) and (RelaysTable.namelayerGroup eq group)
        }) {
            it[RelaysTable.snitchPing] = value
        }
    }

    private fun toRelay(row: ResultRow) = Relay(
        guildId = row[RelaysTable.guildId],
        namelayerGroup = row[RelaysTable.namelayerGroup],
        discordChannelId = row[RelaysTable.discordChannelId],
        isWriter = row[RelaysTable.isWriter],
        showSnitches = row[RelaysTable.showSnitches],
        chatFormat = row[RelaysTable.chatFormat],
        snitchPing = row[RelaysTable.snitchPing],
        createdBy = row[RelaysTable.createdBy],
        createdAt = row[RelaysTable.createdAt],
    )
}
