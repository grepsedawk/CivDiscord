package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object GuildsTable : Table("guilds") {
    val guildId = long("guild_id")
    val joinedAt = long("joined_at")
    val authRoleId = long("auth_role_id").nullable()
    val deletedAt: Column<Long?> = long("deleted_at").nullable()
    override val primaryKey = PrimaryKey(guildId)
}
