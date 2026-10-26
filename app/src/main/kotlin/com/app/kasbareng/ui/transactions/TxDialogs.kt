package com.app.kasbareng.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.app.kasbareng.R
import com.app.kasbareng.data.QrisStore
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.ui.DateField
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.Popup
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.ui.onTextChanged
import com.app.kasbareng.ui.qris.QrisDialogs
import com.app.kasbareng.util.Rupiah
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object TxDialogs {

    // ---------- Bayar kas (arrears = false) / Pelunasan tunggakan (arrears = true) ----------
    fun showPayment(fragment: Fragment, vm: KasViewModel, arrears: Boolean) {
        val ctx = fragment.requireContext()
        val active = vm.memberRows.value.filter { it.member.isActive }
        if (active.isEmpty()) {
            UiFormat.snack(fragment, "Tambahkan anggota aktif dulu di tab Anggota.")
            return
        }
        val members = if (arrears) active.filter { it.status.overdueRupiah > 0 } else active
        if (members.isEmpty()) {
            Popup.info(ctx, "Tidak ada tunggakan", "Semua anggota aktif tidak punya tunggakan.")
            return
        }
        val title = if (arrears) "Pelunasan tunggakan" else "Bayar kas"

        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_payment, null)
        val dd = v.findViewById<AutoCompleteTextView>(R.id.dd_member)
        val etAmount = v.findViewById<TextInputEditText>(R.id.et_amount)
        val etNote = v.findViewById<TextInputEditText>(R.id.et_note)
        val tvInfo = v.findViewById<TextView>(R.id.tv_info)
        val tvPreview = v.findViewById<TextView>(R.id.tv_preview)
        val tvError = v.findViewById<TextView>(R.id.tv_error)
        val btnFill = v.findViewById<TextView>(R.id.btn_fill)
        val methodGroup = v.findViewById<ChipGroup>(R.id.method_group)
        val amountBlock = v.findViewById<View>(R.id.amount_block)
        val qrisNote = v.findViewById<TextView>(R.id.tv_qris_note)
        val dateField = DateField(
            fragment,
            v.findViewById<TextInputLayout>(R.id.til_date),
            v.findViewById<TextInputEditText>(R.id.et_date)
        )

        tvInfo.text = if (arrears) "Hanya anggota yang menunggak yang muncul di daftar."
        else "Untuk iuran rutin yang masih berjalan. Kalau ada tunggakan, pakai Pelunasan tunggakan."

        dd.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, members.map { it.member.name }))
        var selected: MemberRow? = null
        val uuid = vm.newUuid()   // satu uuid per form: tekan Simpan dua kali tidak menggandakan

        fun amount(): Long = etAmount.text?.toString()?.toLongOrNull() ?: 0L
        fun suggested(): Long =
            if (arrears) selected?.status?.overdueRupiah ?: 0L else selected?.status?.upcomingRupiah ?: 0L
        fun suggestionText(): String {
            val s = selected?.status ?: return ""
            return if (arrears) "Tunggakan: ${Rupiah.format(s.overdueRupiah)}"
            else if (s.upcomingRupiah > 0) "Tagihan berjalan: ${Rupiah.format(s.upcomingRupiah)}"
            else ""
        }

        dd.setOnItemClickListener { parent, _, pos, _ ->
            val name = parent.getItemAtPosition(pos) as String
            selected = members.firstOrNull { it.member.name == name }
            val s = selected?.status
            tvInfo.text = if (arrears) {
                "Tunggakan: ${Rupiah.format(s?.overdueRupiah ?: 0L)}"
            } else {
                val up = s?.upcomingRupiah ?: 0L
                val od = s?.overdueRupiah ?: 0L
                buildString {
                    append(if (up > 0) "Tagihan berjalan: ${Rupiah.format(up)}" else "Belum ada tagihan berjalan.")
                    if (od > 0) append("\nAda tunggakan ${Rupiah.format(od)}. Gunakan Pelunasan tunggakan.")
                    val cr = selected?.creditRupiah ?: 0L
                    if (cr > 0) append("\nSaldo di muka: ${Rupiah.format(cr)}")
                }
            }
        }
        btnFill.setOnClickListener {
            val s = suggested()
            if (selected == null) tvInfo.text = "Pilih anggota dulu."
            else if (s > 0) etAmount.setText(s.toString())
            else tvInfo.text = if (arrears) "Tidak ada tunggakan." else "Belum ada tagihan berjalan."
        }
        etAmount.onTextChanged {
            val a = amount()
            tvPreview.text = if (a > 0) Rupiah.format(a) else ""
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()

        fun method(): String? = v.findViewById<Chip>(methodGroup.checkedChipId)?.text?.toString()
        fun isQris(): Boolean = methodGroup.checkedChipId == R.id.m_qris

        fun updateMode() {
            val q = isQris()
            amountBlock.visibility = if (q) View.GONE else View.VISIBLE
            qrisNote.visibility = if (q) View.VISIBLE else View.GONE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.text = if (q) "Tampilkan QRIS" else "Simpan"
        }
        methodGroup.setOnCheckedStateChangeListener { _, _ -> updateMode() }

        fun send(acceptExcess: Boolean, btn: View) {
            val m = selected ?: return
            btn.isEnabled = false
            vm.recordDuesPayment(
                m.member.id, amount(), uuid, dateField.occurredAtMillis(),
                method(), etNote.text?.toString(), acceptExcess, arrears
            ) { out ->
                btn.isEnabled = true
                when (out) {
                    is Outcome.Ok -> {
                        dialog.dismiss()
                        UiFormat.snack(fragment, out.message)
                    }
                    is Outcome.Fail -> {
                        if (out.excessRupiah > 0) {
                            Popup.confirm(
                                ctx, "Pembayaran melebihi kewajiban",
                                "Kelebihan ${Rupiah.format(out.excessRupiah)} disimpan sebagai saldo di muka " +
                                    "dan otomatis menutup tagihan berikutnya. Lanjutkan?",
                                positive = "Simpan"
                            ) { send(true, btn) }
                        } else {
                            tvError.text = out.message
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        fun confirmAndSend(m: MemberRow, btn: View) {
            if (amount() <= 0L) {
                tvError.text = "Isi nominal pembayaran."
                tvError.visibility = View.VISIBLE
                return
            }
            val after = vm.home.value.balance + amount()
            val msg = "Anggota: ${m.member.name}\nJenis: $title\nNominal: ${Rupiah.format(amount())}\n" +
                "Tanggal: ${dateField.label()}\nMetode: ${method() ?: "-"}\n" +
                "Saldo kas jadi: ${Rupiah.format(after)}"
            Popup.confirm(ctx, "Simpan $title?", msg) { send(false, btn) }
        }

        fun startQris(m: MemberRow, btn: View) {
            val bmp = QrisStore.load(ctx)
            if (bmp == null) {
                Popup.info(
                    ctx, "QRIS belum diunggah",
                    "Unggah gambar QRIS dulu di tab Setelan, bagian Pembayaran QRIS."
                )
                return
            }
            QrisDialogs.show(ctx, bmp, "Anggota: ${m.member.name} · ${dateField.label()}") {
                val hint = "Isi nominal yang benar-benar dibayar anggota." +
                    suggestionText().let { if (it.isEmpty()) "" else "\n$it" }
                QrisDialogs.askAmount(ctx, hint) { a ->
                    etAmount.setText(a.toString())
                    confirmAndSend(m, btn)
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                tvError.visibility = View.GONE
                val m = selected
                if (m == null) {
                    tvError.text = "Pilih anggota dulu."
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (isQris()) startQris(m, btn) else confirmAndSend(m, btn)
            }
        }
        dialog.show()
    }

    // ---------- Pengeluaran / pemasukan lain / saldo awal ----------
    fun showOther(fragment: Fragment, vm: KasViewModel, type: String) {
        val ctx = fragment.requireContext()
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_tx_other, null)
        val etAmount = v.findViewById<TextInputEditText>(R.id.et_amount)
        val etDesc = v.findViewById<TextInputEditText>(R.id.et_desc)
        val til = v.findViewById<TextInputLayout>(R.id.til_desc)
        val tvPreview = v.findViewById<TextView>(R.id.tv_preview)
        val tvError = v.findViewById<TextView>(R.id.tv_error)
        val dateField = DateField(
            fragment,
            v.findViewById<TextInputLayout>(R.id.til_date),
            v.findViewById<TextInputEditText>(R.id.et_date)
        )

        val title = when (type) {
            "INCOME" -> "Pemasukan lain"
            "EXPENSE" -> "Pengeluaran"
            else -> "Saldo awal"
        }
        til.hint = if (type == "EXPENSE") "Keperluan" else "Keterangan"
        if (type == "OPENING_BALANCE") etDesc.setText("Saldo awal")

        val uuid = vm.newUuid()
        fun amount(): Long = etAmount.text?.toString()?.toLongOrNull() ?: 0L
        fun currentBalance(): Long = vm.home.value.balance
        fun insufficient(a: Long): Boolean = type == "EXPENSE" && a > currentBalance()
        fun showInsufficient() {
            tvError.text = "Saldo tidak cukup. Saldo kas saat ini ${Rupiah.format(currentBalance())}."
            tvError.visibility = View.VISIBLE
        }

        etAmount.onTextChanged {
            val a = amount()
            tvPreview.text = if (a > 0) Rupiah.format(a) else ""
            if (insufficient(a)) showInsufficient() else tvError.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Catat ${title.lowercase()}")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                tvError.visibility = View.GONE
                val a = amount()
                val desc = etDesc.text?.toString().orEmpty().trim()
                if (a <= 0L) {
                    tvError.text = "Isi nominal."
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (insufficient(a)) {
                    showInsufficient()
                    return@setOnClickListener
                }
                if (desc.isEmpty()) {
                    tvError.text = "Keterangan tidak boleh kosong."
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val balance = currentBalance()
                val after = if (type == "EXPENSE") balance - a else balance + a
                val msg = "Jenis: $title\nNominal: ${Rupiah.format(a)}\nTanggal: ${dateField.label()}\n" +
                    "Keterangan: $desc\nSaldo kas jadi: ${Rupiah.format(after)}"
                Popup.confirm(ctx, "Simpan $title?", msg) {
                    btn.isEnabled = false
                    vm.recordOther(type, a, desc, uuid, dateField.occurredAtMillis()) { out ->
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