# CivDiscord

A Discord ↔ Minecraft bridge bot for Civ-style servers (CivMC, CivPVP, Eden, anywhere using CivModCore + JukeAlert + NameLayer). Replaces upstream [CivMC/Kira](https://github.com/CivMC/Kira) with a leaner, plugin-only deploy: **one Velocity plugin + one Paper companion**. No broker. No Postgres. No dedicated bot host.

## Features

- Discord ↔ MC chat relay scoped per NameLayer chat group
- JukeAlert snitch alerts forwarded to Discord
- `/admin run <server> <command>` console executor
- In-game ↔ Discord account linking (`/discord link` → `/link <code>`)
- Per-guild "verified" auth role on link
- Multi-guild: invite the bot to faction/nation Discords; each manages its own relay rooms
- Optional Patreon role sync

## Installing

1. Drop `CivDiscord-Velocity-*.jar` into your Velocity proxy's `plugins/` directory.
2. Drop `CivDiscord-Paper-*.jar` into **each** Paper backend's `plugins/` directory.
3. Start once — both plugins will write default `config.yml` files into their `plugins/civdiscord/` directories.
4. Edit `velocity/plugins/civdiscord/config.yml`:

```yaml
discord:
  token: your-bot-token        # or omit and set CIVDISCORD_DISCORD_TOKEN env var
  home_guild_id: 1234567890
database:
  path: civdiscord.db
# patreon block optional — see docs/ARCHITECTURE.md for the schema.
```

5. Edit each `<paper-backend>/plugins/civdiscord/config.yml`:

```yaml
server:
  name: citadel    # Velocity's name for this backend
```

6. Restart the proxy + backends.
7. In the home guild, run `/admin guild view` to confirm the bot is online and the config loaded.
8. (Optional) For each invited guild that wants auth-role grants on link, run `/admin guild auth-role role:@yourrole`.

### Bot invite scopes & permissions

Invite the bot with scopes `bot applications.commands` and the following minimum permissions:

- `Manage Roles` (only roles below the bot's top role)
- `View Channels`
- `Send Messages`
- `Use Slash Commands`
- `Embed Links` (for snitch alerts)

Do **NOT** grant `Administrator`. The bot does not need it.

## Permissions

Authorization rides on Discord's native application-command permissions. Defaults are intentionally narrow; guild owners can widen or narrow them via **Server Settings → Integrations → CivDiscord**. The defaults are:

| Command | Default permission | Scope |
| --- | --- | --- |
| `/link <code>` | `@everyone` | any guild the bot is in |
| `/me` | `@everyone` | any guild the bot is in |
| `/discord link`, `/discord status` (in-game) | all players | any backend |
| `/relay …` | `MANAGE_CHANNELS` (per-channel) | the channel it's run in |
| `/admin guild …` | `MANAGE_SERVER` (per-guild) | the guild it's run in — affects only that guild's auth role + relay roster |
| `/admin user …` | `MANAGE_SERVER` in the home guild | cross-network |
| `/admin run …` | `MANAGE_SERVER` in the home guild | dispatches console commands to **any** backend |

Note: a Discord guild owner can override these defaults from the Integrations UI. CivDiscord does no second-layer authorization — if Discord lets the user invoke the command, the bot trusts it.

## Operator runbook

### Rotating the bot token

1. Generate a new token in the Discord Developer Portal.
2. Edit `velocity/plugins/civdiscord/config.yml` and replace `discord.token`, **or** set `CIVDISCORD_DISCORD_TOKEN` in the proxy's environment and delete the `token:` line.
3. Restart the proxy. The Paper backends do not hold the token.

### Patreon access-token refresh

Patreon access tokens expire after ~31 days. If you've configured Patreon sync:

- Provide a `refresh_token` alongside `access_token` in `config.yml`. The bot will rotate the access token automatically when it expires and log the rotation.
- If the refresh token itself is revoked or expires (~1 year), Patreon role sync will start logging `ERROR` lines on every sweep. Re-authorize via the Patreon Creator Dashboard and update both fields.

### Database backup

The SQLite DB runs in WAL mode, so online backups are safe while the bot is running:

```bash
sqlite3 velocity/plugins/civdiscord/civdiscord.db ".backup civdiscord-$(date +%F).bak"
```

Schedule this from cron or your backup system. The DB is small (KBs–low MBs); keep daily snapshots.

### Recovering from a corrupt DB

The DB only holds link bindings, per-guild relay routes, guild config, and Patreon tier caches — none of it is irreplaceable.

1. Stop the proxy.
2. Move the corrupt `civdiscord.db` (and `-wal` / `-shm` siblings) aside.
3. Start the proxy. A fresh DB will be created and migrations re-run.
4. Operators re-run `/admin guild auth-role`, `/relay` for each channel, and players re-link via `/discord link` → `/link <code>`.

### Enabling debug logs

Add to your proxy's `logback.xml` (or backend's):

```xml
<logger name="io.github.grepsedawk.civdiscord" level="DEBUG"/>
```

Or set per-package, e.g. `…civdiscord.velocity.bridge` for just bridge traffic.

## Vendored libraries

`libs/` ships CivMC's plugin primitives (CivModCore, NameLayer, JukeAlert) plus ACF, vendored because they aren't published to a public Maven repository. Every jar in `libs/` has a corresponding entry in [`libs/CHECKSUMS.sha256`](libs/CHECKSUMS.sha256) with source URL and SHA-256.

**Reviewers MUST verify checksums when accepting any PR that touches `libs/`.** CI runs `shasum -a 256 -c libs/CHECKSUMS.sha256` on every push.

To refresh a vendored jar:

1. Download the new release from the source URL.
2. Replace the file in `libs/`.
3. Update its line in `libs/CHECKSUMS.sha256` with the new hash and version comment.
4. Open a PR; the CI checksum job will pass only if your hash matches.

## Threat model

CivDiscord is designed for the standard CivMC-style operator: Velocity proxy fronting trusted Paper backends, none directly reachable on the public internet.

- **Bridge protocol** (`civdiscord:bridge` plugin-message channel): proxy↔backend traffic is **not** authenticated by default. HMAC support exists (drop a `secret.key` in both `plugins/civdiscord/` dirs) and is **opt-in**. Do not expose Paper backends to direct connections from the public internet.
- **`/admin run`**: anyone with `MANAGE_SERVER` in the home guild can dispatch arbitrary console commands (op, deop, stop, ban-ip, anything). Treat home-guild `MANAGE_SERVER` as equivalent to root on every backend.
- **Link codes**: 6-character codes drawn from a CSPRNG, rate-limited per Discord user, single-use, expire after a short TTL. Each redemption is audit-logged. Codes are *not* secret long-term — they're one-shot — but the link flow prints them in plain in-game chat (see streamer note below).
- **Discord-side authorization** is entirely delegated to Discord's app-command permission system. CivDiscord does no second authorization check. Guild owners who widen defaults via the Integrations UI accept the consequences.
- **Patreon credentials** (if configured) are read from disk only. The bot does not log refresh tokens. Restrict `config.yml` permissions to `0600`.

### Streamer / content-creator note

`/discord link` prints the generated code in plain in-game chat. Streamers and content creators should be aware that codes are screenshot- and clip-leakable. Mitigations already in place:

- Codes are single-use and short-lived (TTL on the order of minutes).
- Each Discord user is rate-limited on `/link` attempts.
- Every redemption is audit-logged with timestamp + Discord user + claimed MC UUID.

If you accidentally publish a live code, just generate a new one via `/discord link` again — the old one expires immediately if redeemed, and naturally on TTL.

## Building from source

```bash
mise exec -- ./gradlew :velocity:shadowJar :paper:shadowJar
# Output: velocity/build/libs/CivDiscord-Velocity-*.jar
#         paper/build/libs/CivDiscord-Paper-*.jar
```

Requires Java 21 (pinned via `mise.toml`).

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module layout, bridge protocol, DB schema, link/relay sequence diagrams.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — dev environment, TDD, ktlint, PR checklist.
- [`SECURITY.md`](SECURITY.md) — vulnerability reporting + threat boundaries.
- [`CHANGELOG.md`](CHANGELOG.md) — release notes.

## License

MIT. Retains upstream Kira's copyright. See `LICENSE`.
