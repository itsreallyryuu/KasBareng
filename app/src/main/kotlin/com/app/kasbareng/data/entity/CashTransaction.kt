package com.app.kasbareng.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class CashTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,               // ID transaksi unik
    val type: String,               // DUES_PAYMENT, INCOME, EXPENSE, OPENING_BALANCE
    val amountRupiah: Long,         // selalu positif
    val memberId: Long? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val description: String,
    val method: String? = null,
    val status: String = "VALID",   // VALID atau VOIDED
    val correctionOfId: Long? = null
)