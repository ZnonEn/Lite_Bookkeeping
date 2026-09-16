package com.nonen.Bookkeeping.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 搜索页筛选条件与结果 */
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

    fun resetFilters() {
        startDate = null
        endDate = null
        category = null
        type = null
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeFilters() {
        androidx.compose.runtime.snapshotFlow { Triple(keyword, type to category, startDate to endDate) }
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
