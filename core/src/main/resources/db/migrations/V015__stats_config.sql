CREATE TABLE stats_config (
    id                        INTEGER PRIMARY KEY CHECK (id = 1),
    guild_id                  INTEGER NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
    dashboard_channel_id      INTEGER,
    dashboard_message_id      INTEGER,
    voice_players_channel_id  INTEGER,
    voice_tps_channel_id      INTEGER,
    topic_channel_id          INTEGER,
    created_by                INTEGER NOT NULL,
    created_at                INTEGER NOT NULL
)
