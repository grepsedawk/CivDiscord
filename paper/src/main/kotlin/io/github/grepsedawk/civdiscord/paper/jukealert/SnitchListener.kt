package io.github.grepsedawk.civdiscord.paper.jukealert

import com.untamedears.jukealert.events.PlayerHitSnitchEvent
import com.untamedears.jukealert.events.PlayerLoginSnitchEvent
import com.untamedears.jukealert.events.PlayerLogoutSnitchEvent
import com.untamedears.jukealert.model.Snitch
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class SnitchListener(
    private val serverName: String,
    private val send: (Payload) -> Unit,
) : Listener {

    enum class Kind { ENTER, LOGIN, LOGOUT }

    data class SnitchSummary(
        val name: String,
        val ownerUuid: String,
        val namelayerGroup: String,
        val x: Int,
        val y: Int,
        val z: Int,
    )

    /** Pure function — directly testable without spinning up the server. */
    fun report(
        intruderUuid: String,
        ownerUuid: String,
        x: Int,
        y: Int,
        z: Int,
        snitchName: String,
        namelayerGroup: String,
        kind: Kind,
    ) {
        send(
            Payload.SnitchHit(
                server = serverName,
                snitchOwnerUuid = ownerUuid,
                intruderUuid = intruderUuid,
                x = x, y = y, z = z,
                snitchName = snitchName,
                namelayerGroup = namelayerGroup,
                kind = kind.name,
            ),
        )
    }

    internal fun dispatch(player: Player, summary: SnitchSummary, kind: Kind) {
        report(
            intruderUuid = player.uniqueId.toString(),
            ownerUuid = summary.ownerUuid,
            x = summary.x,
            y = summary.y,
            z = summary.z,
            snitchName = summary.name,
            namelayerGroup = summary.namelayerGroup,
            kind = kind,
        )
    }

    @EventHandler
    fun onHit(event: PlayerHitSnitchEvent) {
        dispatch(event.player ?: return, summarize(event.snitch ?: return), Kind.ENTER)
    }

    @EventHandler
    fun onLogin(event: PlayerLoginSnitchEvent) {
        dispatch(event.player ?: return, summarize(event.snitch ?: return), Kind.LOGIN)
    }

    @EventHandler
    fun onLogout(event: PlayerLogoutSnitchEvent) {
        dispatch(event.player ?: return, summarize(event.snitch ?: return), Kind.LOGOUT)
    }

    private fun summarize(snitch: Snitch): SnitchSummary {
        val loc = snitch.location
        return SnitchSummary(
            name = snitch.name.orEmpty(),
            ownerUuid = (snitch.group?.owner ?: snitch.placer)?.toString().orEmpty(),
            namelayerGroup = snitch.group?.name.orEmpty(),
            x = loc?.blockX ?: 0,
            y = loc?.blockY ?: 0,
            z = loc?.blockZ ?: 0,
        )
    }
}
