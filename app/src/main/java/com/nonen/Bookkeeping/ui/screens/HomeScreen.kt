package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.ui.components.DayHeader
import com.nonen.Bookkeeping.ui.components.TransactionRow
import com.nonen.Bookkeeping.ui.components.formatPlainAmount
import com.nonen.Bookkeeping.ui.components.formatSignedPlain
import com.nonen.Bookkeeping.ui.components.localDateOf
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.InkPrimary
import com.nonen.Bookkeeping.ui.motion.rememberPressScale
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val transactions: StateFlow<List<TransactionEntity>> =
        _month.flatMapLatest { repo.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 近7日（含今天）：独立于所选月份，随账单变化实时刷新
    private val weekStart =
        LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val weekEnd =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val weekTransactions: StateFlow<List<TransactionEntity>> =
        repo.observeRange(weekStart, weekEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun selectMonth(m: YearMonth) {
        _month.value = m
    }
}

/** 首页内容（作为 MainScreen 里 Pager 的一页，底栏由 MainScreen 提供） */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onSearch: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val transactions by vm.transactions.collectAsState()
    val month by vm.month.collectAsState()
    val weekTx by vm.weekTransactions.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    val (income, expense, grouped) = remember(transactions) {
        val incomeSum = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val expenseSum = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
        val groupedByDay = transactions.groupBy { localDateOf(it.timestamp) }.toList().sortedByDescending { it.first }
        Triple(incomeSum, expenseSum, groupedByDay)
    }

    val weekDays = remember(weekTx) {
        val start = LocalDate.now().minusDays(6)
        val byDay = weekTx.groupBy { localDateOf(it.timestamp) }
        (0..6).map { i ->
            val date = start.plusDays(i.toLong())
            val list = byDay[date].orEmpty()
            WeekDayBar(
                date = date,
                income = list.filter { it.amount > 0 }.sumOf { it.amount },
                expense = list.filter { it.amount < 0 }.sumOf { -it.amount },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "轻记账",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "搜索") }
        }

        // 概览卡、近7日卡、账单列表都在同一个滚动容器里，随页面整体滑动
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "overview") {
                OverviewCard(
                    month = month,
                    income = income,
                    expense = expense,
                    onPrev = vm::prevMonth,
                    onNext = vm::nextMonth,
                    onOpenPicker = { showMonthPicker = true },
                )
            }
            item(key = "week") { WeekOverviewCard(weekDays) }

            if (grouped.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✎", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "还没有账单记录",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                grouped.forEach { (date, items) ->
                    val dayIncome = items.filter { it.amount > 0 }.sumOf { it.amount }
                    val dayExpense = items.filter { it.amount < 0 }.sumOf { -it.amount }
                    item(key = "header_$date") { DayHeader(date, dayIncome, dayExpense) }
                    items(items, key = { it.id }) { tx ->
                        TransactionRow(tx = tx, onClick = { onEdit(tx.id) })
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            current = month,
            onSelect = {
                vm.selectMonth(it)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }
}

/** Apple-Card 风格总览卡：绿色渐变底 + 白色数据层级 + 月份切换胶囊 */
@Composable
private fun OverviewCard(
    month: YearMonth,
    income: Double,
    expense: Double,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(InkPrimary.copy(alpha = 0.95f), Color(0xFF2E9F4B).copy(alpha = 0.9f))
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "本月概览",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                MonthChip(month = month, onPrev = onPrev, onNext = onNext, onOpenPicker = onOpenPicker)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatSignedPlain(income - expense),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                StatColumn("收入", income)
                StatColumn("支出", expense)
            }
        }
    }
}

@Composable
private fun MonthChip(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onOpenPicker: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "上个月",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onPrev),
        )
        Text(
            text = "%04d.%02d".format(month.year, month.monthValue),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "下个月",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext),
        )
    }
}

@Composable
private fun StatColumn(label: String, value: Double) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.45f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatSignedPlain(value),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/** 近7日单日收支（用于迷你柱状图） */
private data class WeekDayBar(val date: LocalDate, val income: Double, val expense: Double)

/** 近7日收支卡：每天收入/支出两根迷你柱，随列表滚动 */
@Composable
private fun WeekOverviewCard(days: List<WeekDayBar>) {
    val maxV = days.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)
    val net = days.sumOf { it.income - it.expense }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("近7日", style = MaterialTheme.typography.titleSmall)
                Text(
                    formatSignedPlain(net),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(78.dp)) {
                days.forEach { d ->
                    DayBarColumn(d, maxV, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayBarColumn(d: WeekDayBar, maxV: Double, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            Modifier.height(54.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            MiniBar(d.income, maxV, IncomeColor)
            MiniBar(d.expense, maxV, ExpenseColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${d.date.dayOfMonth}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniBar(v: Double, maxV: Double, color: Color) {
    // 无数据时留 4dp 半透明残柱，标示当天有无发生额
    val h = if (v <= 0) 4.dp else (8 + 44 * (v / maxV)).dp
    Box(
        Modifier
            .width(6.dp)
            .height(h)
            .clip(RoundedCornerShape(3.dp))
            .background(if (v <= 0) color.copy(alpha = 0.35f) else color),
    )
}

/** 月份选择日历：年份可翻页，点选月份直接跳转 */
@Composable
private fun MonthPickerDialog(
    current: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { YearMonth.now() }
    var displayYear by remember { mutableIntStateOf(current.year) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "选择月份",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一年",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { displayYear -= 1 },
                        )
                        Text(
                            text = "${displayYear}年",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一年",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { displayYear += 1 },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..12).chunked(3).forEach { rowMonths ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowMonths.forEach { m ->
                                MonthCell(
                                    month = m,
                                    selected = displayYear == current.year && m == current.monthValue,
                                    isCurrent = displayYear == now.year && m == now.monthValue,
                                    onClick = { onSelect(YearMonth.of(displayYear, m)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    month: Int,
    selected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressScale) = rememberPressScale(0.94f)
    Box(
        modifier = modifier
            .then(pressScale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) InkPrimary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${month}月",
            fontSize = 14.sp,
            fontWeight = if (selected || isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                selected -> Color.White
                isCurrent -> InkPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
