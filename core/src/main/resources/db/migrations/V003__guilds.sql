CREATE TABLE IF NOT EXISTS guilds (
    guild_id      INTEGER PRIMARY KEY,
    joined_at     INTEGER NOT NULL,
    auth_role_id  INTEGER
)
