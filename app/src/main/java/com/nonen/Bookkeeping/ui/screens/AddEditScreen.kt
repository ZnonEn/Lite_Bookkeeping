package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.ui.components.AnimatedSegmented
import com.nonen.Bookkeeping.ui.components.formatDateTime
import com.nonen.Bookkeeping.ui.components.localDateOf
import com.nonen.Bookkeeping.ui.motion.rememberPressScale
import com.nonen.Bookkeeping.ui.theme.AppleBlue
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

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
    var timestamp by mutableStateOf(System.currentTimeMillis())
    var loading by mutableStateOf(txId != 0L)
        private set
    var isEdit by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

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

    fun save(onDone: () -> Unit) {
        val value = amount.toDoubleOrNull()
        if (value == null || value <= 0.0) {
            errorMessage = "请输入正确的金额"
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
                    errorMessage = "已存在完全相同的记录，请勿重复添加"
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(vm: AddEditViewModel, onBack: () -> Unit) {
    if (vm.loading) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(14.dp)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 状态栏/导航栏/键盘 insets 全部让位：头部不顶进状态栏，
            // 键盘弹出时视口收缩，配合 verticalScroll 可滚到被挡住的字段
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 头部：返回 + 标题 + 日期胶囊
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (vm.isEdit) "编辑记录" else "记一笔",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.width(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatDateTime(vm.timestamp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            // 支出/收入 分段控件（滑块弹簧动画，选中侧填充收支色）
            AnimatedSegmented(
                options = listOf("支出", "收入"),
                selectedIndex = if (vm.isIncome) 1 else 0,
                onSelected = { vm.setType(it == 1) },
                thumbColor = if (vm.isIncome) IncomeColor else ExpenseColor,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                TextField(
                    value = vm.amount,
                    onValueChange = { s ->
                        vm.amount = s.filter { it.isDigit() || it == '.' }.take(12)
                    },
                    prefix = {
                        Text(
                            "¥ ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    placeholder = {
                        Text(
                            "0.00",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

            Spacer(Modifier.height(20.dp))
            Text("分类", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            vm.categoryList.chunked(3).forEach { rowCats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowCats.forEach { c ->
                        CategoryCell(
                            name = c,
                            selected = vm.category == c,
                            modifier = Modifier.weight(1f),
                        ) { vm.category = c }
                    }
                    repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.merchant,
                onValueChange = { vm.merchant = it },
                label = { Text("商户 / 交易对象（可选）") },
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.note,
                onValueChange = { vm.note = it },
                label = { Text("备注（可选）") },
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            vm.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))
            val (saveSource, saveScale) = rememberPressScale(0.98f)
            Button(
                onClick = { vm.save(onBack) },
                interactionSource = saveSource,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.isIncome) IncomeColor else ExpenseColor,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                modifier = saveScale
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (vm.isEdit) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { vm.delete(onBack) },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("删除这条记录") }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val initialUtc = remember(vm.timestamp) {
            localDateOf(vm.timestamp).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { sel ->
                        val picked = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                        val oldTime =
                            Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault())
                                .toLocalTime()
                        vm.timestamp =
                            picked.atTime(oldTime).atZone(ZoneId.systemDefault()).toInstant()
                                .toEpochMilli()
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.timestamp = Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryCell(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (source, scale) = rememberPressScale(0.96f)
    Column(
        modifier = modifier
            .then(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AppleBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    AppleBlue.copy(alpha = 0.3f),
                    RoundedCornerShape(18.dp)
                )
                else Modifier
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = Categories.emoji(name), fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppleBlue else MaterialTheme.colorScheme.onSurface,
        )
    }
}
