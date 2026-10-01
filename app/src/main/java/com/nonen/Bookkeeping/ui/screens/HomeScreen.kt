package com.nonen.Bookkeeping.ui.screens

import android.content.Context
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.TodayWatcher
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(context: Context, private val repo: TransactionRepository) : ViewModel() {

    /** 日历观察者：跨天/改时间/换时区后重新读今天，首页跟着走 */
    private val todayWatcher = TodayWatcher.of(context)

    /** 首页当前展示的月份；null = 跟随今天（用户没手动翻过月） */
    private val pinnedMonth = MutableStateFlow<YearMonth?>(null)

    /**
     * 首页月份 = 用户手动选的月份，没选过就是今天所在的月份。
     * 合并 today 流是为了让「跨月自动跳到新月份」经由同一条数据流生效：
     * 今天跨过月末时，只要用户没手动翻过月，这里立刻变成新月份，
     * 下面的账单列表也随之重新查询，不需要重启应用。
     */
    val month: StateFlow<YearMonth> =
        combine(todayWatcher.today, pinnedMonth) { today, pinned -> pinned ?: YearMonth.from(today) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, YearMonth.from(todayWatcher.today.value))

    val transactions: StateFlow<List<TransactionEntity>> =
        month.flatMapLatest { repo.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当月的「今天」，UI 用来判断正看的是不是本月、要不要提示回本月 */
    val today: StateFlow<LocalDate> = todayWatcher.today

    /**
     * 近7日（含今天）：独立于所选月份。
     * `onStart` 让每次重新订阅都重新确认一次今天——回到首页时即使月份没变，跨天后的窗口也能刷新，
     * 因此不需要额外的刷新计数器。
     */
    val weekTransactions: StateFlow<List<TransactionEntity>> =
        todayWatcher.today
            .onStart {
                todayWatcher.refresh()
                emit(todayWatcher.today.value)
            }
            .map { weekRangeOf(it) }
            .flatMapLatest { (start, end) -> repo.observeRange(start, end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 只在页面订阅本 ViewModel 时监听系统时间，离开页面即随作用域取消，不常驻后台
        todayWatcher.wakeUps()
            .onStart { todayWatcher.register(viewModelScope) }
            .launchIn(viewModelScope)
    }

    fun prevMonth() {
        pinnedMonth.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        pinnedMonth.value = month.value.plusMonths(1)
    }

    /** 手动选月份。选回本月时清掉固定，重新跟随日历（跨月自动跳转恢复生效） */
    fun selectMonth(m: YearMonth) {
        pinnedMonth.value = pinnedMonthFor(m, todayWatcher.today.value)
    }

    /** 回到今天所在月份，并重新跟随日历（跨月自动跳转恢复生效） */
    fun backToCurrentMonth() {
        refreshToday()
        pinnedMonth.value = null
    }

    /**
     * 页面重新可见时调用：重新读取今天。
     * 用户自己翻过月份就尊重他的选择，不强行跳走；没翻过则让月份跟着今天走。
     */
    fun refreshToday() {
        todayWatcher.refresh()
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
    val today by vm.today.collectAsState()
    val weekTx by vm.weekTransactions.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    val summary = remember(transactions) { summarizeMonth(transactions) }
    val weekDays = remember(weekTx, today) { weekBarsOf(weekTx, today) }
    val currentMonth = remember(today) { YearMonth.from(today) }
    val viewingCurrentMonth = month == currentMonth

    // 应用从后台回到前台时重新确认今天：过夜/改时间后不必重启应用
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            // 用户自己翻到了别的月份时给一条回本月的路：跨月自动跳转只对「没翻过月」生效
            if (!viewingCurrentMonth) {
                item(key = "back_to_current") {
                    BackToCurrentMonthRow(
                        label = stringResource(R.string.action_back_to_current_month),
                        onClick = vm::backToCurrentMonth,
                    )
                }
            }
            item(key = "week") { WeekOverviewCard(weekDays) }

            if (summary.grouped.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = "✎",
                        text = stringResource(
                            if (viewingCurrentMonth) R.string.home_empty else R.string.home_empty_month
                        ),
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
            today = today,
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

/**
 * 用户手动选 [selected] 月份后，首页月份该固定到哪：选回本月则返回 null（表示重新跟随日历），
 * 否则固定到所选月份。手动停留某个月时不该被自动跳走，这是这里唯一的分叉。
 */
internal fun pinnedMonthFor(selected: YearMonth, today: LocalDate): YearMonth? =
    if (selected == YearMonth.from(today)) null else selected

/** 近 7 日窗口（含今天）的毫秒区间 [起, 止) */
internal fun weekRangeOf(today: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

/** 近 7 日（含今天）逐日收支，固定 7 个数据点，无数据的天补零 */
internal fun weekBarsOf(transactions: List<TransactionEntity>, today: LocalDate = LocalDate.now()): List<WeekDayBar> {
    val start = today.minusDays(6)
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
