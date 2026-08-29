package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 按真实支付完成页/账单详情页的文本节点验证启发式抓取 */
class WindowCaptureAnalyzerTest {

    @Test
    fun `微信支付成功页没有金额标签也能记录`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("支付成功", "¥10.00", "支付方式", "零钱", "完成"))
        assertNotNull(p)
        assertEquals(10.0, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
    }

    @Test
    fun `支付宝付款成功页解析收款方`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("付款成功", "¥25.50", "收款方：苍南公交", "付款方式", "余额"))
        assertNotNull(p)
        assertEquals(25.5, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("苍南公交", p.counterparty)
    }

    @Test
    fun `账单详情页标签与数值分离节点`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("账单详情", "当前状态", "交易成功", "付款金额", "¥100.00", "收款方", "赵一鸣"))
        assertNotNull(p)
        assertEquals(100.0, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
    }

    @Test
    fun `退款页识别为收入`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("退款成功", "退款金额", "¥50.00"))
        assertNotNull(p)
        assertEquals(50.0, p!!.amount, 0.001)
        assertEquals(true, p.isIncome)
    }

    @Test
    fun `人民币符号与数字分离的节点也能提取金额`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("付款成功", "¥", "8.35", "收款方：便利店"))
        assertNotNull(p)
        assertEquals(8.35, p!!.amount, 0.001)
    }

    @Test
    fun `微信聊天内转账成功页`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("支付成功", "待小号确认收款", "¥", "0.01", "完成"))
        assertNotNull(p)
        assertEquals(0.01, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
    }

    @Test
    fun `转账气泡待确认收款也可作为支出证据`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("微信转账", "¥0.01", "待确认收款"))
        assertNotNull(p)
        assertEquals(0.01, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
    }

    @Test
    fun `无方向证据的页面不记录`() {
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信支付", "首页", "我的", "¥88.00")))
    }

    @Test
    fun `方向冲突的页面不记录`() {
        assertNull(WindowCaptureAnalyzer.analyze(listOf("支付成功", "已到账", "¥30.00")))
    }

    @Test
    fun `无金额不记录`() {
        assertNull(WindowCaptureAnalyzer.analyze(listOf("支付成功", "支付方式", "零钱")))
    }
}
