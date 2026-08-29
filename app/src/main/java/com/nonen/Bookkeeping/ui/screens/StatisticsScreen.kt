package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.ui.components.localDateOf
import com.nonen.Bookkeeping.ui.components.formatPlainAmount
import com.nonen.Bookkeeping.ui.theme.ChartColors
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

enum class StatsPeriod(val label: String) {
    WEEK("本周"), MONTH("本月"), YEAR("本年"), CUSTOM("自定义")
}

data class StatsBucket(val label: String, val value: Double)

data class CategoryStat(val category: String, val amount: Double, val count: Int, val percent: Float)

data class StatsData(
    val title: String,
    val total: Double,
    val count: Int,
    val prevTotal: Double,
    val dailyAvg: Double,
    val buckets: List<StatsBucket>,
    val categories: List<CategoryStat>,
)

private val WEEKDAY_SHORT = listOf("日", "一", "二", "三", "四", "五", "六")

class StatsViewModel(private val repo: TransactionRepository) : ViewModel() {

    var period by mutableStateOf(StatsPeriod.MONTH)
    var isIncome by mutableStateOf(false)
    /** 本年模式选中的月份（1-12），null = 整年 */
    var monthSel by mutableStateOf<Int?>(null)
    /** 本月模式选中的周次（1-based，按 7 天切片），null = 整月 */
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
        viewModelScope.launch { stats = compute() }
    }

    private fun typeNoun() = if (isIncome) "收入" else "支出"

    /** 某月按 7 天切片的周区间（第 1 周从 1 号开始），与 inkqilin 逻辑一致 */
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

    private suspend fun compute(): StatsData? {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun startMillis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        fun endMillis(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        var start: LocalDate
        var end: LocalDate
        val title: String
        var weekdayLabels = false
        var monthBuckets = false

        when (period) {
            StatsPeriod.WEEK -> {
                val offset = weekSel ?: 0 // null=本周, -1=上周
                start = today.with(DayOfWeek.MONDAY).plusDays(offset * 7L)
                end = start.plusDays(6)
                title = if (offset < 0) "上周总${typeNoun()}" else "本周总${typeNoun()}"
                weekdayLabels = true
            }
            StatsPeriod.MONTH -> {
                val slices = weekSlicesOfMonth(today.year, today.monthValue)
                val w = weekSel
                if (w != null && slices.isNotEmpty()) {
                    val idx = (w - 1).coerceIn(0, slices.size - 1)
                    start = today.withDayOfMonth(slices[idx].first)
                    end = today.withDayOfMonth(slices[idx].second)
                    title = "第${idx + 1}周总${typeNoun()}"
                    weekdayLabels = true
                } else {
                    start = today.withDayOfMonth(1)
                    end = today.withDayOfMonth(today.lengthOfMonth())
                    title = "本月总${typeNoun()}"
                }
            }
            StatsPeriod.YEAR -> {
                val m = monthSel
                if (m != null) {
                    start = LocalDate.of(today.year, m, 1)
                    end = start.withDayOfMonth(start.lengthOfMonth())
                    title = "${m}月总${typeNoun()}"
                } else {
                    start = LocalDate.of(today.year, 1, 1)
                    end = LocalDate.of(today.year, 12, 31)
                    title = "本年总${typeNoun()}"
                    monthBuckets = true
                }
            }
            StatsPeriod.CUSTOM -> {
                val s = customStart ?: return null
                val e = customEnd ?: return null
                start = minOf(s, e)
                end = maxOf(s, e)
                title = "期间总${typeNoun()}"
                monthBuckets = ChronoUnit.DAYS.between(start, end) > 92
            }
        }

        val signMatch: (Double) -> Boolean = if (isIncome) { v -> v > 0 } else { v -> v < 0 }

        var total = 0.0
        var count = 0
        val bucketTotals = HashMap<Int, Double>()
        val catAmount = HashMap<String, Double>()
        val catCount = HashMap<String, Int>()

        for (t in repo.getRange(startMillis(start), endMillis(end))) {
            if (!signMatch(t.amount)) continue
            val v = abs(t.amount)
            total += v
            count++
            val d = localDateOf(t.timestamp)
            val bIdx = if (monthBuckets) {
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
            monthBuckets -> {
                val months = ChronoUnit.MONTHS.between(
                    start.withDayOfMonth(1), end.plusDays(1).withDayOfMonth(1)
                ).toInt()
                (0 until months).map { i ->
                    StatsBucket("${start.plusMonths(i.toLong()).monthValue}月", bucketTotals[i] ?: 0.0)
                }
            }
            period == StatsPeriod.YEAR && monthSel != null ->
                (0 until start.lengthOfMonth()).map { i -> StatsBucket("${i + 1}", bucketTotals[i] ?: 0.0) }
            weekdayLabels -> {
                val len = ChronoUnit.DAYS.between(start, end).toInt() + 1
                (0 until len).map { i ->
                    val wd = start.plusDays(i.toLong()).dayOfWeek.value % 7 // ISO 周一=1…周日=7 → 日=0
                    StatsBucket(WEEKDAY_SHORT[wd], bucketTotals[i] ?: 0.0)
                }
            }
            else -> {
                val len = ChronoUnit.DAYS.between(start, end).toInt() + 1
                (0 until len).map { i ->
                    StatsBucket("${start.plusDays(i.toLong()).dayOfMonth}", bucketTotals[i] ?: 0.0)
                }
            }
        }

        // 环比上期：月→上个自然月；年→上一年；周/切片/自定义→等长前置窗口
        var prevStart: LocalDate
        var prevEnd: LocalDate
        when {
            period == StatsPeriod.MONTH && weekSel == null -> {
                val pm = start.minusMonths(1)
                prevStart = pm.withDayOfMonth(1)
                prevEnd = pm.withDayOfMonth(pm.lengthOfMonth())
            }
            period == StatsPeriod.YEAR && monthSel == null -> {
                prevStart = start.minusYears(1)
                prevEnd = end.minusYears(1)
            }
            period == StatsPeriod.YEAR -> {
                val pm = start.minusMonths(1)
                prevStart = pm.withDayOfMonth(1)
                prevEnd = pm.withDayOfMonth(pm.lengthOfMonth())
            }
            else -> {
                val len = ChronoUnit.DAYS.between(start, end) + 1
                prevEnd = start.minusDays(1)
                prevStart = prevEnd.minusDays(len - 1)
            }
        }
        var prevTotal = 0.0
        for (t in repo.getRange(startMillis(prevStart), endMillis(prevEnd))) {
            if (signMatch(t.amount)) prevTotal += abs(t.amount)
        }

        // 日均：整个周期的天数（与 inkqilin 一致）
        val days = when (period) {
            StatsPeriod.WEEK -> 7
            StatsPeriod.MONTH -> if (weekSel != null) ChronoUnit.DAYS.between(start, end).toInt() + 1 else start.lengthOfMonth()
            StatsPeriod.YEAR -> if (monthSel != null) start.lengthOfMonth() else start.lengthOfYear()
            StatsPeriod.CUSTOM -> ChronoUnit.DAYS.between(start, end).toInt() + 1
        }.coerceAtLeast(1)

        val categories = catAmount.entries
            .map { (c, v) -> CategoryStat(c, v, catCount[c] ?: 0, if (total > 0) (v / total).toFloat() else 0f) }
            .sortedByDescending { it.amount }

        return StatsData(title, total, count, prevTotal, total / days, buckets, categories)
    }
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

    // 统计页作为 MainScreen Pager 的一页，直接输出滚动内容（底栏由 MainScreen 提供）
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
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
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
                ) {
                    StatsPeriod.entries.forEach { p ->
                        SegmentText(
                            label = p.label,
                            selected = vm.period == p,
                            selectedFill = MaterialTheme.colorScheme.primary,
                            selectedText = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (p == StatsPeriod.CUSTOM) vm.requestCustom() else vm.updatePeriod(p)
                        }
                    }
                }
                IconButton(onClick = { vm.requestCustom() }) {
                    Icon(Icons.Default.DateRange, contentDescription = "自定义日期范围")
                }
            }

            // 下钻：本年 → 选月；本月 → 选周；本周 → 上周/本周
            when (vm.period) {
                StatsPeriod.YEAR -> {
                    SubFilterBar(
                        options = listOf("全部") + (1..12).map { "${it}月" },
                        selectedIndex = vm.monthSel?.let { it },
                        onSelect = { idx -> vm.selectMonth(if (idx == 0) null else idx) },
                    )
                }
                StatsPeriod.MONTH -> {
                    val weekCount = weekCountOfCurrentMonth()
                    SubFilterBar(
                        options = listOf("全部") + (1..weekCount).map { "第${it}周" },
                        selectedIndex = vm.weekSel?.let { it },
                        onSelect = { idx -> vm.selectWeek(if (idx == 0) null else idx) },
                    )
                }
                StatsPeriod.WEEK -> {
                    SubFilterBar(
                        options = listOf("上周", "本周"),
                        selectedIndex = when (vm.weekSel) { -1 -> 0; else -> 1 },
                        onSelect = { idx -> vm.selectWeek(if (idx == 0) -1 else null) },
                    )
                }
                StatsPeriod.CUSTOM -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DateChip(vm.customStart?.toString() ?: "开始日期") { datePickTarget = 0 }
                        Text("至", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DateChip(vm.customEnd?.toString() ?: "结束日期") { datePickTarget = 1 }
                    }
                }
            }

            // 支出 / 收入
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
            ) {
                SegmentText(
                    "支出", selected = !vm.isIncome, selectedFill = ExpenseColor,
                    selectedText = Color.White, modifier = Modifier.weight(1f),
                ) { vm.setType(false) }
                SegmentText(
                    "收入", selected = vm.isIncome, selectedFill = IncomeColor,
                    selectedText = Color.White, modifier = Modifier.weight(1f),
                ) { vm.setType(true) }
            }

            s?.let { s ->
                Spacer(Modifier.height(12.dp))
                // 总额卡
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "¥${formatPlainAmount(s.total)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("共 ${s.count} 笔记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 环比 + 日均
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("环比上期", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (s.prevTotal > 0 || s.total > 0) {
                                    val pct = when {
                                        s.prevTotal > 0 -> (s.total - s.prevTotal) / s.prevTotal * 100
                                        s.total > 0 -> 100.0
                                        else -> 0.0
                                    }
                                    val up = pct >= 0
                                    Icon(
                                        if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = if (up) ExpenseColor else IncomeColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        String.format(Locale.US, "%+.1f%%", pct),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (up) ExpenseColor else IncomeColor,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("上期 ¥${formatPlainAmount(s.prevTotal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("日均${typeNounOf(vm)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "¥${formatPlainAmount(s.dailyAvg)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // 趋势图标题 + 趋势/占比切换
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("趋势图", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp),
                    ) {
                        MiniSegment("趋势", chartTrend, Modifier) { chartTrend = true }
                        MiniSegment("占比", !chartTrend, Modifier) { chartTrend = false }
                    }
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                ) {
                    if (chartTrend) {
                        BarChart(buckets = s.buckets, accent = if (vm.isIncome) IncomeColor else ExpenseColor)
                    } else {
                        DonutSection(s.categories, s.total)
                    }
                }

                if (s.categories.isNotEmpty()) {
                    Text(
                        "分类排行",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    s.categories.forEachIndexed { index, c ->
                        RankCard(c, color = ChartColors[index % ChartColors.size])
                    }
                }
                Spacer(Modifier.height(96.dp))}

    datePickTarget?.let { target ->
        val initial = if (target == 0) vm.customStart else vm.customEnd
        val initialUtc = remember(target, initial) {
            (initial ?: LocalDate.now()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { datePickTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { sel ->
                        val picked = Instant.ofEpochMilli(sel).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        if (target == 0) vm.customStart = picked else vm.customEnd = picked
                    }
                    datePickTarget = if (target == 0 && vm.customEnd == null) 1 else null
                    if (target == 1) vm.load()
                }) { Text(if (target == 0) "下一步" else "确定") }
            },
            dismissButton = { TextButton(onClick = { datePickTarget = null }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}
}

private fun typeNounOf(vm: StatsViewModel) = if (vm.isIncome) "收入" else "支出"

private fun weekCountOfCurrentMonth(): Int {
    val today = LocalDate.now()
    return ((today.lengthOfMonth() + 6) / 7)
}

@Composable
private fun DateChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun SegmentText(
    label: String,
    selected: Boolean,
    selectedFill: Color,
    selectedText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) selectedText else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniSegment(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubFilterBar(options: List<String>, selectedIndex: Int?, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        options.forEachIndexed { idx, label ->
            val selected = selectedIndex != null && (if (idx == 0) selectedIndex == null else selectedIndex == idx)
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 胶囊柱状趋势图（横向可滚动） */
@Composable
private fun BarChart(buckets: List<StatsBucket>, accent: Color) {
    if (buckets.isEmpty() || buckets.all { it.value <= 0.0 }) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val maxV = buckets.maxOf { it.value }.coerceAtLeast(1.0)
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(190.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(buckets, key = { it.label + it.value }) { b ->
            Column(
                Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (b.value > 0) {
                    Text(shortAmount(b.value), fontSize = 10.sp, color = accent)
                    Spacer(Modifier.height(4.dp))
                }
                val h = if (b.value <= 0) 4.dp else (16 + 118 * (b.value / maxV)).dp
                Box(
                    Modifier
                        .width(22.dp)
                        .height(h)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (b.value <= 0) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.9f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    b.label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun shortAmount(v: Double): String = when {
    v >= 10000 -> String.format(Locale.US, "%.1f万", v / 10000)
    v >= 100 -> String.format(Locale.US, "%.0f", v)
    else -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}

/** 占比环图 + 图例 */
@Composable
private fun DonutSection(categories: List<CategoryStat>, total: Double) {
    val slices = categories.filter { it.amount > 0 }.take(12)
    if (slices.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(180.dp)) {
                val stroke = 24.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                var startAngle = -90f
                slices.forEachIndexed { i, c ->
                    val sweep = (c.percent * 360f).coerceAtLeast(0.5f)
                    drawArc(
                        color = ChartColors[i % ChartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("总计", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥${shortAmount(total)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(16.dp))
        slices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ChartColors[slices.indexOf(c) % ChartColors.size]),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${c.category} ${(c.percent * 100).toString().take(4)}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankCard(c: CategoryStat, color: Color) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = Categories.emoji(c.category), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.category,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "¥${formatPlainAmount(c.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(c.percent.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${"%.1f".format(Locale.US, c.percent * 100)}% · ${c.count} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
