package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ReplyRegistryTest {

    @Test
    fun `register returns future that completes when matching reply arrives`() {
        val r = ReplyRegistry()
        val future = r.register("q1")
        r.complete(Payload.NameLayerReply(id = "q1", linkedGroups = listOf("g1", "g2")))
        val got = future.get(50, TimeUnit.MILLISECONDS) as Payload.NameLayerReply
        got.linkedGroups shouldBe listOf("g1", "g2")
    }

    @Test
    fun `complete with unknown id is a no-op`() {
        val r = ReplyRegistry()
        r.complete(Payload.NameLayerReply(id = "ghost", linkedGroups = emptyList()))
        // no exception
    }

    @Test
    fun `future not completed for non-matching id times out`() {
        val r = ReplyRegistry()
        val future = r.register("q2")
        r.complete(Payload.NameLayerReply(id = "other", linkedGroups = emptyList()))
        try {
            future.get(20, TimeUnit.MILLISECONDS)
            error("expected timeout")
        } catch (_: TimeoutException) {
        }
    }

    @Test
    fun `discard removes pending future`() {
        val r = ReplyRegistry()
        val future = r.register("q3")
        r.discard("q3")
        r.complete(Payload.NameLayerReply(id = "q3", linkedGroups = listOf("x")))
        future.isDone shouldBe false
    }
}
