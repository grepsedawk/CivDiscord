package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.relay.RelayService
import io.github.grepsedawk.civdiscord.core.relay.SnitchPing
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import io.github.grepsedawk.civdiscord.velocity.discord.NameLayerPermService
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class RelayCommand(
    private val service: RelayService,
    private val bindings: BindingDao,
    private val permService: NameLayerPermService,
) {

    fun handle(event: SlashCommandInteractionEvent) {
        val guildId = event.guild?.idLong
            ?: return event.reply("This command must be used in a guild.").setEphemeral(true).queue()
        val channelId = event.channel.idLong
        val userId = event.user.idLong
        when (event.subcommandName) {
            "bind" -> handleBind(event, guildId, channelId, userId)
            "unbind" -> handleUnbind(event, channelId)
            "writer" -> handleWriter(event, channelId, userId)
            "list" -> handleList(event, guildId)
            "show" -> handleShow(event, channelId)
            "set" -> handleSet(event, channelId)
            else -> event.reply("Unknown subcommand: ${event.subcommandName}.").setEphemeral(true).queue()
        }
    }

    private fun handleBind(e: SlashCommandInteractionEvent, guildId: Long, channelId: Long, userId: Long) {
        val group = e.getOption("namelayer-group")?.asString
            ?: return e.reply("Missing namelayer-group.").setEphemeral(true).queue()
        val invokerBinding = bindings.findByDiscordId(userId)
        if (invokerBinding == null) {
            e.reply("Run `/discord link` in Minecraft first — only linked users can bind a relay.")
                .setAllowedMentions(emptyList<Message.MentionType>())
                .setEphemeral(true).queue()
            return
        }
        if (!permService.hasPerm(invokerBinding.mcUuid, group, NameLayerPermService.READ_CHAT)) {
            e.reply("You don't have `READ_CHAT` in NameLayer group `${MarkdownSafe.code(group)}`.")
                .setAllowedMentions(emptyList<Message.MentionType>())
                .setEphemeral(true).queue()
            return
        }
        val hasSnitchPerm = permService.hasPerm(invokerBinding.mcUuid, group, NameLayerPermService.SNITCH_NOTIFICATIONS)
        when (service.bind(guildId, channelId, group, showSnitches = hasSnitchPerm, createdBy = userId)) {
            RelayService.BindResult.Writer ->
                e.reply(
                    "Bound `${MarkdownSafe.code(group)}` as **writer**." +
                        snitchSuffix(hasSnitchPerm, group),
                )
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
            RelayService.BindResult.Reader ->
                e.reply(
                    "Bound `${MarkdownSafe.code(group)}` as **reader**." +
                        snitchSuffix(hasSnitchPerm, group),
                )
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
            RelayService.BindResult.ChannelAlreadyBound ->
                e.reply("Channel already bound to `${MarkdownSafe.code(group)}`. Run `/relay unbind group:${MarkdownSafe.code(group)}` first.")
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
        }
    }

    private fun snitchSuffix(hasSnitchPerm: Boolean, group: String): String = if (hasSnitchPerm) {
        " Snitches **on** by default."
    } else {
        " Snitches **off** — binder lacks `SNITCH_NOTIFICATIONS` in `${MarkdownSafe.code(group)}`."
    }

    private fun handleUnbind(e: SlashCommandInteractionEvent, channelId: Long) {
        val relays = service.listForChannel(channelId)
        if (relays.isEmpty()) {
            e.reply("This channel is not bound to anything.").setEphemeral(true).queue()
            return
        }
        val targetGroup = resolveTargetGroup(e, relays) ?: return
        when (service.unbind(channelId, targetGroup)) {
            RelayService.UnbindResult.Unbound ->
                e.reply("Unbound `${MarkdownSafe.code(targetGroup)}`.")
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
            RelayService.UnbindResult.NotBound ->
                e.reply("This channel is not bound to `${MarkdownSafe.code(targetGroup)}`.")
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
        }
    }

    private fun handleWriter(e: SlashCommandInteractionEvent, channelId: Long, userId: Long) {
        val group = e.getOption("namelayer-group")?.asString
            ?: return e.reply("Missing namelayer-group.").setEphemeral(true).queue()
        val invokerBinding = bindings.findByDiscordId(userId)
        if (invokerBinding == null) {
            e.reply("Run `/discord link` in Minecraft first — only linked users can change the writer.")
                .setAllowedMentions(emptyList<Message.MentionType>())
                .setEphemeral(true).queue()
            return
        }
        if (!permService.hasPerm(invokerBinding.mcUuid, group, NameLayerPermService.READ_CHAT)) {
            e.reply("You don't have `READ_CHAT` in NameLayer group `${MarkdownSafe.code(group)}`.")
                .setAllowedMentions(emptyList<Message.MentionType>())
                .setEphemeral(true).queue()
            return
        }
        when (service.promoteWriter(channelId, group)) {
            RelayService.PromoteWriterResult.Promoted ->
                e.reply("`${MarkdownSafe.code(group)}` is now the **writer** for this channel.")
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
            RelayService.PromoteWriterResult.NotBound ->
                e.reply(
                    "Channel is not bound to `${MarkdownSafe.code(group)}`. " +
                        "Bind it first with `/relay bind`.",
                )
                    .setAllowedMentions(emptyList<Message.MentionType>())
                    .setEphemeral(true).queue()
        }
    }

    private fun handleList(e: SlashCommandInteractionEvent, guildId: Long) {
        val rows = service.listForGuild(guildId)
        if (rows.isEmpty()) {
            e.reply("No relays configured for this guild.").setEphemeral(true).queue()
            return
        }
        val body = rows
            .groupBy { it.discordChannelId }
            .toSortedMap()
            .entries.joinToString("\n") { (channelId, list) ->
                val perChannel = list.joinToString(", ") {
                    val tag = if (it.isWriter) "writer" else "reader"
                    val snitch = if (it.showSnitches) "+snitches" else "no snitches"
                    "`${MarkdownSafe.code(it.namelayerGroup)}` ($tag, $snitch)"
                }
                "• <#$channelId> ↔ $perChannel"
            }
        e.reply(body).setEphemeral(true).queue()
    }

    private fun handleShow(e: SlashCommandInteractionEvent, channelId: Long) {
        val rows = service.listForChannel(channelId)
        if (rows.isEmpty()) {
            e.reply("This channel is not bound to anything.")
                .setAllowedMentions(emptyList<Message.MentionType>())
                .setEphemeral(true).queue()
            return
        }
        val body = buildString {
            append("**Channel**: <#$channelId>\n")
            for (r in rows) {
                val role = if (r.isWriter) "writer" else "reader"
                val fmt = r.chatFormat ?: "(default)"
                val ping = r.snitchPing?.let { "`${MarkdownSafe.code(it)}`" } ?: "(none)"
                append("• `${MarkdownSafe.code(r.namelayerGroup)}` — $role; ")
                append("snitches=${r.showSnitches}; format=`${MarkdownSafe.code(fmt)}`; ping=$ping\n")
            }
        }
        e.reply(body)
            .setAllowedMentions(emptyList<Message.MentionType>())
            .setEphemeral(true).queue()
    }

    private fun handleSet(e: SlashCommandInteractionEvent, channelId: Long) {
        val prop = e.getOption("property")?.asString
            ?: return e.reply("Missing property.").setEphemeral(true).queue()
        val value = e.getOption("value")?.asString
            ?: return e.reply("Missing value.").setEphemeral(true).queue()
        if (prop != "show-snitches" && prop != "chat-format" && prop != "snitch-ping") {
            e.reply("Unknown property: `${MarkdownSafe.code(prop)}`. Valid: show-snitches, chat-format, snitch-ping.")
                .setEphemeral(true).queue()
            return
        }
        val rows = service.listForChannel(channelId)
        if (rows.isEmpty()) {
            e.reply("This channel is not bound to anything. Run `/relay bind` first.")
                .setEphemeral(true).queue()
            return
        }
        val targetGroup = resolveTargetGroup(e, rows) ?: return
        val row = rows.firstOrNull { it.namelayerGroup == targetGroup } ?: run {
            e.reply("This channel is not bound to `${MarkdownSafe.code(targetGroup)}`.")
                .setAllowedMentions(emptyList<Message.MentionType>())
                .setEphemeral(true).queue()
            return
        }
        when (prop) {
            "show-snitches" -> {
                val bool = parseBool(value) ?: return e.reply(
                    "Invalid value `${MarkdownSafe.code(value)}`. Use one of: true/false/yes/no/on/off/1/0.",
                ).setEphemeral(true).queue()
                if (bool) {
                    val binderBinding = bindings.findByDiscordId(row.createdBy)
                    val hasPerm = binderBinding != null &&
                        permService.hasPerm(binderBinding.mcUuid, row.namelayerGroup, NameLayerPermService.SNITCH_NOTIFICATIONS)
                    if (!hasPerm) {
                        e.reply(
                            "The relay's binder does not hold `SNITCH_NOTIFICATIONS` in group " +
                                "`${MarkdownSafe.code(row.namelayerGroup)}`. Snitches stay off.",
                        )
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .setEphemeral(true).queue()
                        return
                    }
                }
                when (service.setShowSnitches(channelId, targetGroup, bool)) {
                    RelayService.SetResult.Updated ->
                        e.reply("Set show-snitches=$bool for `${MarkdownSafe.code(targetGroup)}`.")
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .setEphemeral(true).queue()
                    RelayService.SetResult.NotBound ->
                        e.reply("This channel is not bound to `${MarkdownSafe.code(targetGroup)}`.")
                            .setAllowedMentions(emptyList<Message.MentionType>())
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
                when (service.setChatFormat(channelId, targetGroup, v)) {
                    RelayService.SetResult.Updated ->
                        e.reply("Set chat-format=`${MarkdownSafe.code(v ?: "(default)")}` for `${MarkdownSafe.code(targetGroup)}`.")
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .setEphemeral(true).queue()
                    RelayService.SetResult.NotBound ->
                        e.reply("This channel is not bound to `${MarkdownSafe.code(targetGroup)}`.")
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .setEphemeral(true).queue()
                }
            }
            "snitch-ping" -> {
                val cleared = value.isBlank() ||
                    value.equals("null", ignoreCase = true) ||
                    value.equals("none", ignoreCase = true)
                val parsed = if (cleared) null else SnitchPing.parse(value)
                if (!cleared && parsed == null) {
                    e.reply("Value must be a role mention (e.g. @MyRole), a user mention, `@everyone`, or `null` to clear.")
                        .setAllowedMentions(emptyList<Message.MentionType>())
                        .setEphemeral(true).queue()
                    return
                }
                val guildId = e.guild?.idLong
                // Discord's role picker emits <@&{guildId}> for the @everyone role — normalize
                // to the literal @everyone form so dispatch and storage agree.
                val ping = if (parsed is SnitchPing.Role && guildId != null && parsed.id == guildId) {
                    SnitchPing.Everyone
                } else {
                    parsed
                }
                when (service.setSnitchPing(channelId, targetGroup, ping?.mention)) {
                    RelayService.SetResult.Updated -> {
                        val msg = if (ping == null) {
                            "Cleared snitch-ping for `${MarkdownSafe.code(targetGroup)}`."
                        } else {
                            "Set snitch-ping to `${ping.mention}` for `${MarkdownSafe.code(targetGroup)}`."
                        }
                        e.reply(msg)
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .setEphemeral(true).queue()
                    }
                    RelayService.SetResult.NotBound ->
                        e.reply("This channel is not bound to `${MarkdownSafe.code(targetGroup)}`.")
                            .setAllowedMentions(emptyList<Message.MentionType>())
                            .setEphemeral(true).queue()
                }
            }
        }
    }

    private fun resolveTargetGroup(e: SlashCommandInteractionEvent, rows: List<Relay>): String? {
        val arg = e.getOption("namelayer-group")?.asString
        if (arg != null) return arg
        if (rows.size == 1) return rows.first().namelayerGroup
        val groups = rows.joinToString(", ") { "`${MarkdownSafe.code(it.namelayerGroup)}`" }
        e.reply("Specify which group: $groups.").setEphemeral(true).queue()
        return null
    }

    private fun parseBool(s: String): Boolean? = when (s.lowercase()) {
        "true", "1", "yes", "on", "y" -> true
        "false", "0", "no", "off", "n" -> false
        else -> null
    }
}
