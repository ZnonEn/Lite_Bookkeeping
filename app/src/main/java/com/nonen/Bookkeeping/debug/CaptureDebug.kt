package com.nonen.Bookkeeping.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 抓取调试统一入口（主源集内是空实现）。
 *
 * test 分支在 src/debug/java 下有同路径的真实实现：debug 构建时构建类型源集在编译期
 * 覆盖本文件，带出完整调试面板；release 构建只编译本文件，调试代码零残留。
 * 因此共享代码直接调用即可——不要加任何开关判断，也不要在两个分支间修改本文件。
 */
object CaptureDebug {
    val enabled: StateFlow<Boolean> = MutableStateFlow(false)
    val report: StateFlow<String?> = MutableStateFlow(null)

    /** Application.onCreate 里调用一次，恢复持久化的开关状态 */
    fun init(context: Context) {}

    fun setEnabled(context: Context, value: Boolean) {}
    fun refresh(context: Context) {}
    fun clear(context: Context) {}

    /** 目标应用每来一个无障碍事件记一次心跳 */
    fun onEvent(pkg: String) {}

    fun record(pkg: String, origin: String, result: String, texts: List<String>) {}

    /** 同类高频信息限流记录（3 秒一条），避免滚动页面时刷屏 */
    fun recordThrottled(pkg: String, origin: String, result: String, texts: List<String>) {}

    fun appNameOf(pkg: String): String = pkg.substringAfterLast('.').take(10)
}
