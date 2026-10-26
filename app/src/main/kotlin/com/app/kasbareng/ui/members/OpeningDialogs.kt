package com.app.kasbareng.ui.members

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.kasbareng.R
import com.app.kasbareng.data.entity.Member
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.domain.Opening
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.Popup
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.ui.onTextChanged
import com.app.kasbareng.util.Rupiah
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object OpeningDialogs {

    fun showArrears(fragment: Fragment, vm: KasViewModel, preselected: Member? = null) {
        val ctx = fragment.requireContext()
        val members = vm.memberRows.value.filter { it.member.isActive }
        if (members.isEmpty()) {
            UiFormat.snack(fragment, "Tambahkan anggota aktif dulu di tab Anggota.")
            return
        }
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_arrears, null)
        val dd = v.findViewById<AutoCompleteTextView>(R.id.dd_member)
        val etAmount = v.findViewById<TextInputEditText>(R.id.et_amount)
        val tvInfo = v.findViewById<TextView>(R.id.tv_info)
        val tvPreview = v.findViewById<TextView>(R.id.tv_preview)
        val tvError = v.findViewById<TextView>(R.id.tv_error)

        dd.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, members.map { it.member.name }))
        var selected: MemberRow? = null

        fun amount(): Long = etAmount.text?.toString()?.toLongOrNull() ?: 0L

        fun pick(row: MemberRow) {
            selected = row
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val cur = vm.obligations.first()
                    .firstOrNull { it.memberId == row.member.id && it.dueEpochDay == Opening.DUE_DAY }
                if (cur != null) {
                    etAmount.setText(cur.amountRupiah.toString())
                    tvInfo.text = "Tunggakan awal saat ini: ${Rupiah.format(cur.amountRupiah)}, " +
                        "sudah dibayar ${Rupiah.format(cur.paidRupiah)}."
                } else {
                    etAmount.setText("")
                    tvInfo.text = "Belum ada tunggakan awal untuk anggota ini."
                }
            }
        }

        dd.setOnItemClickListener { parent, _, pos, _ ->
            val name = parent.getItemAtPosition(pos) as String
            members.firstOrNull { it.member.name == name }?.let { pick(it) }
        }
        etAmount.onTextChanged {
            val a = amount()
            tvPreview.text = if (a > 0) Rupiah.format(a) else ""
        }
        preselected?.let { p ->
            members.firstOrNull { it.member.id == p.id }?.let {
                dd.setText(it.member.name, false)
                pick(it)
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Tunggakan awal dari buku")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                tvError.visibility = View.GONE
                val m = selected
                if (m == null) {
                    tvError.text = "Pilih anggota dulu."
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val a = amount()
                val msg = "Anggota: ${m.member.name}\nTunggakan awal: ${Rupiah.format(a)}\n\n" +
                    "Nominal ini menggantikan tunggakan awal sebelumnya dan tidak menambah saldo kas."
                Popup.confirm(ctx, "Simpan tunggakan awal?", msg) {
                    btn.isEnabled = false
                    vm.setOpeningArrears(m.member.id, a) { out ->
                        btn.isEnabled = true
                        when (out) {
                            is Outcome.Ok -> {
                                dialog.dismiss()
                                UiFormat.snack(fragment, out.message)
                            }
                            is Outcome.Fail -> {
                                tvError.text = out.message
                                tvError.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }
}