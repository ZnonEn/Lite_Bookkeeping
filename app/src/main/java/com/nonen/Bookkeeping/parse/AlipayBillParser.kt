package com.nonen.Bookkeeping.parse

import com.nonen.Bookkeeping.core.JsonUtil

/**
 * 支付宝「用于个人对账」导出 CSV 账单解析器（GBK 编码，带前置说明与尾部汇总）。
 * 时间格式为 2026/8/27 14:56；仅提取交易时间、交易对方、商品说明、收/支、金额，
 * 交易分类只作为自动分类的关键词提示；订单号等列一律不读取。
 */
object AlipayBillParser {

    const val SOURCE = "alipay"

    fun parse(bytes: ByteArray): List<ParsedBillRow> {
        val records = CsvSupport.readRecords(CsvSupport.decode(bytes))
        val headerIdx = records.indexOfFirst { row ->
            row.any { it.trim() == "交易时间" } && row.any { it.trim() == "收/支" }
        }
        if (headerIdx < 0) return emptyList()
        val header = records[headerIdx].map { it.trim() }

        fun col(name: String): Int = header.indexOf(name)
        val cTime = col("交易时间")
        val cCat = col("交易分类")
        val cParty = col("交易对方")
        val cGoods = col("商品说明")
        val cDir = col("收/支")
        val cAmount = col("金额")
        val cStatus = col("交易状态")
        if (cTime < 0 || cDir < 0 || cAmount < 0) return emptyList()

        return records.drop(headerIdx + 1).mapNotNull { row ->
            if (row.size <= cTime || row.size <= cDir || row.size <= cAmount) return@mapNotNull null
            val time = row[cTime].trim()
            if (!CsvSupport.looksLikeDate(time)) return@mapNotNull null // 前置说明 / 尾部汇总

            val dir = row[cDir].trim()
            val status = row.getOrNull(cStatus)?.trim().orEmpty()
            val party = row.getOrNull(cParty)?.trim().orEmpty()
            val goods = row.getOrNull(cGoods)?.trim().orEmpty()
            val cat = row.getOrNull(cCat)?.trim().orEmpty()
            val amountText = row[cAmount].trim()

            val raw = JsonUtil.obj(
                "time" to time, "party" to party, "goods" to goods,
                "direction" to dir, "amount" to amountText,
            )

            // 仅保留已成功的收支记录；不计收支 / 退款 / 未完成状态一律跳过
            if (dir !in setOf("收入", "支出") || !status.contains("交易成功")) {
                ParsedBillRow(rawData = raw, skipped = true)
            } else {
                ParsedBillRow(
                    timestamp = CsvSupport.parseTimestamp(time),
                    amount = CsvSupport.parseAmount(amountText),
                    isIncome = dir == "收入",
                    merchant = party.ifEmpty { null },
                    note = goods.ifEmpty { null },
                    categoryHint = cat.ifEmpty { null },
                    rawData = raw,
                )
            }
        }
    }
}
