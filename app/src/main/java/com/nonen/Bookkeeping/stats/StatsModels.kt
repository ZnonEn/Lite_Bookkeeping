package com.nonen.Bookkeeping.stats

import androidx.annotation.StringRes
import com.nonen.Bookkeeping.R

/** 统计周期 */
enum class StatsPeriod(@StringRes val labelRes: Int) {
    WEEK(R.string.stats_period_week),
    MONTH(R.string.stats_period_month),
    YEAR(R.string.stats_period_year),
    CUSTOM(R.string.stats_period_custom),
}

/** 趋势图分桶标签的渲染方式：按月 / 按日 / 按星期，均由 UI 层本地化 */
enum class BucketLabelKind { MONTH, DAY, WEEKDAY }

/** 趋势图单个分桶（[labelValue] 的含义由 [labelKind] 决定） */
data class StatsBucket(
    val labelKind: BucketLabelKind,
    val labelValue: Int,
    val value: Double,
)

/** 分类排行项 */
data class CategoryStat(
    val category: String,
    val amount: Double,
    val count: Int,
    val percent: Float,
)

/**
 * 统计结果标题的结构化描述（不含本地化文案，由 UI 层拼装）。
 * 展示形如「本月总支出」「第2周总收入」。
 */
sealed interface StatsTitle {
    data object ThisWeek : StatsTitle
    data object LastWeek : StatsTitle

    /** 本月内的第 [index] 周（1-based） */
    data class MonthWeek(val index: Int) : StatsTitle

    data object ThisMonth : StatsTitle

    /** 本年内指定月份 */
    data class MonthOfYear(val month: Int) : StatsTitle

    data object ThisYear : StatsTitle

    /** 自定义区间 */
    data object Custom : StatsTitle
}

/** 统计页一次查询的完整结果 */
data class StatsData(
    val title: StatsTitle,
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
