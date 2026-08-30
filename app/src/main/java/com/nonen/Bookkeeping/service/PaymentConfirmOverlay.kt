package com.nonen.Bookkeeping.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 自动记账确认卡片（悬浮窗）。
 *
 * 半自动模式：检测到交易后不再静默入库，而是弹出这张卡片让用户核对登记信息，
 * 点「记一笔」才写库，「忽略」后由管线进入免打扰期，不再弹同一笔。
 *
 * 需要「显示在其他应用上层」权限（Settings.canDrawOverlays），无权限时由管线发通知引导授权。
 * 窗口不获焦（不抢微信/支付宝的输入与返回键），触摸按钮仍然有效；新卡片会替换旧卡片（按忽略处理）。
 * 30 秒无操作自动消失。
 */
object PaymentConfirmOverlay {

    private const val COLOR_INCOME = 0xFF34C759.toInt()
    private const val COLOR_EXPENSE = 0xFFFF9500.toInt()
    private const val CARD_BG = 0xF21C1C20.toInt()
    private const val TEXT_PRIMARY = 0xFFF2F2F7.toInt()
    private const val TEXT_SECONDARY = 0x99FFFFFF.toInt()
    private const val TEXT_DISABLED = 0x66FFFFFF.toInt()

    /** 待确认的一笔交易（展示数据已备好，方向可在卡片上切换） */
    class Card(
        val sourceLabel: String,      // 来源应用：微信 / 支付宝
        val amountText: String,       // 已格式化金额，如 ¥12.00
        val isIncome: Boolean,        // 初始方向
        val counterparty: String?,    // 对方 / 商户
        val description: String?,     // 说明
        val categoryExpense: String,  // 预测分类（支出）
        val categoryIncome: String,   // 预测分类（收入）
        val timeText: String,         // 入账时间展示
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentDismiss: (() -> Unit)? = null
    private var dismissRunnable: Runnable? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 弹出确认卡片；同一时间只有一张，重复调用会替换旧的（旧卡按「忽略」处理） */
    @Synchronized
    fun show(context: Context, card: Card, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
        if (!canShow(context)) return
        val app = context.applicationContext
        mainHandler.post { showInternal(app, card, onConfirm, onDismiss) }
    }

    /** 关闭当前卡片（不触发任何回调），用于清理 */
    @Synchronized
    fun hide() {
        mainHandler.post { removeCurrent() }
    }

    private fun removeCurrent() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        currentView?.let { v ->
            runCatching { v.context.getSystemService(WindowManager::class.java)?.removeView(v) }
        }
        currentView = null
    }

    private fun showInternal(app: Context, card: Card, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
        val replaced = currentDismiss
        removeCurrent()
        replaced?.invoke()

        var isIncome = card.isIncome
        val dp = app.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * dp).toInt()

        fun solid(color: Int, radiusDp: Int = 12) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

        fun label(text: String) = TextView(app).apply {
            this.text = text
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
        }
        fun value(text: String) = TextView(app).apply {
            this.text = text
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
        }
        fun row(labelText: String, valueView: TextView) = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            val l = label(labelText)
            l.setPadding(0, 0, dp(12), 0)
            addView(l)
            addView(valueView)
        }

        val titleRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label("自动记账 · 请确认"))
            addView(TextView(app).apply {
                text = card.sourceLabel
                setTextColor(TEXT_SECONDARY)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.END
                }
            })
        }

        val expenseChip = TextView(app).apply {
            text = "支出"
            textSize = 14f
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        val incomeChip = TextView(app).apply {
            text = "收入"
            textSize = 14f
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
        }
        val amountView = TextView(app).apply {
            text = card.amountText
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        }
        val categoryView = value(card.categoryExpense)

        fun refreshSelection() {
            val expenseBg = if (!isIncome) solid(COLOR_INCOME) else solid(Color.TRANSPARENT)
            val incomeBg = if (isIncome) solid(COLOR_INCOME) else solid(Color.TRANSPARENT)
            expenseChip.setTextColor(if (!isIncome) 0xFF10250F.toInt() else TEXT_DISABLED)
            incomeChip.setTextColor(if (isIncome) 0xFF10250F.toInt() else TEXT_DISABLED)
            expenseChip.background = expenseBg
            incomeChip.background = incomeBg
            amountView.setTextColor(if (isIncome) COLOR_INCOME else COLOR_EXPENSE)
            categoryView.text = if (isIncome) card.categoryIncome else card.categoryExpense
        }
        expenseChip.setOnClickListener {
            isIncome = false
            refreshSelection()
        }
        incomeChip.setOnClickListener {
            isIncome = true
            refreshSelection()
        }
        refreshSelection()

        val amountRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(6))
            addView(expenseChip)
            addView(incomeChip)
            addView(TextView(app).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            addView(amountView)
        }

        val buttons = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
            fun pill(text: String, accent: Boolean, onClick: () -> Unit) = TextView(app).apply {
                this.text = text
                textSize = 14f
                setPadding(dp(18), dp(8), dp(18), dp(8))
                if (accent) {
                    setTextColor(0xFF10250F.toInt())
                    background = solid(COLOR_INCOME, 14)
                } else {
                    setTextColor(0xB3FFFFFF.toInt())
                    background = solid(0x22FFFFFF.toInt(), 14)
                }
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(10) }
            }
            addView(pill("忽略", accent = false) {
                removeCurrent()
                onDismiss()
            })
            addView(pill("记一笔", accent = true) {
                val finalIncome = isIncome
                removeCurrent()
                onConfirm(finalIncome)
            })
        }

        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = solid(CARD_BG, 20)
            setPadding(dp(20), dp(16), dp(20), dp(14))
            elevation = dp(10).toFloat()
            addView(titleRow)
            addView(amountRow)
            addView(row("对方", value(card.counterparty ?: "—")))
            card.description?.takeIf { it.isNotBlank() }?.let { addView(row("说明", value(it.take(30)))) }
            addView(row("分类", categoryView))
            addView(row("时间", value(card.timeText)))
            addView(buttons)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.horizontalMargin = dp(12).toFloat() / app.resources.displayMetrics.widthPixels
        params.y = dp(72)

        val dismiss = {
            removeCurrent()
            onDismiss()
        }
        val timeout = Runnable { dismiss() }
        dismissRunnable = timeout
        mainHandler.postDelayed(timeout, 30_000L)

        runCatching {
            app.getSystemService(WindowManager::class.java)?.addView(root, params)
            currentView = root
            currentDismiss = dismiss
        }
    }
}
