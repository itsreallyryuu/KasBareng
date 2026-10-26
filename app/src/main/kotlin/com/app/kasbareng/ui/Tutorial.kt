package com.app.kasbareng.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Tutorial {
    private val steps = listOf(
        "Selamat datang" to
            "KasBareng mencatat kas kelas: siapa yang sudah bayar, siapa yang menunggak, dan berapa saldo kas. " +
            "Tutorial ini bisa dibuka lagi kapan saja di tab Setelan.",
        "1. Atur jadwal kas" to
            "Buka tab Setelan, lalu atur Jadwal kas: centang hari bayar, isi nominal, dan pilih zona waktu. " +
            "Perubahan jadwal berlaku mulai Senin berikutnya. Kewajiban yang sudah ada tidak berubah.",
        "2. Tambah anggota" to
            "Tambahkan anggota di tab Anggota. Kewajiban bayar terbentuk otomatis pada setiap hari bayar, " +
            "mulai dari tanggal anggota ditambahkan.",
        "3. Tiga jenis pencatatan" to
            "Bayar kas: iuran rutin yang masih berjalan.\n\n" +
            "Pelunasan tunggakan: membayar hari-hari yang sudah terlewat.\n\n" +
            "Pengeluaran: uang kas yang dipakai, tidak boleh melebihi saldo.\n\n" +
            "Pemasukan lain dan saldo awal ada di tab Transaksi, tombol Catat.",
        "4. Arti status" to
            "LUNAS: semua kewajiban sudah dibayar.\n" +
            "NUNGGAK: ada hari bayar yang terlewat. Dihitung mulai pukul 00.00 hari berikutnya.\n" +
            "CICILAN: sudah membayar sebagian.\n" +
            "BELUM JATUH TEMPO: tagihan masih berjalan.",
        "5. Salah catat?" to
            "Buka tab Transaksi, ketuk transaksinya, lalu pilih Batalkan transaksi. Transaksi tidak dihapus, " +
            "hanya ditandai dibatalkan, jadi riwayat tetap jujur. Laporan bisa dibagikan dari tab Laporan.",
        "6. Cadangkan data" to
            "Data hanya tersimpan di HP ini. Buka Setelan lalu Cadangkan data secara rutin, " +
            "dan simpan filenya di tempat aman seperti Google Drive."
    )

    fun show(ctx: Context, onFinish: () -> Unit = {}) = step(ctx, 0, onFinish)

    private fun step(ctx: Context, i: Int, onFinish: () -> Unit) {
        val (title, body) = steps[i]
        val last = i == steps.lastIndex
        val b = MaterialAlertDialogBuilder(ctx)
            .setTitle("${i + 1}/${steps.size}  $title")
            .setMessage(body)
            .setCancelable(false)
            .setPositiveButton(if (last) "Mengerti" else "Lanjut") { _, _ ->
                if (last) onFinish() else step(ctx, i + 1, onFinish)
            }
        if (i == 0) b.setNegativeButton("Lewati") { _, _ -> onFinish() }
        else b.setNegativeButton("Kembali") { _, _ -> step(ctx, i - 1, onFinish) }
        b.show()
    }
}