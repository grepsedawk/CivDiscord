package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

class MigrationRunnerTest {

    @Test
    fun `runs every migration and records each name in schema_migrations`() {
        val db = CivDiscordDb.inMemory()

        val applied = transaction(db) {
            exec("SELECT name FROM schema_migrations ORDER BY name") { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }!!
        }
        applied shouldBe Migrations.ALL.sorted()
    }

    @Test
    fun `Migrations ALL is the explicit ordered list the runner applies`() {
        Migrations.ALL shouldBe listOf(
            "V001__init.sql",
            "V002__bindings.sql",
            "V003__guilds.sql",
            "V004__relays.sql",
            "V005__patreon_tiers.sql",
            "V006__guilds_soft_delete.sql",
            "V009__bindings_indexes.sql",
            "V010__relays_guild_index.sql",
            "V011__relays_group_index.sql",
        )
    }

    @Test
    fun `second run is a no-op (already-applied migrations are skipped)`() {
        val db = CivDiscordDb.inMemory()
        val before = transaction(db) {
            exec("SELECT name FROM schema_migrations ORDER BY name") { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }!!
        }

        MigrationRunner(db).run()

        val after = transaction(db) {
            exec("SELECT name FROM schema_migrations ORDER BY name") { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }!!
        }
        after shouldBe before
    }

    @Test
    fun `journal_mode is WAL`() {
        val db = CivDiscordDb.inMemory()
        val mode = transaction(db) {
            exec("PRAGMA journal_mode") { rs ->
                rs.next()
                rs.getString(1).lowercase()
            }
        }
        mode shouldBe "wal"
    }

    @Test
    fun `busy_timeout is 5000ms`() {
        val db = CivDiscordDb.inMemory()
        val timeout = transaction(db) {
            exec("PRAGMA busy_timeout") { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        timeout shouldBe 5000
    }
}
