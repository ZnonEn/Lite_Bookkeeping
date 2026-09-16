package com.nonen.Bookkeeping.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 关键词规则：包含 [keyword] 的交易归入 [category]。
 *
 * 唯一约束是 (keyword, category) 而非 keyword：同一个词在收/支两个方向
 * 可以指向不同分类（「红包」支出→人情、收入→红包），若只约束 keyword，
 * 后播种的那条会被 IGNORE 静默丢弃。同方向内不重复由单测守卫。
 */
@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["keyword", "category"], unique = true)],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val keyword: String,
    val category: String,
    /** false = 内置规则，true = 用户手动添加或编辑交易时自动学习 */
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
