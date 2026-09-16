package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.stats.StatsCalculator
import com.nonen.Bookkeeping.stats.StatsData
import com.nonen.Bookkeeping.stats.StatsPeriod
import com.nonen.Bookkeeping.stats.StatsQuery
import com.nonen.Bookkeeping.ui.components.AnimatedSegmented
import com.nonen.Bookkeeping.ui.theme.ChartColors
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StatsViewModel(private val repo: TransactionRepository) : ViewModel() {

    var period by mutableStateOf(StatsPeriod.MONTH)
    var isIncome by mutableStateOf(false)
    /** 本年模式选中的月份（1-12），null = 整年 */
    var monthSel by mutableStateOf<Int?>(null)
    /** 本周/本月模式选中的切片：本周 0=本周、-1=上周；本月 1-based 周次，null = 整月 */
    var weekSel by mutableStateOf<Int?>(null)
    var customStart by mutableStateOf<LocalDate?>(null)
    var customEnd by mutableStateOf<LocalDate?>(null)
    var customRequest by mutableStateOf(0)
        private set
    var stats by mutableStateOf<StatsData?>(null)
        private set

    init {
        load()
    }

    fun updatePeriod(p: StatsPeriod) {
        if (period == p) return
        period = p
        if (p == StatsPeriod.YEAR) weekSel = null
        if (p == StatsPeriod.MONTH) monthSel = null
        load()
    }

    fun requestCustom() {
        period = StatsPeriod.CUSTOM
        customRequest++
    }

    fun setType(income: Boolean) {
        if (isIncome == income) return
        isIncome = income
        load()
    }

    fun selectMonth(m: Int?) {
        if (monthSel == m) return
        monthSel = m
        load()
    }

    fun selectWeek(w: Int?) {
        if (weekSel == w) return
        weekSel = w
        load()
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        customStart = minOf(start, end)
        customEnd = maxOf(start, end)
        period = StatsPeriod.CUSTOM
        load()
    }

    fun load() {
        viewModelScope.launch {
            // 聚合计算放后台线程，避免占用主线程帧预算
            stats = withContext(Dispatchers.Default) { computeStats() }
        }
    }

    private suspend fun computeStats(): StatsData? = StatsCalculator.compute(
        query = StatsQuery(
            period = period,
            isIncome = isIncome,
            monthSel = monthSel,
            weekSel = weekSel,
            customStart = customStart,
            customEnd = customEnd,
        ),
        fetch = { start, end -> repo.getRange(start, end) },
    )
}

@Composable
fun StatisticsScreen(vm: StatsViewModel) {
    val s = vm.stats
    var chartTrend by remember { mutableStateOf(true) }
    var datePickTarget by remember { mutableStateOf<Int?>(null) } // 0=开始, 1=结束

    // 进入自定义模式自动弹出日期选择
    LaunchedEffect(vm.customRequest) {
        if (vm.customRequest > 0 && vm.period == StatsPeriod.CUSTOM &&
            (vm.customStart == null || vm.customEnd == null)
        ) datePickTarget = 0
    }

    // 统计页作为 MainScreen Pager 的一页；LazyColumn 惰性组合，避免整页全量测量
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item("content") {
            StatsHeaderSection(
                vm = vm,
                stats = s,
                chartTrend = chartTrend,
                onChartTrendChange = { chartTrend = it },
                onPickDate = { datePickTarget = it },
            )
        }
        // 分类排行：列表部分惰性组合、滚动回收
        itemsIndexed(
            s?.categories ?: emptyList(),
            key = { index, c -> "rank_${index}_${c.category}" },
        ) { index, c ->
            RankCard(c, color = ChartColors[index % ChartColors.size])
        }
    }

    datePickTarget?.let { target ->
        CustomRangePickerDialog(
            vm = vm,
            target = target,
            onTargetChange = { datePickTarget = it },
        )
    }
}

/** 页头与图表区：周期分段、下钻筛选、收支切换、汇总卡与趋势图 */
@Composable
private fun StatsHeaderSection(
    vm: StatsViewModel,
    stats: StatsData?,
    chartTrend: Boolean,
    onChartTrendChange: (Boolean) -> Unit,
    onPickDate: (Int) -> Unit,
) {
    Column {
        Text(
            "统计",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        // 周期分段 + 自定义日期入口
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedSegmented(
                options = StatsPeriod.entries.map { it.label },
                selectedIndex = StatsPeriod.entries.indexOf(vm.period),
                onSelected = { idx ->
                    val p = StatsPeriod.entries[idx]
                    if (p == StatsPeriod.CUSTOM) vm.requestCustom() else vm.updatePeriod(p)
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.requestCustom() }) {
                Icon(Icons.Default.DateRange, contentDescription = "自定义日期范围")
            }
        }

        // 下钻：本年 → 选月；本月 → 选周；本周 → 上周/本周
        when (vm.period) {
            StatsPeriod.YEAR -> {
                SubFilterBar(
                    options = listOf("全部") + (1..12).map { "${it}月" },
                    selectedIndex = vm.monthSel,
                    onSelect = { idx -> vm.selectMonth(if (idx == 0) null else idx) },
                )
            }

            StatsPeriod.MONTH -> {
                val weekCount = weekCountOfCurrentMonth()
                SubFilterBar(
                    options = listOf("全部") + (1..weekCount).map { "第${it}周" },
                    selectedIndex = vm.weekSel,
                    onSelect = { idx -> vm.selectWeek(if (idx == 0) null else idx) },
                )
            }

            StatsPeriod.WEEK -> {
                SubFilterBar(
                    options = listOf("上周", "本周"),
                    selectedIndex = if (vm.weekSel == -1) 0 else 1,
                    onSelect = { idx -> vm.selectWeek(if (idx == 0) -1 else null) },
                )
            }

            StatsPeriod.CUSTOM -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateChip(vm.customStart?.toString() ?: "开始日期") { onPickDate(0) }
                    Text("至", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DateChip(vm.customEnd?.toString() ?: "结束日期") { onPickDate(1) }
                }
            }
        }

        // 支出 / 收入
        AnimatedSegmented(
            options = listOf("支出", "收入"),
            selectedIndex = if (vm.isIncome) 1 else 0,
            onSelected = { vm.setType(it == 1) },
            thumbColor = if (vm.isIncome) IncomeColor else ExpenseColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        stats ?: return
        Spacer(Modifier.height(12.dp))
        TotalCard(title = stats.title, total = stats.total, count = stats.count)

        Spacer(Modifier.height(8.dp))
        CompareCard(
            prevTotal = stats.prevTotal,
            total = stats.total,
            dailyAvg = stats.dailyAvg,
            typeNoun = if (vm.isIncome) "收入" else "支出",
            accentUp = ExpenseColor,
            accentDown = IncomeColor,
        )

        Spacer(Modifier.height(16.dp))
        // 趋势图标题 + 趋势/占比切换
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("趋势图", style = MaterialTheme.typography.titleMedium)
            AnimatedSegmented(
                options = listOf("趋势", "占比"),
                selectedIndex = if (chartTrend) 0 else 1,
                onSelected = { onChartTrendChange(it == 0) },
                corner = 8.dp,
                thumbCorner = 6.dp,
                verticalPadding = 5.dp,
                fontSize = 12.sp,
            )
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) {
            if (chartTrend) {
                BarChart(buckets = stats.buckets, accent = if (vm.isIncome) IncomeColor else ExpenseColor)
            } else {
                DonutSection(stats.categories, stats.total)
            }
        }

        if (stats.categories.isNotEmpty()) {
            Text(
                "分类排行",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 自定义区间的起止日期选择。
 * 选完开始日期后不关闭，自动切到结束日期；选完结束日期才触发重算并关闭。
 */
@Composable
private fun CustomRangePickerDialog(
    vm: StatsViewModel,
    target: Int,
    onTargetChange: (Int?) -> Unit,
) {
    val initial = if (target == 0) vm.customStart else vm.customEnd
    val initialUtc = remember(target, initial) {
        (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
    DatePickerDialog(
        onDismissRequest = { onTargetChange(null) },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { sel ->
                    val picked = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                    if (target == 0) vm.customStart = picked else vm.customEnd = picked
                }
                onTargetChange(if (target == 0 && vm.customEnd == null) 1 else null)
                if (target == 1) vm.load()
            }) { Text(if (target == 0) "下一步" else "确定") }
        },
        dismissButton = { TextButton(onClick = { onTargetChange(null) }) { Text("取消") } },
    ) { DatePicker(state = state) }
}

private fun weekCountOfCurrentMonth(): Int {
    val today = LocalDate.now()
    return ((today.lengthOfMonth() + 6) / 7)
}
