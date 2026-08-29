package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.ui.components.TransactionRow
import com.nonen.Bookkeeping.ui.components.formatDate
import com.nonen.Bookkeeping.ui.components.localDateOf
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SearchViewModel(private val repo: TransactionRepository) : ViewModel() {

    var keyword by mutableStateOf("")
    var type by mutableStateOf<String?>(null) // null 全部 / income / expense
    var category by mutableStateOf<String?>(null)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)

    var results by mutableStateOf<List<TransactionEntity>>(emptyList())
        private set
    var allCategories by mutableStateOf<List<String>>(emptyList())
        private set

    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            allCategories = repo.allCategories()
        }
        viewModelScope.launch { observeFilters() }
        viewModelScope.launch {
            refreshTick.collectLatest {
                allCategories = repo.allCategories()
                runSearch()
            }
        }
    }

    /** 从编辑页返回时刷新结果与分类列表 */
    fun refresh() {
        refreshTick.value++
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeFilters() {
        snapshotFlow { Triple(keyword, type to category, startDate to endDate) }
            .debounce(250)
            .collectLatest { runSearch() }
    }

    private suspend fun runSearch() {
        val zone = ZoneId.systemDefault()
        val start = startDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        val end = endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        results = repo.search(keyword, category, type, start, end)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    // 从编辑页返回时自动刷新（返回会触发 ON_RESUME）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var categoryMenu by remember { mutableStateOf(false) }
    var datePickTarget by remember { mutableStateOf<String?>(null) } // "start" / "end"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                TextField(
                    value = vm.keyword,
                    onValueChange = { vm.keyword = it },
                    placeholder = { Text("金额 / 备注 / 商户 / 分类") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(selected = vm.type == null, onClick = { vm.type = null }, label = { Text("全部") })
                FilterChip(selected = vm.type == "expense", onClick = { vm.type = "expense" }, label = { Text("支出") })
                FilterChip(selected = vm.type == "income", onClick = { vm.type = "income" }, label = { Text("收入") })

                Box {
                    FilterChip(
                        selected = vm.category != null,
                        onClick = { categoryMenu = true },
                        label = { Text(vm.category ?: "分类") },
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("全部分类") },
                            onClick = { vm.category = null; categoryMenu = false },
                        )
                        vm.allCategories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${Categories.emoji(c)} $c") },
                                onClick = { vm.category = c; categoryMenu = false },
                            )
                        }
                    }
                }

                FilterChip(
                    selected = vm.startDate != null,
                    onClick = { datePickTarget = "start" },
                    label = { Text(vm.startDate?.let { "开始 ${formatDate(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}" } ?: "开始日期") },
                )
                FilterChip(
                    selected = vm.endDate != null,
                    onClick = { datePickTarget = "end" },
                    label = { Text(vm.endDate?.let { "结束 ${formatDate(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}" } ?: "结束日期") },
                )
            }

            if (vm.startDate != null || vm.endDate != null || vm.category != null || vm.type != null) {
                TextButton(onClick = {
                    vm.startDate = null
                    vm.endDate = null
                    vm.category = null
                    vm.type = null
                }) { Text("重置筛选") }
            }

            Spacer(Modifier.height(4.dp))
            if (vm.results.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✎", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("没有匹配的记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(vm.results, key = { it.id }) { tx ->
                        TransactionRow(tx = tx, onClick = { onEdit(tx.id) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    datePickTarget?.let { target ->
        val initial = (if (target == "start") vm.startDate else vm.endDate)
        val initialUtc = remember(target, initial) {
            (initial ?: localDateOf(System.currentTimeMillis()))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { datePickTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { sel ->
                        val picked = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == "start") vm.startDate = picked else vm.endDate = picked
                    }
                    datePickTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { datePickTarget = null }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}
