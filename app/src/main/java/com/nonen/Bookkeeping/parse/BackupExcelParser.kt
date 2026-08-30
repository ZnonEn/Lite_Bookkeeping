package com.nonen.Bookkeeping.parse

import com.nonen.Bookkeeping.export.BackupExporter

/** 本应用导出的 Excel 备份解析出的一行。 */
data class BackupRow(
    val timestamp: Long? = null,
    val amount: Double? = null,
    val isIncome: Boolean = false,
    /** 类型列缺失/非法时为 false，按失败行计 */
    val typeValid: Boolean = true,
    val category: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val source: String? = null,
    /** 导出时的去重哈希，原样回灌保证「导出→导入」幂等 */
    val hash: String? = null,
)

/**
 * 本应用 Excel 备份解析器：表头按列名定位，导入回灌用。
 * 时间列兼容文本（yyyy-MM-dd HH:mm:ss 等）与被 Excel 重存后的日期序列号。
 */
object BackupExcelParser {

    /** @return null 表示不是本应用的备份格式（找不到表头） */
    fun parse(bytes: ByteArray): List<BackupRow>? {
        val table = XlsxSupport.readFirstSheet(bytes)
        val headerIdx = table.indexOfFirst { row -> row.any { it?.trim() == BackupExporter.HEADER_TIME } }
        if (headerIdx < 0) return null
        val header = table[headerIdx].map { it?.trim().orEmpty() }

        fun col(name: String): Int = header.indexOf(name)
        val cTime = col(BackupExporter.HEADER_TIME)
        val cType = col(BackupExporter.HEADER_TYPE)
        val cAmount = col(BackupExporter.HEADER_AMOUNT)
        val cCategory = col(BackupExporter.HEADER_CATEGORY)
        val cMerchant = col(BackupExporter.HEADER_MERCHANT)
        val cNote = col(BackupExporter.HEADER_NOTE)
        val cSource = col(BackupExporter.HEADER_SOURCE)
        val cHash = col(BackupExporter.HEADER_HASH)
        if (cTime < 0 || cType < 0 || cAmount < 0) return null

        return table.drop(headerIdx + 1)
            .filter { row -> row.any { !it.isNullOrBlank() } }
            .map { row ->
                val type = row.getOrNull(cType)?.trim().orEmpty()
                BackupRow(
                    timestamp = CsvSupport.parseFlexibleTimestamp(row.getOrNull(cTime)),
                    amount = CsvSupport.parseAmount(row.getOrNull(cAmount).orEmpty()),
                    isIncome = type == "收入",
                    typeValid = type in setOf("收入", "支出"),
                    category = row.getOrNull(cCategory)?.trim()?.ifBlank { null },
                    merchant = row.getOrNull(cMerchant)?.trim()?.ifBlank { null },
                    note = row.getOrNull(cNote)?.trim()?.ifBlank { null },
                    source = row.getOrNull(cSource)?.trim()?.ifBlank { null },
                    hash = row.getOrNull(cHash)?.trim()?.ifBlank { null },
                )
            }
    }
}
