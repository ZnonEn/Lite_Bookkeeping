package com.nonen.Bookkeeping.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index("timestamp"),
        Index("category"),
        Index(value = ["hash"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 金额：正数为收入，负数为支出 */
    val amount: Double,
    val category: String,
    val note: String? = null,
    val merchant: String? = null,
    val timestamp: Long,
    /** 来源：manual / wechat / alipay / auto */
    val source: String,
    /** 原始数据（JSON，便于回溯） */
    val rawData: String? = null,
    /** 去重哈希：MD5(时间 + 金额 + 商户 + 来源)，唯一 */
    val hash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
