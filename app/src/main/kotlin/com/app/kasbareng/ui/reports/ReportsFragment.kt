package com.app.kasbareng.ui.reports

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.kasbareng.R
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.domain.Badge
import com.app.kasbareng.domain.ReportBuilder
import com.app.kasbareng.domain.ReportCharts
import com.app.kasbareng.domain.ReportData
import com.app.kasbareng.domain.ReportLine
import com.app.kasbareng.domain.ReportPeriod
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.util.Rupiah
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportsFragment : Fragment(R.layout.fragment_reports) {

    private val vm: KasViewModel by lazy {
        ViewModelProvider(requireActivity())[KasViewModel::class.java]
    }
    private var periodId = R.id.p_week
    private var txs: List<CashTransaction> = emptyList()
    private var rows: List<MemberRow> = emptyList()
    private var balance = 0L
    private var reportText = ""

    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale("id", "ID"))

    private lateinit var tvBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var tvOverdueTitle: TextView
    private lateinit var tvOverdueList: TextView
    private lateinit var barChart: CashChartView
    private lateinit var lineChart: CashChartView
    private lateinit var segSummary: TextView
    private lateinit var segLegend: TextView
    private lateinit var segCard: View
    private lateinit var segLunas: View
    private lateinit var segRunning: View
    private lateinit var segOverdue: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvBalance = view.findViewById(R.id.rp_balance)
        tvIncome = view.findViewById(R.id.rp_income)
        tvExpense = view.findViewById(R.id.rp_expense)
        tvOverdueTitle = view.findViewById(R.id.rp_overdue_title)
        tvOverdueList = view.findViewById(R.id.rp_overdue_list)
        barChart = view.findViewById(R.id.chart_bars)
        lineChart = view.findViewById(R.id.chart_line)
        barChart.mode = CashChartView.Mode.BARS
        lineChart.mode = CashChartView.Mode.LINE
        segSummary = view.findViewById(R.id.seg_summary)
        segLegend = view.findViewById(R.id.seg_legend)
        segCard = view.findViewById(R.id.seg_card)
        segLunas = view.findViewById(R.id.seg_lunas)
        segRunning = view.findViewById(R.id.seg_running)
        segOverdue = view.findViewById(R.id.seg_overdue)

        view.findViewById<ChipGroup>(R.id.period_group).setOnCheckedStateChangeListener { _, ids ->
            periodId = ids.firstOrNull() ?: R.id.p_week
            render()
        }
        view.findViewById<View>(R.id.btn_share).setOnClickListener {
            if (reportText.isBlank()) return@setOnClickListener
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, reportText)
            }
            startActivity(Intent.createChooser(send, "Bagikan laporan"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.transactions.collect { txs = it; render() } }
                launch { vm.memberRows.collect { rows = it; render() } }
                launch { vm.home.collect { balance = it.balance; render() } }
            }
        }
    }

    private fun setWeight(v: View, w: Float) {
        val lp = v.layoutParams as LinearLayout.LayoutParams
        lp.weight = w
        v.layoutParams = lp
    }

    private fun render() {
        val zone = ZoneId.systemDefault()
        val period = when (periodId) {
            R.id.p_month -> ReportPeriod.MONTH
            R.id.p_all -> ReportPeriod.ALL
            else -> ReportPeriod.WEEK
        }
        val label = when (period) {
            ReportPeriod.MONTH -> "Bulan ini"
            ReportPeriod.ALL -> "Semua waktu"
            ReportPeriod.WEEK -> "Minggu ini"
        }
        val start = ReportCharts.periodStart(period, LocalDate.now(zone), zone)
        val valid = txs.filter { it.status == "VALID" && it.occurredAt >= start }
        val duesIncome = valid.filter { it.type == "DUES_PAYMENT" }.sumOf { it.amountRupiah }
        val otherIncome = valid.filter { it.type == "INCOME" || it.type == "OPENING_BALANCE" }
            .sumOf { it.amountRupiah }
        val income = duesIncome + otherIncome
        val expense = valid.filter { it.type == "EXPENSE" }.sumOf { it.amountRupiah }

        // Grafik
        val buckets = ReportCharts.build(txs, period, zone)
        barChart.setData(buckets)
        lineChart.setData(buckets)

        // Status anggota
        val active = rows.filter { it.member.isActive }
        val overdueCount = active.count { it.status.overdueRupiah > 0 }
        val lunasCount = active.count { it.status.badge == Badge.LUNAS }
        val runningCount = active.size - overdueCount - lunasCount
        if (active.isEmpty()) {
            segSummary.text = "Belum ada anggota aktif."
            segCard.visibility = View.GONE
            segLegend.text = ""
        } else {
            val okCount = active.size - overdueCount
            val pct = okCount * 100 / active.size
            segSummary.text = "Tanpa tunggakan: $okCount dari ${active.size} anggota ($pct%)"
            segCard.visibility = View.VISIBLE
            setWeight(segLunas, lunasCount.toFloat())
            setWeight(segRunning, runningCount.toFloat())
            setWeight(segOverdue, overdueCount.toFloat())
            segLegend.text = "Lunas $lunasCount · Berjalan $runningCount · Nunggak $overdueCount"
        }

        // Tunggakan
        val overdue = active
            .filter { it.status.overdueRupiah > 0 }
            .sortedByDescending { it.status.overdueRupiah }
            .map { it.member.name to it.status.overdueRupiah }

        tvBalance.text = Rupiah.format(balance)
        tvIncome.text = Rupiah.format(income)
        tvExpense.text = Rupiah.format(expense)
        if (overdue.isEmpty()) {
            tvOverdueTitle.text = "Tunggakan"
            tvOverdueList.text = "Tidak ada tunggakan."
        } else {
            tvOverdueTitle.text = "Tunggakan (total ${Rupiah.format(overdue.sumOf { it.second })})"
            tvOverdueList.text = overdue.joinToString("\n") { "• ${it.first} - ${Rupiah.format(it.second)}" }
        }

        // Teks laporan untuk dibagikan
        val expenseLines = valid.filter { it.type == "EXPENSE" }
            .sortedBy { it.occurredAt }
            .map {
                ReportLine(
                    date = dateFmt.format(Instant.ofEpochMilli(it.occurredAt).atZone(zone)),
                    text = it.description,
                    amount = it.amountRupiah
                )
            }
        reportText = ReportBuilder.build(
            ReportData(
                periodLabel = label,
                generatedAt = UiFormat.dateTime(System.currentTimeMillis()),
                balance = balance,
                duesIncome = duesIncome,
                otherIncome = otherIncome,
                expense = expense,
                expenses = expenseLines,
                activeMembers = active.size,
                cleanMembers = active.size - overdueCount,
                overdue = overdue
            )
        )
    }
}