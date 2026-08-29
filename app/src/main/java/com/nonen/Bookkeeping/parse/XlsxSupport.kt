package com.nonen.Bookkeeping.parse

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor
import kotlin.math.round
import org.w3c.dom.Element

/**
 * 轻量 XLSX 读取（微信账单为 xlsx 格式）。
 * 只解析 sharedStrings + 工作表 XML（DOM），不引入 Apache POI。
 * 返回按「行 → 列下标」展开的字符串矩阵，空单元格为 null。
 */
object XlsxSupport {

    fun readFirstSheet(bytes: ByteArray): List<List<String?>> {
        var shared: List<String> = emptyList()
        var sheet1Xml: String? = null
        var fallbackXml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" ->
                        shared = parseSharedStrings(zip.readBytes())

                    entry.name == "xl/worksheets/sheet1.xml" ->
                        sheet1Xml = zip.readBytes().toString(Charsets.UTF_8)

                    sheet1Xml == null && fallbackXml == null &&
                        entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") ->
                        fallbackXml = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        val xml = sheet1Xml ?: fallbackXml ?: return emptyList()
        return parseSheet(xml, shared)
    }

    /**
     * Excel 日期序列号 → 毫秒时间戳。
     * 序列号以 1899-12-30 为第 0 天（兼容 1900 闰年 bug），小数部分为当日时间。
     * 例：46235.882407407407 → 2026-08-01 21:10:40（本地时区）。
     */
    fun excelSerialToMillis(serial: Double): Long {
        val days = floor(serial).toLong()
        val secondsOfDay = round((serial - days) * 86400.0).toLong()
        val date = LocalDate.of(1899, 12, 30).plusDays(days)
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
            secondsOfDay * 1000
    }

    /** "A19" → 0，"AB3" → 27 */
    fun colIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            idx = idx * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return idx - 1
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = newDocument(bytes) ?: return emptyList()
        val result = ArrayList<String>()
        val nodes = doc.getElementsByTagName("si")
        for (i in 0 until nodes.length) {
            result.add(nodes.item(i).textContent)
        }
        return result
    }

    private fun parseSheet(xml: String, shared: List<String>): List<List<String?>> {
        val doc = newDocument(xml.toByteArray()) ?: return emptyList()
        val rowsOut = ArrayList<List<String?>>()
        val rows = doc.getElementsByTagName("row")
        for (i in 0 until rows.length) {
            val rowEl = rows.item(i) as Element
            val cells = rowEl.getElementsByTagName("c")
            val map = HashMap<Int, String?>()
            var maxCol = -1
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element
                val col = colIndex(c.getAttribute("r"))
                if (col < 0) continue
                val vNodes = c.getElementsByTagName("v")
                val raw = if (vNodes.length > 0) vNodes.item(0).textContent else null
                map[col] = when {
                    raw == null -> null
                    c.getAttribute("t") == "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) }
                    else -> raw
                }
                if (col > maxCol) maxCol = col
            }
            rowsOut.add(
                if (maxCol < 0) emptyList()
                else (0..maxCol).map { map[it] }
            )
        }
        return rowsOut
    }

    private fun newDocument(bytes: ByteArray) = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }.getOrNull()
}
