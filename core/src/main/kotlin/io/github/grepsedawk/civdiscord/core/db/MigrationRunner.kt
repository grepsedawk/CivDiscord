package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

object Migrations {
    val ALL: List<String> = listOf(
        "V001__init.sql",
        "V002__bindings.sql",
        "V003__guilds.sql",
        "V004__relays.sql",
        "V005__patreon_tiers.sql",
        "V006__guilds_soft_delete.sql",
        "V009__bindings_indexes.sql",
        "V010__relays_guild_index.sql",
        "V011__relays_group_index.sql",
        "V012__relays_snitch_ping.sql",
        "V013__relays_multibind.sql",
        "V014__login_logout_feed.sql",
        "V015__stats_config.sql",
        "V016__stats_counters.sql",
        "V017__seen_players.sql",
        "V018__stats_topic_channels.sql",
        "V019__drop_stats_topic_channel_id.sql",
    )
}

class MigrationRunner(
    private val db: Database,
    private val migrations: List<String> = Migrations.ALL,
) {
    fun run() {
        transaction(db) {
            exec("CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY)")
        }

        val applied = transaction(db) {
            exec("SELECT name FROM schema_migrations") { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toSet()
            } ?: emptySet()
        }

        for (name in migrations) {
            if (name in applied) continue
            val sql = readMigration(name)
            transaction(db) {
                for (stmt in splitStatements(sql)) exec(stmt)
                exec("INSERT INTO schema_migrations (name) VALUES ('$name')")
            }
        }
    }

    // Naive splitter: strips '--' line comments and splits on ';'. Does NOT
    // understand string literals, so a ';' or '--' inside a quoted value would
    // be mangled. We fail loud on unbalanced single quotes (post comment-strip)
    // to surface that case instead of silently corrupting a migration.
    private fun splitStatements(sql: String): List<String> {
        val stripped = sql.lineSequence()
            .map { it.substringBefore("--") }
            .joinToString(" ")
        val quotes = stripped.count { it == '\'' }
        require(quotes % 2 == 0) {
            "Migration contains an unbalanced single quote; the naive splitter " +
                "in MigrationRunner cannot safely handle string literals containing " +
                "';' or '--'. Rewrite the migration to avoid those characters in literals."
        }
        return stripped
            .split(';')
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
    }

    private fun readMigration(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("db/migrations/$name")
            ?: error("Migration resource not found: db/migrations/$name")
        return stream.bufferedReader().use { it.readText() }.trim()
    }
}
