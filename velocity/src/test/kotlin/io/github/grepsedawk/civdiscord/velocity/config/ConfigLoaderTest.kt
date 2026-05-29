package io.github.grepsedawk.civdiscord.velocity.config

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ConfigLoaderTest {

    @Test
    fun `loads minimal valid config`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: bot-token-xxx
              home_guild_id: 12345
            database:
              path: civdiscord.db
            """.trimIndent(),
        )
        val cfg = ConfigLoader.load(dir)
        cfg.discord.token shouldBe "bot-token-xxx"
        cfg.discord.homeGuildId shouldBe 12345L
        cfg.database.path shouldBe "civdiscord.db"
        cfg.patreon shouldBe null
    }

    @Test
    fun `loads optional patreon config when present`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: bot-token-xxx
              home_guild_id: 12345
            database:
              path: civdiscord.db
            patreon:
              access_token: pat-tok
              campaign_id: c123
              tier_roles:
                gold: 11
                silver: 22
              sync_interval_minutes: 60
            """.trimIndent(),
        )
        val cfg = ConfigLoader.load(dir)
        val p = cfg.patreon!!
        p.accessToken shouldBe "pat-tok"
        p.tierRoles shouldBe mapOf("gold" to 11L, "silver" to 22L)
        p.syncIntervalMinutes shouldBe 60
    }

    @Test
    fun `missing discord token raises clear error`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText("discord:\n  home_guild_id: 1\n")
        val ex = runCatching { ConfigLoader.load(dir, env = { null }) }.exceptionOrNull()!!
        ex.message!! shouldContain "discord.token is required"
    }

    @Test
    fun `rejects REPLACE_ME token placeholder`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: REPLACE_ME
              home_guild_id: 12345
            """.trimIndent(),
        )
        val ex = runCatching { ConfigLoader.load(dir) }.exceptionOrNull()!!
        ex.message!! shouldContain "REPLACE_ME"
    }

    @Test
    fun `rejects home_guild_id of zero`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: bot-token-xxx
              home_guild_id: 0
            """.trimIndent(),
        )
        val ex = runCatching { ConfigLoader.load(dir) }.exceptionOrNull()!!
        ex.message!! shouldContain "home_guild_id"
    }

    @Test
    fun `env CIVDISCORD_DISCORD_TOKEN overrides yaml token`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: yaml-token
              home_guild_id: 12345
            """.trimIndent(),
        )
        val cfg = ConfigLoader.load(dir, env = { name -> if (name == "CIVDISCORD_DISCORD_TOKEN") "env-token" else null })
        cfg.discord.token shouldBe "env-token"
    }

    @Test
    fun `env CIVDISCORD_DISCORD_TOKEN is used when yaml has no token`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              home_guild_id: 12345
            """.trimIndent(),
        )
        val cfg = ConfigLoader.load(dir, env = { name -> if (name == "CIVDISCORD_DISCORD_TOKEN") "env-token" else null })
        cfg.discord.token shouldBe "env-token"
    }

    @Test
    fun `dollar-brace env var substitution expands inside yaml strings`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: "${'$'}{MY_TOKEN_VAR}"
              home_guild_id: 12345
            """.trimIndent(),
        )
        val cfg = ConfigLoader.load(dir, env = { name -> if (name == "MY_TOKEN_VAR") "substituted" else null })
        cfg.discord.token shouldBe "substituted"
    }

    @Test
    fun `env CIVDISCORD_PATREON_TOKEN overrides yaml access_token`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: bot-token-xxx
              home_guild_id: 12345
            patreon:
              access_token: yaml-pat
              campaign_id: c123
              tier_roles: {}
              sync_interval_minutes: 60
            """.trimIndent(),
        )
        val cfg =
            ConfigLoader.load(dir, env = { name -> if (name == "CIVDISCORD_PATREON_TOKEN") "env-pat" else null })
        cfg.patreon!!.accessToken shouldBe "env-pat"
    }

    @Test
    fun `bridge hmac_enabled defaults to false and can be flipped`() {
        val dir = Files.createTempDirectory("civd").toFile()
        File(dir, "config.yml").writeText(
            """
            discord:
              token: bot-token-xxx
              home_guild_id: 12345
            bridge:
              hmac_enabled: true
            """.trimIndent(),
        )
        val cfg = ConfigLoader.load(dir, env = { null })
        cfg.bridge.hmacEnabled shouldBe true
    }
}
