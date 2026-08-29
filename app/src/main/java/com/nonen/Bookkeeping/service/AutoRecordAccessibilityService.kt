package com.nonen.Bookkeeping.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.core.JsonUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.prefs.SettingsSnapshot
import com.nonen.Bookkeeping.parse.ParsedPayment
import com.nonen.Bookkeeping.parse.PaymentTextParser
import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * 无障碍自动记账服务：
 * 1. 监听微信/支付宝的支付通知（TYPE_NOTIFICATION_STATE_CHANGED），解析金额与方向；
 * 2. 监听两 App 的窗口内容变化，对「账单详情」页面做启发式抓取；
 * 3. 解析成功 → 哈希去重 → 自动分类 → 写入本地数据库（source = auto）。
 * 无法可靠解析的内容一律忽略，不记录、不联网。
 */
class AutoRecordAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val recentSignatures = HashMap<String, Long>()
    private var scanRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        INSTANCE = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (INSTANCE === this) INSTANCE = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (INSTANCE === this) INSTANCE = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        val type = e.eventType
        // 事件对象在回调返回后会被系统回收，必须在同步代码里先取出所需数据
        val eventText = e.text.joinToString(" ") { it.toString() }.trim()
        val notificationText = extractNotificationText(e)

        when (type) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val text = notificationText ?: eventText
                if (text.isNotBlank()) {
                    scope.launch { handleTextCapture(pkg, text, "notification") }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!notificationText.isNullOrBlank()) {
                    scope.launch { handleTextCapture(pkg, notificationText, "notification") }
                }
                // 新窗口（支付完成页通常是新页面/弹窗）：尽快扫一次
                scheduleWindowScan(pkg, WINDOW_SCAN_FAST_MS)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!notificationText.isNullOrBlank()) {
                    scope.launch { handleTextCapture(pkg, notificationText, "notification") }
                }
                scheduleWindowScan(pkg, WINDOW_SCAN_DELAY_MS)
            }
        }
    }

    private fun extractNotificationText(event: AccessibilityEvent): String? {
        val notification = event.parcelableData as? Notification ?: return null
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return listOfNotNull(title, big, text)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
    }

    private suspend fun settings(): SettingsSnapshot? =
        (applicationContext as? BookkeepingApp)?.container?.settings?.snapshot()

    private suspend fun handleTextCapture(pkg: String, text: String, origin: String) {
        val s = settings() ?: return
        if (!s.autoRecordEnabled || pkg !in s.listenScope.packages) return
        val parsed = PaymentTextParser.parse(text) ?: return
        record(parsed, pkg, origin, text, s.notifyOnRecord)
    }

    private fun scheduleWindowScan(pkg: String, delayMs: Long) {
        scanRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            scope.launch {
                val s = settings() ?: return@launch
                if (!s.autoRecordEnabled || pkg !in s.listenScope.packages) return@launch
                scanWindow(pkg, s.notifyOnRecord)
            }
        }
        scanRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private suspend fun scanWindow(pkg: String, notify: Boolean) {
        val texts = ArrayList<String>(64)
        rootInActiveWindow?.let { collectTexts(it, texts, 0) }
        // 支付完成页可能是独立弹窗而非当前活动窗口，遍历应用窗口兜底
        runCatching { windows }.getOrNull()
            ?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.forEach { w -> w.root?.let { root -> collectTexts(root, texts, 0) } }
        if (texts.isEmpty()) return
        val parsed = WindowCaptureAnalyzer.analyze(texts) ?: return
        // 同一页面在短时间内反复触发内容变化，用签名做短时去重；数据库哈希负责长期去重
        val signature = "$pkg|${parsed.amount}|${parsed.isIncome}|${parsed.counterparty.orEmpty()}"
        val now = System.currentTimeMillis()
        synchronized(recentSignatures) {
            val last = recentSignatures[signature]
            if (last != null && now - last < SIGNATURE_TTL_MS) return
            recentSignatures[signature] = now
            if (recentSignatures.size > 128) {
                recentSignatures.entries.removeIf { now - it.value > SIGNATURE_TTL_MS }
            }
        }
        record(parsed, pkg, "window", texts.joinToString(" "), notify)
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > MAX_NODE_DEPTH || out.size > MAX_NODE_TEXTS) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (!out.contains(it)) out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { if (!out.contains(it)) out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out, depth + 1)
        }
    }

    private suspend fun record(
        parsed: ParsedPayment,
        pkg: String,
        origin: String,
        rawText: String,
        notify: Boolean,
    ) {
        val container = (applicationContext as? BookkeepingApp)?.container ?: return
        val signed = if (parsed.isIncome) parsed.amount else -parsed.amount
        val now = System.currentTimeMillis()
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
            // 时间按 10 分钟取桶：通知与窗口两条路径先后抓到同一笔支付时哈希一致，
            // 由数据库唯一哈希兜底去重，避免同一笔记两次
            hash = HashUtil.transactionHash(
                now / AUTO_HASH_BUCKET_MS * AUTO_HASH_BUCKET_MS,
                signed,
                parsed.counterparty,
                "auto",
            ),
        )
        val inserted = container.transactionRepository.insertIfNew(entity)
        if (inserted && notify) {
            showRecordNotification(signed, entity.category)
        }
    }

    private fun showRecordNotification(signedAmount: Double, category: String) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "自动记账提醒", NotificationManager.IMPORTANCE_LOW)
            )
            val direction = if (signedAmount < 0) "支出" else "收入"
            val text = "$category ¥${String.format(Locale.US, "%.2f", abs(signedAmount))}"
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("已自动记录一笔$direction")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        @Volatile
        var INSTANCE: AutoRecordAccessibilityService? = null
            private set

        private const val CHANNEL_ID = "auto_record"
        private const val NOTIFICATION_ID = 1001
        private const val WINDOW_SCAN_DELAY_MS = 600L
        private const val WINDOW_SCAN_FAST_MS = 120L
        private const val SIGNATURE_TTL_MS = 15 * 60 * 1000L
        private const val AUTO_HASH_BUCKET_MS = 10 * 60 * 1000L
        private const val MAX_NODE_DEPTH = 24
        private const val MAX_NODE_TEXTS = 300
    }
}
