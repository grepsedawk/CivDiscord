package io.github.grepsedawk.civdiscord.core.db

import org.jetbrains.exposed.sql.Table

object StatsCountersTable : Table("stats_counters") {
    val id = integer("id") // always 1; CHECK (id = 1) in the migration
    val peakAllTime = integer("peak_all_time")
    val peakToday = integer("peak_today")
    val peakTodayDate = text("peak_today_date")
    override val primaryKey = PrimaryKey(id)
}
