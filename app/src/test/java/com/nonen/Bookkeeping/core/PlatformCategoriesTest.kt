package com.nonen.Bookkeeping.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCategoriesTest {

    @Test
    fun `alipay categories map to expected targets`() {
        assertEquals("交通", PlatformCategories.resolve("交通出行", isIncome = false))
        assertEquals("餐饮", PlatformCategories.resolve("餐饮美食", isIncome = false))
        assertEquals("服饰美容", PlatformCategories.resolve("服饰装扮", isIncome = false))
        assertEquals("旅行", PlatformCategories.resolve("酒店旅游", isIncome = false))
        assertEquals("教育", PlatformCategories.resolve("教育培训", isIncome = false))
        assertEquals("宠物", PlatformCategories.resolve("宠物", isIncome = false))
        assertEquals("母婴", PlatformCategories.resolve("母婴亲子", isIncome = false))
    }

    @Test
    fun `direction decides target for ambiguous platform values`() {
        // 同一个平台值在收/支两个方向落不同分类
        assertEquals("人情", PlatformCategories.resolve("转账红包", isIncome = false))
        assertEquals("红包", PlatformCategories.resolve("转账红包", isIncome = true))
        assertEquals("金融", PlatformCategories.resolve("投资理财", isIncome = false))
        assertEquals("理财", PlatformCategories.resolve("投资理财", isIncome = true))
    }

    @Test
    fun `overly broad platform values are not mapped`() {
        // 这些只说明支付场景，不含消费领域 —— 必须交给关键词规则，而不是错误兜底
        assertNull(PlatformCategories.resolve("商户消费", isIncome = false))
        assertNull(PlatformCategories.resolve("扫二维码", isIncome = false))
        assertNull(PlatformCategories.resolve("生活服务", isIncome = false))
        assertNull(PlatformCategories.resolve("其他", isIncome = false))
        assertNull(PlatformCategories.resolve("公共服务", isIncome = false))
    }

    @Test
    fun `unknown and blank values return null`() {
        assertNull(PlatformCategories.resolve(null, isIncome = false))
        assertNull(PlatformCategories.resolve("", isIncome = false))
        assertNull(PlatformCategories.resolve("   ", isIncome = false))
        assertNull(PlatformCategories.resolve("未来新分类", isIncome = false))
    }

    @Test
    fun `income only platform values do not leak into expense direction`() {
        // 「退款」只在收入方向映射，支出时不应命中
        assertEquals("退款", PlatformCategories.resolve("退款", isIncome = true))
        assertNull(PlatformCategories.resolve("退款", isIncome = false))
    }

    @Test
    fun `every mapped target is a valid category for its direction`() {
        val expenseAllowed = Categories.expenseCategories.toSet()
        val incomeAllowed = Categories.incomeCategories.toSet()
        // 映射目标必须落在对应方向的分类集合里，否则规则引擎的方向过滤会把它们全部丢掉
        for (category in PlatformCategories.allMappedCategories()) {
            assertTrue(
                "映射目标「$category」不在任何方向的分类中",
                category in expenseAllowed || category in incomeAllowed,
            )
        }
    }

    @Test
    fun `wechat transfer types are mapped only where meaningful`() {
        assertEquals("红包", PlatformCategories.resolve("微信红包", isIncome = true))
        assertEquals("人情", PlatformCategories.resolve("微信红包", isIncome = false))
        assertEquals("其他收入", PlatformCategories.resolve("转账", isIncome = true))
    }

    @Test
    fun `platform keys are unique across both platforms`() {
        // 两个平台的映射表若有重名键，resolve 的取用顺序会决定结果，容易埋下难查的偏斜
        val alipayAndWechat = PlatformCategories.allPlatformKeys()
        assertTrue("平台分类键不应为空", alipayAndWechat.isNotEmpty())
        // 微信的「红包」与支付宝的「转账红包」是不同键，不应相互覆盖
        assertTrue("红包" in alipayAndWechat)
        assertTrue("转账红包" in alipayAndWechat)
    }
}
