package com.nonen.Bookkeeping.service

import android.content.Context
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.core.JsonUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.prefs.SettingsSnapshot
import com.nonen.Bookkeeping.debug.CaptureDebug
import com.nonen.Bookkeeping.parse.ParsedPayment
import com.nonen.Bookkeeping.parse.PaymentTextParser
import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer

/**
 * 无障碍窗口 / 通知监听两条抓取通道共用的解析与入库管线。
 * 同一笔支付可能先后被两条通道抓到，用短时签名去重 + 数据库哈希兜底。
 */
object AutoRecordPipeline {

    private val recentSignatures = HashMap<String, Long>()
    // 成功入账后的全局冷却：期间不再自动记新账单（压制同一笔支付的多通道/多页面连环抓取）
    private const val INSERT_COOLDOWN_MS = 30_000L

    @Volatile
    private var lastInsertAt = 0L
    // 跨通道去重窗口：通知/窗口/OCR 三条通道对同一笔的触发间隔在几秒内，60 秒足够；
    // 太长会把「短时间内连续两笔同额支付」（公交、测试）误拦成重复
    private const val SIGNATURE_TTL_MS = 60 * 1000L
    private const val AUTO_HASH_BUCKET_MS = 10 * 60 * 1000L
    private const val CHANNEL_ID = "auto_record"
    private const val NOTIFICATION_ID = 1001

    /** 通知文本通道（无障碍通知事件 / 通知使用权监听） */
    suspend fun handleNotificationText(
        context: Context,
        pkg: String,
        origin: String,
        text: String,
        s: SettingsSnapshot,
    ) {
        if (!s.autoRecordEnabled || pkg !in s.listenScope.packages) return
        val parsed = PaymentTextParser.parse(text)
        CaptureDebug.record(
            pkg,
            origin,
            if (parsed != null) "通知解析成功（金额 ¥${parsed.amount}）" else "通知文本未解析出交易",
            listOf(text.take(60)),
        )
        if (parsed != null) insert(context, parsed, pkg, origin, text, s, listOf(text.take(60)))
    }

    /** 窗口文本通道（无障碍页面抓取 / OCR 屏幕识别） */
    suspend fun handleWindowTexts(
        context: Context,
        pkg: String,
        texts: List<String>,
        s: SettingsSnapshot,
        origin: String = "window",
    ): Boolean {
        val (parsed, reason) = WindowCaptureAnalyzer.analyzeDetailed(texts)
        if (parsed == null) {
            val preview = texts.take(12)
            // 浏览类页面的拒绝原因高频出现，限流防刷屏
            if (reason.startsWith("未发现方向证据") || reason.startsWith("非支付成功页")) {
                CaptureDebug.recordThrottled(pkg, origin, reason, preview)
            } else {
                CaptureDebug.record(pkg, origin, reason, preview)
            }
            return false
        }
        return insert(context, parsed, pkg, origin, texts.joinToString(" "), s, texts.take(6))
    }

    private suspend fun insert(
        context: Context,
        parsed: ParsedPayment,
        pkg: String,
        origin: String,
        rawText: String,
        s: SettingsSnapshot,
        debugTexts: List<String> = emptyList(),
    ): Boolean {
        val now = System.currentTimeMillis()
        // 冷却检查放在签名去重之前，且不写入签名——冷却期被拦的这笔不该被签名记住，
        // 冷却结束后（例如另一条通道再抓到）仍能正常入账
        if (now - lastInsertAt < INSERT_COOLDOWN_MS) {
            CaptureDebug.recordThrottled(pkg, origin, "已跳过：30 秒冷却期内（刚成功记录一笔）", debugTexts)
            return false
        }
        // 跨通道短时去重：同一笔支付可能先后被通知与窗口两条通道抓到。
        // 签名带上商户：不同商户的同额支付不受影响；去重只拦截几秒内多通道重复抓取
        val signature = "$pkg|${parsed.amount}|${parsed.isIncome}|${parsed.counterparty.orEmpty()}"
        synchronized(recentSignatures) {
            val last = recentSignatures[signature]
            if (last != null && now - last < SIGNATURE_TTL_MS) {
                CaptureDebug.record(pkg, origin, "1 分钟内已记录过同一笔，去重跳过", debugTexts)
                return false
            }
            recentSignatures[signature] = now
            if (recentSignatures.size > 128) {
                recentSignatures.entries.removeIf { now - it.value > SIGNATURE_TTL_MS }
            }
        }
        val container = (context.applicationContext as? BookkeepingApp)?.container ?: return false
        val signed = if (parsed.isIncome) parsed.amount else -parsed.amount
        // 账单详情页提取到真实交易时间时按原时间入账（限最近一年内，防异常值）
        val tradeTime = parsed.timestamp
            ?.takeIf { it in now - 370L * 24 * 60 * 60 * 1000..now + 5 * 60 * 1000 }
            ?: now
        // 语义去重：成功页/详情页/账单导入对同一笔的商户写法可能不同（科蕊小吃店 vs 苍南县科蕊小吃店），
        // 以「同额且时间相近」判同一笔，防止浏览详情页造成重复入账
        if (container.transactionRepository.hasSimilar(parsed.amount, tradeTime)) {
            CaptureDebug.record(
                pkg, origin, "已存在同额且时间相近的记录（浏览详情页/多通道），判为同一笔跳过", debugTexts,
            )
            return false
        }
        val entity = TransactionEntity(
            amount = signed,
            category = container.ruleEngine.categorize(
                listOfNotNull(parsed.counterparty, parsed.description).joinToString(" "),
                parsed.isIncome,
            ),
            note = parsed.description?.takeIf { it.isNotBlank() },
            merchant = parsed.counterparty,
            timestamp = tradeTime,
            source = "auto",
            rawData = JsonUtil.obj(
                "origin" to origin,
                "package" to pkg,
                "text" to rawText.take(300),
            ),
            // 时间按 10 分钟取桶：同一笔支付被多条路径先后抓到时哈希一致，数据库唯一哈希兜底
            hash = HashUtil.transactionHash(
                tradeTime / AUTO_HASH_BUCKET_MS * AUTO_HASH_BUCKET_MS,
                signed,
                parsed.counterparty,
                "auto",
            ),
        )
        val inserted = container.transactionRepository.insertIfNew(entity)
        if (inserted) lastInsertAt = now
        val direction = if (parsed.isIncome) "收入" else "支出"
        CaptureDebug.record(
            pkg,
            origin,
            if (inserted) "已入库：$direction ¥${parsed.amount}" else "与已存在记录重复，哈希去重跳过",
            debugTexts,
        )
        if (inserted && s.notifyOnRecord) {
            showRecordedNotification(context, signed, entity.category)
        }
        return inserted
    }

    private fun showRecordedNotification(context: Context, signedAmount: Double, category: String) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL_ID, "自动记账提醒", android.app.NotificationManager.IMPORTANCE_LOW)
            )
            val direction = if (signedAmount < 0) "支出" else "收入"
            val text = "$category ¥${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(signedAmount))}"
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.nonen.Bookkeeping.R.drawable.ic_launcher_foreground)
                .setContentTitle("已自动记录一笔$direction")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
