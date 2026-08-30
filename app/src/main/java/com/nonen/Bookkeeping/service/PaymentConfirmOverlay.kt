package com.nonen.Bookkeeping.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.nonen.Bookkeeping.core.Categories

/**
 * 自动记账确认卡片（悬浮窗）。
 *
 * 半自动模式：检测到交易后不再静默入库，而是弹出这张卡片让用户核对并**编辑**登记信息——
 * 方向可切换、金额可见、交易对象/备注可直接输入、分类点击展开宫格选择（跟随收支方向），
 * 点「记一笔」按卡片上的最终值入库，「忽略」后由管线进入免打扰期。
 *
 * 需要「显示在其他应用上层」权限；不以标准权限查询为前置门槛（MIUI/HyperOS 误报），
 * addView 被拒时回调 [onError] 降级。
 * 窗口默认不获焦（不抢支付应用的输入）；点输入框时切换为可获焦并把卡片移到顶部，
 * 输入法弹出后可正常打字，失焦或操作完成自动还原到底部。新卡片会替换旧卡片（按忽略处理）。
 * 30 秒无操作自动消失。
 */
object PaymentConfirmOverlay {

    private const val COLOR_INCOME = 0xFF34C759.toInt()
    private const val COLOR_EXPENSE = 0xFFFF9500.toInt()
    private const val CARD_BG = 0xF21C1C20.toInt()
    private const val TEXT_PRIMARY = 0xFFF2F2F7.toInt()
    private const val TEXT_SECONDARY = 0x99FFFFFF.toInt()
    private const val TEXT_DISABLED = 0x66FFFFFF.toInt()

    /** 待确认的一笔交易（展示初值，卡片上可修改） */
    class Card(
        val sourceLabel: String,      // 来源应用：微信 / 支付宝
        val amountText: String,       // 已格式化金额，如 ¥12.00
        val isIncome: Boolean,        // 初始方向
        val counterparty: String?,    // 对方 / 商户初值
        val description: String?,     // 备注初值
        val categoryExpense: String,  // 预测分类（支出）
        val categoryIncome: String,   // 预测分类（收入）
        val timeText: String,         // 入账时间展示
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentDismiss: (() -> Unit)? = null
    private var dismissRunnable: Runnable? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 弹出确认卡片；同一时间只有一张，重复调用会替换旧的（旧卡按「忽略」处理）。
     * [onConfirm] 参数：方向、卡片上修改后的交易对象、备注、分类。
     */
    @Synchronized
    fun show(
        context: Context,
        card: Card,
        onConfirm: (isIncome: Boolean, merchant: String?, note: String?, category: String) -> Unit,
        onDismiss: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val app = context.applicationContext
        mainHandler.post { showInternal(app, card, onConfirm, onDismiss, onError) }
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

    private fun showInternal(
        app: Context,
        card: Card,
        onConfirm: (isIncome: Boolean, merchant: String?, note: String?, category: String) -> Unit,
        onDismiss: () -> Unit,
        onError: (String) -> Unit,
    ) {
        android.util.Log.d("PaymentConfirmOverlay", "showInternal: ${card.sourceLabel} ¥${card.amountText}")
        val replaced = currentDismiss
        removeCurrent()
        replaced?.invoke()

        var isIncome = card.isIncome
        var selectedCategory = if (isIncome) card.categoryIncome else card.categoryExpense
        val density = app.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val wm = app.getSystemService(WindowManager::class.java)
        val imm = app.getSystemService(InputMethodManager::class.java)

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

        // ---- 编辑模式：窗口获焦 + 卡片移到顶部（给输入法让位）----
        var editMode = false
        var rootRef: LinearLayout? = null
        var paramsRef: WindowManager.LayoutParams? = null
        var merchantRef: EditText? = null
        var noteRef: EditText? = null

        fun hideIme() {
            rootRef?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
        }
        fun enterEdit() {
            if (editMode) return
            editMode = true
            val root = rootRef ?: return
            val p = paramsRef ?: return
            runCatching {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                p.y = dp(56)
                wm?.updateViewLayout(root, p)
            }.onFailure { android.util.Log.w("PaymentConfirmOverlay", "enterEdit failed", it) }
        }
        fun exitEdit() {
            hideIme()
            if (!editMode) return
            editMode = false
            val root = rootRef ?: return
            val p = paramsRef ?: return
            runCatching {
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                p.y = dp(72)
                wm?.updateViewLayout(root, p)
            }.onFailure { android.util.Log.w("PaymentConfirmOverlay", "exitEdit failed", it) }
        }

        // ---- 标题 ----
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

        // ---- 方向胶囊 + 金额 ----
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
        val categoryView = value(selectedCategory)
        val gridContainer = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4), 0, dp(4))
        }
        val gridToggle = TextView(app).apply {
            text = "▾"
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
        }

        fun rebuildGrid() {
            gridContainer.removeAllViews()
            val cats = if (isIncome) Categories.incomeCategories else Categories.expenseCategories
            cats.chunked(3).forEach { rowCats ->
                val row = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
                rowCats.forEach { c ->
                    val selected = c == selectedCategory
                    val cell = LinearLayout(app).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(6), dp(8), dp(6), dp(8))
                        background = solid(if (selected) COLOR_INCOME else 0x22FFFFFF.toInt(), 10)
                        addView(TextView(app).apply {
                            text = Categories.emoji(c)
                            textSize = 18f
                        })
                        addView(TextView(app).apply {
                            text = c
                            textSize = 11f
                            setTextColor(if (selected) 0xFF10250F.toInt() else TEXT_PRIMARY)
                        })
                        setOnClickListener {
                            selectedCategory = c
                            categoryView.text = c
                            rebuildGrid()
                            exitEdit()
                        }
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = dp(6)
                        }
                    }
                    row.addView(cell)
                }
                repeat(3 - rowCats.size) {
                    row.addView(View(app), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                gridContainer.addView(row)
            }
        }
        fun toggleGrid() {
            if (gridContainer.visibility == View.VISIBLE) {
                gridContainer.visibility = View.GONE
                gridToggle.text = "▾"
            } else {
                rebuildGrid()
                gridContainer.visibility = View.VISIBLE
                gridToggle.text = "▴"
            }
        }

        fun refreshSelection() {
            expenseChip.setTextColor(if (!isIncome) 0xFF10250F.toInt() else TEXT_DISABLED)
            incomeChip.setTextColor(if (isIncome) 0xFF10250F.toInt() else TEXT_DISABLED)
            expenseChip.background = solid(if (!isIncome) COLOR_INCOME else Color.TRANSPARENT)
            incomeChip.background = solid(if (isIncome) COLOR_INCOME else Color.TRANSPARENT)
            amountView.setTextColor(if (isIncome) COLOR_INCOME else COLOR_EXPENSE)
            val list = if (isIncome) Categories.incomeCategories else Categories.expenseCategories
            if (selectedCategory !in list) selectedCategory = list.first()
            categoryView.text = selectedCategory
            if (gridContainer.visibility == View.VISIBLE) rebuildGrid()
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

        // ---- 可编辑字段：交易对象 / 备注 ----
        fun editField(hint: String, initial: String) = EditText(app).apply {
            setText(initial)
            this.hint = hint
            setHintTextColor(TEXT_DISABLED)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            background = solid(0x22FFFFFF.toInt(), 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val merchantEdit = editField("对方 / 商户（可修改）", card.counterparty.orEmpty())
        val noteEdit = editField("备注（可修改）", card.description.orEmpty())
        noteEdit.imeOptions = EditorInfo.IME_ACTION_DONE
        noteEdit.setOnEditorActionListener { v, _, _ ->
            v.clearFocus()
            true
        }
        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enterEdit()
            } else {
                rootRef?.post {
                    if (merchantRef?.hasFocus() != true && noteRef?.hasFocus() != true) exitEdit()
                }
            }
        }
        merchantEdit.onFocusChangeListener = focusListener
        noteEdit.onFocusChangeListener = focusListener
        merchantRef = merchantEdit
        noteRef = noteEdit
        fun wireEdit(edit: EditText) {
            edit.setOnTouchListener { v, _ ->
                enterEdit()
                v.post { runCatching { imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) } }
                false
            }
        }
        wireEdit(merchantEdit)
        wireEdit(noteEdit)

        fun editRow(labelText: String, edit: EditText) = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            val l = label(labelText)
            l.setPadding(0, 0, dp(12), 0)
            addView(l)
            addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun valueRow(labelText: String, valueView: TextView) = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            val l = label(labelText)
            l.setPadding(0, 0, dp(12), 0)
            addView(l)
            addView(valueView)
        }

        // ---- 分类（点击展开宫格）----
        val categoryRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { toggleGrid() }
            val l = label("分类")
            l.setPadding(0, 0, dp(12), 0)
            addView(l)
            addView(categoryView)
            addView(TextView(app).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(gridToggle)
        }

        // ---- 按钮 ----
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
                val merchant = merchantEdit.text.toString().trim().ifEmpty { null }
                val note = noteEdit.text.toString().trim().ifEmpty { null }
                val category = selectedCategory
                removeCurrent()
                onConfirm(finalIncome, merchant, note, category)
            })
        }

        // ---- 组装 ----
        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = solid(CARD_BG, 20)
            setPadding(dp(20), dp(16), dp(20), dp(14))
            elevation = dp(10).toFloat()
            addView(titleRow)
            addView(amountRow)
            addView(editRow("对方", merchantEdit))
            addView(editRow("备注", noteEdit))
            addView(categoryRow)
            addView(gridContainer)
            addView(valueRow("时间", value(card.timeText)))
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
        rootRef = root
        paramsRef = params

        val dismiss = {
            hideIme()
            removeCurrent()
            onDismiss()
        }
        val timeout = Runnable { dismiss() }
        dismissRunnable = timeout
        mainHandler.postDelayed(timeout, 30_000L)

        try {
            wm?.addView(root, params)
            currentView = root
            currentDismiss = dismiss
        } catch (t: Throwable) {
            // 系统真的拒绝了悬浮窗（含 MIUI/HyperOS 的询问/拒绝模式）：不再静默，降级给管线提示
            android.util.Log.w("PaymentConfirmOverlay", "addView failed", t)
            onError(t.message ?: t.javaClass.simpleName)
        }
    }
}
