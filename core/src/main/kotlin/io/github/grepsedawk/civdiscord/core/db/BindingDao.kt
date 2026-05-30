package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.util.UUID

data class Binding(
    val discordId: Long,
    val mcUuid: UUID,
    val mcName: String,
    val linkedAt: Long,
)

sealed class LinkOutcome {
    data class Linked(val replaced: Boolean) : LinkOutcome()

    data class McAlreadyLinkedTo(val otherDiscordId: Long) : LinkOutcome()
}

class BindingDao(private val db: Database) {
    fun upsert(
        discordId: Long,
        mcUuid: UUID,
        mcName: String,
        now: Long = System.currentTimeMillis(),
    ): LinkOutcome {
        try {
            return transaction(db) {
                val mcOwner =
                    BindingsTable
                        .selectAll()
                        .where { BindingsTable.mcUuid eq mcUuid.toString() }
                        .firstOrNull()
                        ?.get(BindingsTable.discordId)
                if (mcOwner != null && mcOwner != discordId) {
                    return@transaction LinkOutcome.McAlreadyLinkedTo(mcOwner)
                }
                val existing =
                    BindingsTable
                        .selectAll()
                        .where { BindingsTable.discordId eq discordId }
                        .firstOrNull()
                if (existing == null) {
                    BindingsTable.insert {
                        it[BindingsTable.discordId] = discordId
                        it[BindingsTable.mcUuid] = mcUuid.toString()
                        it[BindingsTable.mcName] = mcName
                        it[BindingsTable.linkedAt] = now
                    }
                    LinkOutcome.Linked(replaced = false)
                } else {
                    BindingsTable.update({ BindingsTable.discordId eq discordId }) {
                        it[BindingsTable.mcUuid] = mcUuid.toString()
                        it[BindingsTable.mcName] = mcName
                        it[BindingsTable.linkedAt] = now
                    }
                    LinkOutcome.Linked(replaced = true)
                }
            }
        } catch (e: ExposedSQLException) {
            if (!isSqliteUniqueViolation(e)) throw e
            val owner = findByMcUuid(mcUuid)
            return if (owner != null && owner.discordId != discordId) {
                LinkOutcome.McAlreadyLinkedTo(owner.discordId)
            } else if (owner != null) {
                LinkOutcome.Linked(replaced = true)
            } else {
                throw e
            }
        }
    }

    private fun isSqliteUniqueViolation(e: ExposedSQLException): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            if (cur is SQLException) {
                val code = cur.errorCode
                if (code == SQLITE_CONSTRAINT_UNIQUE || code == SQLITE_CONSTRAINT_BASE) return true
                if (cur.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true) return true
            }
            cur = cur.cause
        }
        return false
    }

    fun findByDiscordId(discordId: Long): Binding? = transaction(db) {
        BindingsTable
            .selectAll()
            .where { BindingsTable.discordId eq discordId }
            .firstOrNull()
            ?.let(::toBinding)
    }

    fun findByMcUuid(mcUuid: UUID): Binding? = transaction(db) {
        BindingsTable
            .selectAll()
            .where { BindingsTable.mcUuid eq mcUuid.toString() }
            .firstOrNull()
            ?.let(::toBinding)
    }

    fun delete(discordId: Long): Boolean = transaction(db) {
        BindingsTable.deleteWhere { BindingsTable.discordId eq discordId } > 0
    }

    private fun toBinding(row: ResultRow) = Binding(
        discordId = row[BindingsTable.discordId],
        mcUuid = UUID.fromString(row[BindingsTable.mcUuid]),
        mcName = row[BindingsTable.mcName],
        linkedAt = row[BindingsTable.linkedAt],
    )

    private companion object {
        const val SQLITE_CONSTRAINT_UNIQUE = 2067
        const val SQLITE_CONSTRAINT_BASE = 19
    }
}
