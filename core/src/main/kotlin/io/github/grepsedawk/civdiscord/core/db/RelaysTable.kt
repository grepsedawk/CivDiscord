package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object RelaysTable : Table("relays") {
    val guildId = long("guild_id").references(GuildsTable.guildId)
    val namelayerGroup = varchar("namelayer_group", 64)
    val discordChannelId = long("discord_channel_id")
    val isWriter = bool("is_writer").default(false)
    val showSnitches = bool("show_snitches").default(false)
    val chatFormat = text("chat_format").nullable()
    val snitchPing = text("snitch_ping").nullable()
    val createdBy = long("created_by")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(discordChannelId, namelayerGroup)
}
