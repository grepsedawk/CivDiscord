package io.github.grepsedawk.civdiscord.velocity.session

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface Transition {
    data class Login(val server: String) : Transition
    data class Switch(val from: String, val to: String) : Transition
    data class Logout(val server: String) : Transition
    data object None : Transition
}

/** Tracks each player's current backend server so logout/switch lines know the server(s) involved. */
class ConnectionTracker {
    private val current = ConcurrentHashMap<UUID, String>()

    fun connected(player: UUID, server: String): Transition {
        val previous = current.put(player, server)
        return when {
            previous == null -> Transition.Login(server)
            previous == server -> Transition.None
            else -> Transition.Switch(previous, server)
        }
    }

    fun disconnected(player: UUID): Transition {
        val last = current.remove(player)
        return if (last == null) Transition.None else Transition.Logout(last)
    }
}
