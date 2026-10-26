package com.app.kasbareng.domain

import com.app.kasbareng.data.entity.CashTransaction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class ReportPeriod { WEEK, MONTH, ALL }

data class ChartBucket(
    val label: String,
    val income: Long,
    val expense: Long,
    val balanceEnd: Long?      // null = periode ini belum dimulai
)

object ReportCharts {
    private val dayNames = arrayOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    private val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale("id", "ID"))

    private data class Range(val label: String, val start: Long, val end: Long)

    private fun ms(d: LocalDate, zone: ZoneId): Long =
        d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun periodStart(period: ReportPeriod, today: LocalDate, zone: ZoneId): Long = when (period) {
        ReportPeriod.WEEK -> ms(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), zone)
        ReportPeriod.MONTH -> ms(today.withDayOfMonth(1), zone)
        ReportPeriod.ALL -> 0L
    }

    fun build(
        txs: List<CashTransaction>, period: ReportPeriod, zone: ZoneId, now: Instant = Instant.now()
    ): List<ChartBucket> {
        val valid = txs.filter { it.status == "VALID" }
        val today = now.atZone(zone).toLocalDate()
        val nowMs = now.toEpochMilli()
        return ranges(period, valid, today, zone).map { r ->
            val inRange = valid.filter { it.occurredAt >= r.start && it.occurredAt < r.end }
            ChartBucket(
                label = r.label,
                income = inRange.filter { it.type != "EXPENSE" }.sumOf { it.amountRupiah },
                expense = inRange.filter { it.type == "EXPENSE" }.sumOf { it.amountRupiah },
                balanceEnd = if (r.start > nowMs) null
                else valid.filter { it.occurredAt < r.end }
                    .sumOf { if (it.type == "EXPENSE") -it.amountRupiah else it.amountRupiah }
            )
        }
    }

    private fun ranges(
        period: ReportPeriod, valid: List<CashTransaction>, today: LocalDate, zone: ZoneId
    ): List<Range> = when (period) {
        ReportPeriod.WEEK -> {
            val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (0..6).map { i ->
                val d = mon.plusDays(i.toLong())
                Range(dayNames[i], ms(d, zone), ms(d.plusDays(1), zone))
            }
        }
        ReportPeriod.MONTH -> {
            val first = today.withDayOfMonth(1)
            val len = first.lengthOfMonth()
            val out = ArrayList<Range>()
            var s = 1
            while (s <= len) {
                val e = minOf(s + 6, len)
                out.add(
                    Range(
                        "$s-$e",
                        ms(first.withDayOfMonth(s), zone),
                        ms(first.withDayOfMonth(e).plusDays(1), zone)
                    )
                )
                s += 7
            }
            out
        }
        ReportPeriod.ALL -> {
            val thisMonth = YearMonth.from(today)
            val earliest = valid.minOfOrNull { it.occurredAt }
            val firstMonth = earliest?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: thisMonth
            var m = if (firstMonth.isBefore(thisMonth.minusMonths(11))) thisMonth.minusMonths(11) else firstMonth
            val out = ArrayList<Range>()
            while (!m.isAfter(thisMonth)) {
                out.add(Range(m.format(monthFmt), ms(m.atDay(1), zone), ms(m.plusMonths(1).atDay(1), zone)))
                m = m.plusMonths(1)
            }
            out
        }
    }
}