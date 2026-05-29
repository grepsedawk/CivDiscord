package io.github.grepsedawk.civdiscord.core.patreon

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PatreonResponseParserTest {
    @Test
    fun `parse extracts discord-id to tier and exposes next link`() {
        val body =
            """
            {
              "data": [
                {
                  "id": "m1",
                  "type": "member",
                  "relationships": {
                    "user": { "data": { "id": "u1", "type": "user" } },
                    "currently_entitled_tiers": { "data": [ { "id": "t1", "type": "tier" } ] }
                  }
                }
              ],
              "included": [
                {
                  "id": "u1",
                  "type": "user",
                  "attributes": {
                    "social_connections": { "discord": { "user_id": "42" } }
                  }
                },
                {
                  "id": "t1",
                  "type": "tier",
                  "attributes": { "title": "Gold" }
                }
              ],
              "links": { "next": "https://www.patreon.com/api/oauth2/v2/page2" }
            }
            """.trimIndent()

        val (rows, nextUrl) = PatreonResponseParser.parse(body)
        rows shouldContainExactly mapOf(42L to "gold")
        nextUrl shouldBe "https://www.patreon.com/api/oauth2/v2/page2"
    }

    @Test
    fun `parse returns null next-link when absent`() {
        val body =
            """
            { "data": [], "included": [] }
            """.trimIndent()

        val (rows, nextUrl) = PatreonResponseParser.parse(body)
        rows.isEmpty() shouldBe true
        nextUrl.shouldBeNull()
    }

    @Test
    fun `parse skips malformed include rows`() {
        val body =
            """
            {
              "data": [
                {
                  "id": "mGood",
                  "type": "member",
                  "relationships": {
                    "user": { "data": { "id": "uGood", "type": "user" } },
                    "currently_entitled_tiers": { "data": [ { "id": "tGood", "type": "tier" } ] }
                  }
                },
                {
                  "id": "mNoIdUser",
                  "type": "member",
                  "relationships": {
                    "user": { "data": { "id": "uMissingId", "type": "user" } },
                    "currently_entitled_tiers": { "data": [ { "id": "tGood", "type": "tier" } ] }
                  }
                },
                {
                  "id": "mNullDiscord",
                  "type": "member",
                  "relationships": {
                    "user": { "data": { "id": "uNullDiscord", "type": "user" } },
                    "currently_entitled_tiers": { "data": [ { "id": "tGood", "type": "tier" } ] }
                  }
                },
                {
                  "id": "mEmptyDiscord",
                  "type": "member",
                  "relationships": {
                    "user": { "data": { "id": "uEmptyDiscord", "type": "user" } },
                    "currently_entitled_tiers": { "data": [ { "id": "tGood", "type": "tier" } ] }
                  }
                }
              ],
              "included": [
                {
                  "type": "user",
                  "attributes": { "social_connections": { "discord": { "user_id": "777" } } }
                },
                {
                  "id": "uMissingId-but-actually-present",
                  "type": "user",
                  "attributes": { "social_connections": { "discord": { "user_id": "not-a-number" } } }
                },
                {
                  "id": "uNullDiscord",
                  "type": "user",
                  "attributes": { "social_connections": { "discord": { "user_id": null } } }
                },
                {
                  "id": "uEmptyDiscord",
                  "type": "user",
                  "attributes": { "social_connections": { "discord": { "user_id": "" } } }
                },
                {
                  "id": "uGood",
                  "type": "user",
                  "attributes": { "social_connections": { "discord": { "user_id": "100" } } }
                },
                {
                  "id": "tGood",
                  "type": "tier",
                  "attributes": { "title": "Silver" }
                }
              ]
            }
            """.trimIndent()

        val (rows, _) = PatreonResponseParser.parse(body)
        rows shouldContainExactly mapOf(100L to "silver")
    }

    @Test
    fun `safeNextUrl accepts canonical patreon https URL`() {
        PatreonResponseParser.safeNextUrl("https://www.patreon.com/api/oauth2/v2/page2") shouldBe
            "https://www.patreon.com/api/oauth2/v2/page2"
    }

    @Test
    fun `safeNextUrl rejects attacker host`() {
        PatreonResponseParser.safeNextUrl("https://attacker.com/api/oauth2/v2/page2").shouldBeNull()
    }

    @Test
    fun `safeNextUrl rejects patreon subdomain spoof`() {
        PatreonResponseParser.safeNextUrl("https://patreon.com.attacker.io/page").shouldBeNull()
    }

    @Test
    fun `safeNextUrl rejects plaintext http`() {
        PatreonResponseParser.safeNextUrl("http://www.patreon.com/api/oauth2/v2/page2").shouldBeNull()
    }

    @Test
    fun `safeNextUrl rejects malformed`() {
        PatreonResponseParser.safeNextUrl("not a url at all").shouldBeNull()
        PatreonResponseParser.safeNextUrl("").shouldBeNull()
        PatreonResponseParser.safeNextUrl(null).shouldBeNull()
    }

    @Test
    fun `parse discards next link from non-patreon host`() {
        val body =
            """
            {
              "data": [],
              "included": [],
              "links": { "next": "https://evil.example.com/page2" }
            }
            """.trimIndent()

        val (_, nextUrl) = PatreonResponseParser.parse(body)
        nextUrl.shouldBeNull()
    }
}
