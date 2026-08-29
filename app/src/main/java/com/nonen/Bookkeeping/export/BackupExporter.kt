package com.nonen.Bookkeeping.export

import com.nonen.Bookkeeping.core.JsonUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity

/** JSON 备份导出（本地文件，方便迁移）。 */
object BackupExporter {

    fun buildJson(transactions: List<TransactionEntity>): String {
        val items = transactions.joinToString(",", "[", "]") { t ->
            JsonUtil.obj(
                "amount" to t.amount,
                "category" to t.category,
                "note" to t.note,
                "merchant" to t.merchant,
                "timestamp" to t.timestamp,
                "source" to t.source,
                "hash" to t.hash,
                "createdAt" to t.createdAt,
                "updatedAt" to t.updatedAt,
            )
        }
        return """{"app":"bookkeeping","version":1,"exportedAt":${System.currentTimeMillis()},"transactions":$items}"""
    }
}
