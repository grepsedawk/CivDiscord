package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.feed.LoginLogoutFeedService
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class LoginFeedCommand(
    private val service: LoginLogoutFeedService,
    private val guilds: GuildDao,
) {
    fun handle(event: SlashCommandInteractionEvent) {
        val guildId = event.guild?.idLong
            ?: return event.reply("This command must be used in a guild.").setEphemeral(true).queue()
        val channelId = event.channel.idLong
        val userId = event.user.idLong
        when (event.subcommandName) {
            "bind" -> handleBind(event, guildId, channelId, userId)
            "unbind" -> handleUnbind(event)
            "status" -> handleStatus(event)
            else -> event.reply("Unknown subcommand: ${event.subcommandName}.").setEphemeral(true).queue()
        }
    }

    private fun handleBind(e: SlashCommandInteractionEvent, guildId: Long, channelId: Long, userId: Long) {
        guilds.ensure(guildId)
        when (val result = service.bind(guildId, channelId, userId)) {
            LoginLogoutFeedService.BindResult.Bound ->
                e.reply("Login/logout feed bound to this channel.").setEphemeral(true).queue()
            is LoginLogoutFeedService.BindResult.AlreadyBound ->
                e.reply("Already bound to <#${result.channelId}>. Run `/loginfeed unbind` first.")
                    .setEphemeral(true).queue()
        }
    }

    private fun handleUnbind(e: SlashCommandInteractionEvent) {
        when (service.unbind()) {
            LoginLogoutFeedService.UnbindResult.Unbound ->
                e.reply("Login/logout feed unbound.").setEphemeral(true).queue()
            LoginLogoutFeedService.UnbindResult.NotBound ->
                e.reply("No login/logout feed is bound.").setEphemeral(true).queue()
        }
    }

    private fun handleStatus(e: SlashCommandInteractionEvent) {
        val ch = service.channelId()
        if (ch == null) {
            e.reply("No login/logout feed is bound.").setEphemeral(true).queue()
        } else {
            e.reply("Login/logout feed is bound to <#$ch>.").setEphemeral(true).queue()
        }
    }
}
