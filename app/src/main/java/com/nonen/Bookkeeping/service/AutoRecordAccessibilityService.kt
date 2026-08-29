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
import com.nonen.Bookkeeping.data.prefs.Packages
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
import java.util.concurrent.atomic.AtomicBoolean
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
    private val scanScheduled = AtomicBoolean(false)

    /**
     * 节流式扫描：窗口期内的第一个事件触发一次扫描，期间的后续事件忽略。
     * 旧实现是「每次事件都取消重排」的防抖——微信页面持续触发事件会把扫描
     * 无限期推迟，导致支付成功页从未被扫描过。
     */
    private fun scheduleWindowScan(delayMs: Long) {
        if (!scanScheduled.compareAndSet(false, true)) return
        handler.postDelayed({
            scanScheduled.set(false)
            scope.launch {
                val s = settings() ?: return@launch
                scanWindow(s)
            }
        }, delayMs)
    }

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
        if (pkg in TARGET_PACKAGES) {
            AutoRecordDebugStore.onEvent(pkg)
        }
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
                scheduleWindowScan(WINDOW_SCAN_FAST_MS)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!notificationText.isNullOrBlank()) {
                    scope.launch { handleTextCapture(pkg, notificationText, "notification") }
                }
                scheduleWindowScan(WINDOW_SCAN_DELAY_MS)
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
        val parsed = PaymentTextParser.parse(text)
        if (s.captureDebug) {
            AutoRecordDebugStore.record(
                pkg,
                origin,
                if (parsed != null) "通知解析成功（金额 ¥${parsed.amount}）" else "通知文本未解析出交易",
                listOf(text.take(60)),
            )
        }
        parsed?.let { record(it, pkg, origin, text, s.notifyOnRecord) }
    }

    private suspend fun scanWindow(s: SettingsSnapshot) {
        val texts = ArrayList<String>(64)
        var sourcePkg: String? = rootInActiveWindow?.packageName?.toString()
        rootInActiveWindow?.let { root ->
            sourcePkg = root.packageName?.toString()
            collectTexts(root, texts, 0)
        }
        // 支付完成页可能是独立弹窗而非当前活动窗口，遍历应用窗口兜底（只认微信/支付宝自己的窗口）
        runCatching { windows }.getOrNull()
            ?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.forEach { w ->
                val wpkg = w.root?.packageName?.toString()
                if (wpkg != null && wpkg in TARGET_PACKAGES && wpkg == sourcePkg) {
                    w.root?.let { root -> collectTexts(root, texts, 0) }
                }
            }
        // 以窗口实际归属为准：事件可能由后台的微信触发，而前台早已切到别的应用，此时不扫
        val pkg = sourcePkg
        if (pkg == null || pkg !in TARGET_PACKAGES) return
        if (!s.autoRecordEnabled || pkg !in s.listenScope.packages) {
            if (s.captureDebug) {
                AutoRecordDebugStore.recordThrottled(
                    pkg, "window", "已跳过：自动记账开关关闭或不在监听范围", emptyList(),
                )
            }
            return
        }
        if (texts.isEmpty()) {
            if (s.captureDebug) {
                AutoRecordDebugStore.recordThrottled(pkg, "window", "未抓到任何文本节点（窗口内容不可用）", emptyList())
            }
            return
        }
        val (parsed, reason) = WindowCaptureAnalyzer.analyzeDetailed(texts)
        if (parsed == null) {
            if (s.captureDebug) {
                val textsPreview = texts.take(12)
                if (reason.startsWith("未发现方向证据")) {
                    AutoRecordDebugStore.recordThrottled(pkg, "window", reason, textsPreview)
                } else {
                    AutoRecordDebugStore.record(pkg, "window", reason, textsPreview)
                }
            }
            return
        }
        // 同一页面在短时间内反复触发内容变化，用签名做短时去重；数据库哈希负责长期去重
        val signature = "$pkg|${parsed.amount}|${parsed.isIncome}|${parsed.counterparty.orEmpty()}"
        val now = System.currentTimeMillis()
        synchronized(recentSignatures) {
            val last = recentSignatures[signature]
            if (last != null && now - last < SIGNATURE_TTL_MS) {
                if (s.captureDebug) {
                    AutoRecordDebugStore.record(pkg, "window", "同一页面 15 分钟内已记录过，短时去重跳过", emptyList())
                }
                return
            }
            recentSignatures[signature] = now
            if (recentSignatures.size > 128) {
                recentSignatures.entries.removeIf { now - it.value > SIGNATURE_TTL_MS }
            }
        }
        val inserted = record(parsed, pkg, "window", texts.joinToString(" "), s.notifyOnRecord)
        if (s.captureDebug) {
            val direction = if (parsed.isIncome) "收入" else "支出"
            AutoRecordDebugStore.record(
                pkg,
                "window",
                if (inserted) "已入库：$direction ¥${parsed.amount}" else "与已存在记录重复，哈希去重跳过",
                texts.take(12),
            )
        }
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
    ): Boolean {
        val container = (applicationContext as? BookkeepingApp)?.container ?: return false
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
        return inserted
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

        private val TARGET_PACKAGES = setOf(Packages.WECHAT, Packages.ALIPAY)
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
