package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 专版专杀解析：成功页 / 微信与支付宝账单详情 / 红包与转账收款详情，其余页面一律不检测 */
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
    fun `支付宝账单详情页带符号金额`() {
        // 真实页面版式（用户实测截图）
        val p = WindowCaptureAnalyzer.analyze(
            listOf(
                "账单详情", "科蕊小吃店", "-0.01", "交易成功", "支付时间", "2026-08-30 14:23:20",
                "付款方式", "招商银行储蓄卡(1186)", "收款方全称", "苍南县科蕊小吃店",
            ),
        )
        assertNotNull(p)
        assertEquals(0.01, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("苍南县科蕊小吃店", p.counterparty)
        val expected = LocalDateTime.of(2026, 8, 30, 14, 23, 20)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, p.timestamp)
    }

    @Test
    fun `支付宝账单详情页支出收入标签`() {
        val p = WindowCaptureAnalyzer.analyze(
            listOf("账单详情", "支出12.80元", "商品说明", "中午外卖", "收款方全称", "沙县小吃"),
        )
        assertNotNull(p)
        assertEquals(12.8, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("沙县小吃", p.counterparty)
        assertEquals("中午外卖", p.description)
    }

    @Test
    fun `支付宝账单详情页金额正上方店名兜底`() {
        val p = WindowCaptureAnalyzer.analyze(
            listOf("账单详情", "蜜雪冰城", "3.50", "交易成功", "订单号", "20260830"),
        )
        assertNotNull(p)
        assertEquals(3.5, p!!.amount, 0.001)
        assertEquals("蜜雪冰城", p.counterparty)
    }

    @Test
    fun `微信账单详情页带符号金额与商品说明`() {
        val p = WindowCaptureAnalyzer.analyze(
            listOf(
                "微信支付", "-25.60", "拼多多平台商户", "交易详情",
                "交易单号", "4200025820260830123456", "商户单号", "198207041234",
                "商品", "苹果一箱", "支付时间", "2026年8月30日 12:01:30",
            ),
        )
        assertNotNull(p)
        assertEquals(25.6, p!!.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("拼多多平台商户", p.counterparty)
        assertEquals("苹果一箱", p.description)
    }

    @Test
    fun `微信红包领取详情页`() {
        val p = WindowCaptureAnalyzer.analyze(listOf("小二的红包", "0.01", "元", "已存入零钱"))
        assertNotNull(p)
        assertEquals(0.01, p!!.amount, 0.001)
        assertEquals(true, p.isIncome)
        assertEquals("小二", p.counterparty)
    }

    @Test
    fun `微信转账已收款详情页`() {
        val p = WindowCaptureAnalyzer.analyze(
            listOf("你已收款，资金已存入零钱", "¥12.00", "转账说明", "请吃饭", "收款时间", "2026年4月8日 20:42"),
        )
        assertNotNull(p)
        assertEquals(12.0, p!!.amount, 0.001)
        assertEquals(true, p.isIncome)
        assertEquals("请吃饭", p.description)
        assertNotNull(p.timestamp)
    }

    @Test
    fun `无法识别的页面不弹卡片`() {
        // 聊天列表/消息预览
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信(1)", "小号", "[转账] 已退还", "微信支付", "已支付¥0.01")))
        // 转账输入等付款前置页
        assertNull(WindowCaptureAnalyzer.analyze(listOf("转账", "转账金额", "¥", "100.00", "添加转账说明")))
        // 普通浏览页面
        assertNull(WindowCaptureAnalyzer.analyze(listOf("微信支付", "首页", "我的", "¥88.00")))
        // 有金额无特征的页面
        assertNull(WindowCaptureAnalyzer.analyze(listOf("零钱", "¥66.00", "常见问题")))
    }
}
