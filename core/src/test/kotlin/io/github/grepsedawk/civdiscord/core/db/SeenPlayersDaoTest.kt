package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SeenPlayersDaoTest {
    private fun dao() = SeenPlayersDao(CivDiscordDb.inMemory())

    @Test
    fun `recording the same uuid twice keeps the first epoch and counts once`() {
        val dao = dao()
        dao.recordSeen("uuid-a", 1000)
        dao.recordSeen("uuid-a", 2000)
        dao.uniqueCount() shouldBe 1L
    }

    @Test
    fun `unique count rises with distinct uuids`() {
        val dao = dao()
        dao.recordSeen("uuid-a", 1000)
        dao.recordSeen("uuid-b", 1000)
        dao.uniqueCount() shouldBe 2L
    }

    @Test
    fun `new-today counts only first-seen at or after the cutoff`() {
        val dao = dao()
        dao.recordSeen("old", 500)
        dao.recordSeen("new1", 1000)
        dao.recordSeen("new2", 1500)
        dao.countSeenOnOrAfter(1000) shouldBe 2
    }
}
