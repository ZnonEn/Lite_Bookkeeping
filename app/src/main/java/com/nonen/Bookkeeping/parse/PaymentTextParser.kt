package com.nonen.Bookkeeping.parse

/** 从通知或界面文本中解析出的一笔交易。 */
data class ParsedPayment(
    val amount: Double,
    val isIncome: Boolean,
    val counterparty: String? = null,
    val description: String? = null,
)

/**
 * 支付通知文本解析：支持微信/支付宝常见的付款、收款、退款通知格式。
 * 解析不出明确方向或金额时返回 null（宁可不记录，也不记错）。
 */
object PaymentTextParser {

    private val YEN_AMOUNT = Regex("""[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val YUAN_AMOUNT = Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*元""")

    private val INCOME_WORDS = listOf("收款", "到账", "收钱", "收到", "转入", "退款", "红包", "返现")
    private val EXPENSE_WORDS = listOf("支付成功", "付款成功", "已支付", "已付款", "支付", "付款", "消费", "扣款", "转出")

    private val COUNTERPARTY_PATTERNS = listOf(
        Regex("""向(.{1,20}?)付款"""),
        Regex("""向(.{1,20}?)(?:转账|支付)"""),
        Regex("""来自(.{1,20}?)[的\s，,]"""),
        Regex("""(?:收款方|付款方|对方账户|对方名称|商户)[:：\s]*(\S{1,25})"""),
    )

    private val BAD_COUNTERPARTY_WORDS = listOf("支付", "付款", "收款", "成功", "余额", "零钱", "转账", "红包", "说明", "账单")

    fun parse(rawText: String): ParsedPayment? {
        val text = rawText.replace('\n', ' ').trim()
        if (text.length < 4) return null
        val amount = extractAmount(text) ?: return null
        if (amount <= 0.0 || amount > 1_000_000.0) return null
        val incomeScore = INCOME_WORDS.count { text.contains(it) }
        val expenseScore = EXPENSE_WORDS.count { text.contains(it) }
        if (incomeScore == expenseScore) return null // 无关键词或方向不明
        val isIncome = incomeScore > expenseScore
        val counterparty = COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.let { cleanCounterparty(it) }
        }
        return ParsedPayment(
            amount = amount,
            isIncome = isIncome,
            counterparty = counterparty,
            description = text.take(80),
        )
    }

    fun extractAmount(text: String): Double? {
        (YEN_AMOUNT.find(text) ?: YUAN_AMOUNT.find(text))?.let {
            return it.groupValues[1].toDoubleOrNull()?.takeIf { v -> v > 0.0 }
        }
        return null
    }

    private fun cleanCounterparty(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || v.any { it.isDigit() }) return null
        if (BAD_COUNTERPARTY_WORDS.any { v.contains(it) }) return null
        return v
    }
}

/**
 * 微信/支付宝支付完成页与账单详情页的启发式抓取。
 * 方向判定：金额标签锚点（付款金额/退款金额…）优先，其次是成功提示词——
 * 支付完成页通常没有「付款金额」标签，只有「支付成功/付款成功 + ¥金额」。
 * 必须同时具备：可判定的方向 + 金额，否则放弃（宁可不记录，也不记错）。
 */
object WindowCaptureAnalyzer {

    private val AMOUNT_WITH_LABEL =
        Regex("""(?:付款金额|支付金额|退款金额|收款金额|入账金额|转账金额)[^\d¥￥]{0,6}[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val STANDALONE_AMOUNT = Regex("""^[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)$""")
    private val AMOUNT_ANYWHERE = Regex("""[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val COUNTERPARTY = Regex("""(?:收款方|付款方|对方账户|对方名称|商户)[:：\s]*(\S{1,25})""")

    private val EXPENSE_ANCHORS = listOf("付款金额", "支付金额", "转账金额")
    private val INCOME_ANCHORS = listOf("退款金额", "收款金额", "入账金额")
    private val EXPENSE_SUCCESS = listOf("支付成功", "付款成功", "已支付", "已付款", "已转账", "转账成功", "扣款成功", "付款时间")
    private val INCOME_SUCCESS = listOf("已收钱", "已收款", "收款成功", "已存入", "已到账", "到账成功", "退款成功", "收款时间")
    // 聊天内转账发出后，成功页/聊天气泡上显示「待XX确认收款」——钱已付出、对方未领
    private val EXPENSE_PATTERNS = listOf(Regex("""待.{0,8}?确认收款"""))

    fun analyze(texts: List<String>): ParsedPayment? {
        if (texts.isEmpty()) return null
        val joined = texts.joinToString(" ") { it.trim() }.replace('\n', ' ')
        val hasExpenseAnchor = EXPENSE_ANCHORS.any { joined.contains(it) }
        val hasIncomeAnchor = INCOME_ANCHORS.any { joined.contains(it) }
        if (hasExpenseAnchor && hasIncomeAnchor) return null // 同时出现，方向不明

        val hasExpenseWord = EXPENSE_SUCCESS.any { joined.contains(it) } ||
            EXPENSE_PATTERNS.any { it.containsMatchIn(joined) }
        val hasIncomeWord = INCOME_SUCCESS.any { joined.contains(it) }
        // 既无金额标签锚点、也无成功提示词的页面一律不抓，避免在普通界面误记
        if (!hasExpenseAnchor && !hasIncomeAnchor && hasExpenseWord == hasIncomeWord) return null

        val amount = texts.firstNotNullOfOrNull { AMOUNT_WITH_LABEL.find(it) }
            ?.groupValues?.get(1)?.toDoubleOrNull()
            ?: AMOUNT_WITH_LABEL.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: texts.firstNotNullOfOrNull { STANDALONE_AMOUNT.find(it.trim()) }
                ?.groupValues?.get(1)?.toDoubleOrNull()
            ?: AMOUNT_ANYWHERE.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        if (amount <= 0.0 || amount > 1_000_000.0) return null

        val counterparty = COUNTERPARTY.find(joined)?.groupValues?.get(1)?.let { raw ->
            val v = raw.trim()
            v.takeIf {
                it.isNotEmpty() && !it.any(Char::isDigit) && it.length in 2..25 &&
                    listOf("支付", "付款", "收款", "成功", "余额", "零钱", "说明").none { w -> it.contains(w) }
            }
        }
        return ParsedPayment(
            amount = amount,
            isIncome = when {
                hasIncomeAnchor -> true
                hasExpenseAnchor -> false
                else -> hasIncomeWord
            },
            counterparty = counterparty,
            description = joined.take(80),
        )
    }
}
