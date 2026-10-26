package com.app.kasbareng.domain

import com.app.kasbareng.util.Rupiah

data class ReportLine(val date: String, val text: String, val amount: Long)

data class ReportData(
    val periodLabel: String,
    val generatedAt: String,
    val balance: Long,
    val duesIncome: Long,
    val otherIncome: Long,
    val expense: Long,
    val expenses: List<ReportLine>,
    val activeMembers: Int,
    val cleanMembers: Int,
    val overdue: List<Pair<String, Long>>
)

object ReportBuilder {
    private const val LINE = "━━━━━━━━━━━━━━━━"
    private const val MAX_EXPENSES = 15
    private const val MAX_OVERDUE = 40

    // Karakter format WhatsApp di nama/keterangan dibuang supaya tidak merusak tampilan
    private fun clean(s: String) = s.replace(Regex("[*_~`]"), "").trim()

    private fun signed(v: Long) = if (v >= 0) "+${Rupiah.format(v)}" else Rupiah.format(v)

    fun build(d: ReportData): String = buildString {
        append("*LAPORAN KAS KELAS*\n")
        append("_Dibuat dengan KasBareng_\n\n")
        append(LINE).append('\n')
        append("*Periode* : ${d.periodLabel}\n")
        append("*Dibuat* : ${d.generatedAt}\n")
        append(LINE).append("\n\n")

        val income = d.duesIncome + d.otherIncome
        append("*1. RINGKASAN KAS*\n")
        append("• Saldo kas saat ini: *${Rupiah.format(d.balance)}*\n")
        append("• Iuran terkumpul: ${Rupiah.format(d.duesIncome)}\n")
        append("• Pemasukan lain: ${Rupiah.format(d.otherIncome)}\n")
        append("• Total pemasukan: ${Rupiah.format(income)}\n")
        append("• Total pengeluaran: ${Rupiah.format(d.expense)}\n")
        append("• Selisih periode: _${signed(income - d.expense)}_\n\n")

        append("*2. PENGELUARAN*\n")
        if (d.expenses.isEmpty()) {
            append("_Belum ada pengeluaran pada periode ini._\n\n")
        } else {
            d.expenses.take(MAX_EXPENSES).forEachIndexed { i, e ->
                append("${i + 1}. ${e.date} · ${clean(e.text)} (${Rupiah.format(e.amount)})\n")
            }
            if (d.expenses.size > MAX_EXPENSES) {
                append("_...dan ${d.expenses.size - MAX_EXPENSES} pengeluaran lainnya_\n")
            }
            append('\n')
        }

        append("*3. STATUS ANGGOTA*\n")
        if (d.activeMembers == 0) {
            append("_Belum ada anggota aktif._\n\n")
        } else {
            val pct = d.cleanMembers * 100 / d.activeMembers
            append("Tanpa tunggakan: ${d.cleanMembers} dari ${d.activeMembers} anggota ($pct%)\n\n")
        }

        append("*4. TUNGGAKAN*\n")
        if (d.overdue.isEmpty()) {
            append("_Tidak ada tunggakan._\n\n")
        } else {
            append("Total: *${Rupiah.format(d.overdue.sumOf { it.second })}*\n")
            d.overdue.take(MAX_OVERDUE).forEachIndexed { i, (name, amt) ->
                append("${i + 1}. ${clean(name)} — ${Rupiah.format(amt)}\n")
            }
            if (d.overdue.size > MAX_OVERDUE) {
                append("_...dan ${d.overdue.size - MAX_OVERDUE} anggota lainnya_\n")
            }
            append('\n')
        }

        append(LINE).append('\n')
        append("_Laporan otomatis dari aplikasi KasBareng_")
    }.trimEnd()
}