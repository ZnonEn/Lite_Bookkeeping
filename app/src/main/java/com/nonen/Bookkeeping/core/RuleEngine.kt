package com.nonen.Bookkeeping.core

import com.nonen.Bookkeeping.data.db.CategoryRuleDao
import com.nonen.Bookkeeping.data.db.CategoryRuleEntity
import com.nonen.Bookkeeping.data.db.MerchantCategoryDao
import com.nonen.Bookkeeping.data.db.MerchantCategoryEntity

/**
 * 分类依据快照：一次查库取全部依据，供批量 import 逐行复用。
 *
 * 逐行调用 categorize 会在每行都查一次规则表（导入 3000 行 = 3000 次全表读），
 * 故批量场景先用 [RuleEngine.loadRules] 取快照，再交给纯函数匹配。
 */
class CategorizationRules(
    val rules: List<CategoryRuleEntity>,
    val merchants: List<MerchantCategoryEntity>,
)

/**
 * 自动分类规则引擎。
 *
 * 命中即返回的优先链（越靠前越权威）：
 * 1. **商户记忆**——用户曾亲手把这个商户归到某分类，最具体也最可信；
 * 2. **自定义关键词规则**——用户在规则页显式写下的映射，同样属于用户意志；
 * 3. **平台自带分类**——支付宝「交易分类」/微信「交易类型」，覆盖面广但较泛，
 *    因此排在用户显式配置之后；
 * 4. **内置关键词规则**——预置的 500+ 条商户/场景词；
 * 5. 兜底「其他」。
 *
 * 每一层都用方向过滤（收入只落收入分类，反之亦然），避免「工资」被归进支出类。
 */
class RuleEngine(
    private val ruleDao: CategoryRuleDao,
    private val merchantDao: MerchantCategoryDao,
) {

    /** 取一次分类依据快照（批量导入等逐行场景务必复用） */
    suspend fun loadRules(): CategorizationRules =
        CategorizationRules(rules = ruleDao.getAll(), merchants = merchantDao.getAll())

    /** 单次分类（内部查库；批量场景请改用 [loadRules] + [classify]） */
    suspend fun categorize(
        text: String?,
        isIncome: Boolean,
        merchant: String? = null,
        platformCategory: String? = null,
    ): String = classify(
        text = text,
        isIncome = isIncome,
        rules = loadRules(),
        merchant = merchant,
        platformCategory = platformCategory,
    )

    companion object {

        /**
         * 分类优先链的纯函数实现（无 IO，便于单测覆盖各层与层间覆盖关系）。
         *
         * @param text 用于关键词匹配的文本（商户 + 备注）
         * @param merchant 原始商户名，用于商户记忆匹配（内部会归一化）
         * @param platformCategory 平台自带分类值（支付宝「交易分类」/ 微信「交易类型」）
         */
        fun classify(
            text: String?,
            isIncome: Boolean,
            rules: CategorizationRules,
            merchant: String? = null,
            platformCategory: String? = null,
        ): String {
            val fallback = if (isIncome) Categories.OTHER_INCOME else Categories.OTHER_EXPENSE
            val allowed = PlatformCategories.allowedCategories(isIncome)

            // 1. 商户记忆
            val merchantKey = MerchantKey.normalize(merchant)
            if (merchantKey != null) {
                val hit = rules.merchants.firstOrNull {
                    it.isIncome == isIncome &&
                        it.category in allowed &&
                        MerchantKey.matches(it.merchantKey, merchantKey)
                }
                if (hit != null) return hit.category
            }

            val body = text.orEmpty()
            val custom = rules.rules.filter { it.isCustom && it.category in allowed }
            val builtin = rules.rules.filter { !it.isCustom && it.category in allowed }

            // 2. 自定义关键词（用户显式配置，先于平台分类）
            if (body.isNotBlank()) {
                custom.forEach { if (body.contains(it.keyword, ignoreCase = true)) return it.category }
            }

            // 3. 平台自带分类
            PlatformCategories.resolve(platformCategory, isIncome)
                ?.takeIf { it in allowed }
                ?.let { return it }

            // 4. 内置关键词
            if (body.isNotBlank()) {
                builtin.forEach { if (body.contains(it.keyword, ignoreCase = true)) return it.category }
            }

            return fallback
        }

        /**
         * 仅关键词匹配（自定义优先于内置），不含商户记忆与平台分类。
         * 保留给「重新分类」等只需要词表语义的场景与既有单测。
         */
        fun matchRules(
            text: String?,
            isIncome: Boolean,
            rules: List<CategoryRuleEntity>,
        ): String = classify(
            text = text,
            isIncome = isIncome,
            rules = CategorizationRules(rules = rules, merchants = emptyList()),
        )
    }
}
