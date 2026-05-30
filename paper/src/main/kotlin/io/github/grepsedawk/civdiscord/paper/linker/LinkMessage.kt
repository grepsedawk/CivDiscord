package io.github.grepsedawk.civdiscord.paper.linker

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor

fun buildLinkMessage(token: String): Component = Component.text("Discord link code: ")
    .append(
        Component.text(token)
            .color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.copyToClipboard(token))
            .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))),
    )
    .append(Component.text(" — paste /link <code> in Discord (10 min)"))
