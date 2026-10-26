package com.app.kasbareng.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.kasbareng.R
import com.app.kasbareng.data.QrisStore
import com.app.kasbareng.data.db.BackupManager
import com.app.kasbareng.data.entity.ScheduleVersion
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.domain.ScheduleText
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.Popup
import com.app.kasbareng.ui.Tutorial
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.ui.donate.DonateDialog
import com.app.kasbareng.ui.members.OpeningDialogs
import com.app.kasbareng.ui.qris.QrisDialogs
import com.app.kasbareng.ui.transactions.TxDialogs
import com.app.kasbareng.util.Prefs
import com.app.kasbareng.util.Rupiah
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val vm: KasViewModel by lazy {
        ViewModelProvider(requireActivity())[KasViewModel::class.java]
    }

    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) runBackup(uri) }

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) confirmRestore(uri) }

    private val pickQris = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) importQris(uri) }

    private lateinit var tvSchedule: TextView
    private lateinit var btnEdit: TextView
    private lateinit var btnCancelPending: View
    private lateinit var qrisPreview: ImageView
    private lateinit var qrisStatus: TextView
    private lateinit var btnQrisUpload: TextView
    private lateinit var btnQrisView: View
    private lateinit var btnQrisDelete: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvSchedule = view.findViewById(R.id.st_schedule)
        btnEdit = view.findViewById(R.id.btn_edit_schedule)
        btnEdit.setOnClickListener { ScheduleDialog.show(this, vm) }
        view.findViewById<View>(R.id.btn_schedule_history).setOnClickListener { showScheduleHistory() }
        btnCancelPending = view.findViewById(R.id.btn_cancel_pending)
        btnCancelPending.setOnClickListener { confirmCancelPending() }

        view.findViewById<View>(R.id.btn_open_balance).setOnClickListener {
            TxDialogs.showOther(this, vm, "OPENING_BALANCE")
        }
        view.findViewById<View>(R.id.btn_open_arrears).setOnClickListener {
            OpeningDialogs.showArrears(this, vm)
        }

        qrisPreview = view.findViewById(R.id.qris_preview)
        qrisStatus = view.findViewById(R.id.qris_status)
        btnQrisUpload = view.findViewById(R.id.btn_qris_upload)
        btnQrisView = view.findViewById(R.id.btn_qris_view)
        btnQrisDelete = view.findViewById(R.id.btn_qris_delete)
        btnQrisUpload.setOnClickListener { pickQris.launch("image/*") }
        btnQrisView.setOnClickListener {
            QrisStore.load(requireContext())?.let { QrisDialogs.preview(requireContext(), it) }
        }
        qrisPreview.setOnClickListener { btnQrisView.performClick() }
        btnQrisDelete.setOnClickListener { confirmDeleteQris() }
        renderQris()

        val ctx = requireContext()
        val group = view.findViewById<ChipGroup>(R.id.theme_group)
        group.check(
            when (Prefs.themeMode(ctx)) {
                AppCompatDelegate.MODE_NIGHT_NO -> R.id.th_light
                AppCompatDelegate.MODE_NIGHT_YES -> R.id.th_dark
                else -> R.id.th_system
            }
        )
        group.setOnCheckedStateChangeListener { _, ids ->
            val mode = when (ids.firstOrNull()) {
                R.id.th_light -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.th_dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (mode != Prefs.themeMode(ctx)) {
                Prefs.setThemeMode(ctx, mode)
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        view.findViewById<View>(R.id.btn_backup).setOnClickListener {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            createDoc.launch("KasBareng-backup-$stamp.db")
        }
        view.findViewById<View>(R.id.btn_restore).setOnClickListener {
            openDoc.launch(arrayOf("*/*"))
        }
        view.findViewById<View>(R.id.btn_donate).setOnClickListener { DonateDialog.show(requireContext()) }
        view.findViewById<View>(R.id.btn_tutorial).setOnClickListener { Tutorial.show(requireContext()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.schedules.collect { renderSchedule(it) }
            }
        }
    }

    // ---------- QRIS ----------
    private fun renderQris() {
        val ctx = requireContext()
        val has = QrisStore.exists(ctx)
        qrisPreview.visibility = if (has) View.VISIBLE else View.GONE
        if (has) qrisPreview.setImageBitmap(QrisStore.load(ctx, 400))
        qrisStatus.text = if (has) "QRIS sudah diunggah." else "Belum ada QRIS."
        btnQrisUpload.text = if (has) "Ganti QRIS" else "Unggah QRIS"
        btnQrisView.visibility = if (has) View.VISIBLE else View.GONE
        btnQrisDelete.visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun importQris(uri: Uri) {
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = QrisStore.import(appCtx, uri)
            if (!isAdded) return@launch
            renderQris()
            if (ok) {
                UiFormat.snack(this@SettingsFragment, "QRIS tersimpan.")
            } else {
                Popup.info(
                    requireContext(), "Gambar tidak bisa dibaca",
                    "Pilih file gambar (PNG atau JPG) yang berisi QRIS."
                )
            }
        }
    }

    private fun confirmDeleteQris() {
        Popup.confirm(
            requireContext(), "Hapus QRIS?",
            "Gambar QRIS dihapus dari aplikasi. Pembayaran via QRIS tidak bisa dipakai sampai kamu mengunggah lagi.",
            positive = "Hapus"
        ) {
            QrisStore.delete(requireContext())
            renderQris()
        }
    }

    // ---------- Jadwal kas ----------
    private fun describe(v: ScheduleVersion): String =
        "Hari bayar: ${ScheduleText.days(v.payDaysMask)}\n" +
            "Nominal: ${Rupiah.format(v.amountRupiah)} per hari bayar\n" +
            "Target mingguan: ${Rupiah.format(Integer.bitCount(v.payDaysMask) * v.amountRupiah)} per anggota\n" +
            "Zona waktu: ${ScheduleText.zoneLabel(v.timeZoneId)}"

    private fun renderSchedule(list: List<ScheduleVersion>) {
        if (list.isEmpty()) {
            tvSchedule.text = "Jadwal kas belum diatur. Atur dulu supaya kewajiban anggota bisa terbentuk."
            btnEdit.text = "Atur jadwal kas"
            btnCancelPending.visibility = View.GONE
            return
        }
        val today = LocalDate.now().toEpochDay()
        val current = list.firstOrNull { it.effectiveFromEpochDay <= today }
        val pending = list.firstOrNull()?.takeIf { it.effectiveFromEpochDay > today }
        tvSchedule.text = buildString {
            current?.let { append(describe(it)) }
            pending?.let {
                if (isNotEmpty()) append("\n\n")
                append("Jadwal baru, mulai ${UiFormat.dateLabel(it.effectiveFromEpochDay)}:\n")
                append(describe(it))
            }
        }
        btnEdit.text = "Ubah jadwal kas"
        btnCancelPending.visibility = if (pending != null) View.VISIBLE else View.GONE
    }

    private fun confirmCancelPending() {
        Popup.confirm(
            requireContext(), "Batalkan jadwal baru?",
            "Jadwal baru dihapus dan jadwal sebelumnya tetap berlaku.",
            positive = "Batalkan jadwal"
        ) {
            vm.cancelPendingSchedule { out ->
                UiFormat.snack(
                    this,
                    when (out) {
                        is Outcome.Ok -> out.message
                        is Outcome.Fail -> out.message
                    }
                )
            }
        }
    }

    private fun showScheduleHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = vm.schedules.first()
            if (list.isEmpty()) {
                Popup.info(requireContext(), "Riwayat jadwal kas", "Belum ada jadwal kas.")
                return@launch
            }
            val today = LocalDate.now().toEpochDay()
            val current = list.firstOrNull { it.effectiveFromEpochDay <= today }
            val msg = list.joinToString("\n\n") { v ->
                val state = when {
                    v.effectiveFromEpochDay > today -> "Akan berlaku"
                    v === current -> "Berlaku sekarang"
                    else -> "Sudah tidak berlaku"
                }
                "${UiFormat.dateLabel(v.effectiveFromEpochDay)} · $state\n${describe(v)}"
            }
            Popup.info(requireContext(), "Riwayat jadwal kas", msg)
        }
    }

    // ---------- Cadangan data ----------
    private fun runBackup(uri: Uri) {
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = BackupManager.backup(appCtx, uri)
            if (!isAdded) return@launch
            if (ok) {
                Popup.info(
                    requireContext(), "Cadangan tersimpan",
                    "Simpan file ini di tempat aman, misalnya Google Drive atau kirim ke diri sendiri."
                )
            } else {
                Popup.info(requireContext(), "Cadangan gagal", "File cadangan tidak bisa dibuat. Coba lagi.")
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        Popup.confirm(
            requireContext(), "Pulihkan data?",
            "Semua data saat ini akan DIGANTI dengan isi file cadangan, lalu aplikasi dimulai ulang. " +
                "Cadangkan data saat ini dulu kalau masih dibutuhkan.",
            positive = "Pulihkan"
        ) {
            val appCtx = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = BackupManager.stageRestore(appCtx, uri)
                if (ok) {
                    restartApp(appCtx)
                } else if (isAdded) {
                    Popup.info(
                        requireContext(), "Pemulihan gagal",
                        "File ini bukan cadangan KasBareng yang valid. Data Anda tidak diubah."
                    )
                }
            }
        }
    }

    private fun restartApp(appCtx: android.content.Context) {
        val intent = appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) appCtx.startActivity(intent)
        Process.killProcess(Process.myPid())
    }
}