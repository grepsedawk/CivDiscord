package io.github.grepsedawk.civdiscord.core.bridge

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PayloadRoundTripTest {

    private inline fun <reified T : Payload> roundTrip(p: T) {
        val encoded = BridgeCodec.encode(p)
        val decoded = BridgeCodec.decode(encoded)
        decoded shouldBe p
    }

    @Test fun `SnitchHit round-trips`() = roundTrip(
        Payload.SnitchHit(
            "citadel", "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002", 1, 64, -2, "TownNorth", "townhall", "ENTER",
        ),
    )

    @Test fun `ConsoleRequest round-trips`() = roundTrip(
        Payload.ConsoleRequest("req-1", "citadel", "say hello"),
    )

    @Test fun `ConsoleReply round-trips`() = roundTrip(
        Payload.ConsoleReply("req-1", true, "ok"),
    )

    @Test fun `LinkRequest round-trips`() = roundTrip(
        Payload.LinkRequest(
            "req-2",
            "00000000-0000-0000-0000-000000000001",
            "alice",
        ),
    )

    @Test fun `LinkReply round-trips`() = roundTrip(
        Payload.LinkReply("req-2", "swift-otter-71", error = null),
    )

    @Test fun `ChatToMc round-trips`() = roundTrip(
        Payload.ChatToMc("*", "townhall", "@alice", "hi everyone"),
    )

    @Test fun `ChatToDiscord round-trips`() = roundTrip(
        Payload.ChatToDiscord(
            "lobby",
            "00000000-0000-0000-0000-000000000001",
            "alice",
            "townhall",
            "hi",
        ),
    )

    @Test fun `NameLayerQuery round-trips`() = roundTrip(
        Payload.NameLayerQuery(
            "req-3",
            "00000000-0000-0000-0000-000000000001",
        ),
    )

    @Test fun `NameLayerReply round-trips`() = roundTrip(
        Payload.NameLayerReply("req-3", listOf("townhall", "ironsworn")),
    )

    @Test fun `StatusRequest round-trips`() = roundTrip(
        Payload.StatusRequest("req-4", "00000000-0000-0000-0000-000000000001"),
    )

    @Test fun `StatusReply round-trips when linked`() = roundTrip(
        Payload.StatusReply("req-4", discordId = 123456789L, mcName = "alice", linkedAt = 1_700_000_000L),
    )

    @Test fun `StatusReply round-trips when unlinked`() = roundTrip(
        Payload.StatusReply("req-4", discordId = null, mcName = null, linkedAt = null),
    )
}
