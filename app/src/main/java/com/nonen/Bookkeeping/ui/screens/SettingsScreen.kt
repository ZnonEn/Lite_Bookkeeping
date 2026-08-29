package com.nonen.Bookkeeping.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.AppContainer
import com.nonen.Bookkeeping.core.AccessibilityUtil
import com.nonen.Bookkeeping.data.prefs.ListenScope
import com.nonen.Bookkeeping.data.prefs.ThemeMode
import com.nonen.Bookkeeping.export.BackupExporter
import com.nonen.Bookkeeping.parse.AlipayBillParser
import com.nonen.Bookkeeping.parse.WechatBillParser
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settings

    var autoRecord by mutableStateOf(true)
    var listenScope by mutableStateOf(ListenScope.ALL)
    var notifyOnRecord by mutableStateOf(true)
    var learnOnEdit by mutableStateOf(true)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var importing by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
    var accessibilityEnabled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val s = settings.snapshot()
            autoRecord = s.autoRecordEnabled
            listenScope = s.listenScope
            notifyOnRecord = s.notifyOnRecord
            learnOnEdit = s.learnOnEdit
            themeMode = s.themeMode
        }
        refreshAccessibility()
    }

    fun refreshAccessibility() {
        accessibilityEnabled = AccessibilityUtil.isServiceEnabled(container.appContext)
    }

    fun updateAutoRecord(v: Boolean) {
        autoRecord = v
        viewModelScope.launch { settings.setAutoRecordEnabled(v) }
    }

    fun updateListenScope(v: ListenScope) {
        listenScope = v
        viewModelScope.launch { settings.setListenScope(v) }
    }

    fun updateNotify(v: Boolean) {
        notifyOnRecord = v
        viewModelScope.launch { settings.setNotifyOnRecord(v) }
    }

    fun updateLearn(v: Boolean) {
        learnOnEdit = v
        viewModelScope.launch { settings.setLearnOnEdit(v) }
    }

    fun updateThemeMode(v: ThemeMode) {
        themeMode = v
        viewModelScope.launch { settings.setThemeMode(v) }
    }

    fun importFromUri(uri: Uri, source: String) {
        viewModelScope.launch {
            importing = true
            statusMessage = runCatching {
                val bytes = container.appContext.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: error("无法读取所选文件")
                val rows = when (source) {
                    WechatBillParser.SOURCE -> WechatBillParser.parse(bytes)
                    else -> AlipayBillParser.parse(bytes)
                }
                if (rows.none { !it.skipped }) {
                    "未从文件中解析到有效账单记录，请确认选择了正确的账单文件"
                } else {
                    val r = container.billImporter.import(rows, source)
                    "导入完成：成功 ${r.success} 条，重复 ${r.duplicates} 条，失败 ${r.failed} 条，忽略 ${r.skipped} 条"
                }
            }.getOrElse { "导入失败：${it.message}" }
            importing = false
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            statusMessage = runCatching {
                val all = container.transactionRepository.getAll()
                val json = BackupExporter.buildJson(all)
                container.appContext.contentResolver.openOutputStream(uri)
                    ?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("无法写入所选文件")
                "备份导出成功：共 ${all.size} 条记录"
            }.getOrElse { "导出失败：${it.message}" }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onRules: () -> Unit) {
    val context = LocalContext.current
    var guideSource by remember { mutableStateOf<String?>(null) }
    var pendingSource by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshAccessibility()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val src = pendingSource
        if (uri != null && src != null) vm.importFromUri(uri, src)
        pendingSource = null
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTo(it) }
    }

    // 设置页作为 MainScreen Pager 的一页，直接输出滚动内容（底栏由 MainScreen 提供）
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
            SectionTitle("外观")
            SectionCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = mode.label,
                            selected = vm.themeMode == mode,
                            onClick = { vm.updateThemeMode(mode) },
                        )
                    }
                }
            }

            SectionTitle("自动记账")
            SectionCard {
                ToggleRow(
                    title = "启用无障碍自动记账",
                    subtitle = "监听支付通知与账单页面，自动记录收支",
                    checked = vm.autoRecord,
                    onChecked = vm::updateAutoRecord,
                )
                if (!vm.accessibilityEnabled) {
                    Text(
                        "⚠ 无障碍服务未开启，自动记账不会生效",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    TextButton(
                        onClick = { context.startActivity(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("去开启无障碍服务") }
                }
            }

            SectionCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ListenScope.entries.forEach { scope ->
                        RadioRow(
                            label = scope.label,
                            selected = vm.listenScope == scope,
                            onClick = { vm.updateListenScope(scope) },
                        )
                    }
                }
            }

            SectionCard {
                ToggleRow(
                    title = "自动记录成功后提醒",
                    subtitle = "发一条本地通知，方便核对",
                    checked = vm.notifyOnRecord,
                    onChecked = vm::updateNotify,
                )
                ToggleRow(
                    title = "手动改分类时自动学习",
                    subtitle = "记住你的修改，下次同类交易自动归类",
                    checked = vm.learnOnEdit,
                    onChecked = vm::updateLearn,
                )
            }

            SectionTitle("账单导入")
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "从微信/支付宝导出账单文件后导入，自动去重、自动分类。\n微信为 xlsx 文件，支付宝为 csv 文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { guideSource = WechatBillParser.SOURCE },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        ) { Text("导入微信账单") }
                        Button(
                            onClick = { guideSource = AlipayBillParser.SOURCE },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        ) { Text("导入支付宝账单") }
                    }
                }
                if (vm.importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp))
                }
                vm.statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    )
                }
            }

            SectionTitle("分类规则")
            SectionCard {
                TextButton(onClick = onRules, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("管理分类规则", color = MaterialTheme.colorScheme.secondary)
                }
            }

            SectionTitle("数据备份")
            SectionCard {
                TextButton(
                    onClick = { exportLauncher.launch("bookkeeping_backup_${LocalDate.now()}.json") },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("导出 JSON 备份", color = MaterialTheme.colorScheme.secondary) }
            }

            SectionTitle("关于")
            SectionCard {
                Text(
                    "本地记账 · 数据仅保存在本机 · 不请求网络权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(96.dp))
    }

    guideSource?.let { source ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { guideSource = null },
            title = { Text(if (source == WechatBillParser.SOURCE) "微信账单导出步骤" else "支付宝账单导出步骤") },
            text = { Text(guideText(source)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingSource = source
                    importLauncher.launch(arrayOf("*/*"))
                    guideSource = null
                }) { Text("选择文件") }
            },
            dismissButton = { TextButton(onClick = { guideSource = null }) { Text("取消") } },
        )
    }
}

private fun guideText(source: String): String = if (source == WechatBillParser.SOURCE) {
    "1. 打开微信：我 → 服务 → 钱包 → 账单\n" +
        "2. 点击右上角「常见问题」\n" +
        "3. 选择「下载账单」→「用于个人对账」\n" +
        "4. 选择时间范围，发送到邮箱\n" +
        "5. 在邮箱中下载账单 xlsx 文件\n" +
        "6. 回到本应用，点击「选择文件」选中该文件"
} else {
    "1. 打开支付宝：我的 → 账单\n" +
        "2. 点击右上角「…」→「开具交易流水证明」\n" +
        "3. 选择「用于个人对账」，选择时间范围\n" +
        "4. 发送到邮箱并下载 csv 文件\n" +
        "5. 回到本应用，点击「选择文件」选中该文件"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
