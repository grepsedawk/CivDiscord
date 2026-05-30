package io.github.grepsedawk.civdiscord.core.patreon

import io.github.grepsedawk.civdiscord.core.util.tryLockdown
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface PatreonClient {
    /** Returns map of Discord-user-id -> tier-name (lowercased). */
    fun fetchCurrentTiers(): Map<Long, String>
}

/**
 * Holds OAuth credentials and exchanges an expired access_token for a fresh one.
 * `clientId`+`clientSecret` are required for refresh; without them a 401 is fatal.
 * `tokensFile` is where refreshed tokens are persisted (created mode 0600) so the
 * plugin keeps working across restarts.
 */
class PatreonRefreshCreds(
    val clientId: String,
    val clientSecret: String,
    initialRefreshToken: String,
    val tokensFile: File? = null,
) {
    @Volatile var refreshToken: String = initialRefreshToken
        private set

    fun updateRefreshToken(newRefreshToken: String) {
        refreshToken = newRefreshToken
    }
}

class OkHttpPatreonClient(
    private val client: OkHttpClient = defaultHttpClient(),
    accessToken: String,
    private val campaignId: String,
    private val refreshCreds: PatreonRefreshCreds? = null,
) : PatreonClient {
    private val log = LoggerFactory.getLogger(OkHttpPatreonClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var accessToken: String = accessToken

    override fun fetchCurrentTiers(): Map<Long, String> {
        val acc = mutableMapOf<Long, String>()
        var url: String? =
            "https://www.patreon.com/api/oauth2/v2/campaigns/$campaignId/members" +
                "?include=currently_entitled_tiers,user" +
                "&fields[member]=patron_status" +
                "&fields[tier]=title" +
                "&fields[user]=social_connections" +
                "&page[count]=200"
        while (url != null) {
            val body = fetchPage(url)
            val (page, nextUrl) = PatreonResponseParser.parse(body)
            acc.putAll(page)
            url = nextUrl
        }
        return acc
    }

    private fun fetchPage(url: String): String {
        val first = doGet(url)
        if (first.code == 401) {
            val creds = refreshCreds
                ?: throw IOException(
                    "Patreon API 401: ${first.bodyString} — refresh credentials missing " +
                        "(set patreon.refresh_token, patreon.client_id, patreon.client_secret)",
                )
            log.warn("Patreon access_token rejected (401); attempting OAuth refresh")
            refreshAccessToken(creds)
            val retry = doGet(url)
            if (!retry.successful) {
                throw IOException("Patreon API ${retry.code} after refresh: ${retry.bodyString}")
            }
            return retry.bodyString
        }
        if (!first.successful) throw IOException("Patreon API ${first.code}: ${first.bodyString}")
        return first.bodyString
    }

    private data class HttpResult(val code: Int, val bodyString: String) {
        val successful: Boolean get() = code in 200..299
    }

    private fun doGet(url: String): HttpResult {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            return HttpResult(resp.code, resp.body?.string().orEmpty())
        }
    }

    private fun refreshAccessToken(creds: PatreonRefreshCreds) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", creds.refreshToken)
            .add("client_id", creds.clientId)
            .add("client_secret", creds.clientSecret)
            .build()
        val req = Request.Builder()
            .url("https://www.patreon.com/api/oauth2/token")
            .post(form)
            .build()
        val body = client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Patreon refresh failed ${resp.code}: $text")
            }
            text
        }
        val obj = json.parseToJsonElement(body).jsonObject
        val newAccess = obj["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Patreon refresh response missing access_token")
        val newRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: creds.refreshToken
        accessToken = newAccess
        creds.updateRefreshToken(newRefresh)
        creds.tokensFile?.let { persistTokens(it, newAccess, newRefresh) }
        log.info("Patreon OAuth refresh succeeded; new access_token in effect")
    }

    private fun persistTokens(file: File, accessToken: String, refreshToken: String) {
        try {
            file.parentFile?.mkdirs()
            val escAccess = accessToken.replace("\\", "\\\\").replace("\"", "\\\"")
            val escRefresh = refreshToken.replace("\\", "\\\\").replace("\"", "\\\"")
            val payload = """{"access_token":"$escAccess","refresh_token":"$escRefresh"}"""
            file.writeText(payload)
            tryLockdown(file.toPath())
        } catch (e: IOException) {
            log.warn("Failed to persist refreshed Patreon tokens to ${file.absolutePath}: ${e.message}")
        }
    }

    companion object {
        /**
         * Hardened OkHttpClient for Patreon: explicit timeouts and no redirect following so
         * an attacker-controlled redirect cannot leak the Authorization header to another host.
         */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
