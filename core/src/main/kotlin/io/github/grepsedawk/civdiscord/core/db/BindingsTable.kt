package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object BindingsTable : Table("bindings") {
    val discordId = long("discord_id")
    val mcUuid = varchar("mc_uuid", 36)
    val mcName = varchar("mc_name", 64)
    val linkedAt = long("linked_at")
    override val primaryKey = PrimaryKey(discordId)
}
