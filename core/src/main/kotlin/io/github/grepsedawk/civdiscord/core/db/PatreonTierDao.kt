package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class PatreonTierDao(private val db: Database) {
    fun set(
        discordId: Long,
        tier: String?,
        now: Long = System.currentTimeMillis(),
    ) = transaction(db) {
        val existing =
            PatreonTiersTable.selectAll()
                .where { PatreonTiersTable.discordId eq discordId }
                .any()
        if (existing) {
            PatreonTiersTable.update({ PatreonTiersTable.discordId eq discordId }) {
                it[PatreonTiersTable.tier] = tier
                it[PatreonTiersTable.syncedAt] = now
            }
        } else {
            PatreonTiersTable.insert {
                it[PatreonTiersTable.discordId] = discordId
                it[PatreonTiersTable.tier] = tier
                it[PatreonTiersTable.syncedAt] = now
            }
        }
    }

    fun get(discordId: Long): String? = transaction(db) {
        PatreonTiersTable.selectAll()
            .where { PatreonTiersTable.discordId eq discordId }
            .firstOrNull()?.get(PatreonTiersTable.tier)
    }

    fun findAll(): Map<Long, String?> = transaction(db) {
        PatreonTiersTable.selectAll().associate {
            it[PatreonTiersTable.discordId] to it[PatreonTiersTable.tier]
        }
    }

    fun deleteByDiscordId(discordId: Long): Boolean = transaction(db) {
        PatreonTiersTable.deleteWhere { PatreonTiersTable.discordId eq discordId } > 0
    }

    fun replaceSnapshot(
        snapshot: Map<Long, String>,
        toRemove: Set<Long>,
        now: Long = System.currentTimeMillis(),
    ) = transaction(db) {
        for (discordId in toRemove) {
            PatreonTiersTable.deleteWhere { PatreonTiersTable.discordId eq discordId }
        }
        for ((discordId, tier) in snapshot) {
            val existing =
                PatreonTiersTable.selectAll()
                    .where { PatreonTiersTable.discordId eq discordId }
                    .any()
            if (existing) {
                PatreonTiersTable.update({ PatreonTiersTable.discordId eq discordId }) {
                    it[PatreonTiersTable.tier] = tier
                    it[PatreonTiersTable.syncedAt] = now
                }
            } else {
                PatreonTiersTable.insert {
                    it[PatreonTiersTable.discordId] = discordId
                    it[PatreonTiersTable.tier] = tier
                    it[PatreonTiersTable.syncedAt] = now
                }
            }
        }
    }
}
