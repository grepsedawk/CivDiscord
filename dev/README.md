# Local development & smoke

## One-time setup

1. **Discord developer portal**: create a bot, enable `GUILD_MEMBERS`, `MESSAGE_CONTENT`, and `GUILD_MESSAGES` intents. Note the bot token + your test guild ID.
2. **Test guild**: invite the bot with the OAuth URL from the dev portal. Grant it `MANAGE_ROLES` (so it can assign auth roles).
3. **Local MC** — a Velocity proxy + one Paper backend, both running Java 21:
   ```
   dev/
     velocity/       (Velocity proxy install — drop in `velocity.toml` and the CivDiscord jar)
     paper-citadel/  (Paper backend named "citadel" — CivModCore/JukeAlert/NameLayer + CivDiscord jar)
   ```

## Build + install

```bash
mise exec -- ./gradlew :velocity:shadowJar :paper:shadowJar
cp velocity/build/libs/CivDiscord-Velocity-*.jar dev/velocity/plugins/
cp paper/build/libs/CivDiscord-Paper-*.jar      dev/paper-citadel/plugins/
```

Edit `dev/velocity/plugins/civdiscord/config.yml` and `dev/paper-citadel/plugins/CivDiscord/config.yml` with your test token + server name (`citadel`). Note: Velocity's data dir is `civdiscord/` (lower), Paper's is `CivDiscord/` (capital). Case matters on Linux.

## Smoke tests — every release must pass these manually

1. **Linker**:
   - Join the Paper server, run `/discord link`. Confirm the code appears as a private message (not in chat).
   - Run `/link <code>` in the test Discord guild. Confirm ephemeral "Linked to MC account `…`" reply.
   - Confirm your account received the configured auth role (set via `/admin guild auth-role`).

2. **Relink semantics**:
   - In MC, run `/discord link` again. Get a new code.
   - Run `/link <new-code>` — confirm "Linked to MC account `…`" again. Old binding is replaced.

3. **Chat relay**:
   - In Discord, run `/relay bind namelayer-group:townhall` in a test channel.
   - In MC, send a message in the `townhall` NameLayer chat group. Confirm it appears in the bound Discord channel.
   - In Discord, send a message in that channel. Confirm it appears in the MC `townhall` chat.

4. **Snitch alert**:
   - Run `/relay set property:show-snitches value:true` in the bound channel.
   - Trigger a JukeAlert snitch in the `townhall` group (e.g. another player walks past).
   - Confirm a snitch line appears in the Discord channel.

5. **Console command**:
   - In the home guild, run `/admin run server:citadel command:say hello`.
   - Confirm "hello" appears in the Paper server console + the in-game chat.

6. **Force unlink**:
   - In the home guild, run `/admin user unlink discord-user:@you`.
   - Run `/me` in Discord. Confirm "You are not linked yet."

7. **Multi-guild auth role**:
   - Invite the bot to a second test guild.
   - In that guild, run `/admin guild auth-role role:@verified`.
   - In Discord, ensure your account doesn't have that role yet.
   - In MC, run `/discord link` + `/link <code>` in the second guild.
   - Confirm the role is granted in BOTH guilds (home + second).

8. **Patreon** (if configured):
   - Trigger a Patreon webhook OR wait for the sync interval.
   - Confirm `/me` shows the tier and the configured Discord role is added.

## End-to-end smoke checklist

Walk every UX path on a fresh dev Velocity + Paper. For each step: do the action, confirm the **expected** UI, then tail the listed log to confirm the **expected** log line.

Logs to keep tailed:

- Velocity: `dev/velocity/logs/latest.log` — look for the `[civdiscord]` prefix from `org.slf4j` (JDA gateway events, bridge dispatch, Patreon sync warnings).
- Paper: `dev/paper-citadel/logs/latest.log` — look for the `[civdiscord]` prefix (plugin load banner, command dispatch).

At startup, you should see:

- Velocity: `CivDiscord-Velocity ready` (and earlier `Wrote default config.yml …` on first boot).
- Paper:    `CivDiscord-Paper loaded for server 'citadel'`.

If you see `JDA never readied — aborting plugin init`, your bot token or intents are wrong — fix `config.yml` and restart.

### 1. Link flow (`/discord link` → `/link <code>`)

- **Do**: On Paper, run `/discord link` as a player.
- **Expect (MC)**: A chat line like `Requested a link code…` followed shortly by a private DM-style line carrying the code (or a fallback in-chat line if the bot can't DM you).
- **Expect (Velocity log)**: A `LinkRequest` bridge payload routed (debug: `Routed plugin message from citadel (… bytes)`).
- **Do**: In Discord, run `/link code:<code>` in **any** guild the bot is in.
- **Expect (Discord)**: Ephemeral reply `Linked to MC account \`<name>\`.`.
- **Expect (Velocity log)**: `Linked user <id> joined guild <id> — granting auth role` (only if the executing guild has an auth-role configured and you are already a member).
- **Failure surfaces**:
  - `No such code or expired.` — code wrong or older than the TTL.
  - `That Minecraft account is already linked to <@…>. Ask an admin to /admin user unlink first.` — collision; resolve via step 7.

### 2. `/me`

- **Do (unlinked)**: Run `/me`. Expect `You are not linked yet. Run /discord link in-game to start.`
- **Do (linked)**: After step 1, run `/me` again. Expect `MC: \`<name>\` (\`<uuid>\`)` + `Linked at: <timestamp>` + (if Patreon configured & tier present) `Patreon tier: <tier>`.

### 3. `/relay bind`

- **Do**: In a fresh test channel, run `/relay bind namelayer-group:townhall`.
- **Expect**: Ephemeral `Bound this channel to NameLayer group \`townhall\`.`
- **Re-bind same channel**: Expect `Channel already bound to \`townhall\`. Run /relay unbind first.`
- **Log**: nothing notable; persisted to SQLite (`civdiscord.db` next to the Velocity jar).

### 4. `/relay list`

- **Do**: In any channel in the same guild, run `/relay list`.
- **Expect**: Ephemeral message listing every bound channel for this guild, one per line: `• <#channel> ↔ \`townhall\`` (with ` (+snitches)` suffix for relays that have it enabled).
- **Empty guild**: `No relays configured for this guild.`

### 5. `/relay show`

- **Do**: In the bound channel from step 3, run `/relay show`.
- **Expect**: Ephemeral block with **Channel**, **Group**, **Snitches** (`false`), **Format** (`(default)`).
- **Unbound channel**: `This channel is not bound to anything.`

### 6. `/relay set`

- **Do (show-snitches)**: `/relay set property:show-snitches value:true` → `Set show-snitches=true.`
- **Do (bad value)**: `/relay set property:show-snitches value:maybe` → `Invalid value \`maybe\`. Use one of: true/false/yes/no/on/off/1/0.`
- **Do (chat-format)**: `/relay set property:chat-format value:[MC] {name}: {text}` → `Set chat-format=\`[MC] {name}: {text}\`.`
- **Verify rendered output**: Send a chat in the `townhall` group from MC. The Discord webhook line must read `[MC] <name>: <text>` (no `**name** [\`server\`]:` prefix). If Discord still shows the default format, the chat-format column is not being read at render time — see ChatRelay.dispatch.
- **Reset format**: `/relay set property:chat-format value:null` → `Set chat-format=\`(default)\`.` Send another MC chat; verify Discord output reverts to `**<name>** [\`<server>\`]: <text>`.
- **Unknown property**: `/relay set property:nope value:x` → `Unknown property: \`nope\`. Valid: show-snitches, chat-format.`
- **On unbound channel**: `This channel is not bound — run /relay bind first.`

### 7. `/relay unbind`

- **Do**: In the bound channel, run `/relay unbind`.
- **Expect**: `Unbound.`
- **Run again immediately**: `This channel is not bound to anything.`
- **Re-bind** for the remaining steps: `/relay bind namelayer-group:townhall`.

### 8. Chat relay (MC → Discord)

- **Do**: Join the Paper server, send a message in the `townhall` NameLayer chat group.
- **Expect (Discord)**: A message in the bound channel formatted `**<name>** [\`citadel\`]: <text>`. Discord mentions (`@everyone`, `<@id>`) and markdown control chars in your MC text should be neutralized (zero-width space inserted, backslash-escaped).
- **Expect (Velocity log)**: debug `Routed plugin message from citadel (… bytes)`.

### 9. Chat relay (Discord → MC)

- **Do**: In the bound channel, type a normal message (no slash).
- **Expect (MC)**: A `townhall` chat group line attributed to your Discord display name.
- **Note**: if nothing arrives, confirm the channel is still bound via `/relay show` and that the Paper server is reachable from the proxy.

### 10. Snitch alert

- **Pre**: `/relay set property:show-snitches value:true` in the bound channel.
- **Do**: Place a JukeAlert snitch under the `townhall` group; trigger it with a second player walking past (enter), logging in inside its radius (login), or logging out inside (logout).
- **Expect (Discord)**: A snitch line in the bound channel naming the intruder, the snitch, coordinates, and the kind (`ENTER`/`LOGIN`/`LOGOUT`).
- **Expect (Paper log)**: nothing dedicated; the event just flows through `SnitchListener` → bridge.
- **No alert?**: confirm `show-snitches=true` via `/relay show`; confirm the snitch's group matches the bound `namelayer-group`.

### 11. `/admin run`

- **Do**: In the home guild, run `/admin run server:citadel command:say hello world`.
- **Expect (Discord)**: A "thinking…" ephemeral that resolves to the captured console output (`(no output)` for commands that print nothing).
- **Expect (Paper console)**: `[Server] hello world` (the `say` broadcast).
- **Unknown server**: `Unknown server \`xyz\`. Known: \`citadel\``.
- **Unknown command**: ephemeral resolves to `unknown command: <cmd>`.
- **Dispatch failure** (Paper offline): ephemeral resolves to `Failed to dispatch console request: …`.

### 12. `/admin user view`

- **Do**: `/admin user view discord-user:@you`.
- **Expect (linked)**: `<@you> → MC \`<name>\` (\`<uuid>\`), linked <timestamp>`.
- **Expect (unlinked)**: `<@you> is not linked.`

### 13. `/admin user unlink`

- **Do**: `/admin user unlink discord-user:@you`.
- **Expect**: `Unlinked <@you>.`
- **Repeat**: `<@you> was not linked.`
- **Confirm**: `/me` now says `You are not linked yet.`
- **Re-link** for the remaining steps: repeat step 1.

### 14. `/admin guild auth-role`

- **Do**: `/admin guild auth-role role:@verified` in the home guild.
- **Expect**: `Auth role set to <@&verified>.`
- **Missing/unknown role**: `Missing role.` / `Unknown role.`
- **Guild not tracked yet** (race on first boot): `This guild isn't tracked yet. Try again in a moment.` — wait a few seconds and retry.
- **Auto-grant on join**: have your test account leave + rejoin the guild. Expect Velocity log `Linked user <id> joined guild <id> — granting auth role` and the role applied automatically.

### 15. `/admin guild view`

- **Do**: `/admin guild view`.
- **Expect**: `Guild config:\n- Auth role: <@&verified>` (or `(unset)` if never set).

### 16. Multi-guild auth role

- Invite the bot to a second test guild; grant `MANAGE_ROLES`.
- In the second guild: `/admin guild auth-role role:@verified-2`.
- From an unlinked state in MC, run `/discord link` then `/link <code>` in the second guild.
- **Expect**: the auth role applied in **both** the home guild and the second guild (link grants across every tracked guild that has an auth-role configured and where you are a member).
- **Log**: one `Linked user <id> joined guild <id> — granting auth role` per guild.

### 17. Patreon sync (only if `patreon:` block configured)

- **Do (pull)**: Wait for the configured sync interval, or restart Velocity to trigger an immediate tick.
- **Expect (Velocity log on failure)**: `Patreon sync failed` warning with the exception. On success, no log noise.
- **Expect (Discord)**: `/me` now shows `Patreon tier: <tier>` for the linked Discord user with an active pledge; the configured Discord role for that tier is applied.
- **Remove pledge**: after the next sync interval, the tier line disappears from `/me` and the role is revoked.

### Pass / fail

Every section above must show the expected UI **and** the expected log. Any deviation is a regression — file a follow-up task, do not ship.

## Smoke log

- _YYYY-MM-DD (vX.Y.Z): all 17 flows PASS / FAIL with notes._
