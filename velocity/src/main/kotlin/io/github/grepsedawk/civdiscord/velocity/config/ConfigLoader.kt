package io.github.grepsedawk.civdiscord.velocity.config

import org.yaml.snakeyaml.Yaml
import java.io.File

object ConfigLoader {
    private val ENV_PATTERN = Regex("\\$\\{([A-Z_][A-Z0-9_]*)\\}")

    fun load(dataDir: File, env: (String) -> String? = System::getenv): Config {
        val file = File(dataDir, "config.yml")
        if (!file.exists()) error("config.yml: not found in ${dataDir.absolutePath}")
        val raw = Yaml().load<Map<String, Any?>>(file.reader()) ?: emptyMap()

        val d = (raw["discord"] as? Map<*, *>) ?: error("config.yml: discord block is required")
        val tokenFromYaml = (d["token"] as? String)?.let { expand(it, env) }?.takeIf { it.isNotBlank() }
        val tokenFromEnv = env("CIVDISCORD_DISCORD_TOKEN")?.takeIf { it.isNotBlank() }
        val token =
            tokenFromEnv
                ?: tokenFromYaml
                ?: error("config.yml: discord.token is required (or set CIVDISCORD_DISCORD_TOKEN)")
        if (token == "REPLACE_ME") {
            error(
                "config.yml: discord.token still has the default placeholder REPLACE_ME — " +
                    "edit it before starting",
            )
        }
        val guild =
            (d["home_guild_id"] as? Number)?.toLong()
                ?: error("config.yml: discord.home_guild_id is required")
        if (guild == 0L) {
            error("config.yml: discord.home_guild_id is 0 — set it to your Discord guild ID")
        }

        val db = (raw["database"] as? Map<*, *>) ?: emptyMap<String, Any?>()
        val path = (db["path"] as? String) ?: "civdiscord.db"

        val bridge =
            (raw["bridge"] as? Map<*, *>)?.let { bm ->
                BridgeConfig(hmacEnabled = (bm["hmac_enabled"] as? Boolean) ?: false)
            } ?: BridgeConfig()

        val p =
            (raw["patreon"] as? Map<*, *>)?.let { pm ->
                val accessFromYaml = (pm["access_token"] as? String)?.let { expand(it, env) }
                val accessFromEnv = env("CIVDISCORD_PATREON_TOKEN")?.takeIf { it.isNotBlank() }
                val access =
                    accessFromEnv
                        ?: accessFromYaml
                        ?: error(
                            "config.yml: patreon.access_token is required when patreon block is present " +
                                "(or set CIVDISCORD_PATREON_TOKEN)",
                        )
                PatreonConfig(
                    accessToken = access,
                    campaignId =
                    (pm["campaign_id"] as? String)
                        ?: error("config.yml: patreon.campaign_id is required when patreon block is present"),
                    tierRoles =
                    ((pm["tier_roles"] as? Map<*, *>) ?: emptyMap<String, Any?>())
                        .mapNotNull { (k, v) ->
                            val n = (v as? Number)?.toLong() ?: return@mapNotNull null
                            (k as? String)?.let { it to n }
                        }.toMap(),
                    syncIntervalMinutes = (pm["sync_interval_minutes"] as? Number)?.toInt() ?: 60,
                    refreshToken = (pm["refresh_token"] as? String)?.let { expand(it, env) },
                    clientId = (pm["client_id"] as? String)?.let { expand(it, env) },
                    clientSecret = (pm["client_secret"] as? String)?.let { expand(it, env) },
                )
            }

        return Config(
            discord = DiscordConfig(token, guild),
            database = DatabaseConfig(path),
            patreon = p,
            bridge = bridge,
        )
    }

    private fun expand(value: String, env: (String) -> String?): String =
        ENV_PATTERN.replace(value) { match -> env(match.groupValues[1]) ?: match.value }
}
