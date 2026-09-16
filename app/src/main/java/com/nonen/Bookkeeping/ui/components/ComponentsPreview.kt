package com.nonen.Bookkeeping.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.ui.theme.BookkeepingTheme
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import java.time.LocalDate

/**
 * 通用组件预览：在 Android Studio 里打开本文件即可在设计面板中调样式，
 * 无需运行 App 或准备数据。`@Preview` 分屏展示深浅两种主题。
 */

private fun sampleTx(
    amount: Double,
    category: String,
    merchant: String? = null,
    note: String? = null,
    timestamp: Long = System.currentTimeMillis(),
) = TransactionEntity(
    amount = amount,
    category = category,
    merchant = merchant,
    note = note,
    timestamp = timestamp,
    source = "manual",
    hash = "preview-${amount}-$category",
)

@Preview(name = "账单行 · 深色", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Preview(name = "账单行 · 浅色", showBackground = true, backgroundColor = 0xFFF5F5F7)
@Composable
private fun TransactionRowPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(vertical = 8.dp)) {
            TransactionRow(sampleTx(-25.5, "餐饮", merchant = "肯德基", note = "工作日午餐"), onClick = {})
            TransactionRow(sampleTx(8800.0, "工资", merchant = "某公司", note = "八月工资"), onClick = {})
            TransactionRow(sampleTx(-3.5, "交通"), onClick = {})
        }
    }
}

@Preview(name = "日分组头", showBackground = true)
@Composable
private fun DayHeaderPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(vertical = 8.dp)) {
            DayHeader(LocalDate.now(), income = 20.0, expense = 128.5)
            DayHeader(LocalDate.now().minusDays(1), income = 0.0, expense = 9.9)
        }
    }
}

@Preview(name = "空状态", showBackground = true)
@Composable
private fun EmptyStatePreview() {
    BookkeepingTheme(darkTheme = true) {
        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            EmptyState(icon = "✎", text = "还没有账单记录", modifier = Modifier.padding(32.dp))
        }
    }
}

@Preview(name = "分段控件", showBackground = true)
@Composable
private fun AnimatedSegmentedPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            AnimatedSegmented(
                options = listOf("支出", "收入"),
                selectedIndex = 0,
                onSelected = {},
                thumbColor = ExpenseColor,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
            AnimatedSegmented(
                options = listOf("本周", "本月", "本年", "自定义"),
                selectedIndex = 2,
                onSelected = {},
                thumbColor = IncomeColor,
            )
        }
    }
}
