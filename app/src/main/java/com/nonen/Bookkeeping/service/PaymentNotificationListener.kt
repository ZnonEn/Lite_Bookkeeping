package com.nonen.Bookkeeping.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.data.prefs.Packages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通知使用权通道：微信/支付宝支付完成后会发布含金额的系统通知（如「微信支付凭证」）。
 * 现代 Android 对无障碍服务屏蔽了其他应用的通知内容，且微信支付页面对无障碍隐藏文本，
 * 该通道是无障碍之外唯一可靠的抓取来源。需要用户在系统设置授予「通知使用权」。
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val pkg = n.packageName ?: return
        if (pkg !in TARGETS) return
        val extras = n.notification?.extras ?: return
        val full = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
        ).filterNotNull().map { it.toString() }.filter { it.isNotBlank() }.joinToString(" ")
        if (full.isBlank()) return
        scope.launch {
            val s = (applicationContext as? BookkeepingApp)?.container?.settings?.snapshot() ?: return@launch
            AutoRecordPipeline.handleNotificationText(applicationContext, pkg, "通知", full, s)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val TARGETS = setOf(Packages.WECHAT, Packages.ALIPAY)
    }
}
