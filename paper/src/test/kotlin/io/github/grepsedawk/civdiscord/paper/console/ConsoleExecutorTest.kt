package io.github.grepsedawk.civdiscord.paper.console

import be.seeseemelk.mockbukkit.MockBukkit
import io.github.grepsedawk.civdiscord.core.bridge.Payload
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConsoleExecutorTest {

    @BeforeEach fun setup() {
        MockBukkit.mock()
    }

    @AfterEach fun teardown() {
        MockBukkit.unmock()
    }

    @Test
    fun `executes known command and returns ok=true`() {
        val exec = ConsoleExecutor(MockBukkit.getMock()!!, "citadel")
        val reply = exec.run(Payload.ConsoleRequest("r-1", "citadel", "version"))
        reply.id shouldBe "r-1"
        reply.ok shouldBe true
    }

    @Test
    fun `unknown command returns ok=false with message`() {
        val exec = ConsoleExecutor(MockBukkit.getMock()!!, "citadel")
        val reply = exec.run(Payload.ConsoleRequest("r-2", "citadel", "absolutelyNotAKnownCmd"))
        reply.ok shouldBe false
        reply.output shouldContain "unknown command: absolutelyNotAKnownCmd"
    }

    @Test
    fun `rejects request for a different server`() {
        val exec = ConsoleExecutor(MockBukkit.getMock()!!, "citadel")
        val reply = exec.run(Payload.ConsoleRequest("r-3", "lobby", "version"))
        reply.ok shouldBe false
        reply.output shouldBe "wrong server"
    }

    @Test
    fun `accepts broadcast wildcard server`() {
        val exec = ConsoleExecutor(MockBukkit.getMock()!!, "citadel")
        val reply = exec.run(Payload.ConsoleRequest("r-4", "*", "version"))
        reply.ok shouldBe true
        reply.output shouldNotContain "wrong server"
    }
}
