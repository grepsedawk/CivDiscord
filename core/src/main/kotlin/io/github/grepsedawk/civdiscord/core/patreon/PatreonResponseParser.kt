package io.github.grepsedawk.civdiscord.core.patreon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object PatreonResponseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private const val PATREON_HOST = "www.patreon.com"

    /** Walks the Patreon members payload extracting (Discord-id -> tier-title-lowercased) and the next-page URL. */
    fun parse(body: String): Pair<Map<Long, String>, String?> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyMap<Long, String>() to null
        val included = root["included"]?.jsonArray ?: JsonArray(emptyList())
        val nextUrl =
            safeNextUrl(root["links"]?.jsonObject?.get("next")?.jsonPrimitive?.contentOrNull)

        val userDiscord = mutableMapOf<String, Long>()
        val tierTitle = mutableMapOf<String, String>()
        for (it in included) {
            val obj = it.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "user" -> {
                    val social = obj["attributes"]?.jsonObject?.get("social_connections")?.jsonObject
                    val discordId =
                        social?.get("discord")?.jsonObject?.get("user_id")
                            ?.jsonPrimitive?.contentOrNull
                    val parsed = discordId?.toLongOrNull() ?: continue
                    userDiscord[id] = parsed
                }
                "tier" -> {
                    val title = obj["attributes"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                    if (title != null) tierTitle[id] = title.lowercase()
                }
            }
        }

        val out = mutableMapOf<Long, String>()
        for (member in data) {
            val rel = member.jsonObject["relationships"]?.jsonObject ?: continue
            val userId =
                rel["user"]?.jsonObject?.get("data")?.jsonObject
                    ?.get("id")?.jsonPrimitive?.contentOrNull ?: continue
            val tierIds =
                rel["currently_entitled_tiers"]?.jsonObject?.get("data")?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
            val tier = tierIds.mapNotNull(tierTitle::get).firstOrNull() ?: continue
            val discord = userDiscord[userId] ?: continue
            out[discord] = tier
        }
        return out to nextUrl
    }

    internal fun safeNextUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val url = raw.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        if (url.host != PATREON_HOST) return null
        return raw
    }
}
