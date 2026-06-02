-- Multi-bind relay: composite PK (discord_channel_id, namelayer_group) with
-- partial unique index enforcing one writer per channel. SQLite cannot ALTER
-- a column's UNIQUE or default, so we recreate the table.

CREATE TABLE relays_new (
    guild_id            INTEGER NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
    namelayer_group     TEXT    NOT NULL,
    discord_channel_id  INTEGER NOT NULL,
    is_writer           INTEGER NOT NULL DEFAULT 0,
    show_snitches       INTEGER NOT NULL DEFAULT 0,
    chat_format         TEXT,
    snitch_ping         TEXT,
    created_by          INTEGER NOT NULL,
    created_at          INTEGER NOT NULL,
    PRIMARY KEY (discord_channel_id, namelayer_group)
);

INSERT INTO relays_new (
    guild_id, namelayer_group, discord_channel_id, is_writer,
    show_snitches, chat_format, snitch_ping, created_by, created_at
)
SELECT
    guild_id, namelayer_group, discord_channel_id, 1,
    show_snitches, chat_format, snitch_ping, created_by, created_at
FROM relays;

DROP TABLE relays;
ALTER TABLE relays_new RENAME TO relays;

CREATE INDEX idx_relays_guild ON relays(guild_id);
CREATE INDEX idx_relays_group ON relays(namelayer_group);
CREATE UNIQUE INDEX idx_relays_one_writer_per_channel
    ON relays(discord_channel_id)
    WHERE is_writer = 1;
