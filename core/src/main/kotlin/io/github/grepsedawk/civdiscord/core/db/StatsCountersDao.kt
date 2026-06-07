package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class Counters(val peakAllTime: Int, val peakToday: Int, val peakTodayDate: String)

class StatsCountersDao(private val db: Database) {
    fun get(): Counters? = transaction(db) {
        StatsCountersTable.selectAll().firstOrNull()?.let {
            Counters(it[StatsCountersTable.peakAllTime], it[StatsCountersTable.peakToday], it[StatsCountersTable.peakTodayDate])
        }
    }

    /** Record a live sample; raise all-time/today peaks, resetting today when the date rolls.
     *  Skips the UPDATE when nothing changed (the common case), to avoid a needless write. */
    fun bumpPeak(current: Int, today: String): Counters = transaction(db) {
        val existing = StatsCountersTable.selectAll().firstOrNull()
        if (existing == null) {
            StatsCountersTable.insert {
                it[id] = 1
                it[peakAllTime] = current
                it[peakToday] = current
                it[peakTodayDate] = today
            }
            Counters(current, current, today)
        } else {
            val prevAll = existing[StatsCountersTable.peakAllTime]
            val prevToday = existing[StatsCountersTable.peakToday]
            val prevDate = existing[StatsCountersTable.peakTodayDate]
            val newToday = if (prevDate != today) current else maxOf(prevToday, current)
            val next = Counters(maxOf(prevAll, current), newToday, today)
            val unchanged = next.peakAllTime == prevAll && next.peakToday == prevToday && next.peakTodayDate == prevDate
            if (!unchanged) {
                StatsCountersTable.update({ StatsCountersTable.id eq 1 }) {
                    it[peakAllTime] = next.peakAllTime
                    it[peakToday] = next.peakToday
                    it[peakTodayDate] = next.peakTodayDate
                }
            }
            next
        }
    }
}
