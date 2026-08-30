package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.core.Categories
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

    @Test
    fun `default rules have no duplicate keyword within same direction`() {
        val rules = Categories.defaultRules()
        val expense = rules.filter { it.second in Categories.expenseCategories }.map { it.first }
        val income = rules.filter { it.second in Categories.incomeCategories }.map { it.first }
        assertEquals("支出方向存在重复关键词：${expense.groupBy { it }.filterValues { it.size > 1 }.keys}", expense.size, expense.toSet().size)
        assertEquals("收入方向存在重复关键词：${income.groupBy { it }.filterValues { it.size > 1 }.keys}", income.size, income.toSet().size)
    }

    @Test
    fun `default rules categorize common merchants with priority order`() {
        val rules = Categories.defaultRules().map { (keyword, category) ->
            CategoryRuleEntity(keyword = keyword, category = category, isCustom = false)
        }
        fun cat(text: String, income: Boolean = false) = RuleEngine.matchRules(text, income, rules)

        // 多义词先行：更具体的组合不被热门泛词抢走
        assertEquals("交通", cat("美团单车月卡"))
        assertEquals("餐饮", cat("美团-外卖订单"))
        assertEquals("医疗", cat("京东健康大药房"))
        assertEquals("购物", cat("京东自营超市"))
        // 重新归类的映射
        assertEquals("金融", cat("中国人寿保险缴费"))
        assertEquals("通讯", cat("移动话费充值"))
        assertEquals("旅行", cat("如家酒店"))
        // 具体词先于泛词
        assertEquals("宠物", cat("宠物医院看病"))
        assertEquals("医疗", cat("县人民医院"))
        assertEquals("娱乐", cat("游戏充值648"))
        assertEquals("交通", cat("违章罚款200"))
        assertEquals("金融", cat("银行罚款滞纳金"))
        assertEquals("服饰美容", cat("优衣库换季服装"))
        assertEquals("人情", cat("婚礼随礼"))
        // 收入方向
        assertEquals("工资", cat("XX公司工资代发", income = true))
        assertEquals("兼职", cat("劳务报酬结算", income = true))
        assertEquals("理财", cat("余额宝收益发放", income = true))
        assertEquals("红包", cat("收到微信红包", income = true))
    }
}
