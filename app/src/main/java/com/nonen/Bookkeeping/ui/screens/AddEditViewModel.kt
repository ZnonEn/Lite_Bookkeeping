package com.nonen.Bookkeeping.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「记一笔 / 编辑记录」表单状态与存删逻辑。
 * txId = 0 表示新建；否则载入该条记录进入编辑态。
 */
class AddEditViewModel(
    private val repo: TransactionRepository,
    private val txId: Long,
) : ViewModel() {

    var isIncome by mutableStateOf(false)
        private set
    var amount by mutableStateOf("")
    var category by mutableStateOf(Categories.expenseCategories.first())
    var note by mutableStateOf("")
    var merchant by mutableStateOf("")
    var timestamp by mutableLongStateOf(System.currentTimeMillis())
    var loading by mutableStateOf(txId != 0L)
        private set
    var isEdit by mutableStateOf(false)
        private set
    /** 表单校验/入库冲突的提示文案（资源 id，由 UI 层解析） */
    var errorMessageRes by mutableStateOf<Int?>(null)

    private var original: TransactionEntity? = null
    private var originalCategory: String? = null

    val categoryList: List<String>
        get() = if (isIncome) Categories.incomeCategories else Categories.expenseCategories

    init {
        if (txId != 0L) {
            viewModelScope.launch {
                repo.getById(txId)?.let { t ->
                    original = t
                    originalCategory = t.category
                    isEdit = true
                    isIncome = t.amount > 0
                    amount = String.format(Locale.US, "%.2f", kotlin.math.abs(t.amount))
                    category = t.category
                    note = t.note.orEmpty()
                    merchant = t.merchant.orEmpty()
                    timestamp = t.timestamp
                }
                loading = false
            }
        }
    }

    fun setType(income: Boolean) {
        if (isIncome == income) return
        isIncome = income
        if (category !in categoryList) category = categoryList.first()
    }

    fun setAmountInput(raw: String) {
        amount = raw.filter { it.isDigit() || it == '.' }.take(12)
    }

    fun save(onDone: () -> Unit) {
        val value = amount.toDoubleOrNull()
        if (value == null || value <= 0.0) {
            errorMessageRes = R.string.error_invalid_amount
            return
        }
        viewModelScope.launch {
            val existing = original
            if (existing != null) {
                val updated = existing.copy(
                    amount = if (isIncome) value else -value,
                    category = category,
                    note = note.trim().ifEmpty { null },
                    merchant = merchant.trim().ifEmpty { null },
                    timestamp = timestamp,
                )
                repo.update(updated)
                repo.learnFromEdit(updated, originalCategory)
                onDone()
            } else {
                val signed = if (isIncome) value else -value
                val m = merchant.trim().ifEmpty { null }
                val entity = TransactionEntity(
                    amount = signed,
                    category = category,
                    note = note.trim().ifEmpty { null },
                    merchant = m,
                    timestamp = timestamp,
                    source = "manual",
                    hash = HashUtil.transactionHash(timestamp, signed, m, "manual"),
                )
                if (repo.insertIfNew(entity)) {
                    onDone()
                } else {
                    errorMessageRes = R.string.error_duplicate_record
                }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val existing = original ?: return
        viewModelScope.launch {
            repo.delete(existing)
            onDone()
        }
    }
}
