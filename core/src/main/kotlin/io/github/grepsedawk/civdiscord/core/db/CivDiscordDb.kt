package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource

object CivDiscordDb {
    fun connect(path: String): Database = build("jdbc:sqlite:$path", runMigrations = true)

    fun connectWithoutMigrations(path: String): Database = build("jdbc:sqlite:$path", runMigrations = false)

    fun inMemory(): Database {
        val file = java.io.File.createTempFile("civdiscord_", ".db").apply { deleteOnExit() }
        return build("jdbc:sqlite:${file.absolutePath}", runMigrations = true)
    }

    private fun build(url: String, runMigrations: Boolean): Database {
        val ds =
            SQLiteDataSource(
                SQLiteConfig().apply {
                    enforceForeignKeys(true)
                    setJournalMode(SQLiteConfig.JournalMode.WAL)
                    setBusyTimeout(5000)
                    setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
                },
            ).apply { this.url = url }
        val db = Database.connect(ds)
        if (runMigrations) MigrationRunner(db).run()
        return db
    }
}
