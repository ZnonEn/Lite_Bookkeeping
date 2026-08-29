package com.nonen.Bookkeeping.service

import android.content.Context
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.core.JsonUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.prefs.SettingsSnapshot
import com.nonen.Bookkeeping.parse.ParsedPayment
import com.nonen.Bookkeeping.parse.PaymentTextParser
import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer

/**
 * 无障碍窗口 / 通知监听两条抓取通道共用的解析与入库管线。
 * 同一笔支付可能先后被两条通道抓到，用短时签名去重 + 数据库哈希兜底。
 */
object AutoRecordPipeline {

    private val recentSignatures = HashMap<String, Long>()
    private const val SIGNATURE_TTL_MS = 2 * 60 * 1000L
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
        val parsed = PaymentTextParser.parse(text) ?: return
        insert(context, parsed, pkg, origin, text, s)
    }

    /** 窗口文本通道（无障碍页面抓取） */
    suspend fun handleWindowTexts(
        context: Context,
        pkg: String,
        texts: List<String>,
        s: SettingsSnapshot,
    ): Boolean {
        val parsed = WindowCaptureAnalyzer.analyze(texts) ?: return false
        return insert(context, parsed, pkg, "window", texts.joinToString(" "), s)
    }

    private suspend fun insert(
        context: Context,
        parsed: ParsedPayment,
        pkg: String,
        origin: String,
        rawText: String,
        s: SettingsSnapshot,
    ): Boolean {
        // 跨通道短时去重：同一笔支付可能先后被通知与窗口两条通道抓到（金额+方向相同即视为同一笔）
        val signature = "$pkg|${parsed.amount}|${parsed.isIncome}"
        val now = System.currentTimeMillis()
        synchronized(recentSignatures) {
            val last = recentSignatures[signature]
            if (last != null && now - last < SIGNATURE_TTL_MS) return false
            recentSignatures[signature] = now
            if (recentSignatures.size > 128) {
                recentSignatures.entries.removeIf { now - it.value > SIGNATURE_TTL_MS }
            }
        }
        val container = (context.applicationContext as? BookkeepingApp)?.container ?: return false
        val signed = if (parsed.isIncome) parsed.amount else -parsed.amount
        val entity = TransactionEntity(
            amount = signed,
            category = container.ruleEngine.categorize(
                listOfNotNull(parsed.counterparty, parsed.description).joinToString(" "),
                parsed.isIncome,
            ),
            note = parsed.description?.takeIf { it.isNotBlank() },
            merchant = parsed.counterparty,
            timestamp = now,
            source = "auto",
            rawData = JsonUtil.obj(
                "origin" to origin,
                "package" to pkg,
                "text" to rawText.take(300),
            ),
            // 时间按 10 分钟取桶：同一笔支付被多条路径先后抓到时哈希一致，数据库唯一哈希兜底
            hash = HashUtil.transactionHash(
                now / AUTO_HASH_BUCKET_MS * AUTO_HASH_BUCKET_MS,
                signed,
                parsed.counterparty,
                "auto",
            ),
        )
        val inserted = container.transactionRepository.insertIfNew(entity)
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
