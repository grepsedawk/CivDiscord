// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Ally Piechowski (grepsedawk)
package io.github.grepsedawk.civdiscord.velocity.namelayer

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.grepsedawk.civdiscord.velocity.config.NameLayerDbConfig
import io.github.grepsedawk.civdiscord.velocity.discord.PermCheck
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

/**
 * Reads NameLayer permission state straight from the Paper backend's MariaDB.
 *
 * The Velocity bridge can only ferry plugin messages to a backend through a connected
 * player's connection — when the server is empty, perm queries time out and every
 * /relay bind fails. Direct DB access removes that dependency.
 */
class NameLayerPermDb(
    private val dataSource: DataSource,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(NameLayerPermDb::class.java)

    fun check(mcUuid: UUID, group: String, perm: String): PermCheck = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(SQL).use { ps ->
                ps.setString(1, group)
                ps.setString(2, mcUuid.toString())
                ps.setString(3, perm)
                ps.executeQuery().use { rs -> if (rs.next()) PermCheck.ALLOWED else PermCheck.DENIED }
            }
        }
    } catch (t: Throwable) {
        log.warn("NameLayer perm lookup failed (group={}, uuid={}, perm={})", group, mcUuid, perm, t)
        PermCheck.UNKNOWN
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    companion object {
        private const val SQL = """
            SELECT 1
            FROM faction_member fm
            JOIN permissionByGroup pbg
              ON pbg.group_id = fm.group_id AND pbg.role = fm.role
            JOIN permissionIdMapping pim
              ON pim.perm_id = pbg.perm_id
            JOIN faction_id fi
              ON fi.group_id = fm.group_id
            WHERE fi.group_name = ?
              AND fm.member_name = ?
              AND pim.name = ?
            LIMIT 1
        """

        fun pooled(cfg: NameLayerDbConfig): NameLayerPermDb {
            val hc = HikariConfig().apply {
                // Explicit driver class so the shadowed jar doesn't depend on
                // DriverManager auto-registration via META-INF/services discovery.
                driverClassName = "org.mariadb.jdbc.Driver"
                jdbcUrl = "jdbc:mariadb://${cfg.host}:${cfg.port}/${cfg.database}"
                username = cfg.user
                password = cfg.password
                maximumPoolSize = 3
                minimumIdle = 0
                idleTimeout = 60_000
                connectionTimeout = 5_000
                poolName = "civdiscord-namelayer"
            }
            return NameLayerPermDb(HikariDataSource(hc))
        }
    }
}
