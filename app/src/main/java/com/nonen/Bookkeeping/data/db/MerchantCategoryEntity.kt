package com.nonen.Bookkeeping.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 商户记忆：记录「这个商户被我归到哪个分类」。
 *
 * 与 [CategoryRuleEntity] 的关键词规则不同，这里按**归一化后的商户名**匹配
 * （见 core/MerchantKey），语义是精确/包含而非子串命中，用来兜住关键词表
 * 覆盖不到的个人商户（楼下小馆、常见便利店等）。
 * 优先级最高：用户在确认卡片或编辑页亲手选定的分类，应当压过一切自动推断。
 */
@Entity(
    tableName = "merchant_categories",
    indices = [
        // (商户键, 方向) 唯一：同一商户在收/支两个方向可以各记一条
        Index(value = ["merchantKey", "isIncome"], unique = true),
    ],
)
data class MerchantCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 归一化后的商户名（MerchantKey.normalize 的结果） */
    val merchantKey: String,
    val category: String,
    val isIncome: Boolean,
    /** 被纠正的次数：重复纠正同一商户时累加，可用于日后排序或清理 */
    val hits: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)
