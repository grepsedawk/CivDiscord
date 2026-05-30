package io.github.grepsedawk.civdiscord.paper.jukealert

import com.untamedears.jukealert.events.PlayerHitSnitchEvent
import com.untamedears.jukealert.events.PlayerLoginSnitchEvent
import com.untamedears.jukealert.events.PlayerLogoutSnitchEvent
import com.untamedears.jukealert.model.Snitch
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SnitchListener(
    private val serverName: String,
    private val send: (Payload) -> Unit,
    private val logger: Logger = LoggerFactory.getLogger(SnitchListener::class.java),
    // PlayerLoginSnitchEvent fires synchronously inside JukeAlert's playerJoinEvent (MONITOR
    // priority), microseconds before the joining player's plugin-message pipeline through
    // Velocity is fully routable for the freshly-connected backend — synchronous
    // sendPluginMessage gets silently dropped on the proxy. Defer LOGIN dispatch by a tick
    // or two so the carrier's channel has settled. ENTER/LOGOUT stay synchronous.
    private val scheduleLogin: (Runnable) -> Unit = { it.run() },
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
        intruderName: String? = null,
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
                intruderName = intruderName,
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
            intruderName = player.name,
        )
    }

    @EventHandler
    fun onHit(event: PlayerHitSnitchEvent) {
        runCatching { handle(event.snitch, event.player, Kind.ENTER) }
            .onFailure { logger.warn("SnitchListener.{} threw — Bukkit would have swallowed this", "ENTER", it) }
    }

    @EventHandler
    fun onLogin(event: PlayerLoginSnitchEvent) {
        val snitch = event.snitch
        val player = event.player
        scheduleLogin(
            Runnable {
                runCatching { handle(snitch, player, Kind.LOGIN) }
                    .onFailure { logger.warn("SnitchListener.{} threw — Bukkit would have swallowed this", "LOGIN", it) }
            },
        )
    }

    @EventHandler
    fun onLogout(event: PlayerLogoutSnitchEvent) {
        runCatching { handle(event.snitch, event.player, Kind.LOGOUT) }
            .onFailure { logger.warn("SnitchListener.{} threw — Bukkit would have swallowed this", "LOGOUT", it) }
    }

    internal fun handle(snitch: Snitch?, player: Player?, kind: Kind) {
        if (snitch == null) {
            logger.warn(
                "SnitchListener.{}: event.snitch was null — JukeAlert dispatched event with no snitch reference",
                kind,
            )
            return
        }
        if (player == null) {
            logger.warn(
                "SnitchListener.{}: event.player was null for snitch id={} group={} — " +
                    "Bukkit.getPlayer(uuid) returned null (player offline or mid-login/logout); SnitchHit dropped",
                kind,
                snitch.id,
                snitch.group?.name,
            )
            return
        }
        val summary = summarize(snitch)
        if (summary.namelayerGroup.isBlank()) {
            logger.warn(
                "SnitchListener.{}: snitch id={} name='{}' has no NameLayer group; skipping send (no relay can match '')",
                kind,
                snitch.id,
                summary.name,
            )
            return
        }
        logger.info(
            "SnitchHit dispatch: kind={} group={} snitch='{}' intruder={} ({})",
            kind,
            summary.namelayerGroup,
            summary.name,
            player.name,
            player.uniqueId,
        )
        dispatch(player, summary, kind)
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
