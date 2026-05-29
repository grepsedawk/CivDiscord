package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.relay.RelayService
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class RelayCommand(private val service: RelayService) {

    fun handle(event: SlashCommandInteractionEvent) {
        val guildId = event.guild?.idLong
            ?: return event.reply("This command must be used in a guild.").setEphemeral(true).queue()
        val channelId = event.channel.idLong
        val userId = event.user.idLong

        when (event.subcommandName) {
            "bind" -> handleBind(event, guildId, channelId, userId)
            "unbind" -> handleUnbind(event, channelId)
            "list" -> handleList(event, guildId)
            "show" -> handleShow(event, channelId)
            "set" -> handleSet(event, channelId)
            else -> event.reply("Unknown subcommand: ${event.subcommandName}.").setEphemeral(true).queue()
        }
    }

    private fun handleList(e: SlashCommandInteractionEvent, guildId: Long) {
        val rows = service.listForGuild(guildId)
        if (rows.isEmpty()) {
            e.reply("No relays configured for this guild.").setEphemeral(true).queue()
            return
        }
        val body = rows.joinToString("\n") { r ->
            val snitch = if (r.showSnitches) " (+snitches)" else ""
            "• <#${r.discordChannelId}> ↔ `${MarkdownSafe.code(r.namelayerGroup)}`$snitch"
        }
        e.reply(body).setEphemeral(true).queue()
    }

    private fun handleShow(e: SlashCommandInteractionEvent, channelId: Long) {
        val r = service.findByChannel(channelId)
        if (r == null) {
            e.reply("This channel is not bound to anything.").setEphemeral(true).queue()
            return
        }
        val format = r.chatFormat ?: "(default)"
        e.reply(
            "**Channel**: <#${r.discordChannelId}>\n" +
                "**Group**: `${MarkdownSafe.code(r.namelayerGroup)}`\n" +
                "**Snitches**: ${r.showSnitches}\n" +
                "**Format**: `${MarkdownSafe.code(format)}`",
        ).setEphemeral(true).queue()
    }

    private fun handleSet(e: SlashCommandInteractionEvent, channelId: Long) {
        val prop = e.getOption("property")?.asString ?: run {
            e.reply("Missing property.").setEphemeral(true).queue()
            return
        }
        val value = e.getOption("value")?.asString ?: run {
            e.reply("Missing value.").setEphemeral(true).queue()
            return
        }
        if (prop != "show-snitches" && prop != "chat-format") {
            e.reply("Unknown property: `${MarkdownSafe.code(prop)}`. Valid: show-snitches, chat-format.")
                .setEphemeral(true).queue()
            return
        }
        if (service.findByChannel(channelId) == null) {
            e.reply("This channel is not bound — run `/relay bind` first.").setEphemeral(true).queue()
            return
        }
        when (prop) {
            "show-snitches" -> {
                val bool = parseBool(value) ?: return e.reply(
                    "Invalid value `${MarkdownSafe.code(value)}`. Use one of: true/false/yes/no/on/off/1/0.",
                ).setEphemeral(true).queue()
                when (service.setShowSnitches(channelId, bool)) {
                    RelayService.SetResult.Updated ->
                        e.reply("Set show-snitches=$bool.").setEphemeral(true).queue()
                    RelayService.SetResult.NotBound ->
                        e.reply("This channel is not bound to anything. Run `/relay bind` first.")
                            .setEphemeral(true).queue()
                }
            }
            "chat-format" -> {
                val v = value.takeIf { it.isNotBlank() && it != "null" }
                if (v != null) {
                    val bogus = ChatRelay.unknownPlaceholders(v)
                    if (bogus.isNotEmpty()) {
                        e.reply(
                            "Unknown placeholder: `${MarkdownSafe.code("{${bogus.first()}}")}`. " +
                                "Allowed: {name}, {server}, {text}, {group}.",
                        ).setEphemeral(true).queue()
                        return
                    }
                }
                when (service.setChatFormat(channelId, v)) {
                    RelayService.SetResult.Updated ->
                        e.reply("Set chat-format=`${MarkdownSafe.code(v ?: "(default)")}`.").setEphemeral(true).queue()
                    RelayService.SetResult.NotBound ->
                        e.reply("This channel is not bound to anything. Run `/relay bind` first.")
                            .setEphemeral(true).queue()
                }
            }
        }
    }

    private fun parseBool(s: String): Boolean? = when (s.lowercase()) {
        "true", "1", "yes", "on", "y" -> true
        "false", "0", "no", "off", "n" -> false
        else -> null
    }

    private fun handleBind(e: SlashCommandInteractionEvent, guildId: Long, channelId: Long, userId: Long) {
        val group = e.getOption("namelayer-group")?.asString ?: run {
            e.reply("Missing namelayer-group.").setEphemeral(true).queue()
            return
        }
        when (service.bind(guildId, channelId, group, userId)) {
            RelayService.BindResult.Bound ->
                e.reply("Bound this channel to NameLayer group `${MarkdownSafe.code(group)}`.").setEphemeral(true).queue()
            RelayService.BindResult.ChannelAlreadyBound -> {
                val existing = service.findByChannel(channelId)
                e.reply(
                    "Channel already bound to `${MarkdownSafe.code(existing?.namelayerGroup ?: "")}`. " +
                        "Run `/relay unbind` first.",
                ).setEphemeral(true).queue()
            }
        }
    }

    private fun handleUnbind(e: SlashCommandInteractionEvent, channelId: Long) {
        when (service.unbind(channelId)) {
            RelayService.UnbindResult.Unbound ->
                e.reply("Unbound.").setEphemeral(true).queue()
            RelayService.UnbindResult.NotBound ->
                e.reply("This channel is not bound to anything.").setEphemeral(true).queue()
        }
    }
}
