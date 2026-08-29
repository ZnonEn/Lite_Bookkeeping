package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.parse.AlipayBillParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlipayBillParserTest {

    /** 结构复刻自真实导出文件：GBK 编码、2026/8/27 斜杠时间、行尾多余逗号、订单号列为空 */
    private val csv = """
        ------------------------------------------------------------------------------------,,,,,,,,,,,
        导出信息：,,,,,,,,,,,
        姓名：某某,,,,,,,,,,,
        支付宝账户：17800000000,,,,,,,,,,,
        起始时间：[2026-08-26 00:00:00]    终止时间：[2026-08-28 23:59:59],,,,,,,,,,,
        共3笔记录,,,,,,,,,,,
        支出：3笔 24.57元,,,,,,,,,,,
        ------------------------支付宝支付科技有限公司  电子客户回单------------------------,,,,,,,,,,,
        交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
        2026/8/27 14:56,交通出行,苍南县城乡公共交通有限公司,/,商品,支出,10,亲情卡(建真(郑建真)),交易成功,,,
        2026/8/27 14:40,交通出行,滴滴出行,chu***@didichuxing.com,滴滴快车打车-曾师傅-08月27日行程,支出,10.92,亲情卡(建真(郑建真)),交易成功,,,
        2026/8/26 17:47,餐饮美食,赵一鸣,/,苍南县登然食品店(个体工商户),支出,3.65,招商银行储蓄卡(1186)&碰一下立减,交易成功,,,
        ------------------------支付宝支付科技有限公司  电子客户回单------------------------,,,,,,,,,,,
    """.trimIndent()

    @Test
    fun `parses gbk encoded alipay bill with slash dates`() {
        val rows = AlipayBillParser.parse(csv.toByteArray(charset("GBK")))
        assertEquals(3, rows.size)

        val first = rows[0]
        assertFalse(first.skipped)
        assertEquals(10.0, first.amount!!, 1e-9)
        assertFalse(first.isIncome)
        assertEquals("苍南县城乡公共交通有限公司", first.merchant)
        assertEquals("交通出行", first.categoryHint)
        assertTrue(first.timestamp!! > 0)
        // 斜杠日期 2026/8/27 14:56 应正确解析
        val ldt = java.time.Instant.ofEpochMilli(first.timestamp!!).atZone(java.time.ZoneId.systemDefault())
        assertEquals(java.time.LocalDate.of(2026, 8, 27), ldt.toLocalDate())
        assertEquals(java.time.LocalTime.of(14, 56), ldt.toLocalTime())

        val second = rows[1]
        assertFalse(second.skipped)
        assertEquals(10.92, second.amount!!, 1e-9)
        assertEquals("滴滴出行", second.merchant)
        assertEquals("滴滴快车打车-曾师傅-08月27日行程", second.note)

        val third = rows[2]
        assertFalse(third.skipped)
        assertEquals(3.65, third.amount!!, 1e-9)
        assertEquals("赵一鸣", third.merchant)
        assertEquals("餐饮美食", third.categoryHint)
    }

    @Test
    fun `parses utf-8 encoded alipay bill too`() {
        val rows = AlipayBillParser.parse(csv.toByteArray(Charsets.UTF_8))
        assertEquals(3, rows.size)
        assertFalse(rows[0].skipped)
    }

    @Test
    fun `skips neutral and unsuccessful rows`() {
        val withJunk = """
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
            2026/8/11 20:00:00,投资理财,基金公司,*,买入基金,不计收支,1000.00,余额宝,交易成功,20260811a,20260811b,
            2026/8/12 20:00,购物,某商城,*,商品订单,支出,50.00,余额,等待付款,20260812a,20260812b,
        """.trimIndent()
        val rows = AlipayBillParser.parse(withJunk.toByteArray(charset("GBK")))
        assertEquals(2, rows.size)
        assertTrue(rows[0].skipped) // 不计收支
        assertTrue(rows[1].skipped) // 未完成状态
    }
}
