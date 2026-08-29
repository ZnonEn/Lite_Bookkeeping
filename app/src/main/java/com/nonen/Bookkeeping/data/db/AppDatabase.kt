package com.nonen.Bookkeeping.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, CategoryRuleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
}
