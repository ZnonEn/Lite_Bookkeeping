package com.nonen.Bookkeeping.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「今天」的时间观察者：跨天 / 改时间 / 换时区后重新读一次日历日，让依赖今天的页面
 * （首页月份、近7日窗口）跟着走，而不是停在首次进入应用那一天。
 *
 * 三条触发通道，缺一不可：
 * - 系统广播（`ACTION_DATE_CHANGED` / `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`）——
 *   应用在后台过夜、用户手动改时间、跨时区飞行时靠它；
 * - 应用在前台跨过零点——零点没有广播，靠 [wakeUps] 在下一个零点（或下次时区切换）唤醒；
 * - 页面重新可见——由使用方在 `ON_RESUME` 调 [refresh]，覆盖前两者都可能漏掉的情况。
 *
 * [today] 只读取「当下系统时间在当前时区是几号」，不存任何日期状态，所以调用时机永远安全。
 * `now` / `zone` / `registrations` 都可注入：单测能纯 JVM 地把时间推过零点、换时区，
 * 不依赖 Android 运行时（见 `TodayWatcherTest`）。
 */
class TodayWatcher(
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val registrations: () -> Registration = { Registration.NONE },
) {

    /** 系统广播订阅句柄；[close] 后不再收到回调 */
    fun interface Registration : AutoCloseable {
        companion object {
            val NONE = Registration { }
        }
    }

    /** 当前日历日；仅在真正跨天时才发射新值 */
    private val _today = MutableStateFlow(readToday())

    val today: StateFlow<LocalDate> = _today.asStateFlow()

    /** 重新读取今天；跨天则通知订阅方，返回值表示今天是否变化 */
    fun refresh(): Boolean {
        val current = readToday()
        return if (current == _today.value) {
            false
        } else {
            _today.value = current
            true
        }
    }

    /**
     * 在订阅期间自动醒来的时刻：下一个零点，或时区规则里的下一次切换（取更早的那个）。
     * 醒来后重新读一次今天；若时区已经变了，会按新时区重新安排。
     */
    fun wakeUps(): Flow<Unit> = callbackFlow {
        // 循环跑在 callbackFlow 自己的作用域里：订阅结束即随作用域一起取消
        launch(Dispatchers.Default) {
            while (true) {
                val current = now()
                delay(delayUntilNextWakeUp(current, zone()))
                refresh()
            }
        }
        awaitClose { }
    }

    /** 订阅期间保持系统广播订阅；[scope] 取消时自动 [Registration.close]，不会泄漏 */
    fun register(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            val registration = registrations()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                runCatching { registration.close() }
            }
        }
    }

    // 用 atZone().toLocalDate() 而不是 LocalDate.ofInstant()：后者要 API 34，本项目 minSdk 26
    private fun readToday(): LocalDate = now().atZone(zone()).toLocalDate()

    companion object {

        /**
         * 生产用法：把系统「日期/时间/时区变了」的广播转发给 [refresh]。
         * 用 [Context.getApplicationContext] 注册，避免持有 Activity 造成泄漏。
         */
        fun of(context: Context): TodayWatcher {
            val appContext = context.applicationContext
            lateinit var watcher: TodayWatcher
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    watcher.refresh()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            watcher = TodayWatcher(
                registrations = {
                    appContext.registerReceiver(receiver, filter)
                    Registration { runCatching { appContext.unregisterReceiver(receiver) } }
                },
            )
            return watcher
        }

        /** 距离下一个可能改变「今天」的时刻还有多久：下一个零点与时区下一次切换取更早者 */
        fun delayUntilNextWakeUp(now: Instant, zone: ZoneId): Long {
            val nextMidnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            val nextZoneChange = zone.rules.nextTransition(now)?.instant
            val next = when {
                nextZoneChange == null -> nextMidnight
                nextZoneChange.isBefore(nextMidnight) -> nextZoneChange
                else -> nextMidnight
            }
            // 至少 1ms：负数会把 wakeUps 的循环打成空转
            return Duration.between(now, next).toMillis().coerceAtLeast(1L)
        }
    }
}
