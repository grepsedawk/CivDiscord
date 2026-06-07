package io.github.grepsedawk.civdiscord.velocity.stats

/** Updates the bot's presence text. Skips identical updates so the gateway isn't spammed.
 *  setActivity returns whether it actually applied; we only remember [last] on a confirmed
 *  apply, so a tick that fires before JDA is ready doesn't poison the dedup and leave the
 *  presence permanently unset when the count never changes. */
class PresencePublisher(private val setActivity: (String) -> Boolean) {
    @Volatile private var last: String? = null

    fun update(playersOnline: Int) {
        val text = "$playersOnline players online"
        if (text == last) return
        if (setActivity(text)) last = text
    }
}
