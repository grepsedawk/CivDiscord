package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object PatreonTiersTable : Table("patreon_tiers") {
    val discordId = long("discord_id")
    val tier = text("tier").nullable()
    val syncedAt = long("synced_at")
    override val primaryKey = PrimaryKey(discordId)
}
