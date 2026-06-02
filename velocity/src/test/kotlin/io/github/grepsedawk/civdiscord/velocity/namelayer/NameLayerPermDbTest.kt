// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Ally Piechowski (grepsedawk)
package io.github.grepsedawk.civdiscord.velocity.namelayer

import io.kotest.matchers.shouldBe
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class NameLayerPermDbTest {

    private lateinit var ds: JdbcDataSource
    private lateinit var db: NameLayerPermDb

    private val uuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")
    private val other = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @BeforeEach
    fun setUp() {
        ds = JdbcDataSource().apply {
            // MySQL mode + an in-memory schema per test
            setUrl("jdbc:h2:mem:nl_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        ds.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE faction_id (
                      group_id INT PRIMARY KEY,
                      group_name VARCHAR(255) NOT NULL
                    )
                    """,
                )
                st.execute(
                    """
                    CREATE TABLE faction_member (
                      group_id INT NOT NULL,
                      member_name VARCHAR(64) NOT NULL,
                      role VARCHAR(64) NOT NULL
                    )
                    """,
                )
                st.execute(
                    """
                    CREATE TABLE permissionByGroup (
                      group_id INT NOT NULL,
                      role VARCHAR(64) NOT NULL,
                      perm_id INT NOT NULL
                    )
                    """,
                )
                st.execute(
                    """
                    CREATE TABLE permissionIdMapping (
                      perm_id INT PRIMARY KEY,
                      name VARCHAR(64) NOT NULL
                    )
                    """,
                )
                st.execute("INSERT INTO faction_id VALUES (8, 'grepsedawk')")
                st.execute("INSERT INTO faction_member VALUES (8, '$uuid', 'OWNER')")
                st.execute("INSERT INTO permissionByGroup VALUES (8, 'OWNER', 43)")
                st.execute("INSERT INTO permissionByGroup VALUES (8, 'MEMBER', 99)")
                st.execute("INSERT INTO permissionIdMapping VALUES (43, 'READ_CHAT')")
                st.execute("INSERT INTO permissionIdMapping VALUES (99, 'BLOCKS_BREAK')")
            }
        }
        db = NameLayerPermDb(ds)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `owner has READ_CHAT`() {
        db.hasPerm(uuid, "grepsedawk", "READ_CHAT") shouldBe true
    }

    @Test
    fun `non-member has no READ_CHAT`() {
        db.hasPerm(other, "grepsedawk", "READ_CHAT") shouldBe false
    }

    @Test
    fun `owner does not have unrelated perm`() {
        db.hasPerm(uuid, "grepsedawk", "DOORS") shouldBe false
    }

    @Test
    fun `unknown group returns false`() {
        db.hasPerm(uuid, "no_such_group", "READ_CHAT") shouldBe false
    }

    @Test
    fun `role without the perm returns false`() {
        // grepsedawk's OWNER role has READ_CHAT (43) but not BLOCKS_BREAK (99).
        db.hasPerm(uuid, "grepsedawk", "BLOCKS_BREAK") shouldBe false
    }

    @Test
    fun `swallowing-broken-connection returns false instead of throwing`() {
        ds.connection.use { conn ->
            conn.createStatement().use { it.execute("DROP TABLE faction_member") }
        }
        db.hasPerm(uuid, "grepsedawk", "READ_CHAT") shouldBe false
    }
}
