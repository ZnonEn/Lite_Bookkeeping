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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.ui.motion.rememberPressScale
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import java.time.LocalDate

/**
 * 账单行卡片：46dp 分类 emoji 圆角芯片（收支色 10% 底）+ 分类/备注 + 右对齐金额与日期。
 * 首页、搜索页共用。
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

/** 日分组头：今天/昨天/M月d日 + 当日收支（收支色） */
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
                    stringResource(R.string.day_header_expense, formatPlainAmount(expense)),
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseColor,
                )
            }
            if (income > 0) {
                Text(
                    stringResource(R.string.day_header_income, formatPlainAmount(income)),
                    style = MaterialTheme.typography.labelSmall,
                    color = IncomeColor,
                )
            }
        }
    }
}

/** 空状态占位（首页/搜索无结果时共用） */
@Composable
fun EmptyState(
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 36.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
