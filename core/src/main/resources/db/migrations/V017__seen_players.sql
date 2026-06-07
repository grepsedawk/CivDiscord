CREATE TABLE seen_players (
    uuid              TEXT PRIMARY KEY,
    first_seen_epoch  INTEGER NOT NULL
);

-- countSeenOnOrAfter(first_seen_epoch >= ?) runs on the stats tick; index it so it
-- doesn't full-scan a table that grows one row per unique player forever.
CREATE INDEX IF NOT EXISTS idx_seen_players_first_seen ON seen_players(first_seen_epoch)
