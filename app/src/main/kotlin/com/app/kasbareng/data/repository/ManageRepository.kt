package com.app.kasbareng.data.repository

import androidx.room.withTransaction
import com.app.kasbareng.data.dao.AllocationDetail
import com.app.kasbareng.data.db.AppDatabase
import com.app.kasbareng.data.entity.Obligation
import com.app.kasbareng.domain.ObligationGenerator
import com.app.kasbareng.domain.Opening
import com.app.kasbareng.util.Rupiah
import java.time.LocalDate

class ManageRepository(private val db: AppDatabase) {
    private val generator = ObligationGenerator(db)

    suspend fun allocationDetails(txId: Long): List<AllocationDetail> =
        db.transactionDao().getAllocationDetails(txId)

    // ---------- Anggota ----------
    suspend fun reactivateMember(id: Long): Outcome = try {
        val m = db.memberDao().getById(id)
        when {
            m == null -> Outcome.Fail("Anggota tidak ditemukan.")
            m.isActive -> Outcome.Fail("Anggota sudah aktif.")
            db.memberDao().getActive().any { it.name.equals(m.name, ignoreCase = true) } ->
                Outcome.Fail("Sudah ada anggota aktif dengan nama yang sama. Ubah nama salah satunya dulu.")
            else -> {
                // addedAt diset ulang agar masa nonaktif tidak jadi tunggakan baru
                db.memberDao().update(
                    m.copy(isActive = true, deactivatedAt = null, addedAt = System.currentTimeMillis())
                )
                generator.generate()
                Outcome.Ok("Anggota diaktifkan kembali. Kewajiban baru dihitung mulai hari ini.")
            }
        }
    } catch (e: Exception) {
        Outcome.Fail("Gagal mengaktifkan anggota. Coba lagi.")
    }

    suspend fun deleteMember(id: Long): Outcome = try {
        db.withTransaction<Outcome> {
            val m = db.memberDao().getById(id)
                ?: return@withTransaction Outcome.Fail("Anggota tidak ditemukan.")
            if (db.transactionDao().countForMember(id) > 0) {
                return@withTransaction Outcome.Fail(
                    "Anggota ini punya riwayat pembayaran, jadi tidak bisa dihapus. " +
                        "Nonaktifkan saja agar riwayat tetap utuh."
                )
            }
            db.obligationDao().deleteAllForMember(id)
            db.memberDao().delete(m)
            Outcome.Ok("Anggota dihapus.")
        }
    } catch (e: Exception) {
        Outcome.Fail("Anggota gagal dihapus. Coba lagi.")
    }

    /** Mengatur tunggakan awal dari buku untuk satu anggota (mengganti nilai lama). */
    suspend fun setOpeningArrears(memberId: Long, amount: Long): Outcome {
        if (amount < 0L || amount > 999_999_999L) return Outcome.Fail("Nominal tidak valid.")
        return try {
            db.withTransaction<Outcome> {
                val m = db.memberDao().getById(memberId)
                    ?: return@withTransaction Outcome.Fail("Anggota tidak ditemukan.")
                if (!m.isActive) return@withTransaction Outcome.Fail("Anggota ini sudah nonaktif.")
                val version = db.scheduleDao().getAll().firstOrNull()
                    ?: return@withTransaction Outcome.Fail(
                        "Atur jadwal kas dulu di Setelan sebelum mencatat tunggakan awal."
                    )
                val dao = db.obligationDao()
                val existing = dao.find(memberId, Opening.DUE_DAY)
                if (existing == null) {
                    if (amount == 0L) return@withTransaction Outcome.Ok("Tidak ada perubahan.")
                    dao.insertAll(
                        listOf(
                            Obligation(
                                memberId = memberId,
                                scheduleVersionId = version.id,
                                dueEpochDay = Opening.DUE_DAY,
                                amountRupiah = amount
                            )
                        )
                    )
                    Outcome.Ok("Tunggakan awal tersimpan.")
                } else {
                    val paid = dao.getWithPaidForMember(memberId)
                        .firstOrNull { it.id == existing.id }?.paidRupiah ?: 0L
                    when {
                        amount < paid -> Outcome.Fail(
                            "Sudah dibayar ${Rupiah.format(paid)}, jadi nominal tidak boleh lebih kecil dari itu."
                        )
                        amount == 0L ->
                            if (dao.deleteUnallocated(existing.id) > 0) Outcome.Ok("Tunggakan awal dihapus.")
                            else Outcome.Fail("Tunggakan awal ini sudah punya riwayat pembayaran, jadi tidak bisa dihapus.")
                        else -> {
                            dao.update(existing.copy(amountRupiah = amount))
                            Outcome.Ok("Tunggakan awal diperbarui.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Outcome.Fail("Tunggakan awal gagal disimpan. Coba lagi.")
        }
    }

    // ---------- Transaksi ----------
    /** description/amount null = tidak diubah. Pembayaran iuran hanya boleh ubah tanggal dan metode. */
    suspend fun updateTransaction(
        id: Long, occurredAt: Long, description: String?, method: String?, amount: Long?
    ): Outcome {
        if (occurredAt > System.currentTimeMillis() + 60_000L) {
            return Outcome.Fail("Tanggal tidak boleh di masa depan.")
        }
        return try {
            db.withTransaction<Outcome> {
                val dao = db.transactionDao()
                val t = dao.getById(id) ?: return@withTransaction Outcome.Fail("Transaksi tidak ditemukan.")
                if (t.status != "VALID") {
                    return@withTransaction Outcome.Fail("Transaksi yang sudah dibatalkan tidak bisa diubah.")
                }
                var updated = t.copy(occurredAt = occurredAt)
                if (t.type == "DUES_PAYMENT") {
                    updated = updated.copy(method = method ?: t.method)
                } else {
                    val desc = description?.trim().orEmpty()
                    if (desc.isEmpty()) return@withTransaction Outcome.Fail("Keterangan tidak boleh kosong.")
                    val newAmount = amount ?: t.amountRupiah
                    if (newAmount <= 0L) return@withTransaction Outcome.Fail("Nominal harus lebih besar dari Rp0.")
                    val balance = dao.incomeTotal() - dao.expenseTotal()
                    val delta = newAmount - t.amountRupiah
                    val after = if (t.type == "EXPENSE") balance - delta else balance + delta
                    if (after < 0) {
                        return@withTransaction Outcome.Fail(
                            "Saldo tidak cukup untuk perubahan ini. Saldo kas saat ini ${Rupiah.format(balance)}."
                        )
                    }
                    updated = updated.copy(description = desc, amountRupiah = newAmount)
                }
                dao.update(updated)
                Outcome.Ok("Perubahan disimpan.")
            }
        } catch (e: Exception) {
            Outcome.Fail("Perubahan gagal disimpan. Coba lagi.")
        }
    }

    suspend fun deleteVoidedTransaction(id: Long): Outcome = try {
        db.withTransaction<Outcome> {
            val dao = db.transactionDao()
            val t = dao.getById(id) ?: return@withTransaction Outcome.Fail("Transaksi tidak ditemukan.")
            if (t.status == "VALID") {
                return@withTransaction Outcome.Fail("Batalkan transaksi dulu sebelum menghapus permanen.")
            }
            dao.deleteAllocations(id)
            dao.deleteById(id)
            Outcome.Ok("Transaksi dihapus permanen.")
        }
    } catch (e: Exception) {
        Outcome.Fail("Transaksi gagal dihapus. Coba lagi.")
    }

    // ---------- Jadwal ----------
    suspend fun cancelPendingSchedule(): Outcome = try {
        val n = db.scheduleDao().deleteFutureUnused(LocalDate.now().toEpochDay())
        if (n > 0) Outcome.Ok("Jadwal baru dibatalkan. Jadwal sebelumnya tetap berlaku.")
        else Outcome.Fail("Tidak ada jadwal baru yang bisa dibatalkan.")
    } catch (e: Exception) {
        Outcome.Fail("Jadwal gagal dibatalkan. Coba lagi.")
    }
}