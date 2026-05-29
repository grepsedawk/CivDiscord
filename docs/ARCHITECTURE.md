# CivDiscord architecture

A field guide for the next engineer. Read top-to-bottom on first contact; afterwards jump to the section you need.

## Module layout

CivDiscord is a Gradle multi-module project with three modules:

```
core/      pure-JVM, no Velocity/Paper API on the classpath
velocity/  shaded into the Velocity plugin jar; depends on core
paper/     shaded into the Paper plugin jar; depends on core
```

### `core/`

Everything testable without booting a server lives here:

- `core.bridge` — the wire-format `Payload` sealed class, `BridgeCodec` (kotlinx.serialization JSON), `BridgeChannel` (the `civdiscord:bridge` channel name constant), `BridgeSigner` (optional HMAC).
- `core.db` — Exposed-backed SQLite tables and DAOs (`BindingDao`, `GuildDao`, `RelayDao`, `PatreonTierDao`), `MigrationRunner`, the migration list.
- `core.patreon` — Patreon API client + tier-to-role mapping.
- `core.relay` — relay routing rules (NameLayer group → set of Discord channels).
- `core.text` — `MarkdownSafe` (Discord output escaping), chat formatting.
- `core.admin`, `core.auth` — cross-module helpers used by both sides of the bridge.

`core/` has **no** dependency on the Velocity or Paper APIs. This keeps it unit-testable on plain JVM and reusable from both plugin sides.

### `velocity/`

The Velocity plugin: JDA gateway, Discord command tree, link state machine, snitch fan-out, chat relay router, bridge **proxy** side.

- `velocity.discord` — JDA bootstrap, command registrar.
- `velocity.commands` — `/link`, `/me`, `/admin guild`, `/admin user`, `/admin run`, `/relay`.
- `velocity.bridge` — `PluginMessageEvent` handler; sends `Payload`s to specific backends or fan-out.
- `velocity.chat` — Discord-message-received → ChatToMc, plus the inverse from `ChatToDiscord`.
- `velocity.snitch` — `SnitchHit` → Discord embed.
- `velocity.auth` — link state, role granting (`RoleGranter`), Patreon role reconciliation.
- `velocity.patreon` — scheduled refresh of tier cache.

### `paper/`

The Paper companion: hooks into NameLayer for group lookups, JukeAlert for snitch events, Bukkit chat for relay, console for `/admin run` dispatch. Owns the bridge **backend** side.

- `paper.bridge` — `PluginMessageListener`; verifies optional HMAC; deserializes; dispatches.
- `paper.chat` — async chat → `ChatToDiscord`; `ChatToMc` → in-game broadcast scoped to NameLayer group.
- `paper.jukealert` — listener for snitch events → `SnitchHit`.
- `paper.linker` — `/discord link` (in-game) → `LinkRequest`; receives `LinkReply` with the code.
- `paper.namelayer` — `NameLayerQuery` / `NameLayerReply`.
- `paper.console` — `ConsoleRequest` → captured-output `ConsoleReply`.

## Trust + threading model

### Trust boundaries

```
        ┌────────────────┐         ┌────────────────┐
Discord ◀──── JDA ─────▶ │ Velocity │ ◀── plug-msg ──▶ │ Paper backend │ ◀── players
                          └────────────────┘         └────────────────┘
        ▲                        ▲                          ▲
        │                        │                          │
   trusted by                 trusted by                 player input,
   bot operator           HMAC (opt-in) or              MarkdownSafe-
                         network topology              escaped before
                                                       leaving toward
                                                       Discord
```

- **Discord → bot**: authorization delegated to Discord's app-command permission system.
- **Proxy ↔ backend**: unauthenticated by default; HMAC opt-in via shared `secret.key`. Operators assume backends are not directly reachable.
- **Backend → proxy → Discord**: all user-controlled strings (player names, chat content, snitch names) flow through `MarkdownSafe` before interpolation into Discord output.

### Threading model

CivDiscord crosses several thread pools; staying on the right one matters.

| Pool | Owner | Use |
| --- | --- | --- |
| **JDA gateway** | JDA | Receives Discord events. Cheap work only — do not block. |
| **JDA REST callbacks** | JDA | `.queue(...)` continuation. Never call `.complete()` here. |
| **Velocity scheduler (async)** | Velocity | DB I/O, OkHttp calls, bridge sends. The right place for most bot logic. |
| **Bukkit main thread** | Paper | Bukkit world reads/writes. Required for snitch lookups, console dispatch, in-game chat broadcast. |
| **Bukkit async scheduler** | Paper | Bridge plugin-message decode, DB I/O. |

Rules of thumb:

- **Never** call `.complete()` on a JDA RestAction inside a JDA event handler — it deadlocks.
- DB transactions go on the Velocity async scheduler (proxy) or Bukkit async (backend).
- Anything that touches `Server.getOnlinePlayers()`, `Bukkit.dispatchCommand`, or NameLayer group state runs on the Bukkit main thread. Route via `Bukkit.getScheduler().runTask(...)`.
- The Velocity-side Patreon sync and pending-hook sweep have their own scheduled tasks; do not piggy-back unrelated jobs on them.

## Bridge protocol

The proxy and each backend exchange `Payload` instances over the `civdiscord:bridge` plugin-message channel.

- **Wire format**: kotlinx.serialization JSON, UTF-8. Sealed-class polymorphism is keyed by `@SerialName` discriminators (`snitch_hit`, `console_request`, `chat_to_mc`, …).
- **Codec**: `BridgeCodec.encode(Payload): ByteArray` / `decode(ByteArray): Payload`. Encoding includes an optional HMAC tag when a `secret.key` is configured.
- **HMAC**: `BridgeSigner` wraps a shared HMAC-SHA-256 secret. When configured on both sides, the codec prepends a tag to every message and verifies on receipt. Unsigned messages are rejected when HMAC is enabled; messages from before HMAC was enabled simply fail to decode.
- **Source filtering**: the Velocity `PluginMessageEvent` handler verifies `event.source` is a known backend before decoding; the Paper `PluginMessageListener` verifies the channel and (when HMAC is enabled) the tag.

### Payload variants

Defined in `core/bridge/Payload.kt`:

- `SnitchHit` — JukeAlert event → Discord embed.
- `ConsoleRequest` / `ConsoleReply` — `/admin run` dispatch + captured output.
- `LinkRequest` / `LinkReply` — `/discord link` (in-game) → bot generates code → backend prints it.
- `ChatToMc` / `ChatToDiscord` — bidirectional chat scoped to a NameLayer group.
- `NameLayerQuery` / `NameLayerReply` — proxy asks a backend which NameLayer groups a UUID belongs to.

## DB schema

SQLite, WAL mode, accessed via Exposed. Located at the path in `database.path` of the Velocity config.

| Table | Purpose |
| --- | --- |
| `schema_migrations` | Applied-migration ledger (managed by `MigrationRunner`). |
| `bindings` | Discord user ↔ MC UUID linkages; one-to-one, audit fields for created-at. |
| `guilds` | Per-guild config: auth role ID, soft-delete flag, etc. |
| `relays` | NameLayer group ↔ Discord channel routes; one group fans out to many channels. |
| `patreon_tiers` | Cached Patreon-tier → Discord-role mapping. |

Migrations live in `core/src/main/resources/db/migrations/` as numbered `Vxxx__name.sql` files. The current set:

```
V001__init.sql
V002__bindings.sql
V003__guilds.sql
V004__relays.sql
V005__patreon_tiers.sql
V006__guilds_soft_delete.sql
V009__bindings_indexes.sql
V010__relays_guild_index.sql
V011__relays_group_index.sql
```

Adding a new migration:

1. Create `Vxxx__short_name.sql` in `core/src/main/resources/db/migrations/`.
2. Append its filename to `Migrations.ALL` in `MigrationRunner.kt`.
3. Add a test in `core/src/test/` that exercises the new schema or DAO.

Migrations are append-only; never edit a `Vxxx` file once it's been released. To revise, write a `Vyyy` that fixes the prior one.

## Link flow

```
Player                Paper                     Velocity (bot)              Discord
  │ /discord link        │                              │                       │
  ├─────────────────────▶│                              │                       │
  │                      │  LinkRequest(id, uuid, name) │                       │
  │                      ├─────────────────────────────▶│                       │
  │                      │                              │ generate code (CSPRNG)│
  │                      │                              │ store: code→uuid TTL  │
  │                      │  LinkReply(id, code, null)   │                       │
  │                      │◀─────────────────────────────┤                       │
  │  "your code: ABC123" │                              │                       │
  │◀─────────────────────┤                              │                       │
  │                                                     │                       │
  │ /link ABC123 (in Discord)                           │                       │
  │─────────────────────────────────────────────────────────────────────────────▶
  │                                                     │   slash interaction   │
  │                                                     │◀──────────────────────┤
  │                                                     │ look up code → uuid   │
  │                                                     │ write bindings row    │
  │                                                     │ grant per-guild role  │
  │                                                     │  (via RoleGranter)    │
  │                                                     │   reply: "linked"     │
  │                                                     ├──────────────────────▶│
```

Failure modes worth knowing:

- Code expired or unknown → reply "not found", rate-limit counter increments.
- Player already linked → reply "already linked", no DB write.
- Code rate-limit exceeded for the Discord user → reply "too many attempts", cooldown.

## Chat relay flow

```
Player (MC)        Paper                  Velocity              Discord channel
  │ chat message     │                       │                        │
  ├─────────────────▶│                       │                        │
  │                  │ look up NL group      │                        │
  │                  │ ChatToDiscord(...)    │                        │
  │                  ├──────────────────────▶│                        │
  │                  │                       │ RelayDao: group→chans  │
  │                  │                       │ format + MarkdownSafe  │
  │                  │                       │ send to each channel   │
  │                  │                       ├───────────────────────▶│

Discord user → channel
                                              │  message-received     │
                                              │◀──────────────────────┤
                                              │ RelayDao: chan→group  │
                                              │ ChatToMc(*, group, …) │
                                  ◀───────────┤                       │
                  │ filter: hosts the group?  │                       │
                  │ broadcast in-game to NL   │                       │
                  │ group members             │                       │
```

The proxy holds the single source of truth for relay routing in `relays`; each backend is unaware of which channels a group fans out to.

## Adding a new slash command

1. Define the JDA `SlashCommandData` in the command registrar (`velocity/discord/CommandRegistrar`).
2. Implement a handler that takes the `SlashCommandInteractionEvent`.
3. Wire it into the command dispatcher.
4. Write the test in `velocity/src/test/.../commands/` against a mocked JDA.
5. Document the default permission in `README.md` under **Permissions**.

If the command needs backend cooperation, see the next section.

## Adding a new bridge payload variant

1. Add a `@Serializable @SerialName("…") data class Foo(…) : Payload()` to `core/bridge/Payload.kt`.
2. Add a sender on the originating side (`velocity/bridge/BridgeSender.kt` or `paper/bridge/BridgeSender.kt`).
3. Add a handler slot on the receiving side — extend the `when` in the receiver's `PluginMessageEvent` / `PluginMessageListener` so the compiler enforces exhaustiveness on the sealed class.
4. Write a round-trip codec test in `core/src/test/.../bridge/` and a handler test on the receiving module.
5. If the payload carries user-controlled strings that will reach Discord, ensure they pass through `MarkdownSafe`.

## What's not here yet

Things on the roadmap; touched briefly so you don't accidentally reinvent them:

- **Bridge protocol versioning.** Today there is no version field; payloads are forward-incompatible on schema change. Once we cut a v1.0 we'll add one.
- **Per-relay targeting.** `ChatToMc.server` currently only ever carries `"*"`; per-relay-route backend targeting is a future refinement.
- **Multi-region failover.** Single SQLite file, single Velocity proxy. Don't deploy this expecting HA.
