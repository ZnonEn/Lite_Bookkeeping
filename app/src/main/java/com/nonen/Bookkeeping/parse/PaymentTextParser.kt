package com.nonen.Bookkeeping.parse

import java.time.LocalDateTime
import java.time.ZoneId

/** 从通知或界面文本中解析出的一笔交易。 */
data class ParsedPayment(
    val amount: Double,
    val isIncome: Boolean,
    val counterparty: String? = null,
    val description: String? = null,
    /** 账单详情类页面解析出的真实交易时间；成功页/通知没有时间字段，按当前时间入账 */
    val timestamp: Long? = null,
)

/**
 * 支付通知文本解析：支持微信/支付宝常见的付款、收款、退款通知格式。
 * 解析不出明确方向或金额时返回 null（宁可不弹卡片，也不弹错的）。
 */
object PaymentTextParser {

    private val YEN_AMOUNT = Regex("""[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val YUAN_AMOUNT = Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*元""")

    // 通知文本里自带方向的金额标签（版式参考 AutoRule 规则库），优先于关键词打分
    private val LABELED_AMOUNTS = listOf(
        Regex("""付款金额[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)""") to false,
        Regex("""扣款金额[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)""") to false,
        Regex("""收款金额[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)""") to true,
        Regex("""退款金额[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)""") to true,
    )

    private val INCOME_WORDS = listOf("收款", "到账", "收钱", "收到", "转入", "退款", "红包", "返现", "入账")
    private val EXPENSE_WORDS = listOf("支付成功", "付款成功", "已支付", "已付款", "支付", "付款", "消费", "扣款", "转出")

    private val COUNTERPARTY_PATTERNS = listOf(
        Regex("""向(.{1,20}?)付款"""),
        Regex("""向(.{1,20}?)(?:转账|支付)"""),
        Regex("""来自(.{1,20}?)[的\s，,]"""),
        Regex("""(?:收款方|付款方|对方账户|对方名称|商户|交易对象|付款人)[:：\s]*(\S{1,25})"""),
    )

    private val BAD_COUNTERPARTY_WORDS = listOf("支付", "付款", "收款", "成功", "余额", "零钱", "转账", "红包", "说明", "账单")

    fun parse(rawText: String): ParsedPayment? {
        val text = rawText.replace('\n', ' ').trim()
        if (text.length < 4) return null
        // 带方向标签的金额最可靠
        for ((regex, isIncome) in LABELED_AMOUNTS) {
            val v = regex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            if (v <= 0.0 || v > 1_000_000.0) continue
            return ParsedPayment(
                amount = v,
                isIncome = isIncome,
                counterparty = extractCounterparty(text),
                description = text.take(80),
            )
        }
        val amount = extractAmount(text) ?: return null
        if (amount <= 0.0 || amount > 1_000_000.0) return null
        val incomeScore = INCOME_WORDS.count { text.contains(it) }
        val expenseScore = EXPENSE_WORDS.count { text.contains(it) }
        if (incomeScore == expenseScore) return null // 无关键词或方向不明
        return ParsedPayment(
            amount = amount,
            isIncome = incomeScore > expenseScore,
            counterparty = extractCounterparty(text),
            description = text.take(80),
        )
    }

    fun extractAmount(text: String): Double? {
        (YEN_AMOUNT.find(text) ?: YUAN_AMOUNT.find(text))?.let {
            return it.groupValues[1].toDoubleOrNull()?.takeIf { v -> v > 0.0 }
        }
        return null
    }

    private fun extractCounterparty(text: String): String? =
        COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.let { cleanCounterparty(it) }
        }

    private fun cleanCounterparty(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || v.any { it.isDigit() }) return null
        if (BAD_COUNTERPARTY_WORDS.any { v.contains(it) }) return null
        return v
    }
}
