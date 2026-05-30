package io.github.grepsedawk.civdiscord.paper.config

import org.yaml.snakeyaml.Yaml
import java.io.File

object ConfigLoader {
    fun load(dataDir: File): Config {
        val file = File(dataDir, "config.yml")
        if (!file.exists()) error("config.yml: not found in ${dataDir.absolutePath}")
        val raw = Yaml().load<Map<String, Any?>>(file.reader()) ?: emptyMap()
        val server = (raw["server"] as? Map<*, *>) ?: emptyMap<String, Any?>()
        val name =
            (server["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: error("config.yml: server.name is required")
        if (name == "REPLACE_ME") {
            error(
                "config.yml: server.name still has the default placeholder REPLACE_ME — " +
                    "edit it before starting",
            )
        }
        val bridge =
            (raw["bridge"] as? Map<*, *>)?.let { bm ->
                BridgeConfig(hmacEnabled = (bm["hmac_enabled"] as? Boolean) ?: true)
            } ?: BridgeConfig()
        return Config(serverName = name, bridge = bridge)
    }
}
