package io.github.grepsedawk.civdiscord.core.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

private val OWNER_RW = PosixFilePermissions.fromString("rw-------")

// Operators routinely scp/rsync secret.key and config.yml in from elsewhere; the source umask
// commonly leaves them world-readable. Tightening only at generate-time means a hand-copied file
// keeps its loose perms forever, so callers run this every load.
fun tryLockdown(path: Path) {
    try {
        Files.setPosixFilePermissions(path, OWNER_RW)
    } catch (_: UnsupportedOperationException) {
    }
}
