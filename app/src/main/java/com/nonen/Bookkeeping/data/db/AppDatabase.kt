package com.nonen.Bookkeeping.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, CategoryRuleEntity::class, MerchantCategoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao

    companion object {

        /**
         * v1 → v2：
         * 1. 新建 merchant_categories（商户记忆）；
         * 2. category_rules 的唯一约束由 keyword 放宽到 (keyword, category)——
         *    原先同一关键词在收/支两个方向各有一条内置规则，唯一索引只留下先插入的那条，
         *    收入侧的「红包/压岁/利息/赔付」等被静默丢弃。改为整表重建（SQLite 不支持改索引）。
         * 只新增表与重建索引，不改动 transactions，用户流水零风险。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `merchantKey` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `isIncome` INTEGER NOT NULL,
                        `hits` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_categories_merchantKey_isIncome` " +
                        "ON `merchant_categories` (`merchantKey`, `isIncome`)",
                )

                // category_rules：搬数据前先按 (keyword, category) 去重，
                // 避免重建唯一索引时因历史重复行失败
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `category_rules_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `category_rules_new` (`keyword`, `category`, `isCustom`, `createdAt`)
                    SELECT `keyword`, `category`, `isCustom`, MIN(`createdAt`)
                    FROM `category_rules`
                    GROUP BY `keyword`, `category`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `category_rules`")
                db.execSQL("ALTER TABLE `category_rules_new` RENAME TO `category_rules`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_category_rules_keyword_category` " +
                        "ON `category_rules` (`keyword`, `category`)",
                )
            }
        }
    }
}
