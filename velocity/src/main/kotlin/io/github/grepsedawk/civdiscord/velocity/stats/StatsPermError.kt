package io.github.grepsedawk.civdiscord.velocity.stats

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color

/** Red, ephemeral reply for a /stats bind the bot can't honor because it lacks channel
 *  permissions. The bind is rejected, so the message says plainly that nothing changed. */
object StatsPermError {
    private val RED = Color(0xE74C3C)

    fun embed(channelId: Long, missing: List<Permission>): MessageEmbed {
        val perms = missing.joinToString(", ") { it.getName() }
        return EmbedBuilder()
            .setColor(RED)
            .setTitle("Missing permissions")
            .setDescription(
                "I can't use <#$channelId> yet. Give me $perms there, then run this again. I didn't bind anything.",
            )
            .build()
    }
}
