package io.github.grepsedawk.civdiscord.paper.config

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ConfigLoaderTest {
    @Test
    fun `loads server name from config yaml`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText("server:\n  name: citadel\n")
        val cfg = ConfigLoader.load(dir)
        cfg.serverName shouldBe "citadel"
    }

    @Test
    fun `missing server name throws with a clear message`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText("server:\n")
        val ex = runCatching { ConfigLoader.load(dir) }.exceptionOrNull()!!
        (ex.message ?: "") shouldContain "server.name"
    }
}
