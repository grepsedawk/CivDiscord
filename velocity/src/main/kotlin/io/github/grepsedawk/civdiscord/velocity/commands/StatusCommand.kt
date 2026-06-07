package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.stats.StatsSnapshot
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class StatusCommand(
    private val sample: () -> StatsSnapshot,
    private val onlineNames: () -> List<String>,
    private val renderEmbed: (StatsSnapshot) -> MessageEmbed,
) {
    fun handle(event: SlashCommandInteractionEvent) {
        val names = onlineNames()
        // Headline count comes from the same live list as the roster so they agree; the cached
        // snapshot only feeds the slower-moving stats (TPS, peaks, uptime). Clamp the peaks up to
        // the live count so we never render an online count above its own peak when the roster grew
        // within the cache window.
        val live = names.size
        val cached = sample()
        val snapshot = cached.copy(
            playersOnline = live,
            peakToday = maxOf(cached.peakToday, live),
            peakAllTime = maxOf(cached.peakAllTime, live),
        )
        event.replyEmbeds(renderEmbed(snapshot))
            .addContent(formatRoster(names))
            .setAllowedMentions(emptyList<Message.MentionType>())
            .setEphemeral(true)
            .queue()
    }
}

// Player names are user-controlled (Bedrock/Geyser names can carry markdown); escape them and cap
// the list so the roster can't garble the card. Paired with setAllowedMentions to never ping.
internal fun formatRoster(names: List<String>): String = if (names.isEmpty()) "_nobody online_" else names.take(50).joinToString(", ") { MarkdownSafe.text(it) }
