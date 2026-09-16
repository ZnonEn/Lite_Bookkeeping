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
import androidx.compose.ui.res.stringResource
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.ui.components.AnimatedSegmented
import com.nonen.Bookkeeping.ui.components.formatDateTime
import com.nonen.Bookkeeping.ui.components.localDateOf
import com.nonen.Bookkeeping.ui.motion.rememberPressScale
import com.nonen.Bookkeeping.ui.theme.AppleBlue
import com.nonen.Bookkeeping.ui.theme.ExpenseColor
import com.nonen.Bookkeeping.ui.theme.IncomeColor
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(vm: AddEditViewModel, onBack: () -> Unit) {
    if (vm.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        AddEditHeader(
            isEdit = vm.isEdit,
            timestamp = vm.timestamp,
            onBack = onBack,
            onPickDateTime = { showDatePicker = true },
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            AnimatedSegmented(
                options = listOf(stringResource(R.string.type_expense), stringResource(R.string.type_income)),
                selectedIndex = if (vm.isIncome) 1 else 0,
                onSelected = { vm.setType(it == 1) },
                thumbColor = if (vm.isIncome) IncomeColor else ExpenseColor,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            AmountField(value = vm.amount, onValueChange = vm::setAmountInput)

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.label_category), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            CategoryGrid(
                categories = vm.categoryList,
                selected = vm.category,
                onSelect = { vm.category = it },
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.merchant,
                onValueChange = { vm.merchant = it },
                label = { Text(stringResource(R.string.field_merchant)) },
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
                label = { Text(stringResource(R.string.field_note)) },
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            vm.errorMessageRes?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
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
                modifier = saveScale.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(R.string.action_save), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (vm.isEdit) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { vm.delete(onBack) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_delete_record)) }
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
                        val oldTime = Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
                        vm.timestamp = picked.atTime(oldTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.dialog_pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.timestamp = Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** 头部：返回 + 标题 + 日期时间胶囊 */
@Composable
private fun AddEditHeader(
    isEdit: Boolean,
    timestamp: Long,
    onBack: () -> Unit,
    onPickDateTime: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(if (isEdit) R.string.title_edit_record else R.string.title_new_record),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPickDateTime)
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
                text = formatDateTime(timestamp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 金额输入卡（大字） */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            prefix = {
                Text("¥ ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            placeholder = {
                Text("0.00", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
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
}

/** 分类九宫格（3 列），选中项高亮描边 */
@Composable
internal fun CategoryGrid(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(3).forEach { rowCats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowCats.forEach { c ->
                    CategoryCell(
                        name = c,
                        selected = selected == c,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(c) },
                    )
                }
                repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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
                if (selected) {
                    Modifier.border(1.dp, AppleBlue.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
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
