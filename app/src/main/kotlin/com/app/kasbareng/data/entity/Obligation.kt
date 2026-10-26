package com.app.kasbareng.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "obligations",
    foreignKeys = [
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ScheduleVersion::class,
            parentColumns = ["id"],
            childColumns = ["scheduleVersionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["memberId", "dueEpochDay"], unique = true),
        Index(value = ["scheduleVersionId"])
    ]
)
data class Obligation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val scheduleVersionId: Long,
    val dueEpochDay: Long,         // hari bayar; menunggak mulai 00.00 hari berikutnya
    val amountRupiah: Long         // nominal dibekukan saat dibuat
)