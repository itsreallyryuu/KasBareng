package com.app.kasbareng.data.dao

import androidx.room.*
import com.app.kasbareng.data.entity.Obligation
import kotlinx.coroutines.flow.Flow

data class ObligationWithPaid(
    val id: Long,
    val memberId: Long,
    val scheduleVersionId: Long,
    val dueEpochDay: Long,
    val amountRupiah: Long,
    val paidRupiah: Long
) {
    fun remaining(): Long = amountRupiah - paidRupiah
}

@Dao
interface ObligationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Obligation>): List<Long>

    @Update
    suspend fun update(o: Obligation)

    @Query("SELECT * FROM obligations WHERE memberId = :memberId AND dueEpochDay = :day LIMIT 1")
    suspend fun find(memberId: Long, day: Long): Obligation?

    @Query("SELECT * FROM obligations WHERE memberId = :memberId ORDER BY dueEpochDay")
    suspend fun getForMember(memberId: Long): List<Obligation>

    @Query(
        """SELECT o.id AS id, o.memberId AS memberId, o.scheduleVersionId AS scheduleVersionId,
        o.dueEpochDay AS dueEpochDay, o.amountRupiah AS amountRupiah,
        COALESCE((SELECT SUM(a.amountRupiah) FROM payment_allocations a
          JOIN transactions t ON t.id = a.transactionId
          WHERE a.obligationId = o.id AND t.status = 'VALID'), 0) AS paidRupiah
        FROM obligations o WHERE o.memberId = :memberId ORDER BY o.dueEpochDay ASC, o.id ASC"""
    )
    suspend fun getWithPaidForMember(memberId: Long): List<ObligationWithPaid>

    @Query(
        """SELECT o.id AS id, o.memberId AS memberId, o.scheduleVersionId AS scheduleVersionId,
        o.dueEpochDay AS dueEpochDay, o.amountRupiah AS amountRupiah,
        COALESCE((SELECT SUM(a.amountRupiah) FROM payment_allocations a
          JOIN transactions t ON t.id = a.transactionId
          WHERE a.obligationId = o.id AND t.status = 'VALID'), 0) AS paidRupiah
        FROM obligations o ORDER BY o.dueEpochDay ASC, o.id ASC"""
    )
    fun observeAllWithPaid(): Flow<List<ObligationWithPaid>>

    @Query(
        "DELETE FROM obligations WHERE memberId = :memberId AND dueEpochDay > :todayEpochDay " +
            "AND id NOT IN (SELECT obligationId FROM payment_allocations)"
    )
    suspend fun deleteFutureUnallocated(memberId: Long, todayEpochDay: Long): Int

    @Query("DELETE FROM obligations WHERE id = :id AND id NOT IN (SELECT obligationId FROM payment_allocations)")
    suspend fun deleteUnallocated(id: Long): Int

    @Query("DELETE FROM obligations WHERE memberId = :memberId")
    suspend fun deleteAllForMember(memberId: Long): Int
}