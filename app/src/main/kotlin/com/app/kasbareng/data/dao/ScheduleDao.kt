package com.app.kasbareng.data.dao

import androidx.room.*
import com.app.kasbareng.data.entity.ScheduleVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Insert suspend fun insert(version: ScheduleVersion): Long

    @Query("SELECT * FROM schedule_versions ORDER BY effectiveFromEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<ScheduleVersion>>

    @Query("SELECT * FROM schedule_versions ORDER BY effectiveFromEpochDay ASC, id ASC")
    suspend fun getAll(): List<ScheduleVersion>

    @Query(
        "SELECT * FROM schedule_versions WHERE effectiveFromEpochDay <= :day " +
            "ORDER BY effectiveFromEpochDay DESC, id DESC LIMIT 1"
    )
    suspend fun getEffectiveOn(day: Long): ScheduleVersion?

    @Query(
        "DELETE FROM schedule_versions WHERE effectiveFromEpochDay > :today " +
            "AND id NOT IN (SELECT scheduleVersionId FROM obligations)"
    )
    suspend fun deleteFutureUnused(today: Long): Int
}