package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class Guild(
    val guildId: Long,
    val joinedAt: Long,
    val authRoleId: Long?,
)

class GuildDao(private val db: Database) {
    fun ensure(
        guildId: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        transaction(db) {
            val present = GuildsTable.selectAll().where { GuildsTable.guildId eq guildId }.any()
            if (present) {
                GuildsTable.update({ GuildsTable.guildId eq guildId }) {
                    it[GuildsTable.deletedAt] = null
                }
            } else {
                GuildsTable.insert {
                    it[GuildsTable.guildId] = guildId
                    it[GuildsTable.joinedAt] = now
                    it[GuildsTable.authRoleId] = null
                    it[GuildsTable.deletedAt] = null
                }
            }
        }
    }

    fun markDeleted(
        guildId: Long,
        now: Long = System.currentTimeMillis(),
    ) = transaction(db) {
        GuildsTable.update({ GuildsTable.guildId eq guildId }) {
            it[GuildsTable.deletedAt] = now
        }
    }

    fun setAuthRole(
        guildId: Long,
        roleId: Long?,
    ) = transaction(db) {
        GuildsTable.update({ GuildsTable.guildId eq guildId }) {
            it[GuildsTable.authRoleId] = roleId
        }
    }

    fun find(guildId: Long): Guild? =
        transaction(db) {
            GuildsTable.selectAll()
                .where { (GuildsTable.guildId eq guildId) and GuildsTable.deletedAt.isNull() }
                .firstOrNull()?.let {
                    Guild(
                        guildId = it[GuildsTable.guildId],
                        joinedAt = it[GuildsTable.joinedAt],
                        authRoleId = it[GuildsTable.authRoleId],
                    )
                }
        }

    fun all(): List<Guild> =
        transaction(db) {
            GuildsTable.selectAll()
                .where { GuildsTable.deletedAt.isNull() }
                .map {
                    Guild(
                        guildId = it[GuildsTable.guildId],
                        joinedAt = it[GuildsTable.joinedAt],
                        authRoleId = it[GuildsTable.authRoleId],
                    )
                }
        }

    fun delete(guildId: Long) =
        transaction(db) {
            GuildsTable.deleteWhere { GuildsTable.guildId eq guildId }
        }
}
