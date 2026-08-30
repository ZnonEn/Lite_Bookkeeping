package com.nonen.Bookkeeping.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 轻量 XLSX 写入（与 parse/XlsxSupport 读取器配套，零第三方依赖）。
 * 字符串走 sharedStrings，数值走原生数字单元格；时间等一律以文本写入，
 * 避免 Excel 序列号/日期样式的解析歧义。
 */
object MiniXlsx {

    /** 写出仅含单个工作表的最小 xlsx。String → 文本单元格，Number → 数字单元格，null → 空单元格。 */
    fun write(rows: List<List<Any?>>): ByteArray {
        val shared = ArrayList<String>()
        val sharedIndex = HashMap<String, Int>()
        fun sharedIdOf(value: String): Int = sharedIndex.getOrPut(value) {
            shared.add(value); shared.size - 1
        }

        val sheetXml = StringBuilder()
        sheetXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sheetXml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            sheetXml.append("<row r=\"").append(rowIndex + 1).append("\">")
            cells.forEachIndexed { colIndex, cell ->
                if (cell == null) return@forEachIndexed
                val ref = colLetter(colIndex) + (rowIndex + 1)
                when (cell) {
                    is Number -> sheetXml.append("<c r=\"").append(ref).append("\"><v>")
                        .append(cell).append("</v></c>")
                    else -> sheetXml.append("<c r=\"").append(ref).append("\" t=\"s\"><v>")
                        .append(sharedIdOf(cell.toString())).append("</v></c>")
                }
            }
            sheetXml.append("</row>")
        }
        sheetXml.append("</sheetData></worksheet>")

        val sharedXml = StringBuilder()
        sharedXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sharedXml.append(
            "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "count=\"${shared.size}\" uniqueCount=\"${shared.size}\">",
        )
        shared.forEach { value ->
            sharedXml.append("<si><t>").append(escape(value)).append("</t></si>")
        }
        sharedXml.append("</sst>")

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>" +
                    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                    "</Types>",
            )
            entry(
                "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>",
            )
            entry(
                "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"账单\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>" +
                    "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                    "</Relationships>",
            )
            entry(
                "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                    "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                    "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>" +
                    "<borders count=\"1\"><border/></borders>" +
                    "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
                    "<cellXfs count=\"1\"><xf/></cellXfs>" +
                    "</styleSheet>",
            )
            entry("xl/sharedStrings.xml", sharedXml.toString())
            entry("xl/worksheets/sheet1.xml", sheetXml.toString())
        }
        return out.toByteArray()
    }

    /** 便捷读取：用配套的 XlsxSupport 读回首个工作表（供导入与测试复用） */
    fun read(bytes: ByteArray): List<List<String?>> =
        com.nonen.Bookkeeping.parse.XlsxSupport.readFirstSheet(bytes)

    private fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
