package com.nonen.Bookkeeping.parse

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 解析后的账单行。timestamp/amount 为 null 表示该行数据无法解析（计为失败）。 */
data class ParsedBillRow(
    val timestamp: Long? = null,
    val amount: Double? = null,
    val isIncome: Boolean = false,
    val merchant: String? = null,
    val note: String? = null,
    /** 平台自带的分类（支付宝「交易分类」），仅用于自动分类匹配，不入库 */
    val categoryHint: String? = null,
    val rawData: String = "",
    /** true 表示该行按规则被跳过（不计收支 / 已退款 / 交易未成功） */
    val skipped: Boolean = false,
)

data class ImportResult(
    val success: Int,
    val duplicates: Int,
    val failed: Int,
    val skipped: Int,
)

object CsvSupport {

    // 微信 CSV：2026-08-01 10:00:00；支付宝 CSV：2026/8/27 14:56
    private val FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    )
    private val DATE_PREFIX = Regex("""^\d{4}[-/]\d{1,2}[-/]\d{1,2}""")

    /** 微信 CSV 为 UTF-8（带 BOM），支付宝 CSV 为 GBK，按内容自动探测。 */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        val asUtf8 = String(bytes, StandardCharsets.UTF_8)
        if (asUtf8.contains("交易时间")) return asUtf8
        return String(bytes, Charset.forName("GBK"))
    }

    fun readRecords(text: String): List<List<String>> {
        val format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).get()
        return CSVParser(StringReader(text), format).use { parser ->
            parser.map { record -> record.toList() }
        }
    }

    fun parseTimestamp(text: String): Long? {
        val t = text.trim()
        for (formatter in FORMATTERS) {
            runCatching {
                return LocalDateTime.parse(t, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return null
    }

    /**
     * 兼容三种时间格式：日期字符串（- 或 / 分隔）与微信 xlsx 的 Excel 日期序列号。
     */
    fun parseFlexibleTimestamp(raw: String?): Long? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        t.toDoubleOrNull()?.let { return XlsxSupport.excelSerialToMillis(it) }
        return parseTimestamp(t)
    }

    /** 表头/分隔线/汇总行不含日期前缀，数据行形如 2026-08-01 10:00 或 2026/8/27 14:56 */
    fun looksLikeDate(text: String): Boolean = DATE_PREFIX.containsMatchIn(text.trim())

    fun parseAmount(text: String): Double? =
        text.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .toDoubleOrNull()
            ?.takeIf { it > 0.0 }
}
