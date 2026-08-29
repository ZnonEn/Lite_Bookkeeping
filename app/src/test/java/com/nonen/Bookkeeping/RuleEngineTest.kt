package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.core.RuleEngine
import com.nonen.Bookkeeping.data.db.CategoryRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEngineTest {

    private fun rule(keyword: String, category: String, custom: Boolean = false) =
        CategoryRuleEntity(keyword = keyword, category = category, isCustom = custom)

    @Test
    fun `builtin keyword matches expense category`() {
        val rules = listOf(rule("美团", "餐饮"), rule("滴滴", "交通"))
        assertEquals("餐饮", RuleEngine.matchRules("美团-外卖订单", isIncome = false, rules))
        assertEquals("交通", RuleEngine.matchRules("滴滴出行", isIncome = false, rules))
    }

    @Test
    fun `custom rule has priority over builtin`() {
        val rules = listOf(rule("美团", "餐饮"), rule("美团", "购物", custom = true))
        assertEquals("购物", RuleEngine.matchRules("美团", isIncome = false, rules))
    }

    @Test
    fun `income text only matches income categories`() {
        val rules = listOf(rule("李四", "餐饮"), rule("工资", "工资"))
        // 收入方向：命中「工资 → 工资」
        assertEquals("工资", RuleEngine.matchRules("李四的工资到账", isIncome = true, rules))
        // 支出方向：收入类规则（工资→工资）被排除，但「李四 → 餐饮」仍命中
        assertEquals("餐饮", RuleEngine.matchRules("李四的工资到账", isIncome = false, rules))
        // 支出方向、无可用关键词时兜底「其他」
        assertEquals("其他", RuleEngine.matchRules("工资到账", isIncome = false, rules))
    }

    @Test
    fun `fallback when nothing matches`() {
        assertEquals("其他", RuleEngine.matchRules("某商店", isIncome = false, emptyList()))
        assertEquals("其他收入", RuleEngine.matchRules(null, isIncome = true, emptyList()))
        assertEquals("其他", RuleEngine.matchRules("", isIncome = false, emptyList()))
    }
}
