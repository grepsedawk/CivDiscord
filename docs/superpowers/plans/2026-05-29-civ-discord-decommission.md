# CivPVP: Decommissioning upstream Kira on the VPS

Once CivDiscord is verified in production for CivPVP (smoke log green, no regressions reported for >=1 week), retire the upstream Kira deploy on the IONOS VPS. **This work lives in `CivPVP-Infra`, not in this repo.**

## What's being retired

The legacy stack consists of:

- `kira` container (CivMC's Discord bridge bot).
- `rabbitmq` broker (Kira <-> gateway plugin transport, TLS on TCP 5671).
- `postgres` database (Kira's account-binding store, Patreon cache).
- Per-server `kira-gateway` Velocity/Paper plugins on each mint-servers slot.

CivDiscord replaces all four with a single Velocity plugin (`civdiscord-velocity`) plus a thin Paper companion (`civdiscord-paper`). No broker, no Postgres, no separate bot process.

## Migration notes

- **Bindings do not migrate.** Kira's Postgres schema for `discord_id <-> minecraft_uuid` is incompatible with CivDiscord's storage. Users must re-link via `/discord link` after cutover. Announce this in `#announcements` 48h before cutover and pin a one-liner explaining the new command.
- **Patreon role state does not migrate.** CivDiscord recomputes Patreon tiers on its next sync after install; expect a short window (<= one sync interval) where supporter roles are stale.
- **Chat history is not bridged.** No backfill of bridged messages exists in either system; the gap during cutover is unavoidable.
- **Channel IDs and webhook URLs are reusable** if CivDiscord's `config.yml` is pointed at the same channels Kira used. Confirm with the Discord admin before reusing.

## Cutover plan

Sequence on cutover day:

1. **T-48h:** Post the re-link announcement in Discord and in-game MOTD.
2. **T-1h:** Snapshot Kira's Postgres (`pg_dump`) and RabbitMQ definitions (`rabbitmqctl export_definitions`) to `/var/backups/kira-decom/`. Keep until the two-week rollback window closes.
3. **T-0:** Run the new `civdiscord.yml` Ansible playbook. Role renders per-instance `config.yml`, lftp-pushes the Velocity + Paper jars to each mint-servers SFTP slot, then `ansible.builtin.pause` for the manual panel restart.
4. **T+0:** Restart each server's panel. Velocity loads `civdiscord-velocity`; each backend loads `civdiscord-paper`.
5. **T+5m:** Verify in Discord: bot online, slash commands registered, bridged chat flowing, `/discord link` issues a code, redeeming the code in-game assigns the linked role.
6. **T+15m:** Stop the `kira` container only (leave `rabbitmq` + `postgres` running): `ansible-playbook playbooks/kira.yml --tags=stop`. This is the rollback-friendly midpoint.
7. **T+1 week:** If smoke log stays green, proceed with the VPS cleanup checklist below.
8. **T+2 weeks:** Delete RabbitMQ + Postgres data volumes.

## VPS cleanup checklist (CivPVP-Infra)

- [ ] Stop `kira` container: `ansible-playbook playbooks/kira.yml --tags=stop`.
- [ ] Stop `rabbitmq` + `postgres` containers (no other tenants depend on them).
- [ ] Remove the `kira`, `rabbitmq`, `postgres` services from `roles/kira/templates/kira.yml.j2` (or delete the role entirely).
- [ ] Delete the IONOS Cloud Panel firewall rule for TCP 5671 (broker TLS). No other service uses this port.
- [ ] Archive `~/Code/CivPVP/kira/` and `~/Code/CivPVP/kira-gateway/` to a `legacy/` sibling directory.
- [ ] Add a new `civdiscord` Ansible role + `civdiscord.yml` playbook to `CivPVP-Infra/ansible/`. Role renders per-instance `config.yml` from templates, lftp-pushes jars to mint-servers' SFTP slots, runs `ansible.builtin.pause` for the manual panel restart. Use the existing `gateway` role as a model.
- [ ] Move Discord bot token + Patreon creds from `kira` vault keys to new `civdiscord` keys in `ansible/group_vars/vps/vault.yml`. Rotate the bot token during the move.
- [ ] Remove `kira-gateway` jars from each mint-servers slot's plugin directory.
- [ ] Drop the `kira` Postgres database and the `kira` RabbitMQ vhost + user.
- [ ] Delete `/var/backups/kira-decom/` after the two-week rollback window closes.
- [ ] Update `docs/superpowers/specs/...` memory with the new state.

## Rollback

If CivDiscord misbehaves in the first week, the upstream Kira stack can be brought back by reverting the `civdiscord.yml` playbook run and re-running `kira.yml`. The DB + RabbitMQ state was left in place during this checklist precisely to allow rollback. Only delete the broker + Postgres data **after the second week of clean CivDiscord operation**.

Rollback steps:

1. `ansible-playbook playbooks/civdiscord.yml --tags=stop` (removes the new jars from each slot).
2. `ansible-playbook playbooks/kira.yml` (starts `kira` against the preserved RabbitMQ + Postgres).
3. Restart each server's panel.
4. Announce in Discord that users on the old binding are linked again automatically; users who ran `/discord link` during the CivDiscord window must re-link on Kira.
