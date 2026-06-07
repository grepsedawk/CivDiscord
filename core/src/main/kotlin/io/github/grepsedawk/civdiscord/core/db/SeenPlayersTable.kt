package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object SeenPlayersTable : Table("seen_players") {
    val uuid = text("uuid")
    val firstSeenEpoch = long("first_seen_epoch")
    override val primaryKey = PrimaryKey(uuid)
}
