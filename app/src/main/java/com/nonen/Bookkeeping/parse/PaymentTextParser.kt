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

/**
 * 窗口文本专版解析（专版专杀，规则参考 Tally 项目与 AutoRule 规则库）：
 * 每种已知页面一套提取规则，不认识的页面一律不弹卡片。
 * —— 支付成功页（固定版式：标记 → 金额 → 收款方）
 * —— 微信转账已收款详情 / 红包领取详情（「已存入零钱」特征）
 * —— 微信账单详情（交易单号/商户单号特征，带符号金额）
 * —— 支付宝账单详情（支出/收入X元、带符号金额、收款方全称标签）
 * 半自动确认模式下，误识别的代价只是多弹一张卡片、由用户把关，
 * 因此覆盖面可以比静默入账时代放开。
 */
object WindowCaptureAnalyzer {

    private val STANDALONE_AMOUNT = Regex("""^[-–—]?[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)$""")
    // 长标签放前面，避免「收款方全称」被「收款方」截断出「全称」；(?!式) 防止「付款方式」被当标签
    private val COUNTERPARTY_LABEL = Regex(
        """(?:收款方全称|收款方名称|对方全称|对方名称|付款方全称|商户名称|收款方|付款方|对方账户|商户)(?!式)[:：\s]*(\S{1,25})""",
    )

    private val SUCCESS_PAGE_EXPENSE_MARKERS = setOf("支付成功", "付款成功", "转账成功")
    private val SUCCESS_PAGE_INCOME_MARKERS = setOf("收款成功", "已收钱")
    private val SUCCESS_PAGE_NOISE = setOf("完成", "返回", "付款方式", "收款方式", "本店优惠", "支付有福利")
    private val COUNTERPARTY_BAD_WORDS =
        listOf("支付", "付款", "收款", "成功", "余额", "零钱", "说明", "优惠", "福利", "积分", "原价")

    private val ALIPAY_DETAIL_MARKERS = setOf("账单详情", "商家订单号", "订单号", "交易详情", "交易订单号")
    private val WECHAT_DETAIL_MARKERS = setOf("交易单号", "商户单号")
    private val DIRECTION_AMOUNT = Regex("""^(支出|收入)\s*[¥￥]?([0-9]+(?:\.[0-9]{1,2})?)元?$""")
    private val SIGNED_YUAN = Regex("""^([+-])\s*[¥￥]?([0-9]+(?:\.[0-9]{1,2})?)元?$""")
    private val WECHAT_SIGNED_AMOUNT = Regex("""^[-+]?[0-9]+\.[0-9]{2}$""")
    private val PLAIN_NUMBER = Regex("""^[-+]?[0-9]+(\.[0-9]{1,2})?$""")
    private val TIME_LABELS = setOf("支付时间", "交易时间", "退款时间", "创建时间", "收款时间", "转账时间", "付款时间", "下单时间")
    private val DATETIME_DASH = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val DATETIME_CN = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})(?::(\d{2}))?""")

    fun analyze(texts: List<String>): ParsedPayment? = analyzeDetailed(texts).first

    /** 解析结果 + 命中的页面类型 / 失败原因（调试面板展示） */
    fun analyzeDetailed(texts: List<String>): Pair<ParsedPayment?, String> {
        if (texts.isEmpty()) return null to "页面无文本"
        parseSuccessPage(texts)?.let { return it to "支付成功页" }
        parseWeChatTransferReceived(texts)?.let { return it to "微信转账已收款页" }
        parseRedPacketIncome(texts)?.let { return it to "微信红包详情页" }
        parseWeChatBillDetail(texts)?.let { return it to "微信账单详情页" }
        parseAlipayBillDetail(texts)?.let { return it to "支付宝账单详情页" }
        return null to "未匹配到已知支付页面"
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
            val v = COUNTERPARTY_LABEL.find(v0)?.groupValues?.get(1)?.trim() ?: v0
            val plausible = v.isNotEmpty() && !v.startsWith("¥") && !v.startsWith("￥") &&
                v.none(Char::isDigit) && v.length in 2..25 &&
                v !in SUCCESS_PAGE_NOISE &&
                SUCCESS_PAGE_EXPENSE_MARKERS.none { v.contains(it) } &&
                COUNTERPARTY_BAD_WORDS.none { w -> v.contains(w) }
            v.takeIf { plausible }
        }
        return ParsedPayment(amount = amount, isIncome = isIncome, counterparty = counterparty)
    }

    /**
     * 微信转账「你已收款，资金已存入零钱」详情页（Tally 规则）。
     * 金额是首个 ¥ 开头节点；转账说明/收款备注在标签之后；收款时间解析真实入账时间。
     */
    private fun parseWeChatTransferReceived(texts: List<String>): ParsedPayment? {
        if (!texts.any { it.contains("你已收款") } || !texts.any { it.contains("已存入零钱") }) return null
        val t = texts.map { it.trim() }
        val amountIdx = t.indexOfFirst { v -> v.startsWith("¥") || v.startsWith("￥") }
        if (amountIdx < 0) return null
        val amount = t[amountIdx].removePrefix("¥").removePrefix("￥").replace(",", "").toDoubleOrNull()
        if (amount == null || amount <= 0.0 || amount > 1_000_000.0) return null
        var note: String? = null
        for (i in t.indices) {
            if (t[i] == "转账说明" || t[i] == "收款备注") {
                note = nextContent(t, i)
                break
            }
        }
        return ParsedPayment(
            amount = amount,
            isIncome = true,
            counterparty = null,
            description = note?.takeIf { it.isNotBlank() } ?: "微信转账收款",
            timestamp = extractTradeTime(texts),
        )
    }

    /**
     * 微信红包领取详情页（Tally 规则）：「已存入零钱」特征；
     * 金额取独立「元」节点的前一个数字节点；「xx的红包」作为对方。
     */
    private fun parseRedPacketIncome(texts: List<String>): ParsedPayment? {
        if (texts.none { it.contains("已存入零钱") }) return null
        val t = texts.map { it.trim() }
        val yuanIdx = t.indexOfFirst { it == "元" }
        if (yuanIdx <= 0) return null
        val amount = t[yuanIdx - 1].toDoubleOrNull()
        if (amount == null || amount <= 0.0 || amount > 1_000_000.0) return null
        val name = t.firstOrNull { it.endsWith("的红包") }
        return ParsedPayment(
            amount = amount,
            isIncome = true,
            counterparty = name?.removeSuffix("的红包")?.takeIf { it.isNotBlank() },
            description = name ?: "微信红包",
        )
    }

    /**
     * 微信「账单详情」页（Tally 规则）：交易单号/商户单号特征；
     * 金额是首个「±X.XX」格式节点（负=支出，正=收入）；
     * 备注层层兜底：金额正下方 → 商户全称/收款方 → 金额正上方。
     */
    private fun parseWeChatBillDetail(texts: List<String>): ParsedPayment? {
        if (texts.none { it.trim() in WECHAT_DETAIL_MARKERS }) return null
        val t = texts.map { it.trim().replace(",", "") }

        val amountIdx = t.indexOfFirst { v -> WECHAT_SIGNED_AMOUNT.matches(v) }
        if (amountIdx < 0) return null
        val raw = t[amountIdx]
        val amount = raw.removePrefix("+").removePrefix("-").toDouble()
        if (amount <= 0.0 || amount > 1_000_000.0) return null
        val isIncome = !raw.startsWith("-")

        // 金额正下方第一个实质文本（通常是商户名），上方第一个非空文本兜底
        val directBelow = t.drop(amountIdx + 1).firstOrNull { v ->
            v.isNotEmpty() && listOf("原价", "优惠", "¥", "￥").none { v.contains(it) }
        }?.takeIf { !it.contains("交易详情") && !it.contains("账单详情") }
        val fallbackAbove = t.take(amountIdx).lastOrNull { it.isNotEmpty() }
            ?.takeIf { !it.contains("支出") && !it.contains("收入") && !it.contains("交易详情") && !it.contains("账单详情") }

        var product: String? = null
        var merchantLabel: String? = null
        for (i in t.indices) {
            when (t[i]) {
                "商品", "商品名称" -> if (product == null) product = nextContent(t, i)
                "商户全称", "收款方" -> if (merchantLabel == null) merchantLabel = nextContent(t, i)
            }
        }
        product = product?.takeIf { !it.startsWith("商户单号") && !it.startsWith("交易单号") }

        return ParsedPayment(
            amount = amount,
            isIncome = isIncome,
            counterparty = directBelow ?: merchantLabel ?: fallbackAbove ?: "微信账单",
            description = product,
            timestamp = extractTradeTime(texts),
        )
    }

    /**
     * 支付宝「账单详情」页（Tally 规则）：账单详情/订单号特征；
     * 金额优先「支出/收入X元」，其次带符号「±X」，最后带小数点的纯数字（跳过「0」「1」数量字段）；
     * 商户取「收款方全称」标签之后，说明取「商品说明/交易说明/交易详情」之后，兜底为金额正上方文本。
     */
    private fun parseAlipayBillDetail(texts: List<String>): ParsedPayment? {
        if (texts.none { it.trim() in ALIPAY_DETAIL_MARKERS }) return null
        val t = texts.map { it.trim().replace(",", "") }

        var amountIdx = -1
        var amount = -1.0
        var isIncome = false
        loop@ for (i in t.indices) {
            val m = DIRECTION_AMOUNT.find(t[i]) ?: continue
            amount = m.groupValues[2].toDouble()
            isIncome = m.groupValues[1] == "收入"
            amountIdx = i
            break@loop
        }
        if (amountIdx < 0) loop@ for (i in t.indices) {
            val m = SIGNED_YUAN.find(t[i]) ?: continue
            amount = m.groupValues[2].toDouble()
            isIncome = m.groupValues[1] == "+"
            amountIdx = i
            break@loop
        }
        if (amountIdx < 0) loop@ for (i in t.indices) {
            val v = t[i]
            if (!PLAIN_NUMBER.matches(v)) continue
            if (!v.startsWith("+") && !v.startsWith("-") && !v.contains(".")) continue
            if (v == "0" || v == "1") continue
            val parsed = v.removePrefix("+").removePrefix("-").toDoubleOrNull() ?: continue
            if (parsed <= 0.0 || parsed > 1_000_000.0) continue
            amount = parsed
            isIncome = v.startsWith("+")
            amountIdx = i
            break@loop
        }
        if (amountIdx < 0 || amount <= 0.0) return null

        var merchant: String? = null
        var note: String? = null
        for (i in t.indices) {
            when (t[i]) {
                "收款方全称" -> if (merchant == null) merchant = nextContent(t, i)
                "商品说明", "交易说明", "交易详情", "管理自动扣款" -> if (note == null) note = nextContent(t, i)
            }
        }
        // 兜底：金额正上方的非空文本（通常是店名），屏蔽淘宝图片乱码
        val fallback = t.take(amountIdx).lastOrNull { v ->
            v.isNotEmpty() && !v.contains("账单详情") && !v.contains("返回") &&
                !v.contains("!!") && !v.startsWith("O1CN")
        }
        val counterparty = (merchant ?: fallback)
            ?.takeIf { c -> c.none(Char::isDigit) && c.length in 2..25 }
            ?: "支付宝账单"
        return ParsedPayment(
            amount = amount,
            isIncome = isIncome,
            counterparty = counterparty,
            description = note?.takeIf { it.isNotBlank() },
            timestamp = extractTradeTime(texts),
        )
    }

    /** 标签（支付时间/交易时间…）之后的日期时间文本 → epoch ms；支持 2026-08-30 14:23:20 与 2026年8月30日 14:23 两种版式 */
    private fun extractTradeTime(texts: List<String>): Long? {
        for (i in texts.indices) {
            if (texts[i].trim() !in TIME_LABELS) continue
            var seen = 0
            for (j in i + 1 until texts.size) {
                val v = texts[j].trim()
                if (v.isEmpty()) continue
                seen++
                if (seen > 6) break
                DATETIME_CN.find(v)?.let { return parseDateTime(it) }
                DATETIME_DASH.find(v)?.let { return parseDateTime(it) }
            }
        }
        return null
    }

    private fun parseDateTime(m: MatchResult): Long? = runCatching {
        val g = m.groupValues
        LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    /** 标签节点的下一个实质文本（跳过空节点与淘宝图片乱码） */
    private fun nextContent(texts: List<String>, labelIdx: Int): String? =
        texts.drop(labelIdx + 1).firstOrNull { v ->
            val c = v.trim()
            c.isNotEmpty() && !c.contains("!!") && !c.startsWith("O1CN")
        }?.trim()
}
