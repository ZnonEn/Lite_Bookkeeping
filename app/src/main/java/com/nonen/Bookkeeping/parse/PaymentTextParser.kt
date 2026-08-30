package com.nonen.Bookkeeping.parse

/** 从通知或界面文本中解析出的一笔交易。 */
data class ParsedPayment(
    val amount: Double,
    val isIncome: Boolean,
    val counterparty: String? = null,
    val description: String? = null,
    /** 预留：真实交易时间；当前成功页不含时间字段，按当前时间入账 */
    val timestamp: Long? = null,
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
 * 支付成功页专用解析（微信/支付宝结果页版式固定）。
 *
 * 策略：**只抓取支付成功的界面，其余页面一律不检测**。
 * 聊天列表、账单详情、红包详情、付款输入页等所有其他页面的消息预览和数字
 * 一律忽略——从根源上消除误记。窗口通道（无障碍/OCR）只在页面出现
 * 「支付成功/付款成功/转账成功」等标记时才记录；通知通道由 PaymentTextParser 负责。
 */
object WindowCaptureAnalyzer {

    private val STANDALONE_AMOUNT = Regex("""^[-–—]?[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)$""")
    // 长标签放前面，避免「收款方全称」被「收款方」截断出「全称」；(?!式) 防止「付款方式」被当标签
    private val COUNTERPARTY = Regex(
        """(?:收款方全称|收款方名称|对方全称|对方名称|付款方全称|商户名称|收款方|付款方|对方账户|商户)(?!式)[:：\s]*(\S{1,25})""",
    )

    private val SUCCESS_PAGE_EXPENSE_MARKERS = setOf("支付成功", "付款成功", "转账成功")
    private val SUCCESS_PAGE_INCOME_MARKERS = setOf("收款成功", "已收钱")
    private val SUCCESS_PAGE_NOISE = setOf("完成", "返回", "付款方式", "收款方式", "本店优惠", "支付有福利")

    fun analyze(texts: List<String>): ParsedPayment? = analyzeDetailed(texts).first

    /** 解析结果 + 失败原因（test 分支的调试面板会展示原因） */
    fun analyzeDetailed(texts: List<String>): Pair<ParsedPayment?, String> {
        if (texts.isEmpty()) return null to "页面无文本"
        parseSuccessPage(texts)?.let { return it to "支付成功页专用提取" }
        return null to "非支付成功页，不检测"
    }

    /**
     * 支付成功页专用提取：
     * 定位「支付成功/付款成功/转账成功」等标记节点，取其下方第一个独立金额节点作为实付金额——
     * 页面更下方的优惠券、积分、推荐位数字全部跳过；金额之后第一段纯文本作为商户。
     * 无障碍与 OCR 抓到的都是自上而下的有序文本，顺序即版式。
     */
    private fun parseSuccessPage(texts: List<String>): ParsedPayment? {
        val markerIdx = texts.indexOfFirst { t ->
            val v = t.trim()
            v in SUCCESS_PAGE_EXPENSE_MARKERS || v in SUCCESS_PAGE_INCOME_MARKERS
        }
        if (markerIdx < 0) return null
        val isIncome = texts[markerIdx].trim() in SUCCESS_PAGE_INCOME_MARKERS

        var amountIdx = -1
        var amount = 0.0
        for (i in markerIdx + 1 until texts.size) {
            val m = STANDALONE_AMOUNT.find(texts[i].trim().replace(",", "")) ?: continue
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (v <= 0.0 || v > 1_000_000.0) continue
            amount = v
            amountIdx = i
            break
        }
        if (amountIdx < 0) return null

        val counterparty = texts.drop(amountIdx + 1).firstNotNullOfOrNull { raw ->
            val v0 = raw.trim()
            if (v0.isEmpty() || v0.startsWith("¥") || v0.startsWith("￥")) return@firstNotNullOfOrNull null
            // 「收款方：XX」这类带标签的先剥出名字，再统一做合理性校验
            val v = COUNTERPARTY.find(v0)?.groupValues?.get(1)?.trim() ?: v0
            val plausible = v.isNotEmpty() && !v.startsWith("¥") && !v.startsWith("￥") &&
                v.none(Char::isDigit) && v.length in 2..25 &&
                v !in SUCCESS_PAGE_NOISE &&
                SUCCESS_PAGE_EXPENSE_MARKERS.none { v.contains(it) } &&
                listOf("支付", "付款", "收款", "成功", "余额", "零钱", "说明", "优惠", "福利", "积分", "原价")
                    .none { w -> v.contains(w) }
            v.takeIf { plausible }
        }
        return ParsedPayment(amount = amount, isIncome = isIncome, counterparty = counterparty)
    }
}
