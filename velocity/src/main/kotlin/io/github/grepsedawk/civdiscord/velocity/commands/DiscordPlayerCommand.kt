package io.github.grepsedawk.civdiscord.velocity.commands

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import io.github.grepsedawk.civdiscord.core.auth.LinkTokenStore
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger

class DiscordPlayerCommand(
    private val linkTokens: LinkTokenStore,
    private val bindings: BindingDao,
    private val logger: Logger,
) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        handle(invocation.source(), invocation.arguments())
    }

    fun handle(source: CommandSource, args: Array<out String>) {
        if (source !is Player) {
            source.sendMessage(Component.text("/discord can only be run by a player."))
            return
        }
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> sendHelp(source)
            "link" -> startLink(source)
            "status" -> showStatus(source)
            else -> source.sendMessage(
                Component.text("Unknown subcommand: ${args[0]}. Try /discord help."),
            )
        }
    }

    private fun sendHelp(p: Player) {
        p.sendMessage(Component.text("/discord link – start linking your Discord account"))
        p.sendMessage(Component.text("/discord status – show your current Discord link"))
    }

    private fun startLink(p: Player) {
        val existing = bindings.findByMcUuid(p.uniqueId)
        if (existing != null) {
            p.sendMessage(
                Component.text("Already linked to Discord user ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(existing.discordId.toString()).color(NamedTextColor.GREEN))
                    .append(Component.text(". Run /discord status for details.").color(NamedTextColor.GRAY)),
            )
            return
        }
        val token = linkTokens.mint(p.uniqueId, p.username)
        logger.info("minted Discord link token for mc={} ({})", p.username, p.uniqueId)
        p.sendMessage(buildLinkMessage(token.code))
    }

    private fun showStatus(p: Player) {
        val binding = bindings.findByMcUuid(p.uniqueId)
        val msg = if (binding == null) {
            Component.text("No Discord account linked. Run /discord link to start.")
                .color(NamedTextColor.GRAY)
        } else {
            Component.text("Linked to Discord user ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(binding.discordId.toString()).color(NamedTextColor.GREEN))
                .append(Component.text(" (mc: ").color(NamedTextColor.GRAY))
                .append(Component.text(binding.mcName).color(NamedTextColor.GREEN))
                .append(Component.text(").").color(NamedTextColor.GRAY))
        }
        p.sendMessage(msg)
    }

    private fun buildLinkMessage(token: String): Component = Component.text("Discord link code: ")
        .append(
            Component.text(token)
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.copyToClipboard(token))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))),
        )
        .append(Component.text(" — paste /link <code> in Discord (10 min)"))
}
