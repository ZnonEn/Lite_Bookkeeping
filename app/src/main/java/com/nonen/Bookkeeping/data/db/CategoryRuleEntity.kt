package com.nonen.Bookkeeping.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["keyword"], unique = true)],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val keyword: String,
    val category: String,
    /** false = 内置规则，true = 用户手动添加或编辑交易时自动学习 */
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
