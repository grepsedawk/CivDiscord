package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SeenPlayersDao(private val db: Database) {
    /** First-seen wins: a duplicate uuid is ignored (PK violation swallowed). */
    fun recordSeen(uuid: String, epoch: Long) = transaction(db) {
        try {
            SeenPlayersTable.insert {
                it[SeenPlayersTable.uuid] = uuid
                it[firstSeenEpoch] = epoch
            }
            Unit
        } catch (e: ExposedSQLException) {
            if (!isSqliteUniqueViolation(e)) throw e
            Unit
        }
    }

    fun uniqueCount(): Long = transaction(db) { SeenPlayersTable.selectAll().count() }

    fun countSeenOnOrAfter(epoch: Long): Int = transaction(db) {
        SeenPlayersTable.selectAll()
            .where { SeenPlayersTable.firstSeenEpoch greaterEq epoch }
            .count().toInt()
    }
}
