package com.app.kasbareng.util

object Rupiah {
    fun format(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val digits = kotlin.math.abs(amount).toString()
        val grouped = digits.reversed().chunked(3)
            .joinToString(".").reversed()
        return "${sign}Rp$grouped"
    }
}