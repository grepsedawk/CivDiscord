package io.github.grepsedawk.civdiscord.paper.chat

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import net.kyori.adventure.text.Component
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RemoteGroupDelivery(
    private val groupLookup: (name: String) -> Any?,
    private val isDisciplined: (group: Any) -> Boolean,
    private val sendRemote: (
        senderId: UUID,
        senderName: String,
        senderDisplayName: Component,
        groupName: String,
        message: Component,
    ) -> Unit,
    private val onMainThread: (Runnable) -> Unit,
    private val senderUuidFor: (Payload.ChatToMc) -> UUID,
) {
    private val log = LoggerFactory.getLogger(RemoteGroupDelivery::class.java)
    private val warnedMissing = ConcurrentHashMap.newKeySet<String>()
    private val warnedDisciplined = ConcurrentHashMap.newKeySet<String>()

    fun handle(msg: Payload.ChatToMc) {
        onMainThread(Runnable { deliver(msg) })
    }

    private fun deliver(msg: Payload.ChatToMc) {
        val group = groupLookup(msg.namelayerGroup)
        if (group == null) {
            if (warnedMissing.add(msg.namelayerGroup)) {
                log.warn("Discord→MC: unknown NameLayer group {}", msg.namelayerGroup)
            }
            return
        }
        if (isDisciplined(group)) {
            if (warnedDisciplined.add(msg.namelayerGroup)) {
                log.warn("Discord→MC: group {} is disciplined", msg.namelayerGroup)
            }
            return
        }
        sendRemote(
            senderUuidFor(msg),
            msg.from,
            Component.text("[Discord] ").append(Component.text(msg.from)),
            msg.namelayerGroup,
            Component.text(msg.text),
        )
    }
}
