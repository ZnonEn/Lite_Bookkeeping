package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.parse.WechatBillParser
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WechatBillParserTest {

    private val csv = """
        微信支付账单明细,,
        ----------------------微信支付账单明细--------------------
        导出时间:[2026-08-01 12:00:00],,
        ----------------------微信支付账单明细--------------------
        交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
        2026-08-01 10:00:00,商户消费,美团平台商户,外卖订单,支出,¥25.00,零钱,已支付,10001,20001,"/"
        2026-08-02 11:30:00,微信红包,张三,红包-生日快乐,收入,¥100.00,零钱,已存零钱,10002,20002,"/"
        2026-08-03 09:00:00,转账,/,零钱提现到银行卡,/,¥500.00,/,已提现,10003,20003,"/"
        2026-08-04 12:00:00,商户消费,全家便利店,早餐,支出,¥12.00,零钱,已全额退款,10004,20004,"/"
        ----------------------微信支付账单明细--------------------
    """.trimIndent()

    @Test
    fun `parses valid rows and skips non-pay rows`() {
        val rows = WechatBillParser.parse(csv.toByteArray(Charsets.UTF_8))
        assertEquals(4, rows.size) // 4 条数据行（含 2 条应跳过的）

        val first = rows[0]
        assertFalse(first.skipped)
        assertEquals(25.0, first.amount!!, 1e-9)
        assertFalse(first.isIncome)
        assertEquals("美团平台商户", first.merchant)
        assertEquals("外卖订单", first.note)
        assertTrue(first.timestamp!! > 0)

        val second = rows[1]
        assertFalse(second.skipped)
        assertTrue(second.isIncome)
        assertEquals(100.0, second.amount!!, 1e-9)
        assertEquals("张三", second.merchant)

        assertTrue(rows[2].skipped) // 「/」不计收支
        assertTrue(rows[3].skipped) // 已全额退款
    }

    @Test
    fun `returns empty for unrelated file`() {
        val rows = WechatBillParser.parse("name,age\nfoo,1".toByteArray(Charsets.UTF_8))
        assertTrue(rows.isEmpty())
    }

    // ---- xlsx（当前官方导出格式，结构复刻自真实文件）----

    private val sharedStringsXml =
        "<sst count=\"21\" uniqueCount=\"21\">" +
            listOf(
                "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态",
                "交易单号", "商户单号", "备注", "扫二维码付款", "开心", "收款方备注:二维码收款", "支出",
                "零钱", "已转账", "/", "陈细芬", "收入", "已收钱",
            ).joinToString("") { "<si><t>$it</t></si>" } +
            "</sst>"

    private val sheetXml =
        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" +
            "<row r=\"18\">" +
            (0..10).joinToString("") { "<c r=\"${'A' + it}18\" t=\"s\"><v>$it</v></c>" } +
            "</row>" +
            "<row r=\"19\">" +
            "<c r=\"A19\" s=\"1\"><v>46235.882407407407</v></c>" +
            "<c r=\"B19\" t=\"s\"><v>11</v></c>" +
            "<c r=\"C19\" t=\"s\"><v>12</v></c>" +
            "<c r=\"D19\" t=\"s\"><v>13</v></c>" +
            "<c r=\"E19\" t=\"s\"><v>14</v></c>" +
            "<c r=\"F19\" s=\"3\"><v>100</v></c>" +
            "<c r=\"G19\" t=\"s\"><v>15</v></c>" +
            "<c r=\"H19\" t=\"s\"><v>16</v></c>" +
            "<c r=\"I19\" s=\"2\"/>" +
            "<c r=\"J19\" s=\"2\"/>" +
            "<c r=\"K19\" t=\"s\"><v>17</v></c>" +
            "</row>" +
            "<row r=\"20\">" +
            "<c r=\"A20\" s=\"1\"><v>46235.882141203707</v></c>" +
            "<c r=\"B20\" t=\"s\"><v>11</v></c>" +
            "<c r=\"C20\" t=\"s\"><v>18</v></c>" +
            "<c r=\"D20\" t=\"s\"><v>13</v></c>" +
            "<c r=\"E20\" t=\"s\"><v>19</v></c>" +
            "<c r=\"F20\" s=\"3\"><v>200</v></c>" +
            "<c r=\"G20\" t=\"s\"><v>15</v></c>" +
            "<c r=\"H20\" t=\"s\"><v>20</v></c>" +
            "<c r=\"I20\" s=\"2\"/>" +
            "<c r=\"J20\" t=\"s\"><v>17</v></c>" +
            "<c r=\"K20\" t=\"s\"><v>17</v></c>" +
            "</row>" +
            "</sheetData></worksheet>"

    private fun buildXlsx(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    @Test
    fun `parses xlsx bill with excel serial dates`() {
        val rows = WechatBillParser.parse(buildXlsx())
        assertEquals(2, rows.size)

        val expense = rows[0]
        assertFalse(expense.skipped)
        assertFalse(expense.isIncome)
        assertEquals(100.0, expense.amount!!, 1e-9)
        assertEquals("开心", expense.merchant)
        assertEquals("收款方备注:二维码收款", expense.note)

        // Excel 序列号 46235.882407407407 → 2026-08-01 21:10:40（本地时区）
        val ldt = Instant.ofEpochMilli(expense.timestamp!!).atZone(ZoneId.systemDefault())
        assertEquals(LocalDate.of(2026, 8, 1), ldt.toLocalDate())
        assertEquals(LocalTime.of(21, 10, 40), ldt.toLocalTime())

        val income = rows[1]
        assertFalse(income.skipped)
        assertTrue(income.isIncome)
        assertEquals(200.0, income.amount!!, 1e-9)
        assertEquals("陈细芬", income.merchant)
        assertEquals(LocalDate.of(2026, 8, 1), Instant.ofEpochMilli(income.timestamp!!).atZone(ZoneId.systemDefault()).toLocalDate())
    }
}
