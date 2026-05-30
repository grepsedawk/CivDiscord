# Security policy

## Reporting a vulnerability

Please report security vulnerabilities **privately**. Do not open a public GitHub issue.

Preferred channels, in order:

1. **GitHub Security Advisory** — open a draft advisory on this repository ("Security" tab → "Report a vulnerability"). This keeps the report private until a fix is ready.
2. **Email** — `security@piechowski.org` with subject `CivDiscord: <short description>`. PGP key on request.

Please include:

- A description of the issue and the impact (what an attacker can do).
- Reproduction steps or a proof-of-concept.
- The affected version (jar filename or commit SHA).
- Any suggested mitigation.

We'll acknowledge within 72 hours and aim to ship a fix within 30 days for high-severity issues. We follow **90-day responsible disclosure**: if a fix isn't possible inside that window we'll coordinate with you on extension or disclosure.

## Supported versions

| Version | Supported |
| --- | --- |
| `main` (HEAD) | Yes |
| Tagged releases | Latest minor only |
| Older releases | No — please upgrade |

CivDiscord is pre-1.0; there is no LTS branch.

## Threat boundaries

The boundaries below describe where CivDiscord's trust assumptions sit. Bugs that cross a boundary are security issues; bugs that operate inside an already-trusted boundary are functional issues.

### Bridge channel (proxy ↔ Paper backends)

Velocity proxy and Paper backends communicate over the `civdiscord:bridge` plugin-message channel. By default this is **unauthenticated**: any plugin on either side that knows the channel name can read or inject payloads. HMAC signing is supported but **opt-in** — drop a `secret.key` file in Velocity's `plugins/civdiscord/` (lowercase) and each Paper backend's `plugins/CivDiscord/` (capital) data dir to enable.

Threat-model assumption: Paper backends are trusted and not directly reachable on the public internet. If you expose backends directly, enable HMAC and rotate the shared secret on operator changes.

### Link-code entropy

Link codes are 6-character codes drawn from a CSPRNG-backed alphabet. The space is intentionally large enough that brute-force at the per-Discord-user rate limit is infeasible inside the TTL window. Codes are single-use and audit-logged. Codes printed to in-game chat are still secret-while-live; see the streamer note in the README.

### Discord-side authorization

CivDiscord trusts Discord's app-command permission system as the sole authorization layer. If a user can invoke a command per Discord's UI, the bot runs it. Operators who widen permissions via **Server Settings → Integrations** are responsible for that decision. The bot does no second-layer permission check; do not rely on the bot to refuse a command Discord allowed.

Two operator-level guardrails worth knowing:

- `/admin run` is gated to `MANAGE_SERVER` in the **home guild** only. Anyone with that permission can dispatch arbitrary console commands to any backend. Treat home-guild `MANAGE_SERVER` as equivalent to root.
- `/admin guild` is gated to `MANAGE_SERVER` per-guild but only affects the guild it's run in. Granting it to a faction Discord doesn't expose the rest of the network.

### Patreon credentials

If Patreon role sync is configured, the bot reads `access_token` and `refresh_token` from `config.yml`. These are bearer credentials to your Patreon Creator account; protect the file with `chmod 0600` and a non-shared owner. The bot does not log refresh tokens. Access-token rotation is automatic when a `refresh_token` is supplied.

### Vendored libraries

`libs/` ships Civ plugin primitives. Each jar has a pinned SHA-256 in `libs/CHECKSUMS.sha256`; CI verifies on every build. PR reviewers must verify any change to those files. Replacing a vendored jar without updating its checksum will fail CI.

## Out of scope

- Denial of service via flooding Discord, the proxy, or backends with traffic. Discord rate-limits the bot side; operators are responsible for backend capacity.
- Vulnerabilities in upstream dependencies (JDA, Velocity, Paper, Exposed, SQLite, OkHttp, vendored libs). Please report to the upstream project. We will fast-track an upgrade once a fix is published.
- Social engineering of Discord guild owners or operators.
