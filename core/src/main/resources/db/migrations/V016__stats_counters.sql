CREATE TABLE stats_counters (
    id               INTEGER PRIMARY KEY CHECK (id = 1),
    peak_all_time    INTEGER NOT NULL,
    peak_today       INTEGER NOT NULL,
    peak_today_date  TEXT NOT NULL
)
