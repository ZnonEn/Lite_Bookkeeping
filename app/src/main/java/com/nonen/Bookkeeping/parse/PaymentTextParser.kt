package com.nonen.Bookkeeping.parse

/** 从通知或界面文本中解析出的一笔交易。 */
data class ParsedPayment(
    val amount: Double,
    val isIncome: Boolean,
    val counterparty: String? = null,
    val description: String? = null,
    /** 账单详情页等场景提取的真实交易时间；null 表示按当前时间入账 */
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
 * 微信/支付宝支付完成页与账单详情页的启发式抓取。
 * 方向判定：金额标签锚点（付款金额/退款金额…）优先，其次是成功提示词——
 * 支付完成页通常没有「付款金额」标签，只有「支付成功/付款成功 + ¥金额」。
 * 必须同时具备：可判定的方向 + 金额，否则放弃（宁可不记录，也不记错）。
 */
object WindowCaptureAnalyzer {

    private val AMOUNT_WITH_LABEL =
        Regex("""(?:付款金额|支付金额|退款金额|收款金额|入账金额|转账金额)[^\d¥￥]{0,6}[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val STANDALONE_AMOUNT = Regex("""^[-–—]?[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)$""")
    private val AMOUNT_ANYWHERE = Regex("""[-–—]?[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    // 长标签放前面，避免「收款方全称」被「收款方」截断出「全称」；(?!式) 防止「付款方式」被当标签
    private val COUNTERPARTY = Regex(
        """(?:收款方全称|收款方名称|对方全称|对方名称|付款方全称|商户名称|收款方|付款方|对方账户|商户)(?!式)[:：\s]*(\S{1,25})""",
    )

    private val EXPENSE_ANCHORS = listOf("付款金额", "支付金额", "转账金额")
    private val INCOME_ANCHORS = listOf("退款金额", "收款金额", "入账金额")
    private val EXPENSE_SUCCESS = listOf("支付成功", "付款成功", "已支付", "已付款", "已转账", "转账成功", "扣款成功", "付款时间")
    private val INCOME_SUCCESS = listOf(
        "已收钱", "已收款", "收款成功", "已存入", "已到账", "到账成功", "退款成功", "收款时间",
        "确认收款", "立即收款", "请收款",
    )
    // 聊天内转账发出后，发送方页面/气泡显示「待<收款人名>确认收款」——钱已付出、对方未领。
    // 收款方自己的页面写「待确认收款」（无人名），因此要求中间有名字才当作支出证据
    private val EXPENSE_PATTERNS = listOf(Regex("""待.{1,8}?确认收款"""))

    // 状态词：证明页面上的交易「已达成」。转账/付款的输入前置页同样带金额标签，
    // 但没有这些标志——Tally 式防误记的关键门槛
    private val STATUS_WORDS = EXPENSE_SUCCESS + INCOME_SUCCESS + listOf("交易成功", "已入账")

    // 支付上下文词：只有支付类页面才有的结构性词。微信主界面/聊天列表的消息预览
    // （如「已支付¥0.01」「[转账] 已退还」）也含状态词与金额，但没有这些词——
    // 缺支付上下文的页面（浏览页/列表页）一律不解析，防止把浏览行为记成收支
    private val PAYMENT_CONTEXT_WORDS = listOf(
        "付款方式", "支付方式", "收款方", "付款金额", "支付金额", "转账金额", "退款金额",
        "收款金额", "入账金额", "交易时间", "支付时间", "账单详情", "交易详情", "转账详情",
        "当前状态", "商家订单号", "红包金额", "已存入零钱", "零钱明细", "立即收款",
    )

    // 「支出4.00」「收入25.00元」方向+金额合一的节点（支付宝账单详情常见）
    private val DIRECTION_AMOUNT = Regex("""^(支出|收入)[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)元?$""")
    // 「-0.01」「+0.01」带符号金额节点（支付宝详情页：负号=支出、正号=收入）
    private val SIGNED_AMOUNT = Regex("""^([+＋\-–—])([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""")

    private val TIME_LABELS = listOf("交易时间", "支付时间", "付款时间", "转账时间", "收款时间", "退款时间", "创建时间")
    private val DATETIME_RE = Regex("""(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})日?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?""")

    // ===== 专版专杀规则（移植自 Tally）=====
    private val ALIPAY_DETAIL_MARKERS = setOf("账单详情", "商家订单号", "订单号", "交易详情", "交易订单号")
    private val WECHAT_DETAIL_MARKERS = setOf("交易单号", "商户单号")
    private val COUNTERPARTY_LABELS = setOf("收款方全称", "收款方名称", "收款方")
    private val NOTE_LABELS = setOf("商品说明", "交易说明", "交易详情")
    private val MINUS_SIGNS = setOf("-", "–", "—")
    private val GARBAGE_PREFIXES = listOf("O1CN", "!!")  // 淘宝图片乱码特征
    private val DIRECTION_YUAN = Regex("""^(支出|收入)[¥￥]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)元$""")
    private val SIGNED_YUAN = Regex("""^([+＋]|[-–—])([0-9][0-9,]*(?:\.[0-9]{1,2})?)元$""")
    private val PLAIN_DECIMAL = Regex("""^[0-9]+\.[0-9]{1,2}$""")
    private val WECHAT_DETAIL_AMOUNT = Regex("""^[-+]?[0-9]+\.[0-9]{2}$""")

    // 支付成功页的独立金额节点提取
    private val SUCCESS_PAGE_EXPENSE_MARKERS = setOf("支付成功", "付款成功")
    private val SUCCESS_PAGE_INCOME_MARKERS = setOf("收款成功", "已收钱")
    private val SUCCESS_PAGE_NOISE = setOf("完成", "返回", "付款方式", "收款方式", "本店优惠", "支付有福利")

    fun analyze(texts: List<String>): ParsedPayment? = analyzeDetailed(texts).first

    /** 解析结果 + 失败原因（原因供抓取调试面板展示） */
    fun analyzeDetailed(texts: List<String>): Pair<ParsedPayment?, String> {
        if (texts.isEmpty()) return null to "页面无文本"

        // 优先走支付成功页的固定版式提取：页面下方优惠券/推荐位的数字极多，
        // 通用正则会误抓（真实案例：实付 0.01 被记成 4.0）
        parseSuccessPage(texts)?.let { return it to "支付成功页专用提取" }
        // Tally 专版专杀：账单详情页与红包页按各自固定版式提取，先于通用规则
        parseAlipayBillDetail(texts)?.let { return it to "支付宝账单详情专用提取" }
        parseWeChatBillDetail(texts)?.let { return it to "微信账单详情专用提取" }
        parseRedPacketIncome(texts)?.let { return it to "红包入账专用提取" }

        val joined = texts.joinToString(" ") { it.trim() }.replace('\n', ' ')

        // 支付上下文门槛：聊天列表/主界面等浏览页的消息预览（已支付¥0.01、[转账]已退还）
        // 同样带状态词与金额，但不是支付页面——没有支付结构性词一律不解析
        if (PAYMENT_CONTEXT_WORDS.none { joined.contains(it) }) {
            return null to "非支付页面（缺支付上下文，如聊天列表/浏览页）"
        }

        // 状态词门槛（Tally 专版专杀的核心防线）：转账/付款的输入前置页同样带金额标签，
        // 但没有「已完成」标志。没有状态词一律不记，砍掉最大的一类误记
        if (STATUS_WORDS.none { joined.contains(it) }) {
            return null to "无交易状态词（可能是付款前置页或普通页面）"
        }

        val hasExpenseAnchor = EXPENSE_ANCHORS.any { joined.contains(it) }
        val hasIncomeAnchor = INCOME_ANCHORS.any { joined.contains(it) }
        if (hasExpenseAnchor && hasIncomeAnchor) return null to "方向冲突：支出与收入金额标签同时出现"

        // 「支出4.00」方向+金额合一的节点（支付宝账单详情常见）
        val directionNode = texts.firstNotNullOfOrNull { DIRECTION_AMOUNT.find(it.trim())?.groupValues }
        // 「-0.01」负号=支出、「+0.01」正号=收入（支付宝详情页）
        val signedNode = texts.firstNotNullOfOrNull { SIGNED_AMOUNT.find(it.trim())?.groupValues }

        // 「待XX确认收款」本身包含「确认收款」，先把它从文本中剔除再匹配收入词，
        // 避免发送方页面同时命中支出与收入证据
        val incomeText = EXPENSE_PATTERNS.fold(joined) { acc, r -> r.replace(acc, " ") }
        val hasExpenseWord = EXPENSE_SUCCESS.any { joined.contains(it) } ||
            EXPENSE_PATTERNS.any { it.containsMatchIn(joined) }
        val hasIncomeWord = INCOME_SUCCESS.any { incomeText.contains(it) }

        val isIncome = when {
            hasIncomeAnchor -> true
            hasExpenseAnchor -> false
            directionNode != null -> directionNode[1] == "收入"
            signedNode != null -> signedNode[1] !in listOf("-", "–", "—")
            hasIncomeWord && !hasExpenseWord -> true
            hasExpenseWord && !hasIncomeWord -> false
            else -> return null to "未发现方向证据（无金额标签/方向词）"
        }

        // OCR 文本常见千分位逗号（1,234.56）：金额匹配前统一去掉，避免整段失配
        val joinedForAmount = joined.replace(",", "")
        val amount = texts.firstNotNullOfOrNull { AMOUNT_WITH_LABEL.find(it.replace(",", "")) }
            ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: AMOUNT_WITH_LABEL.find(joinedForAmount)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: directionNode?.get(2)?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: signedNode?.get(2)?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: texts.firstNotNullOfOrNull { STANDALONE_AMOUNT.find(it.trim().replace(",", "")) }
                ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: AMOUNT_ANYWHERE.find(joinedForAmount)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null to "未发现金额"
        if (amount <= 0.0 || amount > 1_000_000.0) return null to "金额超出可记录范围"

        val counterparty = COUNTERPARTY.find(joined)?.groupValues?.get(1)?.let { raw ->
            val v = raw.trim()
            v.takeIf {
                it.isNotEmpty() && !it.any(Char::isDigit) && it.length in 2..25 &&
                    !GARBAGE_PREFIXES.any { g -> v.startsWith(g) } &&
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
            timestamp = extractTradeTime(texts),
        ) to "解析成功"
    }

    /**
     * 账单详情页常带历史交易时间：提取真实发生时间，浏览旧账单时按原时间入账，
     * 与账单文件导入的哈希一致、自动去重，不会产生「当前时间」的重复记录。
     */
    private fun extractTradeTime(texts: List<String>): Long? {
        for (i in texts.indices) {
            val node = texts[i].trim()
            if (TIME_LABELS.none { node.startsWith(it) }) continue
            val candidates = buildList {
                add(node.substringAfter('：').substringAfter(':').trim())
                texts.getOrNull(i + 1)?.trim()?.let { add(it) }
            }
            for (c in candidates) {
                val m = DATETIME_RE.find(c) ?: continue
                val second = m.groupValues[6].ifEmpty { "0" }.toInt()
                return java.time.LocalDateTime.of(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(),
                    m.groupValues[5].toInt(),
                    second,
                ).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    /**
     * 支付成功页专用提取（微信/支付宝结果页版式固定）：
     * 定位「支付成功/付款成功」等标记节点，取其下方第一个独立金额节点作为实付金额——
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

    /**
     * 支付宝账单详情页（含历史账单/免密支付）——规则移植自 Tally：
     * 标记：账单详情/商家订单号/订单号/交易详情；金额形态：支出X元、收入X元、±X元（元可选）、
     * 带符号纯数字、无符号小数（裸整数是年份/件数，跳过）；商户=收款方全称等标签值，
     * 商品说明入备注；兜底商户=金额正上方节点；时间=支付时间/创建时间/收款时间。
     */
    private fun parseAlipayBillDetail(texts: List<String>): ParsedPayment? {
        val trimmed = texts.map { it.trim() }
        if (trimmed.none { it in ALIPAY_DETAIL_MARKERS }) return null

        var amount = -1.0
        var isIncome = false
        var amountIdx = -1
        for (i in trimmed.indices) {
            val v = trimmed[i]
            var found: Double? = null
            DIRECTION_YUAN.find(v)?.let { m ->
                isIncome = m.groupValues[1] == "收入"
                found = m.groupValues[2].replace(",", "").toDoubleOrNull()
            }
            if (found == null) SIGNED_YUAN.find(v)?.let { m ->
                isIncome = m.groupValues[1] == "+"
                found = m.groupValues[2].replace(",", "").toDoubleOrNull()
            }
            if (found == null && amount == -1.0) SIGNED_AMOUNT.find(v)?.let { m ->
                isIncome = m.groupValues[1] !in MINUS_SIGNS
                found = m.groupValues[2].replace(",", "").toDoubleOrNull()
            }
            if (found == null && amount == -1.0 && PLAIN_DECIMAL.matches(v)) {
                // 无符号小数在详情页即消费金额；裸整数（年份/件数）不可信
                isIncome = false
                found = v.toDoubleOrNull()
            }
            val parsed = found ?: continue
            if (parsed <= 0.0) continue
            amount = parsed
            amountIdx = i
            break
        }
        if (amountIdx < 0) return null

        var counterparty = ""
        var note = ""
        var fallbackTitle = ""
        for (i in trimmed.indices) {
            val v = trimmed[i]
            if (v in COUNTERPARTY_LABELS && counterparty.isEmpty()) counterparty = firstUsable(trimmed, i)
            if (v in NOTE_LABELS && note.isEmpty()) note = firstUsable(trimmed, i)
            if (i == amountIdx - 1 && fallbackTitle.isEmpty() && v != "账单详情" && v != "返回") {
                fallbackTitle = v
            }
        }
        if (counterparty.isEmpty()) counterparty = fallbackTitle
        if (counterparty.isEmpty()) counterparty = "支付宝账单"
        return ParsedPayment(
            amount = amount,
            isIncome = isIncome,
            counterparty = counterparty.take(25),
            description = note.ifEmpty { null },
            timestamp = extractTradeTime(texts),
        )
    }

    /**
     * 微信账单详情页——规则移植自 Tally：
     * 标记：交易单号/商户单号；金额为 ±X.XX 精确两位小数节点，符号定方向；
     * 无符号时靠字段特征定方向（收款时间/已收钱=收入，付款时间/商品说明/收款方=支出）；
     * 备注链：商品 → 金额正下方（排除原价/优惠/金额）→ 商户全称/收款方 → 金额正上方。
     */
    private fun parseWeChatBillDetail(texts: List<String>): ParsedPayment? {
        val trimmed = texts.map { it.trim() }
        if (trimmed.none { it in WECHAT_DETAIL_MARKERS }) return null

        var amount = -1.0
        var isIncome = false
        var signed = false
        var amountIdx = -1
        for (i in trimmed.indices) {
            val m = WECHAT_DETAIL_AMOUNT.find(trimmed[i]) ?: continue
            val v = m.value.toDoubleOrNull() ?: continue
            if (v == 0.0) continue
            amount = kotlin.math.abs(v)
            isIncome = v > 0
            signed = m.value.startsWith("-") || m.value.startsWith("+")
            amountIdx = i
            break
        }
        if (amountIdx < 0) return null

        if (!signed) {
            val joined = trimmed.joinToString(" ")
            isIncome = when {
                joined.contains("已收钱") || joined.contains("收款时间") || joined.contains("已存入零钱") -> true
                trimmed.contains("付款时间") || trimmed.contains("商品说明") || trimmed.contains("收款方") -> false
                else -> return null // 方向判不出，交给通用路径
            }
        }

        var product = ""
        var directBelow = ""
        var merchant = ""
        var fallback = ""
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                in NOTE_LABELS -> if (product.isEmpty()) product = firstUsable(trimmed, i)
                in COUNTERPARTY_LABELS, "商户全称" -> if (merchant.isEmpty()) merchant = firstUsable(trimmed, i)
            }
            if (i == amountIdx) {
                for (j in i + 1 until trimmed.size) {
                    val next = trimmed[j]
                    if (next.isEmpty() || next in STATUS_WORDS || next in SUCCESS_PAGE_NOISE ||
                        next.contains("原价") || next.contains("优惠") || next.contains("¥") || next.contains("￥")
                    ) continue
                    directBelow = next
                    break
                }
                for (j in i - 1 downTo 0) {
                    val prev = trimmed[j]
                    if (prev.isEmpty() || prev == "账单详情" || prev == "返回" ||
                        prev.contains("支出") || prev.contains("收入")
                    ) continue
                    fallback = prev
                    break
                }
            }
        }
        val note = when {
            product.isNotEmpty() && !product.startsWith("商户单号") && !product.startsWith("交易单号") -> product
            directBelow.isNotEmpty() && !directBelow.contains("详情") -> directBelow
            merchant.isNotEmpty() -> merchant
            fallback.isNotEmpty() && !fallback.contains("详情") -> fallback
            else -> "微信账单"
        }.take(25)
        return ParsedPayment(amount, isIncome, note, null, extractTradeTime(texts))
    }

    /**
     * 微信红包领取详情——规则移植自 Tally：
     * 标志=「已存入零钱」；金额=「元」节点前的纯数字节点（红包页金额与元分離渲染）；
     * 「XX的红包」作为对方标识；固定记收入。
     */
    private fun parseRedPacketIncome(texts: List<String>): ParsedPayment? {
        if (texts.none { it.contains("已存入零钱") }) return null
        var amount = -1.0
        for (i in 1 until texts.size) {
            if (texts[i].trim() == "元") {
                val v = texts[i - 1].trim().replace(",", "").toDoubleOrNull()
                if (v != null && v > 0.0) {
                    amount = v
                    break
                }
            }
        }
        if (amount <= 0.0) {
            amount = texts.firstNotNullOfOrNull { AMOUNT_ANYWHERE.find(it.trim())?.groupValues?.get(1)?.toDoubleOrNull() }
                ?: return null
        }
        val name = texts.firstOrNull { it.trim().endsWith("的红包") }?.trim() ?: "微信红包"
        return ParsedPayment(amount, isIncome = true, counterparty = name, description = "红包入账")
    }

    /** 标签后的第一个有效值节点（跳过空节点与淘宝图片乱码） */
    private fun firstUsable(texts: List<String>, labelIdx: Int): String {
        for (j in labelIdx + 1 until minOf(texts.size, labelIdx + 5)) {
            val v = texts[j]
            if (v.isEmpty()) continue
            if (GARBAGE_PREFIXES.any { v.startsWith(it) }) continue
            return v
        }
        return ""
    }
}
