package com.app.kasbareng.ui.transactions

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.kasbareng.R
import com.app.kasbareng.data.dao.AllocationDetail
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.ui.DateField
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.Popup
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.ui.onTextChanged
import com.app.kasbareng.util.Rupiah
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

object TxDetail {

    private fun msg(out: Outcome): String = when (out) {
        is Outcome.Ok -> out.message
        is Outcome.Fail -> out.message
    }

    fun show(fragment: Fragment, vm: KasViewModel, t: CashTransaction) {
        val ctx = fragment.requireContext()
        val v = LayoutInflater.from(ctx).inflate(R.layout.sheet_tx_detail, null)
        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val voided = t.status != "VALID"
        val isOut = t.type == "EXPENSE"

        v.findViewById<TextView>(R.id.tx_title).text = t.description
        val big = v.findViewById<TextView>(R.id.tx_amount_big)
        big.text = (if (isOut) "-" else "+") + Rupiah.format(t.amountRupiah)
        big.setTextColor(
            ContextCompat.getColor(
                ctx,
                when {
                    voided -> R.color.status_nonaktif
                    isOut -> R.color.status_nunggak
                    else -> R.color.status_lunas
                }
            )
        )
        if (voided) big.paintFlags = big.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        val memberName = t.memberId?.let { id ->
            vm.memberRows.value.firstOrNull { it.member.id == id }?.member?.name
        }
        val info = v.findViewById<TextView>(R.id.tx_info)
        fun render(alloc: List<AllocationDetail>?) {
            info.text = buildString {
                append("Jenis: ${UiFormat.typeLabel(t.type)}\n")
                append("Tanggal: ${UiFormat.dateOnly(t.occurredAt)}\n")
                t.method?.let { append("Metode: $it\n") }
                memberName?.let { append("Anggota: $it\n") }
                append("Dicatat: ${UiFormat.dateTime(t.createdAt)}\n")
                append("Status: ${if (voided) "Dibatalkan" else "Berlaku"}")
                if (!alloc.isNullOrEmpty()) {
                    append("\n\nMelunasi:")
                    alloc.forEach {
                        append("\n• ${UiFormat.dueLabel(it.dueEpochDay)}: ${Rupiah.format(it.amountRupiah)}")
                    }
                }
            }
        }
        render(null)
        if (t.type == "DUES_PAYMENT") {
            fragment.viewLifecycleOwner.lifecycleScope.launch { render(vm.allocationDetails(t.id)) }
        }

        val btnEdit = v.findViewById<View>(R.id.btn_tx_edit)
        val btnVoid = v.findViewById<View>(R.id.btn_tx_void)
        val btnDelete = v.findViewById<View>(R.id.btn_tx_delete)
        btnEdit.visibility = if (voided) View.GONE else View.VISIBLE
        btnVoid.visibility = if (voided) View.GONE else View.VISIBLE
        btnDelete.visibility = if (voided) View.VISIBLE else View.GONE

        btnEdit.setOnClickListener {
            dialog.dismiss()
            showEdit(fragment, vm, t)
        }
        btnVoid.setOnClickListener {
            Popup.confirm(
                ctx, "Batalkan transaksi ini?",
                "Transaksi tidak dihapus, hanya ditandai dibatalkan dan tidak dihitung lagi. " +
                    "Kalau ini pembayaran iuran, tunggakannya muncul kembali.",
                positive = "Batalkan"
            ) {
                dialog.dismiss()
                vm.voidTransaction(t.id) { out -> UiFormat.snack(fragment, msg(out)) }
            }
        }
        btnDelete.setOnClickListener {
            Popup.confirm(
                ctx, "Hapus permanen?",
                "Transaksi yang sudah dibatalkan ini dihapus selamanya dan tidak bisa dikembalikan.",
                positive = "Hapus"
            ) {
                dialog.dismiss()
                vm.deleteTransaction(t.id) { out -> UiFormat.snack(fragment, msg(out)) }
            }
        }
        dialog.show()
    }

    private fun showEdit(fragment: Fragment, vm: KasViewModel, t: CashTransaction) {
        val ctx = fragment.requireContext()
        val dues = t.type == "DUES_PAYMENT"
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_tx_edit, null)
        val tilAmount = v.findViewById<TextInputLayout>(R.id.til_amount)
        val tilDesc = v.findViewById<TextInputLayout>(R.id.til_desc)
        val etAmount = v.findViewById<TextInputEditText>(R.id.et_amount)
        val etDesc = v.findViewById<TextInputEditText>(R.id.et_desc)
        val tvPreview = v.findViewById<TextView>(R.id.tv_preview)
        val tvError = v.findViewById<TextView>(R.id.tv_error)
        val group = v.findViewById<ChipGroup>(R.id.method_group)

        val initial = Instant.ofEpochMilli(t.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val dateField = DateField(
            fragment,
            v.findViewById<TextInputLayout>(R.id.til_date),
            v.findViewById<TextInputEditText>(R.id.et_date),
            initial
        )

        fun amount(): Long = etAmount.text?.toString()?.toLongOrNull() ?: 0L
        fun method(): String? = v.findViewById<Chip>(group.checkedChipId)?.text?.toString()

        if (dues) {
            tilAmount.visibility = View.GONE
            tvPreview.visibility = View.GONE
            tilDesc.visibility = View.GONE
            v.findViewById<View>(R.id.tv_method_label).visibility = View.VISIBLE
            group.visibility = View.VISIBLE
            for (i in 0 until group.childCount) {
                val c = group.getChildAt(i) as Chip
                if (c.text.toString() == t.method) group.check(c.id)
            }
            if (group.checkedChipId == View.NO_ID) group.check(R.id.m_other)
        } else {
            etAmount.setText(t.amountRupiah.toString())
            etDesc.setText(t.description)
            tilDesc.hint = if (t.type == "EXPENSE") "Keperluan" else "Keterangan"
            tvPreview.text = Rupiah.format(t.amountRupiah)
            etAmount.onTextChanged {
                val a = amount()
                tvPreview.text = if (a > 0) Rupiah.format(a) else ""
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Edit transaksi")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                tvError.visibility = View.GONE
                val desc = etDesc.text?.toString().orEmpty().trim()
                val a = amount()
                if (!dues) {
                    if (a <= 0L) {
                        tvError.text = "Isi nominal."
                        tvError.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    if (desc.isEmpty()) {
                        tvError.text = "Keterangan tidak boleh kosong."
                        tvError.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                }
                val summary = if (dues) {
                    "Tanggal: ${dateField.label()}\nMetode: ${method() ?: "-"}"
                } else {
                    "Nominal: ${Rupiah.format(a)}\nTanggal: ${dateField.label()}\nKeterangan: $desc"
                }
                Popup.confirm(ctx, "Simpan perubahan?", summary) {
                    btn.isEnabled = false
                    vm.updateTransaction(
                        t.id,
                        dateField.millisKeepingTimeOf(t.occurredAt),
                        if (dues) null else desc,
                        if (dues) method() else null,
                        if (dues) null else a
                    ) { out ->
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