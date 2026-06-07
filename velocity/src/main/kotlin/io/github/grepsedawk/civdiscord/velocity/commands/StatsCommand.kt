package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.stats.StatsConfigService
import io.github.grepsedawk.civdiscord.core.stats.StatsTopicChannels
import io.github.grepsedawk.civdiscord.velocity.stats.StatsPermError
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class StatsCommand(
    private val service: StatsConfigService,
    private val topicChannels: StatsTopicChannels,
    private val guilds: GuildDao,
    private val refreshDashboardNow: () -> Unit,
    private val deleteMessage: (channelId: Long, messageId: Long) -> Unit,
) {
    fun handle(event: SlashCommandInteractionEvent) {
        val guildId = event.guild?.idLong
            ?: return event.reply("Run this in a server channel, not a DM.").setEphemeral(true).queue()
        val userId = event.user.idLong
        when (event.subcommandName) {
            "dashboard-set" -> {
                val channel = event.guildChannel
                if (!requireTextChannel(event, channel)) return
                if (!botCanUse(event, channel, DASHBOARD_PERMS)) return
                guilds.ensure(guildId)
                val orphan = service.bindDashboardChannel(guildId, channel.idLong, userId)
                orphan?.let { (oldCh, oldMid) -> deleteMessage(oldCh, oldMid) }
                refreshDashboardNow()
                event.reply("Dashboard is live here. It refreshes about once a minute.").setEphemeral(true).queue()
            }
            "dashboard-clear" -> {
                val removed = service.clearDashboard()
                removed?.let { (ch, mid) -> deleteMessage(ch, mid) }
                event.reply(
                    if (removed != null) "Dashboard unbound, and I removed the panel." else "Dashboard unbound.",
                ).setEphemeral(true).queue()
            }
            "show" -> {
                val b = service.binding()
                val topics = topicChannels.channels()
                if (b == null && topics.isEmpty()) {
                    event.reply("Nothing bound yet. Start with /stats dashboard-set.").setEphemeral(true).queue()
                } else {
                    val topicStr = if (topics.isEmpty()) "none" else topics.joinToString(" ") { "<#$it>" }
                    event.reply(
                        "Bound surfaces: dashboard ${chan(b?.dashboardChannelId)}, " +
                            "players VC ${chan(b?.voicePlayersChannelId)}, " +
                            "TPS VC ${chan(b?.voiceTpsChannelId)}, topics $topicStr.",
                    ).setEphemeral(true).queue()
                }
            }
            "players-channel" -> {
                val channel = event.getOption("channel")!!.asChannel
                if (!botCanUse(event, channel, RENAME_PERMS)) return
                guilds.ensure(guildId)
                service.setVoicePlayersChannel(guildId, channel.idLong, userId)
                event.reply("Player count will show in that channel, updated about every 10 minutes.").setEphemeral(true).queue()
            }
            "tps-channel" -> {
                val channel = event.getOption("channel")!!.asChannel
                if (!botCanUse(event, channel, RENAME_PERMS)) return
                guilds.ensure(guildId)
                service.setVoiceTpsChannel(guildId, channel.idLong, userId)
                event.reply("TPS will show in that channel, updated about every 10 minutes.").setEphemeral(true).queue()
            }
            "topic-add" -> {
                val channel = event.guildChannel
                if (!requireTextChannel(event, channel)) return
                if (!botCanUse(event, channel, TOPIC_PERMS)) return
                guilds.ensure(guildId)
                val added = topicChannels.add(guildId, channel.idLong, userId)
                event.reply(
                    if (added) {
                        "This channel's topic now carries the stats line, updated about every 10 minutes."
                    } else {
                        "This channel already carries the stats line."
                    },
                ).setEphemeral(true).queue()
            }
            "topic-remove" -> {
                val removed = topicChannels.remove(event.channel.idLong)
                event.reply(
                    if (removed) {
                        "Stopped updating this channel's topic. The current text stays put."
                    } else {
                        "This channel wasn't carrying the stats line."
                    },
                ).setEphemeral(true).queue()
            }
            "voice-clear" -> {
                service.setVoicePlayersChannel(guildId, null, userId)
                service.setVoiceTpsChannel(guildId, null, userId)
                event.reply("Voice stat channels unbound. I left their current names in place.").setEphemeral(true).queue()
            }
            "topic-clear" -> {
                val cleared = topicChannels.clear()
                event.reply("Cleared the stats line from $cleared channel(s). Their current topics stay put.").setEphemeral(true).queue()
            }
            else -> event.reply("I don't know the subcommand ${event.subcommandName}.").setEphemeral(true).queue()
        }
    }

    // The dashboard/topic publishers resolve the channel via getTextChannelById, which only returns
    // standard text channels — reject threads/forums/voice so we don't accept a bind we can't serve.
    private fun requireTextChannel(event: SlashCommandInteractionEvent, channel: GuildChannel): Boolean {
        if (channel.type == ChannelType.TEXT) return true
        event.reply("I can only do that in a normal text channel — not a thread, forum, or voice channel.")
            .setEphemeral(true).queue()
        return false
    }

    // Reject a bind the bot can't actually serve: a dashboard it can't post in, or a channel it
    // can't rename. Replies with the missing perms and leaves the binding untouched.
    private fun botCanUse(event: SlashCommandInteractionEvent, channel: GuildChannel, required: List<Permission>): Boolean {
        val self = event.guild!!.selfMember
        val missing = required.filterNot { self.hasPermission(channel, it) }
        if (missing.isEmpty()) return true
        event.replyEmbeds(StatsPermError.embed(channel.idLong, missing)).setEphemeral(true).queue()
        return false
    }

    private fun chan(id: Long?): String = if (id == null) "unset" else "<#$id>"

    companion object {
        // MESSAGE_MANAGE so the post path can pin the panel; without it botCanUse would
        // pass a bind it can't fully serve (the dashboard posts but never pins).
        private val DASHBOARD_PERMS = listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE)
        private val RENAME_PERMS = listOf(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL)
        private val TOPIC_PERMS = listOf(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL)
    }
}
