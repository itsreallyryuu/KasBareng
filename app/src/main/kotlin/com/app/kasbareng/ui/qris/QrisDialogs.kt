package com.app.kasbareng.ui.qris

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.app.kasbareng.R
import com.app.kasbareng.ui.onTextChanged
import com.app.kasbareng.util.Rupiah
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

object QrisDialogs {

    /** QR besar dengan pilihan Batal / Sudah dibayar. */
    fun show(ctx: Context, bmp: Bitmap, subtitle: String, onPaid: () -> Unit) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_qris, null)
        v.findViewById<ImageView>(R.id.qr_image).setImageBitmap(bmp)
        v.findViewById<TextView>(R.id.qr_subtitle).text = subtitle
        v.findViewById<TextView>(R.id.qr_hint).text =
            "Scan QRIS ini. Pilih Sudah dibayar setelah pembayaran berhasil, lalu isi nominalnya."
        val d = MaterialAlertDialogBuilder(ctx)
            .setTitle("Bayar via QRIS")
            .setView(v)
            .setCancelable(false)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Sudah dibayar") { _, _ -> onPaid() }
            .create()
        d.setOnShowListener { brighten(d) }
        d.show()
    }

    /** Pratinjau QRIS (dari Setelan). */
    fun preview(ctx: Context, bmp: Bitmap) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_qris, null)
        v.findViewById<ImageView>(R.id.qr_image).setImageBitmap(bmp)
        v.findViewById<View>(R.id.qr_subtitle).visibility = View.GONE
        v.findViewById<TextView>(R.id.qr_hint).text = "Inilah QRIS yang tampil saat anggota membayar."
        val d = MaterialAlertDialogBuilder(ctx)
            .setTitle("QRIS pembayaran")
            .setView(v)
            .setPositiveButton("Tutup", null)
            .create()
        d.setOnShowListener { brighten(d) }
        d.show()
    }

    /** Menanyakan nominal setelah pembayaran QRIS dikonfirmasi. */
    fun askAmount(ctx: Context, hint: String, onAmount: (Long) -> Unit) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_amount, null)
        val et = v.findViewById<TextInputEditText>(R.id.et_amount)
        val tvPreview = v.findViewById<TextView>(R.id.tv_preview)
        val tvError = v.findViewById<TextView>(R.id.tv_error)
        v.findViewById<TextView>(R.id.tv_hint).text = hint

        fun amount(): Long = et.text?.toString()?.toLongOrNull() ?: 0L
        et.onTextChanged {
            val a = amount()
            tvPreview.text = if (a > 0) Rupiah.format(a) else ""
        }

        val d = MaterialAlertDialogBuilder(ctx)
            .setTitle("Nominal yang dibayar")
            .setView(v)
            .setCancelable(false)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut", null)
            .create()
        d.setOnShowListener {
            et.requestFocus()
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = amount()
                if (a <= 0L) {
                    tvError.text = "Isi nominal yang dibayar anggota."
                    tvError.visibility = View.VISIBLE
                } else {
                    d.dismiss()
                    onAmount(a)
                }
            }
        }
        d.show()
    }

    private fun brighten(d: AlertDialog) {
        val w = d.window ?: return
        val lp = w.attributes
        lp.screenBrightness = 1f
        w.attributes = lp
    }
}