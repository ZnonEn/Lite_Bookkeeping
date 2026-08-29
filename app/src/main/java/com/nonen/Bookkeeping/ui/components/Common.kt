package com.nonen.Bookkeeping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.ui.motion.rememberPressScale
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun formatAmount(amount: Double): String {
    val value = String.format(Locale.US, "%.2f", abs(amount))
    return if (amount < 0) "-¥$value" else "+¥$value"
}

fun formatSignedPlain(amount: Double): String {
    val value = String.format(Locale.US, "%.2f", abs(amount))
    return if (amount < 0) "-¥$value" else "¥$value"
}

fun formatPlainAmount(amount: Double): String = String.format(Locale.US, "%.2f", amount)

fun formatTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

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

/**
 * 交易行卡片：46dp 分类 emoji 圆角芯片（收支色 10% 底）+ 分类/备注 + 右对齐金额与日期。
 */
@Composable
fun TransactionRow(tx: TransactionEntity, onClick: () -> Unit) {
    val isExpense = tx.amount < 0
    val accent = if (isExpense) ExpenseColor else IncomeColor
    val title = tx.merchant?.takeIf { it.isNotBlank() }
        ?: tx.note?.takeIf { it.isNotBlank() }
        ?: tx.category
    val sub = tx.note?.takeIf { it.isNotBlank() && it != title }
    val (pressSource, pressScale) = rememberPressScale(0.98f)

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .then(pressScale)
            .clickable(interactionSource = pressSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = Categories.emoji(tx.category), fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAmount(tx.amount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = monthDayLabel(tx.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 日分组头：今天/昨天/M月d日 + 当日收支（收支色）。
 */
@Composable
fun DayHeader(date: LocalDate, income: Double, expense: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dateLabel(date),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (expense > 0) {
                Text(
                    "支 ¥${formatPlainAmount(expense)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseColor,
                )
            }
            if (income > 0) {
                Text(
                    "收 ¥${formatPlainAmount(income)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IncomeColor,
                )
            }
        }
    }
}

/** 首页大卡片用的纯色状态点（备用） */
@Composable
fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}
