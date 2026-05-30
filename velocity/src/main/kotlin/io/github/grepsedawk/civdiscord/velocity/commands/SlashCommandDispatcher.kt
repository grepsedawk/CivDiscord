package io.github.grepsedawk.civdiscord.velocity.commands

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.hooks.ListenerAdapter

class SlashCommandDispatcher(
    private val homeGuildId: Long,
    private val restErrorHandler: ErrorHandler,
    private val link: LinkCommand,
    private val me: MeCommand,
    private val relay: RelayCommand,
    private val adminUser: AdminUserCommand,
    private val adminGuild: AdminGuildCommand,
    private val adminRun: AdminRunCommand,
    private val backends: () -> List<String>,
) : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "link" -> link.handle(event)
            "me" -> me.handle(event)
            "relay" -> relay.handle(event)
            "admin" -> handleAdmin(event)
        }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.name == "admin" && event.focusedOption.name == "server") {
            val matches = backends()
                .filter { it.startsWith(event.focusedOption.value, ignoreCase = true) }
                .take(25)
            event.replyChoiceStrings(matches).queue(null, restErrorHandler)
        }
    }

    private fun handleAdmin(event: SlashCommandInteractionEvent) {
        when (event.subcommandGroup) {
            "user" -> {
                if (!requireHomeGuild(event)) return
                adminUser.handle(event)
            }
            "guild" -> adminGuild.handle(event)
            null ->
                when (event.subcommandName) {
                    "run" -> {
                        if (!requireHomeGuild(event)) return
                        adminRun.handle(event)
                    }
                    else ->
                        event.reply("Unknown subcommand.")
                            .setEphemeral(true).queue(null, restErrorHandler)
                }
            else ->
                event.reply("Unknown subcommand group.")
                    .setEphemeral(true).queue(null, restErrorHandler)
        }
    }

    private fun requireHomeGuild(event: SlashCommandInteractionEvent): Boolean {
        if (event.guild?.idLong == homeGuildId) return true
        event.reply("This command is only available in the home guild.")
            .setEphemeral(true).queue(null, restErrorHandler)
        return false
    }
}
