package com.app.kasbareng.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String? = null,
    val addedAt: Long,
    val isActive: Boolean = true,
    val deactivatedAt: Long? = null
)