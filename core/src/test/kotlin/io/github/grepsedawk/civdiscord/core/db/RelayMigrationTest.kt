package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RelayMigrationTest {

    @Test
    fun `V013 backfills existing relay row as writer with composite PK`() {
        val tmp = Files.createTempFile("civd-", ".db").toFile().apply { deleteOnExit() }
        val db = CivDiscordDb.connectWithoutMigrations(tmp.absolutePath)
        // Apply migrations up to V012 only.
        val pre = Migrations.ALL.takeWhile { it != "V013__relays_multibind.sql" }
        MigrationRunner(db, pre).run()
        // Insert a legacy row (single bind per channel).
        transaction(db) {
            exec(
                "INSERT INTO guilds (guild_id, joined_at, auth_role_id, deleted_at) VALUES (10, 0, NULL, NULL)",
            )
            exec(
                "INSERT INTO relays (guild_id, namelayer_group, discord_channel_id, " +
                    "show_snitches, chat_format, snitch_ping, created_by, created_at) " +
                    "VALUES (10, 'townhall', 99, 1, NULL, NULL, 5, 1000)",
            )
        }
        // Now apply V013.
        MigrationRunner(db).run()
        // Verify backfilled row.
        val rows = transaction(db) {
            val out = mutableListOf<Triple<Long, String, Boolean>>()
            exec(
                "SELECT discord_channel_id, namelayer_group, is_writer FROM relays",
            ) { rs ->
                while (rs.next()) out += Triple(rs.getLong(1), rs.getString(2), rs.getBoolean(3))
            }
            out
        }
        rows shouldBe listOf(Triple(99L, "townhall", true))
    }

    @Test
    fun `V013 partial unique index rejects a second writer on same channel`() {
        val tmp = Files.createTempFile("civd-", ".db").toFile().apply { deleteOnExit() }
        val db = CivDiscordDb.connect(tmp.absolutePath)
        MigrationRunner(db).run()
        transaction(db) {
            exec(
                "INSERT INTO guilds (guild_id, joined_at, auth_role_id, deleted_at) VALUES (10, 0, NULL, NULL)",
            )
            exec(
                "INSERT INTO relays (guild_id, namelayer_group, discord_channel_id, " +
                    "show_snitches, chat_format, snitch_ping, created_by, created_at, is_writer) " +
                    "VALUES (10, 'a', 99, 0, NULL, NULL, 5, 1000, 1)",
            )
        }
        val ex = runCatching {
            transaction(db) {
                exec(
                    "INSERT INTO relays (guild_id, namelayer_group, discord_channel_id, " +
                        "show_snitches, chat_format, snitch_ping, created_by, created_at, is_writer) " +
                        "VALUES (10, 'b', 99, 0, NULL, NULL, 5, 1000, 1)",
                )
            }
        }.exceptionOrNull()
        ex.shouldBeInstanceOf<ExposedSQLException>()
    }

    @Test
    fun `V013 allows multiple non-writer binds on same channel`() {
        val tmp = Files.createTempFile("civd-", ".db").toFile().apply { deleteOnExit() }
        val db = CivDiscordDb.connect(tmp.absolutePath)
        MigrationRunner(db).run()
        transaction(db) {
            exec("INSERT INTO guilds (guild_id, joined_at, auth_role_id, deleted_at) VALUES (10, 0, NULL, NULL)")
            exec(
                "INSERT INTO relays (guild_id, namelayer_group, discord_channel_id, " +
                    "show_snitches, chat_format, snitch_ping, created_by, created_at, is_writer) " +
                    "VALUES (10, 'a', 99, 0, NULL, NULL, 5, 1000, 1)",
            )
            exec(
                "INSERT INTO relays (guild_id, namelayer_group, discord_channel_id, " +
                    "show_snitches, chat_format, snitch_ping, created_by, created_at, is_writer) " +
                    "VALUES (10, 'b', 99, 0, NULL, NULL, 5, 1000, 0)",
            )
            exec(
                "INSERT INTO relays (guild_id, namelayer_group, discord_channel_id, " +
                    "show_snitches, chat_format, snitch_ping, created_by, created_at, is_writer) " +
                    "VALUES (10, 'c', 99, 0, NULL, NULL, 5, 1000, 0)",
            )
        }
        val cnt = transaction(db) {
            var n = 0
            exec("SELECT COUNT(*) FROM relays WHERE discord_channel_id = 99") { rs ->
                if (rs.next()) n = rs.getInt(1)
            }
            n
        }
        cnt shouldBe 3
    }
}
