package com.app.kasbareng.data.dao

import androidx.room.*
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.data.entity.PaymentAllocation
import kotlinx.coroutines.flow.Flow

data class AllocationDetail(val dueEpochDay: Long, val amountRupiah: Long)

data class TxCredit(val id: Long, val amountRupiah: Long, val allocated: Long)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: CashTransaction): Long   // -1 jika uuid sudah ada

    @Insert
    suspend fun insertAllocations(items: List<PaymentAllocation>)

    @Update
    suspend fun update(t: CashTransaction)

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<CashTransaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): CashTransaction?

    @Query("UPDATE transactions SET status = 'VOIDED' WHERE id = :id AND status = 'VALID'")
    suspend fun markVoided(id: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'OPENING_BALANCE' AND status = 'VALID'")
    suspend fun openingBalanceCount(): Int

    @Query("SELECT COALESCE(SUM(amountRupiah), 0) FROM transactions WHERE status = 'VALID' AND type IN ('DUES_PAYMENT','INCOME','OPENING_BALANCE')")
    suspend fun incomeTotal(): Long

    @Query("SELECT COALESCE(SUM(amountRupiah), 0) FROM transactions WHERE status = 'VALID' AND type = 'EXPENSE'")
    suspend fun expenseTotal(): Long

    @Query("SELECT COALESCE(SUM(amountRupiah), 0) FROM transactions WHERE status = 'VALID' AND type IN ('DUES_PAYMENT','INCOME','OPENING_BALANCE')")
    fun observeIncomeTotal(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountRupiah), 0) FROM transactions WHERE status = 'VALID' AND type = 'EXPENSE'")
    fun observeExpenseTotal(): Flow<Long>

    @Query("DELETE FROM payment_allocations WHERE transactionId = :id")
    suspend fun deleteAllocations(id: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE memberId = :id")
    suspend fun countForMember(id: Long): Int

    @Query(
        "SELECT o.dueEpochDay AS dueEpochDay, a.amountRupiah AS amountRupiah " +
            "FROM payment_allocations a JOIN obligations o ON o.id = a.obligationId " +
            "WHERE a.transactionId = :id ORDER BY o.dueEpochDay"
    )
    suspend fun getAllocationDetails(id: Long): List<AllocationDetail>

    @Query(
        "SELECT t.id AS id, t.amountRupiah AS amountRupiah, " +
            "COALESCE((SELECT SUM(a.amountRupiah) FROM payment_allocations a WHERE a.transactionId = t.id), 0) AS allocated " +
            "FROM transactions t WHERE t.memberId = :memberId AND t.type = 'DUES_PAYMENT' AND t.status = 'VALID' " +
            "ORDER BY t.occurredAt ASC, t.id ASC"
    )
    suspend fun creditRows(memberId: Long): List<TxCredit>
}