package com.nonen.Bookkeeping.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.data.prefs.Packages
import com.nonen.Bookkeeping.data.prefs.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 无障碍自动记账服务：
 * 1. 监听微信/支付宝的窗口变化，对支付结果页做启发式抓取（注意：两家的支付页面对无障碍隐藏文本，
 *    窗口通道抓不到属正常，可靠来源是 [PaymentNotificationListener] 通知使用权通道）；
 * 2. 旧系统的通知事件也顺带解析；
 * 3. 解析成功 → 去重 → 自动分类 → 写入本地数据库（source = auto），入库统一走 [AutoRecordPipeline]。
 */
class AutoRecordAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanScheduled = AtomicBoolean(false)

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
        if (pkg in TARGET_PACKAGES) {
            AutoRecordDebugStore.onEvent(pkg)
        }
        // 事件对象在回调返回后会被系统回收，必须在同步代码里先取出所需数据
        val eventText = e.text.joinToString(" ") { it.toString() }.trim()
        val notificationText = extractNotificationText(e)

        when (e.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val text = notificationText ?: eventText
                if (text.isNotBlank()) {
                    scope.launch {
                        val s = settings() ?: return@launch
                        AutoRecordPipeline.handleNotificationText(applicationContext, pkg, "notification", text, s)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val nt = notificationText
                if (!nt.isNullOrBlank()) {
                    scope.launch {
                        val s = settings() ?: return@launch
                        AutoRecordPipeline.handleNotificationText(applicationContext, pkg, "notification", nt, s)
                    }
                }
                // 新窗口（支付完成页通常是新页面/弹窗）：尽快扫一次
                scheduleWindowScan(WINDOW_SCAN_FAST_MS)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val nt = notificationText
                if (!nt.isNullOrBlank()) {
                    scope.launch {
                        val s = settings() ?: return@launch
                        AutoRecordPipeline.handleNotificationText(applicationContext, pkg, "notification", nt, s)
                    }
                }
                scheduleWindowScan(WINDOW_SCAN_DELAY_MS)
            }
        }
    }

    private fun extractNotificationText(event: AccessibilityEvent): String? {
        val notification = event.parcelableData as? android.app.Notification ?: return null
        val extras = notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val big = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        return listOfNotNull(title, big, text)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
    }

    private suspend fun settings(): SettingsSnapshot? =
        (applicationContext as? BookkeepingApp)?.container?.settings?.snapshot()

    /**
     * 节流式扫描：窗口期内的第一个事件触发一次扫描，期间的后续事件忽略。
     * 旧实现是「每次事件都取消重排」的防抖——微信页面持续触发事件会把扫描
     * 无限期推迟，导致支付页从未被扫描过。
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

    private suspend fun scanWindow(s: SettingsSnapshot) {
        val texts = ArrayList<String>(64)
        var sourcePkg: String? = rootInActiveWindow?.packageName?.toString()
        rootInActiveWindow?.let { root ->
            sourcePkg = root.packageName?.toString()
            collectTexts(root, texts, 0)
        }
        // 支付完成页可能是独立弹窗而非当前活动窗口，遍历应用窗口兜底（只认微信/支付宝自己的窗口）
        val visibleWindows = runCatching { windows }.getOrNull()
        visibleWindows
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
                val visible = visibleWindows
                    ?.mapNotNull { it.root?.packageName?.toString() }
                    ?.distinct()
                    ?.joinToString("、") { AutoRecordDebugStore.appNameOf(it) }
                AutoRecordDebugStore.recordThrottled(
                    pkg, "window", "未抓到任何文本节点（对方对无障碍隐藏了内容；可见窗口：${visible ?: "无"}）", emptyList(),
                )
            }
            return
        }
        AutoRecordPipeline.handleWindowTexts(applicationContext, pkg, texts, s)
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

    companion object {
        @Volatile
        var INSTANCE: AutoRecordAccessibilityService? = null
            private set

        private val TARGET_PACKAGES = setOf(Packages.WECHAT, Packages.ALIPAY)
        private const val WINDOW_SCAN_DELAY_MS = 600L
        private const val WINDOW_SCAN_FAST_MS = 120L
        private const val MAX_NODE_DEPTH = 24
        private const val MAX_NODE_TEXTS = 300
    }
}
