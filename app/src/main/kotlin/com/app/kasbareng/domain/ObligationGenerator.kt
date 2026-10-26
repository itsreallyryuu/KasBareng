package com.app.kasbareng.domain

import com.app.kasbareng.data.db.AppDatabase
import com.app.kasbareng.data.entity.Member
import com.app.kasbareng.data.entity.Obligation
import com.app.kasbareng.data.entity.ScheduleVersion
import com.app.kasbareng.data.repository.CreditApplier
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object PayDays {
    // bit0 = Senin ... bit6 = Minggu
    fun bitOf(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun maskOf(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or bitOf(d) }
    fun contains(mask: Int, day: DayOfWeek): Boolean = (mask and bitOf(day)) != 0
}

object DueRules {
    // Menunggak mulai 00.00 hari setelah hari bayar, di zona waktu jadwal
    fun isOverdue(dueEpochDay: Long, now: Instant, zone: ZoneId): Boolean {
        val overdueFrom = LocalDate.ofEpochDay(dueEpochDay + 1).atStartOfDay(zone).toInstant()
        return !now.isBefore(overdueFrom)
    }
}

class ObligationGenerator(private val db: AppDatabase) {

    /** Membuat kewajiban sampai akhir minggu ini, lalu memakai saldo di muka. Aman dipanggil berulang. */
    suspend fun generate(now: Instant = Instant.now()): Int {
        val versions = db.scheduleDao().getAll()
        if (versions.isEmpty()) return 0

        val deviceToday = LocalDate.now()
        val current = db.scheduleDao().getEffectiveOn(deviceToday.toEpochDay()) ?: versions.first()
        val zone = ZoneId.of(current.timeZoneId)
        val today = now.atZone(zone).toLocalDate()
        val endDay = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        var created = 0
        for (member in db.memberDao().getActive()) {
            val items = buildFor(member, versions, zone, endDay)
            if (items.isNotEmpty()) {
                created += db.obligationDao().insertAll(items).count { it != -1L }
            }
        }
        CreditApplier(db).applyAll()
        return created
    }

    companion object {
        /** Fungsi murni: tanpa database, mudah diuji. versions harus urut naik. */
        fun buildFor(
            member: Member,
            versions: List<ScheduleVersion>,
            zone: ZoneId,
            endDay: LocalDate
        ): List<Obligation> {
            if (versions.isEmpty()) return emptyList()
            val addedDay = Instant.ofEpochMilli(member.addedAt).atZone(zone).toLocalDate()
            val firstEffective = LocalDate.ofEpochDay(versions.first().effectiveFromEpochDay)
            var day = if (addedDay.isAfter(firstEffective)) addedDay else firstEffective

            val result = ArrayList<Obligation>()
            while (!day.isAfter(endDay)) {
                val v = versions.lastOrNull { it.effectiveFromEpochDay <= day.toEpochDay() }
                if (v != null && PayDays.contains(v.payDaysMask, day.dayOfWeek)) {
                    result.add(
                        Obligation(
                            memberId = member.id,
                            scheduleVersionId = v.id,
                            dueEpochDay = day.toEpochDay(),
                            amountRupiah = v.amountRupiah
                        )
                    )
                }
                day = day.plusDays(1)
            }
            return result
        }
    }
}