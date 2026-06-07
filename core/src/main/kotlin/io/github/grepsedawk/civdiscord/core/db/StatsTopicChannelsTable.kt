package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object StatsTopicChannelsTable : Table("stats_topic_channels") {
    val channelId = long("channel_id")
    val guildId = long("guild_id").references(GuildsTable.guildId)
    val createdBy = long("created_by")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(channelId)
}
