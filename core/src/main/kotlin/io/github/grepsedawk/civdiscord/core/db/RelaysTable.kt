package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object RelaysTable : Table("relays") {
    val id = long("id").autoIncrement()
    val guildId = long("guild_id").references(GuildsTable.guildId)
    val namelayerGroup = varchar("namelayer_group", 64)
    val discordChannelId = long("discord_channel_id").uniqueIndex()
    val showSnitches = bool("show_snitches").default(false)
    val chatFormat = text("chat_format").nullable()
    val createdBy = long("created_by")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
