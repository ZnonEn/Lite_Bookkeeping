package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 只抓取支付成功页：成功页解析正确性 + 其余页面一律不检测 */
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
    fun `微信聊天内转账成功页`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("支付成功", "待小号确认收款", "¥", "0.01", "完成"))
        assertNotNull(p)
        assertEquals(0.01, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
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
    fun `人民币符号与数字分离的节点也能提取金额`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("付款成功", "¥", "8.35", "收款方：便利店"))
        assertNotNull(p)
        assertEquals(8.35, p!!.amount, 0.001)
    }

    @Test
    fun `成功页缺金额不记录`() {
        assertNull(WindowCaptureAnalyzer.analyze(listOf("支付成功", "支付方式", "零钱")))
    }

    @Test
    fun `非支付成功页一律不检测`() {
        // 聊天列表/消息预览
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信(1)", "小号", "[转账] 已退还", "微信支付", "已支付¥0.01")))
        // 账单详情页
        assertNull(
            WindowCaptureAnalyzer.analyze(
                listOf(
                    "账单详情", "科蕊小吃店", "-0.01", "交易成功", "支付时间", "2026-08-30 14:23:20",
                    "付款方式", "招商银行储蓄卡(1186)", "收款方全称", "苍南县科蕊小吃店",
                ),
            )
        )
        // 红包详情页
        assertNull(WindowCaptureAnalyzer.analyze(listOf("小二的红包", "0.01", "元", "已存入零钱")))
        // 转账输入等付款前置页
        assertNull(WindowCaptureAnalyzer.analyze(listOf("转账", "转账金额", "¥", "100.00", "添加转账说明")))
        // 普通浏览页面
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信支付", "首页", "我的", "¥88.00")))
    }
}
