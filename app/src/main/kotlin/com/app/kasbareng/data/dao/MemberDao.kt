package com.app.kasbareng.data.dao

import androidx.room.*
import com.app.kasbareng.data.entity.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Insert suspend fun insert(member: Member): Long
    @Update suspend fun update(member: Member)
    @Delete suspend fun delete(member: Member)

    @Query("SELECT * FROM members ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE isActive = 1")
    suspend fun getActive(): List<Member>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: Long): Member?
}