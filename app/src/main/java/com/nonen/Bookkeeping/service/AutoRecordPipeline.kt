package com.nonen.Bookkeeping.service

import android.content.Context
import androidx.core.net.toUri
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.core.JsonUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.prefs.Packages
import com.nonen.Bookkeeping.data.prefs.SettingsSnapshot
import com.nonen.Bookkeeping.debug.CaptureDebug
import com.nonen.Bookkeeping.parse.ParsedPayment
import com.nonen.Bookkeeping.parse.PaymentTextParser
import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 无障碍窗口 / 通知监听两条抓取通道共用的解析与确认管线。
 *
 * 半自动模式：解析成功后**不再静默入库**，而是去重后弹出确认卡片
 * （PaymentConfirmOverlay），用户核对登记信息、点「记一笔」才写库。
 * 同一笔可能被多条通道先后抓到：展示时写 60 秒签名去重，「忽略」后进入
 * 10 分钟免打扰期，确认入库时再用同额时间相近 + 数据库哈希兜底。
 */
object AutoRecordPipeline {

    private val recentSignatures = HashMap<String, Long>()
    private val dismissedSignatures = HashMap<String, Long>()
    // 跨通道去重窗口：通知/窗口两条通道对同一笔的触发间隔在几秒内，60 秒足够；
    // 太长会把「短时间内连续两笔同额支付」（公交、测试）误拦成重复
    private const val SIGNATURE_TTL_MS = 60 * 1000L
    /** 忽略后的免打扰窗口：同一笔在窗口内重复抓到不再弹卡片打扰 */
    private const val DISMISS_TTL_MS = 10 * 60 * 1000L
    private const val AUTO_HASH_BUCKET_MS = 10 * 60 * 1000L
    private const val CHANNEL_ID = "auto_record"
    private const val NOTIFICATION_ID = 1001
    private const val PERMISSION_NOTIFICATION_ID = 1002
    private const val TAG = "AutoRecordPipeline"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastPermissionPromptAt = 0L

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
        if (parsed != null) present(context, parsed, pkg, origin, listOf(text.take(60)), s)
    }

    /** 窗口文本通道（无障碍页面抓取） */
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
            if (reason.startsWith("未匹配到已知支付页面")) {
                CaptureDebug.recordThrottled(pkg, origin, reason, preview)
            } else {
                CaptureDebug.record(pkg, origin, reason, preview)
            }
            return false
        }
        return present(context, parsed, pkg, origin, texts.take(6), s)
    }

    /** 解析成功 → 去重 → 弹确认卡片；返回是否弹出了卡片 */
    private suspend fun present(
        context: Context,
        parsed: ParsedPayment,
        pkg: String,
        origin: String,
        debugTexts: List<String>,
        s: SettingsSnapshot,
    ): Boolean {
        val now = System.currentTimeMillis()
        // 跨通道短时去重：同一笔支付可能先后被通知与窗口两条通道抓到。
        // 签名带上商户：不同商户的同额支付不受影响
        val signature = "$pkg|${parsed.amount}|${parsed.isIncome}|${parsed.counterparty.orEmpty()}"
        synchronized(recentSignatures) {
            val last = recentSignatures[signature]
            if (last != null && now - last < SIGNATURE_TTL_MS) {
                CaptureDebug.record(pkg, origin, "60 秒内已弹过同一笔，去重跳过", debugTexts)
                return false
            }
            recentSignatures[signature] = now
            if (recentSignatures.size > 128) {
                recentSignatures.entries.removeIf { now - it.value > SIGNATURE_TTL_MS }
            }
        }
        synchronized(dismissedSignatures) {
            val dismissed = dismissedSignatures[signature]
            if (dismissed != null && now - dismissed < DISMISS_TTL_MS) {
                CaptureDebug.recordThrottled(pkg, origin, "该笔刚被忽略，免打扰期内不再弹卡片", debugTexts)
                return false
            }
            if (dismissedSignatures.size > 128) {
                dismissedSignatures.entries.removeIf { now - it.value > DISMISS_TTL_MS }
            }
        }
        val container = (context.applicationContext as? BookkeepingApp)?.container ?: return false
        // 已有同额且时间相近的记录（手动记过/导入过）→ 不再打扰
        val tradeTime = effectiveTradeTime(parsed, now)
        if (container.transactionRepository.hasSimilar(parsed.amount, tradeTime)) {
            CaptureDebug.record(pkg, origin, "已存在同额且时间相近的记录，判为同一笔，不弹卡片", debugTexts)
            return false
        }
        if (!PaymentConfirmOverlay.canShow(context)) {
            // 仅作提示参考：MIUI/HyperOS 的开关可能与标准查询不同步，后面仍会尝试直接弹卡
            CaptureDebug.record(pkg, origin, "标准悬浮窗权限查询为未授权，仍将尝试弹出确认卡片", debugTexts)
        }

        val keywords = listOfNotNull(parsed.counterparty, parsed.description).joinToString(" ")
        // 卡片初值就按完整优先链预测：商户记忆命中时，用户直接确认即可，无需再改分类
        val card = PaymentConfirmOverlay.Card(
            sourceLabel = when (pkg) {
                Packages.WECHAT -> context.getString(R.string.source_wechat)
                Packages.ALIPAY -> context.getString(R.string.source_alipay)
                else -> pkg.substringAfterLast('.').take(8)
            },
            amountText = String.format(java.util.Locale.US, "¥%.2f", parsed.amount),
            isIncome = parsed.isIncome,
            counterparty = parsed.counterparty,
            description = parsed.description,
            categoryExpense = container.ruleEngine.categorize(
                text = keywords, isIncome = false, merchant = parsed.counterparty,
            ),
            categoryIncome = container.ruleEngine.categorize(
                text = keywords, isIncome = true, merchant = parsed.counterparty,
            ),
            timeText = formatTradeTime(tradeTime),
        )
        CaptureDebug.record(
            pkg, origin,
            "已弹出确认卡片：${if (parsed.isIncome) "收入" else "支出"} ¥${parsed.amount}",
            debugTexts,
        )
        PaymentConfirmOverlay.show(
            context,
            card,
            onConfirm = { finalIncome, merchant, note, category ->
                synchronized(dismissedSignatures) { dismissedSignatures[signature] = System.currentTimeMillis() }
                confirmInsert(context.applicationContext, parsed, pkg, origin, finalIncome, merchant, note, category)
            },
            onDismiss = {
                synchronized(dismissedSignatures) { dismissedSignatures[signature] = System.currentTimeMillis() }
            },
            onError = { reason ->
                CaptureDebug.record(pkg, origin, "确认卡片弹出失败：$reason", debugTexts)
                promptOverlayPermission(context, reason)
            },
        )
        return true
    }

    /** 账单详情页提取到真实交易时间时按原时间入账（限最近一年内，防异常值） */
    private fun effectiveTradeTime(parsed: ParsedPayment, now: Long): Long =
        parsed.timestamp
            ?.takeIf { it in now - 370L * 24 * 60 * 60 * 1000..now + 5 * 60 * 1000 }
            ?: now

    /** 确认卡片上的入账时间展示：MM-dd HH:mm */
    private fun formatTradeTime(ts: Long): String =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    /** 用户在确认卡片点「记一笔」后入库；交易对象/备注/分类以卡片上修改后的值为准 */
    private fun confirmInsert(
        context: Context,
        parsed: ParsedPayment,
        pkg: String,
        origin: String,
        isIncome: Boolean,
        merchant: String?,
        note: String?,
        category: String,
    ) {
        scope.launch {
            val container = (context.applicationContext as? BookkeepingApp)?.container ?: return@launch
            val tradeTime = effectiveTradeTime(parsed, System.currentTimeMillis())
            // 确认期间可能已手动记过或另一张卡片入过库
            if (container.transactionRepository.hasSimilar(parsed.amount, tradeTime)) {
                CaptureDebug.record(pkg, origin, "确认入库时发现已有同额相近记录，跳过", emptyList())
                return@launch
            }
            val finalMerchant = merchant?.takeIf { it.isNotBlank() }
            val finalNote = note?.takeIf { it.isNotBlank() }
            val finalCategory = category.ifBlank {
                container.ruleEngine.categorize(
                    text = listOfNotNull(finalMerchant, finalNote).joinToString(" "),
                    isIncome = isIncome,
                    merchant = finalMerchant,
                )
            }
            // 用户确认的分类即最高价值的学习样本：记住这个商户，下次直接命中
            container.transactionRepository.learnFromConfirm(finalMerchant, finalCategory, isIncome)
            val signed = if (isIncome) parsed.amount else -parsed.amount
            val entity = TransactionEntity(
                amount = signed,
                category = finalCategory,
                note = finalNote,
                merchant = finalMerchant,
                timestamp = tradeTime,
                source = "auto",
                rawData = JsonUtil.obj(
                    "origin" to origin,
                    "package" to pkg,
                    "text" to (finalNote ?: parsed.description ?: ""),
                    "confirmed" to true,
                ),
                // 时间按 10 分钟取桶：同一笔支付被多条路径先后抓到时哈希一致，数据库唯一哈希兜底
                hash = HashUtil.transactionHash(
                    tradeTime / AUTO_HASH_BUCKET_MS * AUTO_HASH_BUCKET_MS,
                    signed,
                    finalMerchant,
                    "auto",
                ),
            )
            val inserted = container.transactionRepository.insertIfNew(entity)
            if (inserted) {
                CaptureDebug.record(
                    pkg, origin,
                    "已确认入库：${if (isIncome) "收入" else "支出"} ¥${parsed.amount} → $finalCategory",
                    emptyList(),
                )
                if (container.settings.snapshot().notifyOnRecord) {
                    showRecordedNotification(context, signed, entity.category)
                }
            }
        }
    }

    /**
     * 悬浮窗被拒/未授权时的降级提醒（5 分钟限流）。
     * MIUI/HyperOS 除「显示悬浮窗」外还需「后台弹出界面」「锁屏显示」，
     * 通知给出标准悬浮窗页与应用信息页两个入口。
     */
    private fun promptOverlayPermission(context: Context, reason: String? = null) {
        val now = System.currentTimeMillis()
        if (now - lastPermissionPromptAt < 5 * 60 * 1000L) return
        lastPermissionPromptAt = now
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            val channelId = "${CHANNEL_ID}_permission"
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId,
                    context.getString(R.string.channel_auto_record_permission),
                    android.app.NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            val overlayIntent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
            val detailsIntent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            )
            val pendingOverlay = android.app.PendingIntent.getActivity(
                context, 1, overlayIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val pendingDetails = android.app.PendingIntent.getActivity(
                context, 2, detailsIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (reason == null) {
                context.getString(R.string.notif_overlay_needed)
            } else {
                context.getString(R.string.notif_overlay_rejected, reason)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.nonen.Bookkeeping.R.drawable.ic_launcher_foreground)
                .setContentTitle(
                    context.getString(
                        if (reason == null) R.string.notif_pending_title else R.string.notif_pending_title_failed,
                    ),
                )
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .addAction(0, context.getString(R.string.notif_action_overlay_settings), pendingOverlay)
                .addAction(0, context.getString(R.string.notif_action_app_info), pendingDetails)
                .setContentIntent(pendingOverlay)
                .setAutoCancel(true)
                .build()
            manager.notify(PERMISSION_NOTIFICATION_ID, notification)
        }.onFailure { android.util.Log.w(TAG, "悬浮窗权限提醒发送失败", it) }
    }

    private fun showRecordedNotification(context: Context, signedAmount: Double, category: String) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_auto_record),
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val direction = context.getString(
                if (signedAmount < 0) R.string.type_expense else R.string.type_income,
            )
            val text = "$category ¥${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(signedAmount))}"
            val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.nonen.Bookkeeping.R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notif_recorded_title, direction))
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { android.util.Log.w(TAG, "记账结果通知发送失败", it) }
    }
}
