package com.app.kasbareng.ui.donate

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import com.app.kasbareng.R
import com.app.kasbareng.util.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DonateDialog {
    const val URL = "https://saweria.co/itsmeryuu"
    private const val SNOOZE_MS = 24L * 60L * 60L * 1000L

    fun shouldShow(ctx: Context): Boolean =
        System.currentTimeMillis() >= Prefs.donateHideUntil(ctx)

    fun openLink(ctx: Context) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL)))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Tautan tidak bisa dibuka. Salin: $URL", Toast.LENGTH_LONG).show()
        }
    }

    fun show(ctx: Context, onClosed: () -> Unit = {}) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_donate, null)
        val image = v.findViewById<ImageView>(R.id.donate_image)
        val badge = v.findViewById<View>(R.id.donate_badge)
        val check = v.findViewById<CheckBox>(R.id.cb_snooze)

        // Gambar opsional: taruh donate_header.png di res/drawable-nodpi/
        val resId = ctx.resources.getIdentifier("donate_header", "drawable", ctx.packageName)
        if (resId != 0) {
            image.setImageResource(resId)
            image.visibility = View.VISIBLE
            badge.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(ctx).setView(v).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            badge,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        v.findViewById<View>(R.id.btn_support).setOnClickListener {
            openLink(ctx)
            dialog.dismiss()
        }
        v.findViewById<View>(R.id.btn_later).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            pulse.cancel()
            if (check.isChecked) {
                Prefs.setDonateHideUntil(ctx, System.currentTimeMillis() + SNOOZE_MS)
            }
            onClosed()
        }

        dialog.show()
        dialog.window?.setLayout(
            (ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        pulse.start()
    }
}