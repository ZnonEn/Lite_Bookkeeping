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
    fun `聊天气泡与列表预览不作为记账依据`() {
        // 聊天页/列表页的转账气泡会长期存在，重复浏览会产生重复记录——真正的
        // 转账记录来自支付成功页与账单详情页，气泡缺支付上下文一律不记
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信转账", "¥0.01", "待对方确认收款")))
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信(1)", "小号", "[转账] 已退还", "微信支付", "已支付¥0.01")))
    }

    @Test
    fun `转账收款确认页识别为收入`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("小号", "¥0.01", "待确认收款", "立即收款", "退还"))
        assertNotNull(p)
        assertEquals(0.01, p!!.amount, 0.001)
        assertEquals(true, p.isIncome)
    }

    @Test
    fun `支付宝支付成功页跳过优惠券与推荐位金额`() {
        val texts = listOf(
            "支付成功", "¥ 24.00", "杭州麦当劳城西银泰餐厅", "¥24.00",
            "付款方式", "招商银行信用卡(3386)",
            "本店优惠", "7.5元", "海盐椰子风味甜筒特价券", "原价10元",
            "支付有福利", "去使用",
            "37.7元", "双层制霸2人餐特价券", "原价67元",
            "29元", "出神卤化鸡架单人餐特价券", "原价40.5元",
            "关注小米生活号享更多资讯", "0元预约赢新品耳机", "完成",
        )
        val p = WindowCaptureAnalyzer.analyze(texts)
        assertNotNull(p)
        assertEquals(24.0, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("杭州麦当劳城西银泰餐厅", p.counterparty)
    }

    @Test
    fun `转账输入等付款前置页不记录`() {
        // 转账输入页带「转账金额」标签但没有交易状态词——钱还没付
        assertNull(WindowCaptureAnalyzer.analyze(listOf("转账", "转账金额", "¥", "100.00", "添加转账说明")))
        // 付款确认页同理
        assertNull(WindowCaptureAnalyzer.analyze(listOf("向商家付款", "¥4.00", "付款方式", "零钱")))
    }

    @Test
    fun `微信账单详情页提取真实交易时间`() {
        val p = WindowCaptureAnalyzer.analyze(
            listOf("账单详情", "当前状态", "交易成功", "付款金额", "¥100.00", "收款方", "赵一鸣", "交易时间", "2026-08-30 12:30:05"),
        )
        assertNotNull(p)
        assertEquals(100.0, p!!.amount, 0.001)
        val expected = java.time.LocalDateTime.of(2026, 8, 30, 12, 30, 5)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, p.timestamp)
    }

    @Test
    fun `支付宝账单详情的方向金额合一节点`() {
        val p = WindowCaptureAnalyzer.analyze(
            listOf("账单详情", "交易成功", "支出4.00", "收款方", "苍南公交", "交易时间", "2026年8月30日 14:23"),
        )
        assertNotNull(p)
        assertEquals(4.0, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("苍南公交", p.counterparty)
    }

    @Test
    fun `无方向证据的页面不记录`() {
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信支付", "首页", "我的", "¥88.00")))
    }

    @Test
    fun `方向冲突的页面不记录`() {
        // 无支付成功页标记时，支出与收入提示词同时出现仍视为方向不明
        assertNull(WindowCaptureAnalyzer.analyze(listOf("扣款成功", "已到账", "¥30.00")))
    }

    @Test
    fun `无金额不记录`() {
        assertNull(WindowCaptureAnalyzer.analyze(listOf("支付成功", "支付方式", "零钱")))
    }
}
