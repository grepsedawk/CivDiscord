package io.github.grepsedawk.civdiscord.core.db

import io.kotest.assertions.throwables.shouldThrowAny
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

class StatsMigrationTest {
    @Test
    fun `stats tables exist and singletons reject a non-1 id`() {
        val db = CivDiscordDb.inMemory()
        transaction(db) {
            exec("INSERT INTO guilds (guild_id, joined_at, auth_role_id, deleted_at) VALUES (10, 0, NULL, NULL)")
            exec("INSERT INTO stats_config (id, guild_id, created_by, created_at) VALUES (1, 10, 5, 0)")
            exec("INSERT INTO stats_counters (id, peak_all_time, peak_today, peak_today_date) VALUES (1, 0, 0, '2026-06-03')")
            exec("INSERT INTO seen_players (uuid, first_seen_epoch) VALUES ('abc', 0)")
        }
        shouldThrowAny {
            transaction(db) { exec("INSERT INTO stats_config (id, guild_id, created_by, created_at) VALUES (2, 10, 5, 0)") }
        }
    }
}
