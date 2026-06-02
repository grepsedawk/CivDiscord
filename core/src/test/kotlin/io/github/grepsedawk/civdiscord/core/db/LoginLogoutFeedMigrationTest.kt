package io.github.grepsedawk.civdiscord.core.db

import io.kotest.assertions.throwables.shouldThrowAny
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

class LoginLogoutFeedMigrationTest {
    @Test
    fun `migration creates login_logout_feed and the CHECK rejects a non-1 id`() {
        val db = CivDiscordDb.inMemory()
        transaction(db) {
            exec("INSERT INTO guilds (guild_id, joined_at, auth_role_id, deleted_at) VALUES (10, 0, NULL, NULL)")
            exec("INSERT INTO login_logout_feed (id, guild_id, channel_id, created_by, created_at) VALUES (1, 10, 1001, 5, 0)")
        }
        shouldThrowAny {
            transaction(db) {
                exec("INSERT INTO login_logout_feed (id, guild_id, channel_id, created_by, created_at) VALUES (2, 10, 2002, 6, 0)")
            }
        }
    }

    @Test
    fun `a second row with id 1 is rejected by the primary key`() {
        val db = CivDiscordDb.inMemory()
        transaction(db) {
            exec("INSERT INTO guilds (guild_id, joined_at, auth_role_id, deleted_at) VALUES (10, 0, NULL, NULL)")
            exec("INSERT INTO login_logout_feed (id, guild_id, channel_id, created_by, created_at) VALUES (1, 10, 1001, 5, 0)")
        }
        shouldThrowAny {
            transaction(db) {
                exec("INSERT INTO login_logout_feed (id, guild_id, channel_id, created_by, created_at) VALUES (1, 10, 2002, 6, 0)")
            }
        }
    }
}
