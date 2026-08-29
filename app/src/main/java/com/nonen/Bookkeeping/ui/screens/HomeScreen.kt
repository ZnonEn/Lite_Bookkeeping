package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.nonen.Bookkeeping.ui.theme.InkPrimary
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

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
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

    val (income, expense, grouped) = remember(transactions) {
        val incomeSum = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val expenseSum = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
        val groupedByDay = transactions.groupBy { localDateOf(it.timestamp) }.toList().sortedByDescending { it.first }
        Triple(incomeSum, expenseSum, groupedByDay)
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

        OverviewCard(
            month = month,
            income = income,
            expense = expense,
            onPrev = vm::prevMonth,
            onNext = vm::nextMonth,
        )

        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                grouped.forEach { (date, items) ->
                    val dayIncome = items.filter { it.amount > 0 }.sumOf { it.amount }
                    val dayExpense = items.filter { it.amount < 0 }.sumOf { -it.amount }
                    item(key = "header_$date") { DayHeader(date, dayIncome, dayExpense) }
                    items(items, key = { it.id }) { tx ->
                        TransactionRow(tx = tx, onClick = { onEdit(tx.id) })
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
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
                MonthChip(month = month, onPrev = onPrev, onNext = onNext)
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
private fun MonthChip(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
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
