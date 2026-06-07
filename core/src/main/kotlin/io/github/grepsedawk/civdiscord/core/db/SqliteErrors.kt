package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.sql.SQLException

// The xerial driver collapses every extended constraint code to the base 19 in getErrorCode(), so
// matching code 19 would also swallow NOT NULL / FOREIGN KEY / CHECK failures. We therefore key off
// the "UNIQUE constraint failed" message (the reliable discriminator), and the extended unique code
// only if a driver ever exposes it.
private const val SQLITE_CONSTRAINT_UNIQUE = 2067

/** True if [e] — or any cause in its chain — is a SQLite UNIQUE/PRIMARY KEY violation, and not some
 *  other constraint. Exposed wraps the driver SQLException, so the signal can live on a nested cause. */
fun isSqliteUniqueViolation(e: ExposedSQLException): Boolean {
    var cur: Throwable? = e
    while (cur != null) {
        if (cur is SQLException) {
            if (cur.errorCode == SQLITE_CONSTRAINT_UNIQUE) return true
            if (cur.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true) return true
        }
        cur = cur.cause
    }
    return false
}
