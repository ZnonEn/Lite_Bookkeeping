package com.nonen.Bookkeeping.stats

import com.nonen.Bookkeeping.data.db.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 统计聚合：周期解析、分桶、环比与分类汇总（重构后抽出为纯函数，可脱离 Android 测试） */
class StatsCalculatorTest {

    private val zone = ZoneId.systemDefault()

    private fun tx(amount: Double, category: String, date: LocalDate) = TransactionEntity(
        amount = amount,
        category = category,
        timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        source = "manual",
        hash = "h-${amount}-$category-$date",
    )

    private fun millisOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 用给定账单构造 fetch 回调：按区间过滤（含边界） */
    private fun fetcher(all: List<TransactionEntity>) =
        { start: Long, end: Long -> all.filter { it.timestamp in start..end } }

    private fun compute(
        query: StatsQuery,
        all: List<TransactionEntity>,
        today: LocalDate,
    ) = runBlocking { StatsCalculator.compute(query, fetcher(all), today) }

    @Test
    fun `month period sums only the matching sign and groups by category`() {
        val today = LocalDate.of(2026, 3, 15)
        val bills = listOf(
            tx(-100.0, "餐饮", today),
            tx(-50.0, "餐饮", today.minusDays(1)),
            tx(-30.0, "交通", today.minusDays(2)),
            tx(200.0, "工资", today),          // 收入，支出统计应忽略
            tx(-999.0, "购物", today.minusMonths(1)), // 上月，应忽略
        )
        val data = compute(StatsQuery(StatsPeriod.MONTH, isIncome = false), bills, today)!!

        assertEquals(StatsTitle.ThisMonth, data.title)
        assertEquals(180.0, data.total, 0.001)
        assertEquals(3, data.count)
        // 分类按金额倒序
        assertEquals("餐饮", data.categories.first().category)
        assertEquals(150.0, data.categories.first().amount, 0.001)
        assertEquals(2, data.categories.first().count)
        // 占比：150/180
        assertEquals(150.0 / 180.0, data.categories.first().percent.toDouble(), 0.001)
    }

    @Test
    fun `month bucket count equals days in month and labels are day numbers`() {
        val today = LocalDate.of(2026, 2, 10)
        val data = compute(StatsQuery(StatsPeriod.MONTH, isIncome = false), emptyList(), today)!!
        assertEquals(28, data.buckets.size) // 2026-02 有 28 天
        assertEquals(BucketLabelKind.DAY, data.buckets.first().labelKind)
        assertEquals(1, data.buckets.first().labelValue)
        assertEquals(BucketLabelKind.DAY, data.buckets.last().labelKind)
        assertEquals(28, data.buckets.last().labelValue)
    }

    @Test
    fun `month week slice uses 7 day window with weekday labels`() {
        val today = LocalDate.of(2026, 3, 15)
        val data = compute(StatsQuery(StatsPeriod.MONTH, isIncome = false, weekSel = 1), emptyList(), today)!!
        assertEquals(StatsTitle.MonthWeek(1), data.title)
        assertEquals(7, data.buckets.size)
        // 第 1 周从 1 号开始：2026-03-01 是周日
        assertEquals(BucketLabelKind.WEEKDAY, data.buckets.first().labelKind)
        assertEquals(0, data.buckets.first().labelValue)
    }

    @Test
    fun `year period without month selection buckets by month`() {
        val today = LocalDate.of(2026, 6, 1)
        val bills = listOf(
            tx(-10.0, "餐饮", LocalDate.of(2026, 1, 5)),
            tx(-20.0, "餐饮", LocalDate.of(2026, 3, 5)),
        )
        val data = compute(StatsQuery(StatsPeriod.YEAR, isIncome = false), bills, today)!!
        assertEquals(StatsTitle.ThisYear, data.title)
        assertEquals(12, data.buckets.size)
        assertEquals(BucketLabelKind.MONTH, data.buckets[0].labelKind)
        assertEquals(1, data.buckets[0].labelValue)
        assertEquals(10.0, data.buckets[0].value, 0.001)
        assertEquals(20.0, data.buckets[2].value, 0.001)
        assertEquals(30.0, data.total, 0.001)
    }

    @Test
    fun `year with month selection buckets by day`() {
        val today = LocalDate.of(2026, 6, 1)
        val data = compute(StatsQuery(StatsPeriod.YEAR, isIncome = false, monthSel = 2), emptyList(), today)!!
        assertEquals(StatsTitle.MonthOfYear(2), data.title)
        assertEquals(28, data.buckets.size)
    }

    @Test
    fun `week period previous week offset uses equal length window for compare`() {
        val today = LocalDate.of(2026, 3, 18) // 周三
        val thisWeek = today.with(java.time.DayOfWeek.MONDAY)
        val bills = listOf(
            tx(-70.0, "餐饮", thisWeek),
            tx(-30.0, "餐饮", thisWeek.minusWeeks(1)),
        )
        val data = compute(StatsQuery(StatsPeriod.WEEK, isIncome = false), bills, today)!!
        assertEquals(StatsTitle.ThisWeek, data.title)
        assertEquals(70.0, data.total, 0.001)
        assertEquals(30.0, data.prevTotal, 0.001)
    }

    @Test
    fun `income mode sums positive amounts only`() {
        val today = LocalDate.of(2026, 3, 15)
        val bills = listOf(tx(500.0, "工资", today), tx(-100.0, "餐饮", today))
        val data = compute(StatsQuery(StatsPeriod.MONTH, isIncome = true), bills, today)!!
        assertEquals(StatsTitle.ThisMonth, data.title)
        assertEquals(500.0, data.total, 0.001)
    }

    @Test
    fun `custom period without both dates returns null`() {
        val today = LocalDate.of(2026, 3, 15)
        assertNull(compute(StatsQuery(StatsPeriod.CUSTOM, isIncome = false), emptyList(), today))
        assertNull(
            compute(
                StatsQuery(StatsPeriod.CUSTOM, isIncome = false, customStart = today),
                emptyList(),
                today,
            ),
        )
    }

    @Test
    fun `custom period longer than 92 days buckets by month`() {
        val today = LocalDate.of(2026, 3, 15)
        val data = compute(
            StatsQuery(
                StatsPeriod.CUSTOM,
                isIncome = false,
                customStart = LocalDate.of(2026, 1, 1),
                customEnd = LocalDate.of(2026, 6, 30),
            ),
            emptyList(),
            today,
        )!!
        assertEquals(StatsTitle.Custom, data.title)
        assertEquals(6, data.buckets.size)
        assertEquals(BucketLabelKind.MONTH, data.buckets.first().labelKind)
        assertEquals(1, data.buckets.first().labelValue)
    }

    @Test
    fun `empty data yields zero totals and empty categories`() {
        val today = LocalDate.of(2026, 3, 15)
        val data = compute(StatsQuery(StatsPeriod.MONTH, isIncome = false), emptyList(), today)!!
        assertNotNull(data)
        assertEquals(0.0, data.total, 0.001)
        assertEquals(0, data.count)
        assertEquals(0.0, data.dailyAvg, 0.001)
        assertEquals(0, data.categories.size)
    }
}
