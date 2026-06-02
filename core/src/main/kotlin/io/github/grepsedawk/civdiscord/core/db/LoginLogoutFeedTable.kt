package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object LoginLogoutFeedTable : Table("login_logout_feed") {
    val id = integer("id") // always 1 — single-row table; CHECK (id = 1) in the migration
    val guildId = long("guild_id").references(GuildsTable.guildId)
    val channelId = long("channel_id")
    val createdBy = long("created_by")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
