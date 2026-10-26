package com.app.kasbareng.ui

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Kolom tanggal wajib: default hari ini, tidak bisa memilih masa depan. */
class DateField(
    private val fragment: Fragment,
    til: TextInputLayout,
    private val et: TextInputEditText,
    initial: LocalDate = LocalDate.now(ZoneId.systemDefault())
) {
    var date: LocalDate = initial
        private set

    init {
        show()
        val open = View.OnClickListener { pick() }
        et.setOnClickListener(open)
        til.setEndIconOnClickListener(open)
    }

    fun label(): String = UiFormat.dateLabel(date.toEpochDay())

    private fun show() {
        et.setText(label())
    }

    /** Tanggal pilihan + jam saat ini (untuk transaksi baru). */
    fun occurredAtMillis(): Long {
        val zone = ZoneId.systemDefault()
        return date.atTime(LocalTime.now(zone)).atZone(zone).toInstant().toEpochMilli()
    }

    /** Tanggal pilihan + jam dari waktu asli transaksi (untuk edit), tidak pernah melewati sekarang. */
    fun millisKeepingTimeOf(original: Long): Long {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(original).atZone(zone).toLocalTime()
        val ms = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return minOf(ms, System.currentTimeMillis())
    }

    private fun utcMillis(d: LocalDate): Long =
        d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun pick() {
        val todayUtc = utcMillis(LocalDate.now(ZoneId.systemDefault()))
        val constraints = CalendarConstraints.Builder()
            .setEnd(todayUtc)
            .setValidator(DateValidatorPointBackward.before(todayUtc))
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Tanggal transaksi")
            .setSelection(utcMillis(date))
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            date = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate()
            show()
        }
        picker.show(fragment.parentFragmentManager, "date_picker")
    }
}