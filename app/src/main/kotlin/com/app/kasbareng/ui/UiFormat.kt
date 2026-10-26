package com.app.kasbareng.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.app.kasbareng.R
import com.app.kasbareng.domain.Badge
import com.app.kasbareng.domain.Opening
import com.google.android.material.snackbar.Snackbar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object UiFormat {
    private val localeId = Locale("id", "ID")

    fun badgeColor(ctx: Context, badge: Badge): Int = ContextCompat.getColor(
        ctx,
        when (badge) {
            Badge.LUNAS -> R.color.status_lunas
            Badge.NUNGGAK -> R.color.status_nunggak
            Badge.CICILAN -> R.color.status_cicilan
            Badge.NONAKTIF -> R.color.status_nonaktif
            else -> R.color.status_netral
        }
    )

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun dateLabel(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", localeId))

    /** Label kewajiban: tunggakan awal dari buku ditampilkan dengan namanya, bukan tanggal. */
    fun dueLabel(epochDay: Long): String =
        if (epochDay == Opening.DUE_DAY) "Tunggakan awal (dari buku)" else dateLabel(epochDay)

    fun dateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", localeId))

    fun dateOnly(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", localeId))

    fun typeLabel(type: String): String = when (type) {
        "DUES_PAYMENT" -> "Iuran"
        "INCOME" -> "Pemasukan"
        "EXPENSE" -> "Pengeluaran"
        "OPENING_BALANCE" -> "Saldo awal"
        else -> type
    }

    fun snack(fragment: Fragment, message: String) {
        fragment.view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }
}