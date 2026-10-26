package com.app.kasbareng.ui.members

import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.app.kasbareng.R
import com.app.kasbareng.data.entity.Member
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.util.Rupiah
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object MemberDialogs {

    fun showForm(fragment: Fragment, vm: KasViewModel, member: Member?) {
        val ctx = fragment.requireContext()
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_member, null)
        val til = v.findViewById<TextInputLayout>(R.id.til_name)
        val etName = v.findViewById<TextInputEditText>(R.id.et_name)
        val etNote = v.findViewById<TextInputEditText>(R.id.et_note)
        member?.let {
            etName.setText(it.name)
            etNote.setText(it.note)
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (member == null) "Tambah anggota" else "Edit anggota")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            etName.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                btn.isEnabled = false
                til.error = null
                val handle: (Outcome) -> Unit = { out ->
                    btn.isEnabled = true
                    when (out) {
                        is Outcome.Ok -> {
                            dialog.dismiss()
                            UiFormat.snack(fragment, out.message)
                        }
                        is Outcome.Fail -> til.error = out.message
                    }
                }
                val name = etName.text?.toString().orEmpty()
                val note = etNote.text?.toString()
                if (member == null) vm.addMember(name, note, handle)
                else vm.updateMember(member.id, name, note, handle)
            }
        }
        dialog.show()
    }

    fun showDetail(fragment: Fragment, vm: KasViewModel, row: MemberRow) {
        val ctx = fragment.requireContext()
        val m = row.member
        val s = row.status
        val msg = buildString {
            append("Status: ${s.badge.label}\n\n")
            if (s.overdueItems.isEmpty()) {
                append("Tidak ada tunggakan.")
            } else {
                append("Rincian tunggakan:\n")
                s.overdueItems.forEach {
                    append("• ${UiFormat.dateLabel(it.dueEpochDay)}: ${Rupiah.format(it.remaining())} belum dibayar\n")
                }
                append("Total tunggakan: ${Rupiah.format(s.overdueRupiah)}")
            }
            m.note?.let { append("\n\nCatatan: $it") }
        }
        val b = MaterialAlertDialogBuilder(ctx)
            .setTitle(m.name)
            .setMessage(msg)
            .setNeutralButton("Riwayat") { _, _ -> MemberHistory.show(fragment, vm, row) }
        if (m.isActive) {
            b.setPositiveButton("Edit") { _, _ -> showForm(fragment, vm, m) }
            b.setNegativeButton("Nonaktifkan") { _, _ -> confirmDeactivate(fragment, vm, m) }
        }
        b.show()
    }

    private fun confirmDeactivate(fragment: Fragment, vm: KasViewModel, m: Member) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Nonaktifkan ${m.name}?")
            .setMessage(
                "Riwayat dan tunggakan yang sudah ada tetap tersimpan. " +
                    "Kewajiban masa depan yang belum dibayar akan dibatalkan."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Nonaktifkan") { _, _ ->
                vm.deactivateMember(m.id) { out ->
                    UiFormat.snack(
                        fragment,
                        when (out) {
                            is Outcome.Ok -> out.message
                            is Outcome.Fail -> out.message
                        }
                    )
                }
            }
            .show()
    }
}