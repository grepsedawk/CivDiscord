package io.github.grepsedawk.civdiscord.velocity.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData

class CommandRegistrar(private val jda: JDA, private val homeGuildId: Long) {
    fun register() {
        jda.updateCommands().addCommands(
            Commands.slash("link", "Complete an in-game-initiated Discord<->MC link")
                .addOption(OptionType.STRING, "code", "The code shown after running /discord link in-game", true),
            Commands.slash("me", "Show your binding, Patreon tier, and linked roles"),
            Commands.slash("relay", "Bind Discord channels to NameLayer chat groups")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addSubcommands(
                    SubcommandData("bind", "Bind this channel to a NameLayer chat group")
                        .addOption(OptionType.STRING, "namelayer-group", "Group name", true),
                    SubcommandData("unbind", "Remove this channel's relay binding"),
                    SubcommandData("list", "List all relays in this guild"),
                    SubcommandData("show", "Show this channel's binding and settings"),
                    SubcommandData("set", "Update a property of this channel's relay")
                        .addOption(OptionType.STRING, "property", "show-snitches | chat-format", true)
                        .addOption(OptionType.STRING, "value", "new value", true),
                ),
            Commands.slash("admin", "Bot administration")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommandGroups(
                    SubcommandGroupData("guild", "Per-guild configuration").addSubcommands(
                        SubcommandData("auth-role", "Set the auth role granted on link for this guild")
                            .addOption(OptionType.ROLE, "role", "Role to grant", true),
                        SubcommandData("view", "Show this guild's CivDiscord config"),
                    ),
                ),
        ).queue(null) {}

        // Guild-scoped registration overrides the global `admin` command in the home guild —
        // Discord prioritizes guild-scoped over global with the same name. Intentional: the
        // home guild gets the extra `user` group and `run` subcommand, and instant
        // propagation while iterating (global commands can take up to an hour).
        jda.getGuildById(homeGuildId)?.updateCommands()?.addCommands(
            Commands.slash("admin", "Bot administration")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommandGroups(
                    SubcommandGroupData("user", "Manage CivDiscord users").addSubcommands(
                        SubcommandData("view", "Inspect a binding")
                            .addOption(OptionType.USER, "discord-user", "User to inspect", true),
                        SubcommandData("unlink", "Force-remove a binding")
                            .addOption(OptionType.USER, "discord-user", "User to unlink", true),
                    ),
                    SubcommandGroupData("guild", "Per-guild configuration").addSubcommands(
                        SubcommandData("auth-role", "Set the auth role granted on link for this guild")
                            .addOption(OptionType.ROLE, "role", "Role to grant", true),
                        SubcommandData("view", "Show this guild's CivDiscord config"),
                    ),
                )
                .addSubcommands(
                    SubcommandData("run", "Run a Minecraft console command")
                        .addOption(OptionType.STRING, "server", "Backend server name", true, true)
                        .addOption(OptionType.STRING, "command", "Command to execute", true),
                ),
        )?.queue(null) {}
    }
}
