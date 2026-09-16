package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nonen.Bookkeeping.stats.CategoryStat
import com.nonen.Bookkeeping.stats.BucketLabelKind
import com.nonen.Bookkeeping.stats.StatsBucket
import com.nonen.Bookkeeping.ui.theme.BookkeepingTheme
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import java.time.LocalDate
import java.time.YearMonth

/**
 * 页面级组件预览（首页卡片 / 统计图表 / 设置行）。
 * 组件均为无状态入参，因此可直接喂样例数据渲染，无需 ViewModel。
 */

private val sampleDays = (0..6).map { i ->
    WeekDayBar(
        date = LocalDate.now().minusDays((6 - i).toLong()),
        income = listOf(0.0, 12.5, 0.0, 301.0, 0.01, 0.0, 20.5)[i],
        expense = listOf(8.0, 20.5, 0.0, 304.0, 20.9, 6.6, 15.2)[i],
    )
}

@Preview(name = "总览卡", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun OverviewCardPreview() {
    BookkeepingTheme(darkTheme = true) {
        OverviewCard(
            month = YearMonth.now(),
            income = 881.51,
            expense = 636.01,
            onPrev = {},
            onNext = {},
            onOpenPicker = {},
        )
    }
}

@Preview(name = "近7日卡", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun WeekOverviewCardPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(vertical = 12.dp)) {
            WeekOverviewCard(sampleDays)
        }
    }
}

@Preview(name = "月份选择", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun MonthPickerPreview() {
    BookkeepingTheme(darkTheme = true) {
        MonthPickerDialog(current = YearMonth.now(), onSelect = {}, onDismiss = {})
    }
}

@Preview(name = "趋势柱状图", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun BarChartPreview() {
    BookkeepingTheme(darkTheme = true) {
        BarChart(
            buckets = listOf(
                StatsBucket(BucketLabelKind.WEEKDAY, 1, 120.0), StatsBucket(BucketLabelKind.WEEKDAY, 2, 40.0),
                StatsBucket(BucketLabelKind.WEEKDAY, 3, 305.0), StatsBucket(BucketLabelKind.WEEKDAY, 4, 88.0),
                StatsBucket(BucketLabelKind.WEEKDAY, 5, 220.0), StatsBucket(BucketLabelKind.WEEKDAY, 6, 55.0),
                StatsBucket(BucketLabelKind.WEEKDAY, 0, 180.0),
            ),
            accent = ExpenseColor,
        )
    }
}

@Preview(name = "占比环图", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun DonutPreview() {
    BookkeepingTheme(darkTheme = true) {
        DonutSection(
            categories = listOf(
                CategoryStat("餐饮", 320.0, 8, 0.45f),
                CategoryStat("交通", 180.0, 5, 0.25f),
                CategoryStat("购物", 140.0, 3, 0.20f),
                CategoryStat("娱乐", 70.0, 2, 0.10f),
            ),
            total = 710.0,
        )
    }
}

@Preview(name = "分类排行", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun RankCardPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(vertical = 8.dp)) {
            RankCard(CategoryStat("餐饮", 320.0, 8, 0.45f), ExpenseColor)
            RankCard(CategoryStat("工资", 8800.0, 1, 0.92f), IncomeColor)
        }
    }
}

@Preview(name = "设置行", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun SettingsRowsPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionCard {
                ToggleRow("启用自动记账", "检测到支付时弹出确认卡片", checked = true, onChecked = {})
                ToggleRow("手动改分类时自动学习", "记住你的修改", checked = false, onChecked = {})
            }
            SectionCard {
                RadioRow("全部（微信 + 支付宝）", selected = true, onClick = {})
                RadioRow("仅微信", selected = false, onClick = {})
            }
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    ActionButtonRow {
                        SettingsActionButton("导出 Excel 备份", {})
                        SettingsActionButton("导入 Excel 备份", {})
                    }
                }
            }
        }
    }
}

@Preview(name = "折叠分组", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun CollapsibleSectionPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CollapsibleSection(title = "外观", emoji = "🎨") {
                Column(Modifier.padding(16.dp)) {
                    androidx.compose.material3.Text("（展开后的内容）")
                }
            }
            CollapsibleSection(title = "自动记账", emoji = "⚡", initiallyExpanded = true) {
                ToggleRow("启用自动记账", "检测到支付时弹出确认卡片", checked = true, onChecked = {})
            }
        }
    }
}

@Preview(name = "分类九宫格", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun CategoryGridPreview() {
    BookkeepingTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            CategoryGrid(
                categories = com.nonen.Bookkeeping.core.Categories.expenseCategories,
                selected = "餐饮",
                onSelect = {},
            )
        }
    }
}
