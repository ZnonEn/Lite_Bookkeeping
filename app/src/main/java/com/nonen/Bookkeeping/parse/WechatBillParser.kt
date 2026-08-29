package com.nonen.Bookkeeping.parse

import com.nonen.Bookkeeping.core.JsonUtil

/**
 * 微信「用于个人对账」导出账单解析器。
 * 支持 xlsx（当前官方格式）与旧版 CSV；两种格式的列结构一致。
 * 仅提取：交易时间、交易对方、商品、收/支、金额；订单号等列一律不读取。
 */
object WechatBillParser {

    const val SOURCE = "wechat"

    fun parse(bytes: ByteArray): List<ParsedBillRow> {
        val table: List<List<String?>> = if (isZip(bytes)) {
            XlsxSupport.readFirstSheet(bytes)
        } else {
            CsvSupport.readRecords(CsvSupport.decode(bytes)).map { row -> row.map { it as String? } }
        }
        return parseTable(table)
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() // "PK"

    private fun parseTable(table: List<List<String?>>): List<ParsedBillRow> {
        val headerIdx = table.indexOfFirst { row -> row.any { it?.trim() == "交易时间" } }
        if (headerIdx < 0) return emptyList()
        val header = table[headerIdx].map { it?.trim().orEmpty() }

        fun col(name: String): Int = header.indexOf(name)
        val cTime = col("交易时间")
        val cParty = col("交易对方")
        val cGoods = col("商品")
        val cDir = col("收/支")
        val cAmount = col("金额(元)")
        val cStatus = col("当前状态")
        if (cTime < 0 || cDir < 0 || cAmount < 0) return emptyList()

        return table.drop(headerIdx + 1).mapNotNull { row ->
            val time = row.getOrNull(cTime)?.trim().orEmpty()
            // xlsx 交易时间为 Excel 序列号，CSV 为日期字符串；其余是说明行/汇总行
            if (!(time.toDoubleOrNull() != null || CsvSupport.looksLikeDate(time))) return@mapNotNull null

            val dir = row.getOrNull(cDir)?.trim().orEmpty()
            val status = row.getOrNull(cStatus)?.trim().orEmpty()
            val party = row.getOrNull(cParty)?.trim().orEmpty()
            val goods = row.getOrNull(cGoods)?.trim().orEmpty()
            val amountText = row.getOrNull(cAmount)?.trim().orEmpty()

            val raw = JsonUtil.obj(
                "time" to time, "party" to party, "goods" to goods,
                "direction" to dir, "amount" to amountText,
            )

            // 「/」为不计收支（理财、充值、提现等）；已退款的原记录跳过，避免与退款行重复计账
            if (dir !in setOf("收入", "支出") || status.contains("退款")) {
                ParsedBillRow(rawData = raw, skipped = true)
            } else {
                ParsedBillRow(
                    timestamp = CsvSupport.parseFlexibleTimestamp(time),
                    amount = CsvSupport.parseAmount(amountText),
                    isIncome = dir == "收入",
                    merchant = party.ifEmpty { null },
                    note = goods.ifEmpty { null },
                    rawData = raw,
                )
            }
        }
    }
}
