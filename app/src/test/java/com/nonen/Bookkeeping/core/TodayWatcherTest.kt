package com.nonen.Bookkeeping.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 纯 JVM 的观察者：不注册系统广播，只走注入的 now / zone */
private fun watcherAt(
    instant: Instant,
    zone: ZoneId = ZoneOffset.UTC,
    registrations: () -> TodayWatcher.Registration = { TodayWatcher.Registration.NONE },
): TodayWatcher = TodayWatcher({ instant }, { zone }, registrations)

/**
 * 首页「今天」跟随逻辑的单测：跨天判定、时区变化、下一个唤醒时刻、广播订阅的注销。
 * 全部在纯 JVM 上跑，不需要 Android 运行时。
 */
class TodayWatcherTest {

    // --- 用户报的场景：10 月 1 日打开应用，首页不该还停在 9 月 ---

    @Test
    fun `跨月后重新确认今天会走到新月份`() {
        var instant = Instant.parse("2026-09-30T23:30:00Z")
        val watcher = TodayWatcher({ instant }, { ZoneOffset.UTC })
        assertEquals(LocalDate.of(2026, 9, 30), watcher.today.value)

        instant = Instant.parse("2026-10-01T00:10:00Z")
        assertTrue("跨天后 refresh() 应报告今天已变化", watcher.refresh())
        assertEquals(LocalDate.of(2026, 10, 1), watcher.today.value)
    }

    @Test
    fun `同一天内重复确认不会重复通知`() {
        val watcher = watcherAt(Instant.parse("2026-10-05T08:00:00Z"))
        assertFalse(watcher.refresh())
        assertFalse(watcher.refresh())
        assertEquals(LocalDate.of(2026, 10, 5), watcher.today.value)
    }

    @Test
    fun `跨年后读到新一年的1月1日`() {
        var instant = Instant.parse("2026-12-31T20:00:00Z")
        val watcher = TodayWatcher({ instant }, { ZoneOffset.UTC })
        instant = Instant.parse("2027-01-01T00:30:00Z")
        assertTrue(watcher.refresh())
        assertEquals(LocalDate.of(2027, 1, 1), watcher.today.value)
    }

    // --- 换时区：同一瞬间在不同时区可能是不同的一天，必须按当下时区重新判定 ---

    @Test
    fun `换时区后同一瞬间可能已经跨天`() {
        val instant = Instant.parse("2026-09-30T16:30:00Z") // UTC 9/30 16:30 = 北京 10/1 00:30
        assertEquals(LocalDate.of(2026, 9, 30), watcherAt(instant, ZoneOffset.UTC).today.value)

        var zone: ZoneId = ZoneOffset.UTC
        val watcher = TodayWatcher({ instant }, { zone })
        zone = ZoneId.of("Asia/Shanghai")
        assertTrue("换到东八区后已经是 10 月 1 日", watcher.refresh())
        assertEquals(LocalDate.of(2026, 10, 1), watcher.today.value)
    }

    @Test
    fun `今天只由当前时区决定不受构造时时区影响`() {
        // 北京 2026-10-01 00:30：UTC 还是 9/30，东八区已经是 10/1
        val instant = Instant.parse("2026-09-30T16:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 1), watcherAt(instant, ZoneId.of("Asia/Shanghai")).today.value)
        assertEquals(LocalDate.of(2026, 9, 30), watcherAt(instant, ZoneOffset.UTC).today.value)
    }

    // --- 前台等到零点：下一个唤醒时刻必须落在下一个零点 ---

    @Test
    fun `下一次唤醒落在下一个零点`() {
        val now = Instant.parse("2026-09-30T13:45:12Z")
        val delay = TodayWatcher.delayUntilNextWakeUp(now, ZoneOffset.UTC)
        assertEquals(Duration.ofHours(10).plusMinutes(14).plusSeconds(48).toMillis(), delay)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), now.plusMillis(delay))
    }

    @Test
    fun `刚到零点不会把下一次唤醒算成零`() {
        val delay = TodayWatcher.delayUntilNextWakeUp(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)
        assertEquals(Duration.ofDays(1).toMillis(), delay)
    }

    @Test
    fun `唤醒时刻至少为正数不会把循环打成死转`() {
        val now = Instant.parse("2026-10-01T00:00:00Z")
        assertTrue("延迟必须 > 0，否则后台循环会空转", TodayWatcher.delayUntilNextWakeUp(now, ZoneOffset.UTC) > 0L)
    }

    @Test
    fun `夏令时切换日按当地零点剩余时长安排`() {
        // 纽约 2026-11-01 01:00 EDT 回拨到 01:00 EST，当天有 25 小时
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-10-31T12:00:00Z") // 当地 08:00 EDT
        val wake = now.plusMillis(TodayWatcher.delayUntilNextWakeUp(now, zone))
        assertEquals("唤醒时刻应是 11 月 1 日的当地零点", LocalDate.of(2026, 11, 1), wake.atZone(zone).toLocalDate())
        assertEquals("当地零点", 0L, wake.atZone(zone).hour.toLong())
        assertEquals("当地时间 00:00", 0L, wake.atZone(zone).minute.toLong())
    }

    // --- 系统广播订阅必须在作用域取消时注销，否则离开页面会泄漏接收器 ---

    @Test
    fun `作用域取消时注销系统广播订阅`() {
        var closed = 0
        val watcher = watcherAt(
            Instant.parse("2026-10-01T08:00:00Z"),
            registrations = { TodayWatcher.Registration { closed++ } },
        )
        val scope = CoroutineScope(Job())
        watcher.register(scope)

        // 取消作用域是异步的，给注销协程一点时间收尾
        scope.cancel()
        val deadline = System.currentTimeMillis() + 2_000
        while (closed == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)

        assertEquals("作用域取消后订阅必须恰好注销一次", 1, closed)
    }

    @Test
    fun `未取消作用域时不会提前注销`() {
        var closed = 0
        val watcher = watcherAt(
            Instant.parse("2026-10-01T08:00:00Z"),
            registrations = { TodayWatcher.Registration { closed++ } },
        )
        val scope = CoroutineScope(Job())
        try {
            watcher.register(scope)
            Thread.sleep(200)
            assertEquals("订阅仍在使用时不应注销", 0, closed)
        } finally {
            scope.cancel()
        }
    }
}
