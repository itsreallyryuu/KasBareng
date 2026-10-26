package com.app.kasbareng.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

object QrisStore {
    private const val FILE = "qris.png"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun delete(ctx: Context) {
        file(ctx).delete()
    }

    fun load(ctx: Context, maxDim: Int = 1200): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Menyalin gambar pilihan pengguna ke penyimpanan aplikasi (maks 2000 px, latar putih). */
    suspend fun import(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
            val src = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false

            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(src, 0f, 0f, null)
            src.recycle()

            val tmp = File(ctx.filesDir, "qris.tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out.recycle()
            val target = file(ctx)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }
}