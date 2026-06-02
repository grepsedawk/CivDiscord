package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

data class Feed(val guildId: Long, val channelId: Long)

class LoginLogoutFeedDao(private val db: Database) {
    sealed class BindOutcome {
        data object Inserted : BindOutcome()
        data object AlreadyBound : BindOutcome()
    }

    fun bind(
        guildId: Long,
        channelId: Long,
        createdBy: Long,
        now: Long = System.currentTimeMillis(),
    ): BindOutcome = transaction(db) {
        try {
            LoginLogoutFeedTable.insert {
                it[LoginLogoutFeedTable.id] = 1
                it[LoginLogoutFeedTable.guildId] = guildId
                it[LoginLogoutFeedTable.channelId] = channelId
                it[LoginLogoutFeedTable.createdBy] = createdBy
                it[LoginLogoutFeedTable.createdAt] = now
            }
            BindOutcome.Inserted
        } catch (e: ExposedSQLException) {
            if (!isSqliteUniqueViolation(e)) throw e
            BindOutcome.AlreadyBound
        }
    }

    fun unbind(): Boolean = transaction(db) {
        LoginLogoutFeedTable.deleteAll() > 0
    }

    fun get(): Feed? = transaction(db) {
        LoginLogoutFeedTable.selectAll().firstOrNull()?.let(::toFeed)
    }

    private fun toFeed(row: ResultRow) = Feed(
        guildId = row[LoginLogoutFeedTable.guildId],
        channelId = row[LoginLogoutFeedTable.channelId],
    )

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
}
