package com.app.kasbareng.data.repository

import androidx.room.withTransaction
import com.app.kasbareng.data.db.AppDatabase
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.data.entity.PaymentAllocation
import com.app.kasbareng.domain.DueRules
import com.app.kasbareng.domain.ObligationGenerator
import com.app.kasbareng.util.Rupiah
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PaymentRepository(private val db: AppDatabase) {
    private val generator = ObligationGenerator(db)
    private val credit = CreditApplier(db)

    private suspend fun zone(): ZoneId {
        val v = db.scheduleDao().getEffectiveOn(LocalDate.now().toEpochDay())
            ?: db.scheduleDao().getAll().firstOrNull()
        return v?.let { ZoneId.of(it.timeZoneId) } ?: ZoneId.systemDefault()
    }

    /**
     * Bayar kas (arrears = false) atau pelunasan tunggakan (arrears = true).
     * Kelebihan bayar disimpan sebagai saldo di muka dan otomatis menutup kewajiban berikutnya.
     */
    suspend fun recordDuesPayment(
        memberId: Long, amount: Long, uuid: String, occurredAt: Long,
        method: String?, note: String?, acceptExcess: Boolean, arrears: Boolean
    ): Outcome {
        if (amount <= 0L) return Outcome.Fail("Nominal harus lebih besar dari Rp0.")
        if (occurredAt > System.currentTimeMillis() + 60_000L) {
            return Outcome.Fail("Tanggal tidak boleh di masa depan.")
        }
        val member = db.memberDao().getById(memberId) ?: return Outcome.Fail("Anggota tidak ditemukan.")
        if (!member.isActive) return Outcome.Fail("Anggota ini sudah nonaktif.")
        return try {
            generator.generate()
            val z = zone()
            val now = Instant.now()
            val result = db.withTransaction<Outcome> {
                val t = db.transactionDao()
                val open = db.obligationDao().getWithPaidForMember(memberId).filter { it.remaining() > 0 }
                val overdue = open.filter { DueRules.isOverdue(it.dueEpochDay, now, z) }
                val upcoming = open.filter { !DueRules.isOverdue(it.dueEpochDay, now, z) }
                val overdueTotal = overdue.sumOf { it.remaining() }
                val pool = if (arrears) overdue else upcoming
                val poolTotal = pool.sumOf { it.remaining() }

                if (arrears && poolTotal == 0L) {
                    return@withTransaction Outcome.Fail("Anggota ini tidak punya tunggakan.")
                }
                val allocatable = minOf(amount, poolTotal)
                val excess = amount - allocatable
                if (excess > 0) {
                    if (!arrears && overdueTotal > 0) {
                        return@withTransaction Outcome.Fail(
                            "Anggota ini masih punya tunggakan ${Rupiah.format(overdueTotal)}. " +
                                "Bayar tunggakan lewat Pelunasan tunggakan."
                        )
                    }
                    if (!acceptExcess) {
                        return@withTransaction Outcome.Fail(
                            "Pembayaran melebihi kewajiban sebesar ${Rupiah.format(excess)}.", excess
                        )
                    }
                }

                val label = if (arrears) "Pelunasan tunggakan" else "Bayar kas"
                val extra = note?.trim()?.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
                val id = t.insert(
                    CashTransaction(
                        uuid = uuid, type = "DUES_PAYMENT", amountRupiah = amount,
                        memberId = memberId, occurredAt = occurredAt,
                        createdAt = System.currentTimeMillis(),
                        description = "$label ${member.name}$extra", method = method
                    )
                )
                if (id == -1L) return@withTransaction Outcome.Fail("Pembayaran ini sudah tersimpan.")

                var left = allocatable
                val allocs = ArrayList<PaymentAllocation>()
                for (o in pool) {
                    if (left <= 0L) break
                    val a = minOf(left, o.remaining())
                    allocs.add(PaymentAllocation(transactionId = id, obligationId = o.id, amountRupiah = a))
                    left -= a
                }
                if (allocs.isNotEmpty()) t.insertAllocations(allocs)

                Outcome.Ok(
                    if (excess > 0) "Pembayaran tersimpan. Kelebihan ${Rupiah.format(excess)} disimpan sebagai saldo di muka."
                    else "Pembayaran tersimpan.",
                    id
                )
            }
            if (result is Outcome.Ok) credit.applyForMember(memberId)
            result
        } catch (e: Exception) {
            Outcome.Fail("Pembayaran gagal disimpan. Coba lagi.")
        }
    }
}