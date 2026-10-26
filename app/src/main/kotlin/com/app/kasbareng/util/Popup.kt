package com.app.kasbareng.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Popup {
    fun confirm(
        ctx: Context, title: String, message: String,
        positive: String = "Simpan", onOk: () -> Unit
    ) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Periksa lagi", null)
            .setPositiveButton(positive) { _, _ -> onOk() }
            .show()
    }

    fun info(ctx: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Mengerti", null)
            .show()
    }
}