package com.nonen.Bookkeeping.core

import com.nonen.Bookkeeping.data.db.CategoryRuleEntity
import com.nonen.Bookkeeping.data.db.MerchantCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分类优先链的层间关系测试。
 *
 * 链：商户记忆 > 自定义关键词 > 平台自带分类 > 内置关键词 > 兜底。
 * 这里只测纯函数 [RuleEngine.classify]（无 Android / Room 依赖）。
 */
class ClassifyChainTest {

    private fun builtin(keyword: String, category: String) =
        CategoryRuleEntity(keyword = keyword, category = category, isCustom = false)

    private fun custom(keyword: String, category: String) =
        CategoryRuleEntity(keyword = keyword, category = category, isCustom = true)

    private fun memory(merchant: String, category: String, isIncome: Boolean = false) =
        MerchantCategoryEntity(
            merchantKey = MerchantKey.normalize(merchant)!!,
            category = category,
            isIncome = isIncome,
        )

    private fun rules(
        rules: List<CategoryRuleEntity> = emptyList(),
        merchants: List<MerchantCategoryEntity> = emptyList(),
    ) = CategorizationRules(rules = rules, merchants = merchants)

    @Test
    fun `merchant memory outranks every keyword rule`() {
        val r = rules(
            rules = listOf(builtin("肯德基", "餐饮"), custom("肯德基", "娱乐")),
            merchants = listOf(memory("肯德基", "购物")),
        )
        // 用户曾把这个商户归到购物 → 即便内置与自定义关键词都指向别处，仍以记忆为准
        assertEquals("购物", RuleEngine.classify("肯德基", false, r, merchant = "肯德基"))
    }

    @Test
    fun `merchant memory matches despite branch suffix differences`() {
        val r = rules(merchants = listOf(memory("肯德基（XX路店）", "购物")))
        assertEquals(
            "购物",
            RuleEngine.classify("", false, r, merchant = "肯德基(浦东店)"),
        )
    }

    @Test
    fun `merchant memory is direction aware`() {
        val r = rules(
            merchants = listOf(
                memory("张三", "人情", isIncome = false),
                memory("张三", "其他收入", isIncome = true),
            ),
        )
        assertEquals("人情", RuleEngine.classify(null, false, r, merchant = "张三"))
        assertEquals("其他收入", RuleEngine.classify(null, true, r, merchant = "张三"))
    }

    @Test
    fun `memory for opposite direction is ignored`() {
        // 只学过支出方向的记忆，不应影响收入方向的判断
        val r = rules(merchants = listOf(memory("某店", "餐饮", isIncome = false)))
        assertEquals("其他收入", RuleEngine.classify(null, true, r, merchant = "某店"))
    }

    @Test
    fun `custom keyword outranks platform category and builtin`() {
        val r = rules(rules = listOf(builtin("美团", "餐饮"), custom("美团", "购物")))
        // 平台分类说是餐饮，但用户自定义规则指向购物 → 用户的意志优先
        assertEquals(
            "购物",
            RuleEngine.classify("美团订单", false, r, platformCategory = "餐饮美食"),
        )
    }

    @Test
    fun `platform category outranks builtin keyword`() {
        val r = rules(rules = listOf(builtin("美宜佳", "购物")))
        // 平台「酒店旅游」比内置词更权威
        assertEquals(
            "旅行",
            RuleEngine.classify("美宜佳", false, r, platformCategory = "酒店旅游"),
        )
    }

    @Test
    fun `platform category ignored when it has no mapping`() {
        val r = rules(rules = listOf(builtin("美团", "餐饮")))
        // 「商户消费」不映射 → 继续走关键词，命中内置「美团」
        assertEquals(
            "餐饮",
            RuleEngine.classify("美团订单", false, r, platformCategory = "商户消费"),
        )
    }

    @Test
    fun `builtin keyword used when nothing else applies`() {
        val r = rules(rules = listOf(builtin("滴滴", "交通")))
        assertEquals("交通", RuleEngine.classify("滴滴出行", false, r))
    }

    @Test
    fun `falls back when no evidence at all`() {
        val empty = rules()
        assertEquals("其他", RuleEngine.classify("某小店", false, empty))
        assertEquals("其他收入", RuleEngine.classify("某笔进账", true, empty))
        assertEquals("其他", RuleEngine.classify(null, false, empty))
    }

    @Test
    fun `rules for the opposite direction never apply`() {
        // 收入类分类的关键词不该出现在支出方向的判断里
        val r = rules(rules = listOf(builtin("工资", "工资"), builtin("红包", "红包")))
        assertEquals("其他", RuleEngine.classify("工资到账", false, r))
        assertEquals("工资", RuleEngine.classify("工资到账", true, r))
    }

    @Test
    fun `merchant memory is ignored when its category is invalid for the direction`() {
        // 防御性：即便库里存了方向不匹配的记忆（历史数据/手工改动），也不能采纳
        val r = rules(merchants = listOf(memory("某店", "工资", isIncome = false)))
        assertEquals("其他", RuleEngine.classify(null, false, r, merchant = "某店"))
    }

    @Test
    fun `blank merchant does not crash or match`() {
        val r = rules(merchants = listOf(memory("某店", "购物")))
        assertEquals("其他", RuleEngine.classify("", false, r, merchant = null))
        assertEquals("其他", RuleEngine.classify("", false, r, merchant = "   "))
    }
}
