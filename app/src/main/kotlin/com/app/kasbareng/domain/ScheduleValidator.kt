package com.app.kasbareng.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

object ScheduleValidator {
    /** Mengembalikan pesan error untuk pengguna, atau null kalau valid. */
    fun validate(
        payDaysMask: Int,
        amountRupiah: Long,
        timeZoneId: String,
        effectiveFrom: LocalDate,
        today: LocalDate,
        hasExistingSchedule: Boolean
    ): String? {
        if ((payDaysMask and 127) == 0) return "Pilih minimal satu hari pembayaran."
        if (amountRupiah <= 0L) return "Nominal harus lebih besar dari Rp0."
        if (runCatching { ZoneId.of(timeZoneId) }.isFailure) return "Zona waktu tidak dikenali."
        if (hasExistingSchedule && effectiveFrom.dayOfWeek != DayOfWeek.MONDAY) {
            return "Tanggal berlaku harus hari Senin."
        }
        if (hasExistingSchedule && !effectiveFrom.isAfter(today)) {
            return "Perubahan jadwal berlaku mulai minggu berikutnya."
        }
        return null
    }
}