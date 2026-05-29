package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class MessageRelayListener(private val chatRelay: ChatRelay) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        if (!event.isFromGuild) return
        val display = event.member?.effectiveName ?: event.author.effectiveName
        chatRelay.fromDiscord(
            channelId = event.channel.idLong,
            fromDisplay = display,
            text = event.message.contentDisplay,
        )
    }
}
