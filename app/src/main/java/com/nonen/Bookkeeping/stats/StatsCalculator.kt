package com.nonen.Bookkeeping.stats

import com.nonen.Bookkeeping.data.db.TransactionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min

/**
 * 统计聚合计算：把「周期 + 下钻选择」解析成日期区间，再对区间内账单做汇总。
 *
 * 从 StatsViewModel 抽出为纯计算（不依赖 Android / Compose），
 * 数据来源通过 [compute] 的 fetch 回调注入，便于单测覆盖各种周期与切片。
 */
object StatsCalculator {

    /**
     * @param fetch 按 [startMillis, endMillis] 读取账单（含边界）
     * @param today 计算基准日，测试时可注入固定日期
     */
    suspend fun compute(
        query: StatsQuery,
        fetch: suspend (startMillis: Long, endMillis: Long) -> List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
    ): StatsData? {
        val zone = ZoneId.systemDefault()
        fun startMillis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        fun endMillis(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val range = resolveRange(query, today) ?: return null
        val start = range.start
        val end = range.end
        val signMatch: (Double) -> Boolean = if (query.isIncome) { v -> v > 0 } else { v -> v < 0 }

        var total = 0.0
        var count = 0
        val bucketTotals = HashMap<Int, Double>()
        val catAmount = HashMap<String, Double>()
        val catCount = HashMap<String, Int>()

        for (t in fetch(startMillis(start), endMillis(end))) {
            if (!signMatch(t.amount)) continue
            val v = abs(t.amount)
            total += v
            count++
            val d = localDateOf(t.timestamp, zone)
            val bIdx = if (range.monthBuckets) {
                (d.year - start.year) * 12 + (d.monthValue - start.monthValue)
            } else {
                ChronoUnit.DAYS.between(start, d).toInt()
            }
            if (bIdx >= 0) bucketTotals[bIdx] = (bucketTotals[bIdx] ?: 0.0) + v
            catAmount[t.category] = (catAmount[t.category] ?: 0.0) + v
            catCount[t.category] = (catCount[t.category] ?: 0) + 1
        }

        // 趋势分桶：年(整年)/超长自定义 → 按月；年+选月 → 按日；周/月切片 → 7 天；其余 → 按日
        val buckets: List<StatsBucket> = when {
            range.monthBuckets -> {
                val months = ChronoUnit.MONTHS.between(
                    start.withDayOfMonth(1), end.plusDays(1).withDayOfMonth(1)
                ).toInt()
                (0 until months).map { i ->
                    StatsBucket(BucketLabelKind.MONTH, start.plusMonths(i.toLong()).monthValue, bucketTotals[i] ?: 0.0)
                }
            }
            query.period == StatsPeriod.YEAR && query.monthSel != null ->
                (0 until start.lengthOfMonth()).map { i ->
                    StatsBucket(BucketLabelKind.DAY, i + 1, bucketTotals[i] ?: 0.0)
                }
            range.weekdayLabels -> {
                val len = ChronoUnit.DAYS.between(start, end).toInt() + 1
                (0 until len).map { i ->
                    val wd = start.plusDays(i.toLong()).dayOfWeek.value % 7 // ISO 周一=1…周日=7 → 日=0
                    StatsBucket(BucketLabelKind.WEEKDAY, wd, bucketTotals[i] ?: 0.0)
                }
            }
            else -> {
                val len = ChronoUnit.DAYS.between(start, end).toInt() + 1
                (0 until len).map { i ->
                    StatsBucket(BucketLabelKind.DAY, start.plusDays(i.toLong()).dayOfMonth, bucketTotals[i] ?: 0.0)
                }
            }
        }

        // 环比上期：月→上个自然月；年→上一年；周/切片/自定义→等长前置窗口
        val prev = resolvePrevRange(query, start, end)
        var prevTotal = 0.0
        for (t in fetch(startMillis(prev.first), endMillis(prev.second))) {
            if (signMatch(t.amount)) prevTotal += abs(t.amount)
        }

        // 日均：整个周期的天数
        val days = when (query.period) {
            StatsPeriod.WEEK -> 7
            StatsPeriod.MONTH ->
                if (query.weekSel != null) ChronoUnit.DAYS.between(start, end).toInt() + 1 else start.lengthOfMonth()
            StatsPeriod.YEAR -> if (query.monthSel != null) start.lengthOfMonth() else start.lengthOfYear()
            StatsPeriod.CUSTOM -> ChronoUnit.DAYS.between(start, end).toInt() + 1
        }.coerceAtLeast(1)

        val categories = catAmount.entries
            .map { (c, v) -> CategoryStat(c, v, catCount[c] ?: 0, if (total > 0) (v / total).toFloat() else 0f) }
            .sortedByDescending { it.amount }

        return StatsData(range.title, total, count, prevTotal, total / days, buckets, categories)
    }

    /** 解析后的统计区间 */
    private data class DateRange(
        val start: LocalDate,
        val end: LocalDate,
        val title: StatsTitle,
        val weekdayLabels: Boolean = false,
        val monthBuckets: Boolean = false,
    )

    private fun resolveRange(query: StatsQuery, today: LocalDate): DateRange? =
        when (query.period) {
            StatsPeriod.WEEK -> {
                val offset = query.weekSel ?: 0 // null=本周, -1=上周
                val start = today.with(DayOfWeek.MONDAY).plusDays(offset * 7L)
                DateRange(
                    start = start,
                    end = start.plusDays(6),
                    title = if (offset < 0) StatsTitle.LastWeek else StatsTitle.ThisWeek,
                    weekdayLabels = true,
                )
            }

            StatsPeriod.MONTH -> {
                val slices = weekSlicesOfMonth(today.year, today.monthValue)
                val w = query.weekSel
                if (w != null && slices.isNotEmpty()) {
                    val idx = (w - 1).coerceIn(0, slices.size - 1)
                    DateRange(
                        start = today.withDayOfMonth(slices[idx].first),
                        end = today.withDayOfMonth(slices[idx].second),
                        title = StatsTitle.MonthWeek(idx + 1),
                        weekdayLabels = true,
                    )
                } else {
                    DateRange(
                        start = today.withDayOfMonth(1),
                        end = today.withDayOfMonth(today.lengthOfMonth()),
                        title = StatsTitle.ThisMonth,
                    )
                }
            }

            StatsPeriod.YEAR -> {
                val m = query.monthSel
                if (m != null) {
                    val start = LocalDate.of(today.year, m, 1)
                    DateRange(start = start, end = start.withDayOfMonth(start.lengthOfMonth()), title = StatsTitle.MonthOfYear(m))
                } else {
                    DateRange(
                        start = LocalDate.of(today.year, 1, 1),
                        end = LocalDate.of(today.year, 12, 31),
                        title = StatsTitle.ThisYear,
                        monthBuckets = true,
                    )
                }
            }

            StatsPeriod.CUSTOM -> {
                val s = query.customStart ?: return null
                val e = query.customEnd ?: return null
                val start = minOf(s, e)
                val end = maxOf(s, e)
                DateRange(
                    start = start,
                    end = end,
                    title = StatsTitle.Custom,
                    monthBuckets = ChronoUnit.DAYS.between(start, end) > 92,
                )
            }
        }

    private fun resolvePrevRange(query: StatsQuery, start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate> = when {
        query.period == StatsPeriod.MONTH && query.weekSel == null -> {
            val pm = start.minusMonths(1)
            pm.withDayOfMonth(1) to pm.withDayOfMonth(pm.lengthOfMonth())
        }

        query.period == StatsPeriod.YEAR && query.monthSel == null -> start.minusYears(1) to end.minusYears(1)

        query.period == StatsPeriod.YEAR -> {
            val pm = start.minusMonths(1)
            pm.withDayOfMonth(1) to pm.withDayOfMonth(pm.lengthOfMonth())
        }

        else -> {
            val len = ChronoUnit.DAYS.between(start, end) + 1
            val prevEnd = start.minusDays(1)
            prevEnd.minusDays(len - 1) to prevEnd
        }
    }

    /** 某月按 7 天切片的周区间（第 1 周从 1 号开始） */
    private fun weekSlicesOfMonth(year: Int, month: Int): List<Pair<Int, Int>> {
        val len = LocalDate.of(year, month, 1).lengthOfMonth()
        val out = ArrayList<Pair<Int, Int>>()
        var s = 1
        while (s <= len) {
            out.add(s to min(s + 6, len))
            s += 7
        }
        return out
    }

    private fun localDateOf(ts: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
}
