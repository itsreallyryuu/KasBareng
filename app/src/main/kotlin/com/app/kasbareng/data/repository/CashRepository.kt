package com.app.kasbareng.data.repository

import androidx.room.withTransaction
import com.app.kasbareng.data.db.AppDatabase
import com.app.kasbareng.data.entity.*
import com.app.kasbareng.domain.ObligationGenerator
import com.app.kasbareng.domain.ScheduleValidator
import com.app.kasbareng.util.Rupiah
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

sealed class Outcome {
    data class Ok(val message: String, val transactionId: Long = 0) : Outcome()
    data class Fail(val message: String, val excessRupiah: Long = 0) : Outcome()
}

class CashRepository(private val db: AppDatabase) {
    private val generator = ObligationGenerator(db)

    val members = db.memberDao().observeAll()
    val schedules = db.scheduleDao().observeAll()
    val transactions = db.transactionDao().observeAll()
    val obligations = db.obligationDao().observeAllWithPaid()
    val incomeTotal = db.transactionDao().observeIncomeTotal()
    val expenseTotal = db.transactionDao().observeExpenseTotal()

    suspend fun zone(): ZoneId {
        val v = db.scheduleDao().getEffectiveOn(LocalDate.now().toEpochDay())
            ?: db.scheduleDao().getAll().firstOrNull()
        return v?.let { ZoneId.of(it.timeZoneId) } ?: ZoneId.systemDefault()
    }

    suspend fun refreshObligations() {
        try { generator.generate() } catch (_: Exception) { }
    }

    // ---------- Jadwal ----------
    suspend fun saveSchedule(payDaysMask: Int, amount: Long, zoneId: String): Outcome {
        val existing = db.scheduleDao().getAll().isNotEmpty()
        val today = LocalDate.now()
        val effective = if (existing) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
        ScheduleValidator.validate(payDaysMask, amount, zoneId, effective, today, existing)
            ?.let { return Outcome.Fail(it) }
        return try {
            db.scheduleDao().insert(
                ScheduleVersion(
                    payDaysMask = payDaysMask,
                    amountRupiah = amount,
                    timeZoneId = zoneId,
                    effectiveFromEpochDay = effective.toEpochDay(),
                    createdAt = System.currentTimeMillis()
                )
            )
            generator.generate()
            Outcome.Ok(if (existing) "Jadwal baru berlaku mulai Senin depan." else "Jadwal kas tersimpan.")
        } catch (e: Exception) {
            Outcome.Fail("Jadwal gagal disimpan. Coba lagi.")
        }
    }

    // ---------- Anggota ----------
    private fun cleanName(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

    suspend fun addMember(name: String, note: String?): Outcome {
        val clean = cleanName(name)
        if (clean.isEmpty()) return Outcome.Fail("Nama anggota tidak boleh kosong.")
        if (clean.length > 50) return Outcome.Fail("Nama maksimal 50 karakter.")
        return try {
            if (db.memberDao().getActive().any { it.name.equals(clean, ignoreCase = true) }) {
                return Outcome.Fail("Nama sudah dipakai anggota aktif.")
            }
            db.memberDao().insert(
                Member(
                    name = clean,
                    note = note?.trim()?.ifEmpty { null },
                    addedAt = System.currentTimeMillis()
                )
            )
            generator.generate()
            Outcome.Ok("Anggota ditambahkan.")
        } catch (e: Exception) {
            Outcome.Fail("Anggota gagal disimpan. Coba lagi.")
        }
    }

    suspend fun updateMember(id: Long, name: String, note: String?): Outcome {
        val clean = cleanName(name)
        if (clean.isEmpty()) return Outcome.Fail("Nama anggota tidak boleh kosong.")
        if (clean.length > 50) return Outcome.Fail("Nama maksimal 50 karakter.")
        return try {
            val m = db.memberDao().getById(id) ?: return Outcome.Fail("Anggota tidak ditemukan.")
            if (db.memberDao().getActive().any { it.id != id && it.name.equals(clean, ignoreCase = true) }) {
                return Outcome.Fail("Nama sudah dipakai anggota aktif.")
            }
            db.memberDao().update(m.copy(name = clean, note = note?.trim()?.ifEmpty { null }))
            Outcome.Ok("Perubahan disimpan.")
        } catch (e: Exception) {
            Outcome.Fail("Perubahan gagal disimpan. Coba lagi.")
        }
    }

    suspend fun deactivateMember(id: Long): Outcome {
        val z = zone()
        return try {
            val m = db.memberDao().getById(id) ?: return Outcome.Fail("Anggota tidak ditemukan.")
            if (!m.isActive) return Outcome.Fail("Anggota sudah nonaktif.")
            db.withTransaction {
                db.memberDao().update(m.copy(isActive = false, deactivatedAt = System.currentTimeMillis()))
                db.obligationDao().deleteFutureUnallocated(id, LocalDate.now(z).toEpochDay())
            }
            Outcome.Ok("Anggota dinonaktifkan. Riwayatnya tetap tersimpan.")
        } catch (e: Exception) {
            Outcome.Fail("Gagal menonaktifkan anggota. Coba lagi.")
        }
    }

    // ---------- Pemasukan, pengeluaran, saldo awal ----------
    suspend fun recordOther(
        type: String, amount: Long, description: String, uuid: String, occurredAt: Long
    ): Outcome {
        if (type !in setOf("INCOME", "EXPENSE", "OPENING_BALANCE")) return Outcome.Fail("Jenis transaksi tidak dikenal.")
        if (amount <= 0L) return Outcome.Fail("Nominal harus lebih besar dari Rp0.")
        if (occurredAt > System.currentTimeMillis() + 60_000L) {
            return Outcome.Fail("Tanggal tidak boleh di masa depan.")
        }
        val desc = description.trim()
        if (desc.isEmpty()) return Outcome.Fail("Keterangan tidak boleh kosong.")
        return try {
            db.withTransaction<Outcome> {
                val t = db.transactionDao()
                if (type == "OPENING_BALANCE" && t.openingBalanceCount() > 0) {
                    return@withTransaction Outcome.Fail("Saldo awal sudah pernah dicatat. Batalkan yang lama jika salah.")
                }
                if (type == "EXPENSE") {
                    val balance = t.incomeTotal() - t.expenseTotal()
                    if (amount > balance) {
                        return@withTransaction Outcome.Fail(
                            "Saldo tidak cukup. Saldo kas saat ini ${Rupiah.format(balance)}."
                        )
                    }
                }
                val id = t.insert(
                    CashTransaction(
                        uuid = uuid, type = type, amountRupiah = amount,
                        occurredAt = occurredAt, createdAt = System.currentTimeMillis(),
                        description = desc
                    )
                )
                if (id == -1L) Outcome.Fail("Transaksi ini sudah tersimpan.")
                else Outcome.Ok("Transaksi tersimpan.", id)
            }
        } catch (e: Exception) {
            Outcome.Fail("Transaksi gagal disimpan. Coba lagi.")
        }
    }

    // ---------- Pembatalan ----------
    suspend fun voidTransaction(id: Long): Outcome = try {
        db.withTransaction<Outcome> {
            val t = db.transactionDao()
            val tx = t.getById(id) ?: return@withTransaction Outcome.Fail("Transaksi tidak ditemukan.")
            if (tx.status != "VALID") return@withTransaction Outcome.Fail("Transaksi ini sudah dibatalkan.")
            if (tx.type != "EXPENSE") {
                val after = t.incomeTotal() - t.expenseTotal() - tx.amountRupiah
                if (after < 0) return@withTransaction Outcome.Fail("Pembatalan ini membuat saldo kas negatif.")
            }
            if (t.markVoided(id) == 0) Outcome.Fail("Transaksi gagal dibatalkan.")
            else Outcome.Ok("Transaksi dibatalkan.")
        }
    } catch (e: Exception) {
        Outcome.Fail("Transaksi gagal dibatalkan. Coba lagi.")
    }
}