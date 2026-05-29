CREATE TABLE IF NOT EXISTS patreon_tiers (
    discord_id INTEGER PRIMARY KEY,
    tier       TEXT,
    synced_at  INTEGER NOT NULL
)
