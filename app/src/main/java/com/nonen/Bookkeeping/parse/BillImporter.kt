package com.nonen.Bookkeeping.parse

import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.core.RuleEngine
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository

/**
 * 账单导入：解析行 → 自动分类 → 哈希去重 → 入库。
 * 入库字段：收支类型、收支方（商户）、金额，以及用于记账/分类的
 * 交易时间与商品说明；订单号、支付方式等一律不写入。
 */
class BillImporter(
    private val repository: TransactionRepository,
    private val ruleEngine: RuleEngine,
) {

    suspend fun import(
        rows: List<ParsedBillRow>,
        source: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        var success = 0
        var duplicates = 0
        var failed = 0
        var skipped = 0
        val total = rows.size

        // 分类依据一次取齐：逐行调用 categorize 会在每行都查一次规则表，
        // 导入数千行就是数千次全表读（N+1）
        val rules = ruleEngine.loadRules()

        for ((index, row) in rows.withIndex()) {
            val timestamp = row.timestamp
            val amount = row.amount
            when {
                row.skipped -> skipped++
                timestamp == null || amount == null -> failed++
                else -> {
                    val signedAmount = if (row.isIncome) amount else -amount
                    val text = listOfNotNull(row.merchant, row.note).joinToString(" ")
                    val entity = TransactionEntity(
                        amount = signedAmount,
                        category = RuleEngine.classify(
                            text = text,
                            isIncome = row.isIncome,
                            rules = rules,
                            merchant = row.merchant,
                            platformCategory = row.platformCategory,
                        ),
                        note = row.note,
                        merchant = row.merchant,
                        timestamp = timestamp,
                        source = source,
                        rawData = row.rawData,
                        hash = HashUtil.transactionHash(timestamp, signedAmount, row.merchant, source),
                    )
                    if (repository.insertIfNew(entity)) success++ else duplicates++
                }
            }
            onProgress(index + 1, total)
        }
        return ImportResult(success, duplicates, failed, skipped)
    }
}
