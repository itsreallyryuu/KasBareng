package com.app.kasbareng.data.repository

import androidx.room.withTransaction
import com.app.kasbareng.data.db.AppDatabase
import com.app.kasbareng.data.entity.PaymentAllocation

/** Memakai saldo di muka (kelebihan bayar) untuk menutup kewajiban terbuka, dari yang terlama. */
class CreditApplier(private val db: AppDatabase) {

    suspend fun applyForMember(memberId: Long) {
        db.withTransaction {
            val rows = db.transactionDao().creditRows(memberId)
                .filter { it.amountRupiah - it.allocated > 0 }
            if (rows.isEmpty()) return@withTransaction
            val open = db.obligationDao().getWithPaidForMember(memberId).filter { it.remaining() > 0 }
            if (open.isEmpty()) return@withTransaction

            val allocs = ArrayList<PaymentAllocation>()
            var ti = 0
            var left = rows[0].amountRupiah - rows[0].allocated
            for (o in open) {
                var need = o.remaining()
                while (need > 0 && ti < rows.size) {
                    val take = minOf(need, left)
                    allocs.add(PaymentAllocation(transactionId = rows[ti].id, obligationId = o.id, amountRupiah = take))
                    need -= take
                    left -= take
                    if (left <= 0L) {
                        ti++
                        if (ti < rows.size) left = rows[ti].amountRupiah - rows[ti].allocated
                    }
                }
                if (ti >= rows.size) break
            }
            if (allocs.isNotEmpty()) db.transactionDao().insertAllocations(allocs)
        }
    }

    suspend fun applyAll() {
        for (m in db.memberDao().getActive()) applyForMember(m.id)
    }
}