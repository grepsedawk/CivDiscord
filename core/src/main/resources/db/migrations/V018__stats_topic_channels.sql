CREATE TABLE stats_topic_channels (
    channel_id  INTEGER PRIMARY KEY,
    guild_id    INTEGER NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
    created_by  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
)
