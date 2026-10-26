package com.app.kasbareng.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = CashTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Obligation::class,
            parentColumns = ["id"],
            childColumns = ["obligationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("transactionId"), Index("obligationId")]
)
data class PaymentAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val obligationId: Long,
    val amountRupiah: Long
)