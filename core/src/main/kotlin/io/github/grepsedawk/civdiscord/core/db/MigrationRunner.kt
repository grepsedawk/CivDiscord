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
                exec(sql)
                exec("INSERT INTO schema_migrations (name) VALUES ('$name')")
            }
        }
    }

    private fun readMigration(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("db/migrations/$name")
            ?: error("Migration resource not found: db/migrations/$name")
        return stream.bufferedReader().use { it.readText() }.trim()
    }
}
