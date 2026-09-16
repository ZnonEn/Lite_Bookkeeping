package com.nonen.Bookkeeping.ui.screens

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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.ui.components.EmptyState
import com.nonen.Bookkeeping.ui.components.TransactionRow
import com.nonen.Bookkeeping.ui.components.formatDate
import com.nonen.Bookkeeping.ui.components.localDateOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    // 从编辑页返回时自动刷新（返回会触发 ON_RESUME）
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
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
                FilterChip(selected = vm.type == null, onClick = { vm.type = null }, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(selected = vm.type == "expense", onClick = { vm.type = "expense" }, label = { Text(stringResource(R.string.type_expense)) })
                FilterChip(selected = vm.type == "income", onClick = { vm.type = "income" }, label = { Text(stringResource(R.string.type_income)) })

                Box {
                    FilterChip(
                        selected = vm.category != null,
                        onClick = { categoryMenu = true },
                        label = { Text(vm.category ?: stringResource(R.string.label_category)) },
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.filter_all_categories)) },
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
                    label = { Text(dateChipLabel(stringResource(R.string.date_start_prefix), R.string.date_start_placeholder, vm.startDate)) },
                )
                FilterChip(
                    selected = vm.endDate != null,
                    onClick = { datePickTarget = "end" },
                    label = { Text(dateChipLabel(stringResource(R.string.date_end_prefix), R.string.date_end_placeholder, vm.endDate)) },
                )
            }

            if (vm.startDate != null || vm.endDate != null || vm.category != null || vm.type != null) {
                TextButton(onClick = vm::resetFilters) { Text(stringResource(R.string.action_reset_filters)) }
            }

            Spacer(Modifier.height(4.dp))
            if (vm.results.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(icon = "✎", text = stringResource(R.string.search_empty))
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
        val initial = if (target == "start") vm.startDate else vm.endDate
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
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

/** 日期筛选胶囊文案：未选中时显示占位（如「开始日期」），选中后显示具体日期 */
@Composable
private fun dateChipLabel(prefix: String, placeholderRes: Int, date: LocalDate?): String =
    date?.let { "$prefix ${formatDate(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}" }
        ?: stringResource(placeholderRes)
