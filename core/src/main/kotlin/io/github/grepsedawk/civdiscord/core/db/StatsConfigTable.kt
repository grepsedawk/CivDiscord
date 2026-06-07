package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object StatsConfigTable : Table("stats_config") {
    val id = integer("id") // always 1 — single-row table; CHECK (id = 1) in the migration
    val guildId = long("guild_id").references(GuildsTable.guildId)
    val dashboardChannelId = long("dashboard_channel_id").nullable()
    val dashboardMessageId = long("dashboard_message_id").nullable()
    val voicePlayersChannelId = long("voice_players_channel_id").nullable()
    val voiceTpsChannelId = long("voice_tps_channel_id").nullable()
    val createdBy = long("created_by")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
