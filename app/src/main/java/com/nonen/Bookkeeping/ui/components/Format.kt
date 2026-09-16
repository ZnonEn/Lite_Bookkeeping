package com.nonen.Bookkeeping.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 全局格式化工具：金额、日期、时间。
 * UI 层各处只应调用这里的函数，避免格式化规则散落导致不一致。
 */

fun formatAmount(amount: Double): String {
    val value = String.format(Locale.US, "%.2f", abs(amount))
    return if (amount < 0) "-¥$value" else "+¥$value"
}

fun formatSignedPlain(amount: Double): String {
    val value = String.format(Locale.US, "%.2f", abs(amount))
    return if (amount < 0) "-¥$value" else "¥$value"
}

fun formatPlainAmount(amount: Double): String = String.format(Locale.US, "%.2f", amount)

/**
 * 图表轴/柱顶的紧凑金额：125 / 10.9 / 1.2万。
 * 空间紧张处（迷你柱、统计柱、环形图中心）统一用它。
 */
fun formatCompactAmount(v: Double): String = when {
    v >= 10000 -> String.format(Locale.US, "%.1f万", v / 10000)
    v >= 100 -> String.format(Locale.US, "%.0f", v)
    v >= 10 -> String.format(Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}

fun formatDateTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

fun formatDate(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

fun localDateOf(ts: Long): LocalDate =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

/** 日期分组标签：今天 / 昨天 / M月d日 */
fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
    }
}

fun monthDayLabel(ts: Long): String =
    localDateOf(ts).format(DateTimeFormatter.ofPattern("M月d日"))
