package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.GuildDao
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class GuildLifecycleListener(private val guilds: GuildDao) : ListenerAdapter() {
    override fun onGuildReady(event: GuildReadyEvent) {
        guilds.ensure(event.guild.idLong)
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        guilds.ensure(event.guild.idLong)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        guilds.markDeleted(event.guild.idLong)
    }
}
