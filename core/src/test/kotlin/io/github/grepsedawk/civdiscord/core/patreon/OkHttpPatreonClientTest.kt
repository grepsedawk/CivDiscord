package io.github.grepsedawk.civdiscord.core.patreon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files

class OkHttpPatreonClientTest {
    private fun pageBody(
        memberIds: List<Pair<String, String>>,
        nextUrl: String?,
    ): String {
        val data =
            memberIds.joinToString(",") { (memberId, _) ->
                """
                {
                  "id": "$memberId",
                  "type": "member",
                  "relationships": {
                    "user": { "data": { "id": "u-$memberId", "type": "user" } },
                    "currently_entitled_tiers": { "data": [ { "id": "t1", "type": "tier" } ] }
                  }
                }
                """.trimIndent()
            }
        val included =
            memberIds.joinToString(",") { (memberId, discordId) ->
                """
                {
                  "id": "u-$memberId",
                  "type": "user",
                  "attributes": { "social_connections": { "discord": { "user_id": "$discordId" } } }
                }
                """.trimIndent()
            } + """,{"id":"t1","type":"tier","attributes":{"title":"Gold"}}"""
        val linksJson = if (nextUrl != null) ""","links":{"next":"$nextUrl"}""" else ""
        return """{"data":[$data],"included":[$included]$linksJson}"""
    }

    @Test
    fun `fetchCurrentTiers follows pagination across pages`() {
        val firstPage =
            pageBody(
                memberIds = (1..25).map { "m$it" to "${1000 + it}" },
                nextUrl = "https://www.patreon.com/page2",
            )
        val secondPage =
            pageBody(
                memberIds = (26..40).map { "m$it" to "${1000 + it}" },
                nextUrl = null,
            )
        val pages = listOf(firstPage, secondPage).iterator()
        val urlsHit = mutableListOf<String>()
        val fakeTransport =
            Interceptor { chain ->
                val req = chain.request()
                urlsHit.add(req.url.toString())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pages.next().toResponseBody("application/json".toMediaType()))
                    .build()
            }
        val client = OkHttpClient.Builder().addInterceptor(fakeTransport).build()
        val sut = OkHttpPatreonClient(client, accessToken = "tok", campaignId = "camp")

        val result = sut.fetchCurrentTiers()

        result.size shouldBe 40
        result.keys shouldContainExactlyInAnyOrder (1001L..1040L).toList()
        urlsHit.size shouldBe 2
        urlsHit[1] shouldBe "https://www.patreon.com/page2"
    }

    @Test
    fun `defaultHttpClient enforces timeouts and disables redirects`() {
        val c = OkHttpPatreonClient.defaultHttpClient()
        c.callTimeoutMillis shouldBe 60_000
        c.connectTimeoutMillis shouldBe 10_000
        c.readTimeoutMillis shouldBe 30_000
        c.followRedirects shouldBe false
        c.followSslRedirects shouldBe false
    }

    private fun jsonResponse(req: okhttp3.Request, code: Int, body: String): Response = Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Error")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    @Test
    fun `on 401 the client refreshes the token and retries once`() {
        val authTokensUsed = mutableListOf<String>()
        val pathsHit = mutableListOf<String>()
        val page200 = pageBody(memberIds = listOf("m1" to "555"), nextUrl = null)
        val transport = Interceptor { chain ->
            val req = chain.request()
            pathsHit.add(req.url.encodedPath)
            when (req.url.encodedPath) {
                "/api/oauth2/v2/campaigns/camp/members" -> {
                    val auth = req.header("Authorization").orEmpty()
                    authTokensUsed.add(auth)
                    if (auth == "Bearer stale") {
                        jsonResponse(req, 401, """{"errors":["expired"]}""")
                    } else {
                        jsonResponse(req, 200, page200)
                    }
                }
                "/api/oauth2/token" -> {
                    val form = req.body!!.let { body ->
                        val buf = okio.Buffer()
                        body.writeTo(buf)
                        buf.readUtf8()
                    }
                    form shouldContain "grant_type=refresh_token"
                    form shouldContain "refresh_token=rt1"
                    form shouldContain "client_id=cid"
                    form shouldContain "client_secret=csec"
                    jsonResponse(
                        req,
                        200,
                        """{"access_token":"fresh","refresh_token":"rt2","expires_in":2678400}""",
                    )
                }
                else -> error("Unexpected URL: ${req.url}")
            }
        }
        val client = OkHttpClient.Builder().addInterceptor(transport).build()
        val tokensFile = Files.createTempFile("patreon-tokens", ".json").toFile().apply { delete() }
        val creds = PatreonRefreshCreds(
            clientId = "cid",
            clientSecret = "csec",
            initialRefreshToken = "rt1",
            tokensFile = tokensFile,
        )
        val sut = OkHttpPatreonClient(
            client = client,
            accessToken = "stale",
            campaignId = "camp",
            refreshCreds = creds,
        )

        val result = sut.fetchCurrentTiers()

        result shouldBe mapOf(555L to "gold")
        authTokensUsed shouldBe listOf("Bearer stale", "Bearer fresh")
        creds.refreshToken shouldBe "rt2"
        tokensFile.exists() shouldBe true
        val persisted = tokensFile.readText()
        persisted shouldContain "fresh"
        persisted shouldContain "rt2"
    }

    @Test
    fun `401 without refresh creds throws a clear error`() {
        val transport = Interceptor { chain ->
            jsonResponse(chain.request(), 401, """{"errors":["expired"]}""")
        }
        val client = OkHttpClient.Builder().addInterceptor(transport).build()
        val sut = OkHttpPatreonClient(client, accessToken = "stale", campaignId = "camp")

        val ex = shouldThrow<IOException> { sut.fetchCurrentTiers() }
        ex.message!! shouldContain "refresh credentials missing"
    }

    @Test
    fun `401 then refresh endpoint failing surfaces refresh failure`() {
        val transport = Interceptor { chain ->
            val req = chain.request()
            when (req.url.encodedPath) {
                "/api/oauth2/v2/campaigns/camp/members" ->
                    jsonResponse(req, 401, """{"errors":["expired"]}""")
                "/api/oauth2/token" ->
                    jsonResponse(req, 400, """{"error":"invalid_grant"}""")
                else -> error("Unexpected URL: ${req.url}")
            }
        }
        val client = OkHttpClient.Builder().addInterceptor(transport).build()
        val creds = PatreonRefreshCreds("cid", "csec", "rt1")
        val sut = OkHttpPatreonClient(client, accessToken = "stale", campaignId = "camp", refreshCreds = creds)

        val ex = shouldThrow<IOException> { sut.fetchCurrentTiers() }
        ex.message!! shouldContain "Patreon refresh failed 400"
    }
}
