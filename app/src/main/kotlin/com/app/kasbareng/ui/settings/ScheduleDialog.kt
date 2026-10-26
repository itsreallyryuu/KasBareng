package com.app.kasbareng.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.kasbareng.R
import com.app.kasbareng.data.entity.ScheduleVersion
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.domain.ScheduleText
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.Popup
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.ui.onTextChanged
import com.app.kasbareng.util.Rupiah
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ScheduleDialog {

    private val dayIds = intArrayOf(
        R.id.chip_d0, R.id.chip_d1, R.id.chip_d2, R.id.chip_d3,
        R.id.chip_d4, R.id.chip_d5, R.id.chip_d6
    )

    fun show(fragment: Fragment, vm: KasViewModel) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val latest = vm.schedules.first().firstOrNull()
            open(fragment, vm, latest)
        }
    }

    private fun open(fragment: Fragment, vm: KasViewModel, latest: ScheduleVersion?) {
        val ctx = fragment.requireContext()
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_schedule, null)
        val etAmount = v.findViewById<TextInputEditText>(R.id.et_amount)
        val target = v.findViewById<TextView>(R.id.tv_target)
        val error = v.findViewById<TextView>(R.id.tv_error)
        val zoneGroup = v.findViewById<ChipGroup>(R.id.zone_group)
        zoneGroup.isSelectionRequired = false

        if (latest != null) {
            // Mengubah jadwal: tampilkan nilai yang berlaku sekarang
            dayIds.forEachIndexed { i, id ->
                v.findViewById<Chip>(id).isChecked = ((latest.payDaysMask shr i) and 1) == 1
            }
            etAmount.setText(latest.amountRupiah.toString())
            zoneGroup.check(
                when (latest.timeZoneId) {
                    "Asia/Makassar" -> R.id.z_wita
                    "Asia/Jayapura" -> R.id.z_wit
                    else -> R.id.z_wib
                }
            )
        } else {
            // Jadwal pertama: semua kosong, pengguna yang mengisi
            dayIds.forEach { v.findViewById<Chip>(it).isChecked = false }
            zoneGroup.clearCheck()
        }
        v.findViewById<View>(R.id.tv_note).visibility = if (latest != null) View.VISIBLE else View.GONE

        fun mask(): Int = dayIds.foldIndexed(0) { i, acc, id ->
            if (v.findViewById<Chip>(id).isChecked) acc or (1 shl i) else acc
        }
        fun amount(): Long = etAmount.text?.toString()?.toLongOrNull() ?: 0L
        fun zoneIdOrNull(): String? = when (zoneGroup.checkedChipId) {
            R.id.z_wib -> "Asia/Jakarta"
            R.id.z_wita -> "Asia/Makassar"
            R.id.z_wit -> "Asia/Jayapura"
            else -> null
        }
        fun updateTarget() {
            val days = Integer.bitCount(mask())
            val amt = amount()
            target.text = if (days == 0 || amt <= 0L) "Target mingguan: pilih hari dan isi nominal."
            else "Target mingguan per anggota: ${Rupiah.format(days * amt)} " +
                "($days hari × ${Rupiah.format(amt)})"
        }
        updateTarget()
        dayIds.forEach { v.findViewById<Chip>(it).setOnCheckedChangeListener { _, _ -> updateTarget() } }
        etAmount.onTextChanged { updateTarget() }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Jadwal kas")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                error.visibility = View.GONE
                val m = mask()
                val a = amount()
                val z = zoneIdOrNull()
                val problem = when {
                    m == 0 -> "Pilih minimal satu hari pembayaran."
                    a <= 0L -> "Isi nominal per pembayaran."
                    else -> null
                }
                if (problem != null || z == null) {
                    error.text = problem ?: "Pilih zona waktu."
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val message = buildString {
                    append("Hari bayar: ${ScheduleText.days(m)}\n")
                    append("Nominal: ${Rupiah.format(a)} per hari bayar\n")
                    append("Target mingguan: ${Rupiah.format(Integer.bitCount(m) * a)} per anggota\n")
                    append("Zona waktu: ${ScheduleText.zoneLabel(z)}\n\n")
                    append(
                        if (latest != null) "Berlaku mulai Senin depan. Kewajiban yang sudah ada tidak berubah."
                        else "Berlaku mulai hari ini."
                    )
                }
                Popup.confirm(ctx, "Simpan jadwal kas?", message) {
                    btn.isEnabled = false
                    vm.saveSchedule(m, a, z) { out ->
                        btn.isEnabled = true
                        when (out) {
                            is Outcome.Ok -> {
                                dialog.dismiss()
                                UiFormat.snack(fragment, out.message)
                            }
                            is Outcome.Fail -> {
                                error.text = out.message
                                error.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }
}