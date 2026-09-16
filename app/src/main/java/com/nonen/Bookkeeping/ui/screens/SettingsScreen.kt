package com.nonen.Bookkeeping.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nonen.Bookkeeping.data.prefs.ListenScope
import com.nonen.Bookkeeping.data.prefs.ThemeMode
import com.nonen.Bookkeeping.debug.CaptureDebugCard
import com.nonen.Bookkeeping.parse.AlipayBillParser
import com.nonen.Bookkeeping.parse.WechatBillParser
import java.time.LocalDate

/** GitHub Releases 页（自动重定向到最新版本），由系统浏览器打开，应用自身不联网 */
private const val RELEASE_PAGE_URL = "https://github.com/ZnonEn/Lite_Bookkeeping/releases/latest"

@Composable
fun SettingsScreen(vm: SettingsViewModel, onRules: () -> Unit) {
    val context = LocalContext.current
    var guideSource by remember { mutableStateOf<String?>(null) }
    var pendingSource by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showReclassifyDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshRuntimeState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val src = pendingSource
        if (uri != null && src != null) vm.importBillFile(uri, src)
        pendingSource = null
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importBackup(it) }
    }
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            vm.startOcr(context, data)
        }
    }

    // 设置页作为 MainScreen Pager 的一页，直接输出滚动内容（底栏由 MainScreen 提供）
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        AppearanceSection(vm)

        // 自动记账是核心功能且承载授权状态提醒，默认展开
        CollapsibleSection(title = "自动记账", emoji = "⚡", initiallyExpanded = true) {
            AutoRecordSection(
                vm = vm,
                onStartProjection = {
                    context.getSystemService(MediaProjectionManager::class.java)?.let { mgr ->
                        projectionLauncher.launch(mgr.createScreenCaptureIntent())
                    }
                },
            )
            CaptureDebugCard()
            SectionDivider()
            ListenScopeSection(vm)
            SectionDivider()
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

        CollapsibleSection(title = "账单导入", emoji = "📥") {
            BillImportSection(
                importing = vm.importing,
                importProgress = vm.importProgress,
                statusMessage = vm.statusMessage,
                onImportWechat = { guideSource = WechatBillParser.SOURCE },
                onImportAlipay = { guideSource = AlipayBillParser.SOURCE },
            )
        }

        CollapsibleSection(title = "分类规则", emoji = "🏷️") {
            CategoryRuleSection(
                reclassifying = vm.reclassifying,
                result = vm.reclassifyResult,
                onManage = onRules,
                onReclassify = { showReclassifyDialog = true },
            )
        }

        CollapsibleSection(title = "数据备份", emoji = "💾") {
            BackupSection(
                importing = vm.importing,
                importProgress = vm.importProgress,
                statusMessage = vm.statusMessage,
                onExport = { exportLauncher.launch("bookkeeping_backup_${LocalDate.now()}.xlsx") },
                onImport = { backupLauncher.launch(arrayOf("*/*")) },
            )
        }

        CollapsibleSection(title = "关于", emoji = "ℹ️") {
            AboutSection(versionName = vm.versionName, onCheckUpdate = { showUpdateDialog = true })
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("检查更新") },
            text = {
                Column {
                    Text("当前版本：v${vm.versionName}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "为保护隐私，应用不申请网络权限，因此不会自动检查更新。" +
                            "获取最新版本请前往 GitHub Releases 页面手动查看与下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE_URL)))
                }) { Text("前往 Releases") }
            },
        )
    }

    if (showReclassifyDialog) {
        AlertDialog(
            onDismissRequest = { showReclassifyDialog = false },
            title = { Text("重新分类历史账单") },
            text = { Text("将按当前规则重算所有账单的分类，会覆盖手动改过的分类（手动学习产生的自定义规则仍然优先）。确定执行？") },
            dismissButton = { TextButton(onClick = { showReclassifyDialog = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    showReclassifyDialog = false
                    vm.reclassifyAll()
                }) { Text("确定") }
            },
        )
    }

    guideSource?.let { source ->
        AlertDialog(
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

/** 外观分组：主题模式单选 */
@Composable
private fun AppearanceSection(vm: SettingsViewModel) {
    CollapsibleSection(title = "外观", emoji = "🎨") {
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
}

/** 自动记账分组：总开关、权限引导、OCR 通道 */
@Composable
private fun AutoRecordSection(
    vm: SettingsViewModel,
    onStartProjection: () -> Unit,
) {
    val context = LocalContext.current
    ToggleRow(
        title = "启用自动记账",
        subtitle = "检测到支付时弹出确认卡片，手动确认后登记入账",
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

    if (vm.accessibilityEnabled && !vm.overlayPermissionEnabled) {
        Text(
            "检测到支付但没弹确认卡片？去 系统设置→应用→轻记账→权限 开启「显示悬浮窗」；" +
                "MIUI/HyperOS 还需开启「后台弹出界面」与「锁屏显示」（部分系统此处显示未授权但实际可用，可直接付款试试）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(
                        SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("去授权悬浮窗") }
    }

    if (vm.accessibilityEnabled && !vm.notificationAccessEnabled) {
        Text(
            "微信/支付宝的支付页面对无障碍隐藏内容，建议同时开启「通知使用权」——支付完成后的系统通知会带金额，由它兜底记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("去开启通知使用权") }
    }

    // 屏幕识别（OCR）兜底通道
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("屏幕识别（OCR 兜底）", style = MaterialTheme.typography.bodyMedium)
            Text(
                "通知没有金额且支付页面对无障碍隐藏时（如支付宝扫码），抓取屏幕文字识别金额与方向。需授权屏幕录制，重启手机后需重新授权",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (vm.ocrRunning) "状态：运行中 · ${vm.ocrStatus}" else "状态：未开启",
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.ocrRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = onStartProjection) {
            Text(if (vm.ocrRunning) "重新授权屏幕录制" else "授权屏幕录制并开启")
        }
        if (vm.ocrRunning) {
            TextButton(onClick = { vm.stopOcr(context) }) { Text("停止") }
        }
    }
}

/** 监听范围单选 */
@Composable
private fun ListenScopeSection(vm: SettingsViewModel) {
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

/** 账单导入分组 */
@Composable
private fun BillImportSection(
    importing: Boolean,
    importProgress: Float,
    statusMessage: String?,
    onImportWechat: () -> Unit,
    onImportAlipay: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            "从微信/支付宝导出账单文件后导入，自动去重、自动分类。\n微信为 xlsx 文件，支付宝为 csv 文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ActionButtonRow {
            SettingsActionButton("导入微信账单", onImportWechat)
            SettingsActionButton("导入支付宝账单", onImportAlipay)
        }
    }
    if (importing) {
        ImportProgressRow(
            progress = importProgress,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
    }
    statusMessage?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
    }
}

/** 分类规则分组 */
@Composable
private fun CategoryRuleSection(
    reclassifying: Boolean,
    result: String?,
    onManage: () -> Unit,
    onReclassify: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        ActionButtonRow {
            SettingsActionButton("管理分类规则", onManage)
            SettingsActionButton(
                text = if (reclassifying) "正在重算…" else "重新分类历史账单",
                onClick = onReclassify,
                enabled = !reclassifying,
            )
        }
        result?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 数据备份分组 */
@Composable
private fun BackupSection(
    importing: Boolean,
    importProgress: Float,
    statusMessage: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            "导出 / 导入本应用专属的 Excel 备份（.xlsx，可用 Excel/WPS 打开）；导入按校验码自动去重",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ActionButtonRow {
            SettingsActionButton("导出 Excel 备份", onExport)
            SettingsActionButton("导入 Excel 备份", onImport)
        }
        if (importing) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { importProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 关于分组 */
@Composable
private fun AboutSection(versionName: String, onCheckUpdate: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("轻记账 v$versionName", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "本地记账 · 数据仅保存在本机 · 不请求网络权限",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = onCheckUpdate, modifier = Modifier.padding(horizontal = 8.dp)) { Text("检查更新") }
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
