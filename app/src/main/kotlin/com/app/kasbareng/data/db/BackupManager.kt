package com.app.kasbareng.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackupManager {
    private const val PENDING = "pending_restore.db"

    suspend fun backup(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.get(ctx)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val file = ctx.getDatabasePath(AppDatabase.NAME)
            val out = ctx.contentResolver.openOutputStream(uri) ?: return@withContext false
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Menyimpan file cadangan ke tempat sementara. Diterapkan saat aplikasi dibuka berikutnya. */
    suspend fun stageRestore(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(ctx.filesDir, PENDING)
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext false
            input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            val ok = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { sdb ->
                sdb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' " +
                        "AND name IN ('members','transactions','obligations')", null
                ).use { it.count == 3 }
            }
            File(tmp.path + "-wal").delete()
            File(tmp.path + "-shm").delete()
            if (!ok) tmp.delete()
            ok
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    /** Dipanggil sebelum database dibuka. */
    fun applyPendingRestore(ctx: Context) {
        val pending = File(ctx.filesDir, PENDING)
        if (!pending.exists()) return
        try {
            val dbFile = ctx.getDatabasePath(AppDatabase.NAME)
            dbFile.parentFile?.mkdirs()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            pending.copyTo(dbFile, overwrite = true)
        } catch (_: Exception) {
        } finally {
            pending.delete()
        }
    }
}