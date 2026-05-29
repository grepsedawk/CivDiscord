package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class Relay(
    val id: Long,
    val guildId: Long,
    val namelayerGroup: String,
    val discordChannelId: Long,
    val showSnitches: Boolean,
    val chatFormat: String?,
    val createdBy: Long,
    val createdAt: Long,
)

class RelayDao(private val db: Database) {
    sealed class BindOutcome {
        data class Inserted(val id: Long) : BindOutcome()
        data object AlreadyBound : BindOutcome()
    }

    fun bind(
        guildId: Long,
        channelId: Long,
        group: String,
        createdBy: Long,
        now: Long = System.currentTimeMillis(),
    ): BindOutcome =
        transaction(db) {
            val existing =
                RelaysTable.selectAll().where { RelaysTable.discordChannelId eq channelId }
                    .firstOrNull()
            if (existing != null) {
                BindOutcome.AlreadyBound
            } else {
                val row =
                    RelaysTable.insert {
                        it[RelaysTable.guildId] = guildId
                        it[RelaysTable.namelayerGroup] = group
                        it[RelaysTable.discordChannelId] = channelId
                        it[RelaysTable.showSnitches] = false
                        it[RelaysTable.chatFormat] = null
                        it[RelaysTable.createdBy] = createdBy
                        it[RelaysTable.createdAt] = now
                    }
                BindOutcome.Inserted(row[RelaysTable.id])
            }
        }

    fun unbind(channelId: Long): Boolean =
        transaction(db) {
            RelaysTable.deleteWhere { RelaysTable.discordChannelId eq channelId } > 0
        }

    fun findByChannel(channelId: Long): Relay? =
        transaction(db) {
            RelaysTable.selectAll().where { RelaysTable.discordChannelId eq channelId }
                .firstOrNull()?.let(::toRelay)
        }

    fun listForGuild(guildId: Long): List<Relay> =
        transaction(db) {
            RelaysTable.selectAll().where { RelaysTable.guildId eq guildId }.map(::toRelay)
        }

    fun findRelaysForGroup(group: String): List<Relay> =
        transaction(db) {
            RelaysTable.selectAll().where { RelaysTable.namelayerGroup eq group }.map(::toRelay)
        }

    fun setShowSnitches(
        channelId: Long,
        value: Boolean,
    ): Int =
        transaction(db) {
            RelaysTable.update({ RelaysTable.discordChannelId eq channelId }) {
                it[RelaysTable.showSnitches] = value
            }
        }

    fun setChatFormat(
        channelId: Long,
        value: String?,
    ): Int =
        transaction(db) {
            RelaysTable.update({ RelaysTable.discordChannelId eq channelId }) {
                it[RelaysTable.chatFormat] = value
            }
        }

    private fun toRelay(row: ResultRow) =
        Relay(
            id = row[RelaysTable.id],
            guildId = row[RelaysTable.guildId],
            namelayerGroup = row[RelaysTable.namelayerGroup],
            discordChannelId = row[RelaysTable.discordChannelId],
            showSnitches = row[RelaysTable.showSnitches],
            chatFormat = row[RelaysTable.chatFormat],
            createdBy = row[RelaysTable.createdBy],
            createdAt = row[RelaysTable.createdAt],
        )
}
