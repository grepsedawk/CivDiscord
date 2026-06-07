package io.github.grepsedawk.civdiscord.velocity.session

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import java.time.Instant

/** Records every proxy login into the seen-players table (first-seen wins) for unique/new counts. */
class SeenPlayerListener(
    private val record: (uuid: String, epoch: Long) -> Unit,
    private val now: () -> Long = { Instant.now().epochSecond },
) {
    fun record(uuid: String) = record(uuid, now())

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        record(event.player.uniqueId.toString())
    }
}
