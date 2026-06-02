CREATE TABLE IF NOT EXISTS relays (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id            INTEGER NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
    namelayer_group     TEXT    NOT NULL,
    discord_channel_id  INTEGER NOT NULL UNIQUE,
    show_snitches       INTEGER NOT NULL DEFAULT 0,
    chat_format         TEXT,
    created_by          INTEGER NOT NULL,
    created_at          INTEGER NOT NULL
)
