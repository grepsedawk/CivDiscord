CREATE TABLE IF NOT EXISTS bindings (
    discord_id INTEGER PRIMARY KEY,
    mc_uuid    TEXT NOT NULL UNIQUE,
    mc_name    TEXT NOT NULL,
    linked_at  INTEGER NOT NULL
)
