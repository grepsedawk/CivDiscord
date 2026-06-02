CREATE TABLE login_logout_feed (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    guild_id    INTEGER NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
    channel_id  INTEGER NOT NULL,
    created_by  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
)
