package com.app.kasbareng.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_versions")
data class ScheduleVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payDaysMask: Int,          // bit0=Senin ... bit6=Minggu
    val amountRupiah: Long,        // bilangan bulat, bukan string
    val timeZoneId: String,        // contoh: "Asia/Jakarta"
    val effectiveFromEpochDay: Long,
    val createdAt: Long
)