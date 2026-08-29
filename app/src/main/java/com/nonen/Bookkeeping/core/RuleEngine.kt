package com.nonen.Bookkeeping.core

import com.nonen.Bookkeeping.data.db.CategoryRuleDao
import com.nonen.Bookkeeping.data.db.CategoryRuleEntity

/**
 * 自动分类规则引擎：优先级匹配，命中即返回。
 * 自定义规则（用户添加 / 编辑学习）优先于内置规则。
 */
class RuleEngine(private val ruleDao: CategoryRuleDao) {

    suspend fun categorize(text: String?, isIncome: Boolean): String =
        matchRules(text, isIncome, ruleDao.getAll())

    companion object {

        fun matchRules(
            text: String?,
            isIncome: Boolean,
            rules: List<CategoryRuleEntity>,
        ): String {
            val fallback = if (isIncome) Categories.OTHER_INCOME else Categories.OTHER_EXPENSE
            if (text.isNullOrBlank()) return fallback
            // 收入文本只在收入分类里匹配，反之亦然，避免「工资」被误归入支出类
            val allowed = if (isIncome) Categories.incomeCategories.toSet() else Categories.expenseCategories.toSet()
            val custom = rules.filter { it.isCustom && it.category in allowed }
            val builtin = rules.filter { !it.isCustom && it.category in allowed }
            for (rule in custom) {
                if (text.contains(rule.keyword, ignoreCase = true)) return rule.category
            }
            for (rule in builtin) {
                if (text.contains(rule.keyword, ignoreCase = true)) return rule.category
            }
            return fallback
        }
    }
}
