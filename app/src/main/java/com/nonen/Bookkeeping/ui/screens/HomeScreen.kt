package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.ui.components.DayHeader
import com.nonen.Bookkeeping.ui.components.EmptyState
import com.nonen.Bookkeeping.ui.components.TransactionRow
import com.nonen.Bookkeeping.ui.components.localDateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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

    val summary = remember(transactions) { summarizeMonth(transactions) }
    val weekDays = remember(weekTx) { weekBarsOf(weekTx) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search)) }
        }

        // 概览卡、近7日卡、账单列表都在同一个滚动容器里，随页面整体滑动
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "overview") {
                OverviewCard(
                    month = month,
                    income = summary.income,
                    expense = summary.expense,
                    onPrev = vm::prevMonth,
                    onNext = vm::nextMonth,
                    onOpenPicker = { showMonthPicker = true },
                )
            }
            item(key = "week") { WeekOverviewCard(weekDays) }

            if (summary.grouped.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = "✎",
                        text = stringResource(R.string.home_empty),
                        modifier = Modifier.height(280.dp),
                    )
                }
            } else {
                summary.grouped.forEach { (date, items) ->
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

/** 当月汇总结果：总收入 / 总支出 / 按日分组（日期倒序） */
internal data class MonthSummary(
    val income: Double,
    val expense: Double,
    val grouped: List<Pair<LocalDate, List<TransactionEntity>>>,
)

internal fun summarizeMonth(transactions: List<TransactionEntity>): MonthSummary {
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val grouped = transactions.groupBy { localDateOf(it.timestamp) }
        .toList()
        .sortedByDescending { it.first }
    return MonthSummary(income, expense, grouped)
}

/** 近 7 日（含今天）逐日收支，固定 7 个数据点，无数据的天补零 */
internal fun weekBarsOf(transactions: List<TransactionEntity>): List<WeekDayBar> {
    val start = LocalDate.now().minusDays(6)
    val byDay = transactions.groupBy { localDateOf(it.timestamp) }
    return (0..6).map { i ->
        val date = start.plusDays(i.toLong())
        val list = byDay[date].orEmpty()
        WeekDayBar(
            date = date,
            income = list.filter { it.amount > 0 }.sumOf { it.amount },
            expense = list.filter { it.amount < 0 }.sumOf { -it.amount },
        )
    }
}
