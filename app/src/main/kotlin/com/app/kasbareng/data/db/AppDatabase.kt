package com.app.kasbareng.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.app.kasbareng.data.dao.*
import com.app.kasbareng.data.entity.*

@Database(
    entities = [
        Member::class, ScheduleVersion::class, Obligation::class,
        CashTransaction::class, PaymentAllocation::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun obligationDao(): ObligationDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "kasbareng.db"
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    BackupManager.applyPendingRestore(context.applicationContext)
                    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
                }.also { instance = it }
            }
    }
}