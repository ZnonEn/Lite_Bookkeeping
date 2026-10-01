package com.nonen.Bookkeeping.ui.screens

import com.nonen.Bookkeeping.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

/**
 * 首页日期跟随的纯计算部分：近7日窗口、按日分组、月份归属。
 * 这些函数以前隐含「只在 ViewModel 创建时算一次」，也是首页卡在旧日期的一部分成因。
 */
class HomePeriodTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun txAt(date: LocalDate, amount: Double, id: Long = 0L): TransactionEntity {
        val ts = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return TransactionEntity(
            id = id,
            amount = amount,
            category = "餐饮",
            timestamp = ts,
            merchant = null,
            note = null,
            source = "manual",
            hash = "hash-$id",
        )
    }

    // --- 近7日窗口：随「今天」滑动，跨月时不能被月份截断 ---

    @Test
    fun `窗口含今天且向前共7天`() {
        val today = LocalDate.of(2026, 10, 1)
        val (start, end) = weekRangeOf(today)
        assertEquals(LocalDate.of(2026, 9, 25), millisToDate(start))
        assertEquals("止端不含次日", LocalDate.of(2026, 10, 2), millisToDate(end))
        assertEquals("正好 7 天", 7L, (end - start) / 86_400_000L)
    }

    @Test
    fun `跨月的近7日不会被月初截断`() {
        // 10 月 1 日的近7日应回到 9 月 25 日，而不是停在 10 月 1 日
        val (start, _) = weekRangeOf(LocalDate.of(2026, 10, 1))
        assertTrue("窗口起点应落在上个月", millisToDate(start).monthValue == 9)
    }

    @Test
    fun `换一天窗口整体平移一天`() {
        val (s1, e1) = weekRangeOf(LocalDate.of(2026, 10, 1))
        val (s2, e2) = weekRangeOf(LocalDate.of(2026, 10, 2))
        assertEquals(86_400_000L, s2 - s1)
        assertEquals(86_400_000L, e2 - e1)
    }

    @Test
    fun `跨年当天窗口仍然连续`() {
        val (start, end) = weekRangeOf(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 26), millisToDate(start))
        assertEquals(LocalDate.of(2027, 1, 2), millisToDate(end))
    }

    // --- 近7日柱状图：固定 7 个点、按传入的今天对齐、无数据补零 ---

    @Test
    fun `柱状图按传入的今天生成7个数据点`() {
        val today = LocalDate.of(2026, 10, 1)
        val bars = weekBarsOf(listOf(txAt(today, -20.0)), today)
        assertEquals(7, bars.size)
        assertEquals("最后一个是今天", today, bars.last().date)
        assertEquals("最早一个是6天前", today.minusDays(6), bars.first().date)
        assertEquals(20.0, bars.last().expense, 1e-9)
        assertEquals("没有账单的天补零", 0.0, bars.first().expense, 1e-9)
    }

    @Test
    fun `柱状图把窗口外的账单排除在外`() {
        val today = LocalDate.of(2026, 10, 1)
        val bars = weekBarsOf(listOf(txAt(today.minusDays(7), -99.0)), today)
        assertEquals("第 8 天前的账单不在窗口内", 0.0, bars.sumOf { it.expense }, 1e-9)
    }

    // --- 按日分组：日期倒序，与月份归属一致 ---

    @Test
    fun `当月汇总按日期倒序分组`() {
        val summary = summarizeMonth(
            listOf(
                txAt(LocalDate.of(2026, 10, 1), -10.0, 1),
                txAt(LocalDate.of(2026, 10, 3), -30.0, 2),
                txAt(LocalDate.of(2026, 10, 1), 100.0, 3),
            )
        )
        assertEquals(100.0, summary.income, 1e-9)
        assertEquals(40.0, summary.expense, 1e-9)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1)), summary.grouped.map { it.first })
        assertEquals("同一天的账单分到同一组", 2, summary.grouped.last().second.size)
    }

    // --- 月份归属：首页月份必须等于「今天」所在月 ---

    @Test
    fun `跨月时今天的月份归属变成新月份`() {
        assertEquals(YearMonth.of(2026, 9), YearMonth.from(LocalDate.of(2026, 9, 30)))
        assertEquals(YearMonth.of(2026, 10), YearMonth.from(LocalDate.of(2026, 10, 1)))
        assertTrue("跨月判定不依赖时区实现", YearMonth.from(LocalDate.of(2026, 10, 1)) != YearMonth.of(2026, 9))
    }

    // --- 手动翻月后是否继续跟随日历：翻走时尊重用户，选回本月则恢复跟随 ---

    @Test
    fun `翻到别的月份要固定住不被自动跳走`() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals(YearMonth.of(2026, 9), pinnedMonthFor(YearMonth.of(2026, 9), today))
        assertEquals(YearMonth.of(2026, 11), pinnedMonthFor(YearMonth.of(2026, 11), today))
    }

    @Test
    fun `选回本月则恢复跟随日历`() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals(null, pinnedMonthFor(YearMonth.of(2026, 10), today))
    }

    @Test
    fun `跨月后选回新月份也恢复跟随`() {
        // 9 月 30 日翻到 9 月是固定；10 月 1 日再选 10 月应恢复跟随
        val todayNow = LocalDate.of(2026, 10, 1)
        assertEquals(YearMonth.of(2026, 9), pinnedMonthFor(YearMonth.of(2026, 9), todayNow))
        assertEquals(null, pinnedMonthFor(YearMonth.of(2026, 10), todayNow))
    }

    private fun millisToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
