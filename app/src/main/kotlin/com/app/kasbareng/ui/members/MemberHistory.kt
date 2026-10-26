package com.app.kasbareng.ui.members

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.kasbareng.R
import com.app.kasbareng.data.dao.ObligationWithPaid
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.domain.DueRules
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.ui.transactions.TransactionAdapter
import com.app.kasbareng.ui.transactions.TxDetail
import com.app.kasbareng.util.Rupiah
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

object MemberHistory {

    fun show(fragment: Fragment, vm: KasViewModel, row: MemberRow) {
        val ctx = fragment.requireContext()
        val member = row.member

        val v = LayoutInflater.from(ctx).inflate(R.layout.sheet_member_history, null)
        val height = (ctx.resources.displayMetrics.heightPixels * 0.85f).toInt()
        v.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

        val title = v.findViewById<TextView>(R.id.hs_title)
        val summary = v.findViewById<TextView>(R.id.hs_summary)
        val chips = v.findViewById<ChipGroup>(R.id.hs_chips)
        val empty = v.findViewById<TextView>(R.id.hs_empty)
        val list = v.findViewById<RecyclerView>(R.id.hs_list)

        title.text = "Riwayat ${member.name}"
        val payAdapter = TransactionAdapter { TxDetail.show(fragment, vm, it) }
        val dueAdapter = DueAdapter()
        list.layoutManager = LinearLayoutManager(ctx)

        var showPayments = true
        var txs: List<CashTransaction> = emptyList()
        var dues: List<DueRow> = emptyList()

        fun render() {
            if (showPayments) {
                list.adapter = payAdapter
                payAdapter.submitList(txs)
                empty.text = "Belum ada pembayaran."
                empty.visibility = if (txs.isEmpty()) View.VISIBLE else View.GONE
            } else {
                list.adapter = dueAdapter
                dueAdapter.submitList(dues)
                empty.text = "Belum ada kewajiban. Kewajiban terbentuk otomatis pada hari bayar."
                empty.visibility = if (dues.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        chips.setOnCheckedStateChangeListener { _, ids ->
            showPayments = (ids.firstOrNull() ?: R.id.hs_pay) == R.id.hs_pay
            render()
        }

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.transactions, vm.obligations) { t, o -> t to o }.collect { (t, o) ->
                val zone = vm.zone()
                val now = Instant.now()
                txs = t.filter { it.memberId == member.id }
                val mine = o.filter { it.memberId == member.id }
                dues = mine.sortedByDescending { it.dueEpochDay }.map { toRow(it, now, zone) }

                val paid = txs.filter { it.status == "VALID" && it.type == "DUES_PAYMENT" }
                    .sumOf { it.amountRupiah }
                val overdue = mine.filter { it.remaining() > 0 && DueRules.isOverdue(it.dueEpochDay, now, zone) }
                    .sumOf { it.remaining() }
                summary.text = "Total dibayar ${Rupiah.format(paid)} · Tunggakan ${Rupiah.format(overdue)}"
                render()
            }
        }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    private fun toRow(o: ObligationWithPaid, now: Instant, zone: ZoneId): DueRow {
        val rem = o.remaining()
        val overdue = rem > 0 && DueRules.isOverdue(o.dueEpochDay, now, zone)
        val (tag, color) = when {
            rem == 0L -> "LUNAS" to R.color.status_lunas
            overdue -> "NUNGGAK" to R.color.status_nunggak
            o.paidRupiah > 0 -> "SEBAGIAN" to R.color.status_cicilan
            else -> "BERJALAN" to R.color.status_netral
        }
        val sub = when {
            rem == 0L -> "Dibayar penuh ${Rupiah.format(o.amountRupiah)}"
            o.paidRupiah > 0 -> "Dibayar ${Rupiah.format(o.paidRupiah)} dari ${Rupiah.format(o.amountRupiah)}"
            else -> "Tagihan ${Rupiah.format(o.amountRupiah)}"
        }
        return DueRow(o.id, UiFormat.dueLabel(o.dueEpochDay), sub, tag, color)
    }
}