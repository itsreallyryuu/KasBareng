package com.app.kasbareng.ui.members

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.kasbareng.R
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.Popup
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.util.Rupiah
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object MemberDetail {

    private fun msg(out: Outcome): String = when (out) {
        is Outcome.Ok -> out.message
        is Outcome.Fail -> out.message
    }

    fun show(fragment: Fragment, vm: KasViewModel, row: MemberRow) {
        val ctx = fragment.requireContext()
        val m = row.member
        val s = row.status
        val v = LayoutInflater.from(ctx).inflate(R.layout.sheet_member_detail, null)
        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        v.findViewById<TextView>(R.id.md_name).text = m.name

        val badge = v.findViewById<TextView>(R.id.md_badge)
        badge.text = s.badge.label
        val color = UiFormat.badgeColor(ctx, s.badge)
        badge.setTextColor(color)
        badge.background = GradientDrawable().apply {
            cornerRadius = UiFormat.dp(ctx, 6).toFloat()
            setStroke(UiFormat.dp(ctx, 1), color)
        }

        val note = v.findViewById<TextView>(R.id.md_note)
        if (m.note.isNullOrBlank()) {
            note.visibility = View.GONE
        } else {
            note.text = "Catatan: ${m.note}"
            note.visibility = View.VISIBLE
        }

        val info = v.findViewById<TextView>(R.id.md_info)
        fun render(paid: Long?) {
            info.text = buildString {
                append("Tunggakan: ${Rupiah.format(s.overdueRupiah)}\n")
                append("Tagihan berjalan: ${Rupiah.format(s.upcomingRupiah)}\n")
                if (row.creditRupiah > 0) append("Saldo di muka: ${Rupiah.format(row.creditRupiah)}\n")
                append("Total dibayar: ${paid?.let { Rupiah.format(it) } ?: "..."}\n")
                append("Bergabung: ${UiFormat.dateOnly(m.addedAt)}")
                m.deactivatedAt?.let { append("\nNonaktif sejak: ${UiFormat.dateOnly(it)}") }
            }
        }
        render(null)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val paid = vm.transactions.first()
                .filter { it.memberId == m.id && it.status == "VALID" && it.type == "DUES_PAYMENT" }
                .sumOf { it.amountRupiah }
            render(paid)
        }

        v.findViewById<View>(R.id.btn_hist).setOnClickListener {
            dialog.dismiss()
            MemberHistory.show(fragment, vm, row)
        }

        val btnOpening = v.findViewById<View>(R.id.btn_opening)
        btnOpening.visibility = if (m.isActive) View.VISIBLE else View.GONE
        btnOpening.setOnClickListener {
            dialog.dismiss()
            OpeningDialogs.showArrears(fragment, vm, m)
        }

        val btnEdit = v.findViewById<View>(R.id.btn_edit)
        btnEdit.visibility = if (m.isActive) View.VISIBLE else View.GONE
        btnEdit.setOnClickListener {
            dialog.dismiss()
            MemberDialogs.showForm(fragment, vm, m)
        }

        val btnToggle = v.findViewById<TextView>(R.id.btn_toggle)
        btnToggle.text = if (m.isActive) "Nonaktifkan anggota" else "Aktifkan kembali"
        btnToggle.setOnClickListener {
            if (m.isActive) {
                Popup.confirm(
                    ctx, "Nonaktifkan ${m.name}?",
                    "Riwayat dan tunggakan yang sudah ada tetap tersimpan. " +
                        "Kewajiban masa depan yang belum dibayar akan dibatalkan.",
                    positive = "Nonaktifkan"
                ) {
                    dialog.dismiss()
                    vm.deactivateMember(m.id) { out -> UiFormat.snack(fragment, msg(out)) }
                }
            } else {
                Popup.confirm(
                    ctx, "Aktifkan kembali ${m.name}?",
                    "Kewajiban baru dihitung mulai hari ini. Tunggakan lama (kalau ada) tetap tercatat.",
                    positive = "Aktifkan"
                ) {
                    dialog.dismiss()
                    vm.reactivateMember(m.id) { out -> UiFormat.snack(fragment, msg(out)) }
                }
            }
        }

        v.findViewById<View>(R.id.btn_delete).setOnClickListener {
            Popup.confirm(
                ctx, "Hapus ${m.name}?",
                "Anggota dihapus permanen beserta kewajibannya. Hanya bisa dilakukan kalau belum ada " +
                    "riwayat pembayaran. Kalau sudah ada, gunakan Nonaktifkan.",
                positive = "Hapus"
            ) {
                vm.deleteMember(m.id) { out ->
                    if (out is Outcome.Ok) dialog.dismiss()
                    UiFormat.snack(fragment, msg(out))
                }
            }
        }
        dialog.show()
    }
}