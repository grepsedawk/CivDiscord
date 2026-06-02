package io.github.grepsedawk.civdiscord.core.db

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BindingDaoTest {
    private fun newDb() = CivDiscordDb.inMemory()

    @Test
    fun `upsert inserts a new binding`() {
        val db = newDb()
        val dao = BindingDao(db)
        val uuid = UUID.randomUUID()
        val outcome = dao.upsert(discordId = 111L, mcUuid = uuid, mcName = "alice")
        outcome shouldBe LinkOutcome.Linked(replaced = false)
        val row = dao.findByDiscordId(111L)
        row.shouldNotBeNull()
        row.mcUuid shouldBe uuid
        row.mcName shouldBe "alice"
    }

    @Test
    fun `upsert replaces an existing binding (relink semantics)`() {
        val db = newDb()
        val dao = BindingDao(db)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        dao.upsert(111L, first, "alice")
        val outcome = dao.upsert(111L, second, "alice2")
        outcome shouldBe LinkOutcome.Linked(replaced = true)
        dao.findByDiscordId(111L)!!.mcUuid shouldBe second
    }

    @Test
    fun `upsert refuses to steal an mc_uuid already linked to another Discord user`() {
        val db = newDb()
        val dao = BindingDao(db)
        val shared = UUID.randomUUID()
        dao.upsert(111L, shared, "alice")

        val outcome = dao.upsert(222L, shared, "alice")

        outcome shouldBe LinkOutcome.McAlreadyLinkedTo(111L)
        dao.findByDiscordId(222L).shouldBeNull()
        dao.findByMcUuid(shared)!!.discordId shouldBe 111L
    }

    @Test
    fun `upsert re-linking the same Discord to its own mc_uuid is a no-op replace`() {
        val db = newDb()
        val dao = BindingDao(db)
        val uuid = UUID.randomUUID()
        dao.upsert(111L, uuid, "alice")

        val outcome = dao.upsert(111L, uuid, "alice")

        outcome shouldBe LinkOutcome.Linked(replaced = true)
    }

    @Test
    fun `delete removes a binding`() {
        val db = newDb()
        val dao = BindingDao(db)
        dao.upsert(111L, UUID.randomUUID(), "alice")
        dao.delete(111L)
        dao.findByDiscordId(111L).shouldBeNull()
    }

    @Test
    fun `findByMcUuid returns the binding for that uuid`() {
        val db = newDb()
        val dao = BindingDao(db)
        val uuid = UUID.randomUUID()
        dao.upsert(111L, uuid, "alice")
        dao.findByMcUuid(uuid)?.discordId shouldBe 111L
    }

    @Test
    fun `concurrent upserts for the same mc_uuid never violate UNIQUE`() {
        val db = newDb()
        val dao = BindingDao(db)
        val shared = UUID.randomUUID()
        val threads = 8
        val reps = 50
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val wins = AtomicInteger(0)
        val losses = AtomicInteger(0)
        val errors = AtomicInteger(0)

        repeat(threads) { tIdx ->
            pool.submit {
                start.await()
                repeat(reps) { rep ->
                    val discordId = (1000L + tIdx * reps + rep)
                    try {
                        when (dao.upsert(discordId, shared, "name$tIdx-$rep")) {
                            is LinkOutcome.Linked -> wins.incrementAndGet()
                            is LinkOutcome.McAlreadyLinkedTo -> losses.incrementAndGet()
                        }
                    } catch (_: Throwable) {
                        errors.incrementAndGet()
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

        errors.get() shouldBe 0
        val owner = dao.findByMcUuid(shared)
        owner.shouldNotBeNull()
        (wins.get() >= 1) shouldBe true
    }
}
