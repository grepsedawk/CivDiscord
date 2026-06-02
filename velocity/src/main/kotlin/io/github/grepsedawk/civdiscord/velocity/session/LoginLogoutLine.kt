package io.github.grepsedawk.civdiscord.velocity.session

/**
 * Renders a [Transition] as one Discord line. Time uses Discord's dynamic timestamp
 * (`<t:EPOCH:T>` = long time with seconds, shown in each viewer's local zone). Player names
 * are Minecraft usernames ([A-Za-z0-9_]) and server names are trusted proxy config, so no
 * markdown escaping is needed. Returns null for [Transition.None] (nothing to post).
 */
object LoginLogoutLine {
    fun render(name: String, transition: Transition, epochSeconds: Long): String? {
        val time = "<t:$epochSeconds:T>"
        return when (transition) {
            is Transition.Login -> "$time `[${transition.server}]` $name logged in"
            is Transition.Logout -> "$time `[${transition.server}]` $name logged out"
            is Transition.Switch -> "$time $name moved from `${transition.from}` to `${transition.to}`"
            Transition.None -> null
        }
    }
}
