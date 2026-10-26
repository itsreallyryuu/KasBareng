package com.app.kasbareng.domain

object ScheduleText {
    private val names = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    fun days(mask: Int): String =
        names.indices.filter { ((mask shr it) and 1) == 1 }.joinToString(", ") { names[it] }

    fun zoneLabel(id: String): String = when (id) {
        "Asia/Makassar" -> "WITA"
        "Asia/Jayapura" -> "WIT"
        else -> "WIB"
    }
}