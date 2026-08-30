package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.parse.PaymentTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTextParserTest {

    @Test
    fun `wechat payment notification - expense`() {
        val p = PaymentTextParser.parse("你已成功付款¥25.50")!!
        assertEquals(25.50, p.amount, 1e-9)
        assertFalse(p.isIncome)
    }

    @Test
    fun `wechat collect notification - income with counterparty`() {
        val p = PaymentTextParser.parse("收款到账微信零钱，来自张三，¥100.00")!!
        assertEquals(100.0, p.amount, 1e-9)
        assertTrue(p.isIncome)
        assertEquals("张三", p.counterparty)
    }

    @Test
    fun `alipay payment notification - expense with counterparty`() {
        val p = PaymentTextParser.parse("支付成功：你已向美团-外卖付款￥33.00")!!
        assertEquals(33.0, p.amount, 1e-9)
        assertFalse(p.isIncome)
        assertEquals("美团-外卖", p.counterparty)
    }

    @Test
    fun `yuan suffix amount - income`() {
        val p = PaymentTextParser.parse("收款成功 8.88元")!!
        assertEquals(8.88, p.amount, 1e-9)
        assertTrue(p.isIncome)
    }

    @Test
    fun `direction ambiguous returns null`() {
        assertNull(PaymentTextParser.parse("余额变动 ¥10.00"))
    }

    @Test
    fun `no amount returns null`() {
        assertNull(PaymentTextParser.parse("这是一条普通消息"))
    }

    @Test
    fun `no keyword returns null`() {
        assertNull(PaymentTextParser.parse("会员日 ¥9.9"))
    }

    @Test
    fun `wechat pay title joined with text keeps direction`() {
        // 服务端会把通知标题与正文拼接在一起
        val p = PaymentTextParser.parse("微信支付 你已成功付款¥25.00")!!
        assertFalse(p.isIncome)
        assertEquals(25.0, p.amount, 1e-9)
    }

    @Test
    fun `labeled amount takes priority with direction`() {
        val p = PaymentTextParser.parse("微信支付\n付款金额¥12.30\n支付方式 零钱\n交易状态 支付成功")!!
        assertEquals(12.3, p.amount, 1e-9)
        assertFalse(p.isIncome)
    }

    @Test
    fun `income labeled amount`() {
        val p = PaymentTextParser.parse("微信支付\n收款金额￥8.00\n汇总 收款8.00元")!!
        assertEquals(8.0, p.amount, 1e-9)
        assertTrue(p.isIncome)
    }

    @Test
    fun `refund labeled amount is income`() {
        val p = PaymentTextParser.parse("退款到账通知\n退款金额￥20.00\n退款方式 退回零钱")!!
        assertEquals(20.0, p.amount, 1e-9)
        assertTrue(p.isIncome)
    }
}
