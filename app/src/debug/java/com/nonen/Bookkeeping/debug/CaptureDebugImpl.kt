package com.nonen.Bookkeeping.debug

import android.content.Context
import com.nonen.Bookkeeping.core.AccessibilityUtil
import com.nonen.Bookkeeping.service.AutoRecordDebugStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 抓取调试真实实现（src/debug 源集，仅 test 分支存在，debug 构建时覆盖主源集的空实现，
 * release 构建不参与编译）。开关状态持久化在独立 SharedPreferences，不占用 SettingsStore。
 */
object CaptureDebug {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report

    fun init(context: Context) {
        val app = context.applicationContext
        _enabled.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, value)
            .apply()
        _enabled.value = value
        _report.value = if (value) buildReport(context) else null
    }

    fun refresh(context: Context) {
        _report.value = buildReport(context)
    }

    fun clear(context: Context) {
        AutoRecordDebugStore.clear()
        _report.value = buildReport(context)
    }

    fun onEvent(pkg: String) = AutoRecordDebugStore.onEvent(pkg)

    fun record(pkg: String, origin: String, result: String, texts: List<String>) {
        if (!_enabled.value) return
        AutoRecordDebugStore.record(pkg, origin, result, texts)
    }

    fun recordThrottled(pkg: String, origin: String, result: String, texts: List<String>) {
        if (!_enabled.value) return
        AutoRecordDebugStore.recordThrottled(pkg, origin, result, texts)
    }

    fun appNameOf(pkg: String): String = AutoRecordDebugStore.appNameOf(pkg)

    private fun buildReport(context: Context): String =
        AutoRecordDebugStore.buildReport(AccessibilityUtil.isServiceEnabled(context.applicationContext))

    private const val PREFS = "capture_debug"
    private const val KEY = "enabled"
}
