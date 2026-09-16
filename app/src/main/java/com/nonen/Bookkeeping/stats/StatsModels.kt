package com.nonen.Bookkeeping.stats

/** 统计周期 */
enum class StatsPeriod(val label: String) {
    WEEK("本周"), MONTH("本月"), YEAR("本年"), CUSTOM("自定义")
}

/** 趋势图单个分桶（一天 / 一周 / 一月） */
data class StatsBucket(val label: String, val value: Double)

/** 分类排行项 */
data class CategoryStat(
    val category: String,
    val amount: Double,
    val count: Int,
    val percent: Float,
)

/** 统计页一次查询的完整结果 */
data class StatsData(
    val title: String,
    val total: Double,
    val count: Int,
    val prevTotal: Double,
    val dailyAvg: Double,
    val buckets: List<StatsBucket>,
    val categories: List<CategoryStat>,
)

/** 统计查询条件（与 UI 状态解耦，便于单测） */
data class StatsQuery(
    val period: StatsPeriod,
    val isIncome: Boolean,
    /** 本年模式选中的月份（1-12），null = 整年 */
    val monthSel: Int? = null,
    /** 本周/本月模式选中的切片：本周 0=本周、-1=上周；本月 1-based 周次，null = 整月 */
    val weekSel: Int? = null,
    val customStart: java.time.LocalDate? = null,
    val customEnd: java.time.LocalDate? = null,
)
