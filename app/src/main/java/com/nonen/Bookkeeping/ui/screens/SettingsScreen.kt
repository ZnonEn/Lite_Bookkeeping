package com.nonen.Bookkeeping.ui.screens

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nonen.Bookkeeping.R
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

    // 设置页作为 MainScreen Pager 的一页，直接输出滚动内容（底栏由 MainScreen 提供）
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        AppearanceSection(vm)

        // 自动记账是核心功能且承载授权状态提醒，默认展开
        CollapsibleSection(title = stringResource(R.string.section_auto_record), emoji = "⚡", initiallyExpanded = true) {
            AutoRecordSection(vm)
            CaptureDebugCard()
            SectionDivider()
            ListenScopeSection(vm)
            SectionDivider()
            ToggleRow(
                title = stringResource(R.string.toggle_notify_title),
                subtitle = stringResource(R.string.toggle_notify_subtitle),
                checked = vm.notifyOnRecord,
                onChecked = vm::updateNotify,
            )
            ToggleRow(
                title = stringResource(R.string.toggle_learn_title),
                subtitle = stringResource(R.string.toggle_learn_subtitle),
                checked = vm.learnOnEdit,
                onChecked = vm::updateLearn,
            )
        }

        CollapsibleSection(title = stringResource(R.string.section_bill_import), emoji = "📥") {
            BillImportSection(
                importing = vm.importing,
                importProgress = vm.importProgress,
                statusMessage = vm.statusMessage,
                onImportWechat = { guideSource = WechatBillParser.SOURCE },
                onImportAlipay = { guideSource = AlipayBillParser.SOURCE },
            )
        }

        CollapsibleSection(title = stringResource(R.string.section_category_rules), emoji = "🏷️") {
            CategoryRuleSection(
                reclassifying = vm.reclassifying,
                result = vm.reclassifyResult,
                onManage = onRules,
                onReclassify = { showReclassifyDialog = true },
            )
        }

        CollapsibleSection(title = stringResource(R.string.section_backup), emoji = "💾") {
            BackupSection(
                importing = vm.importing,
                importProgress = vm.importProgress,
                statusMessage = vm.statusMessage,
                onExport = { exportLauncher.launch("bookkeeping_backup_${LocalDate.now()}.xlsx") },
                onImport = { backupLauncher.launch(arrayOf("*/*")) },
            )
        }

        CollapsibleSection(title = stringResource(R.string.section_about), emoji = "ℹ️") {
            AboutSection(versionName = vm.versionName, onCheckUpdate = { showUpdateDialog = true })
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.dialog_update_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_update_current_version, vm.versionName))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dialog_update_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, RELEASE_PAGE_URL.toUri()))
                }) { Text(stringResource(R.string.action_go_releases)) }
            },
        )
    }

    if (showReclassifyDialog) {
        AlertDialog(
            onDismissRequest = { showReclassifyDialog = false },
            title = { Text(stringResource(R.string.dialog_reclassify_title)) },
            text = { Text(stringResource(R.string.dialog_reclassify_message)) },
            dismissButton = { TextButton(onClick = { showReclassifyDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    showReclassifyDialog = false
                    vm.reclassifyAll()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
        )
    }

    guideSource?.let { source ->
        AlertDialog(
            onDismissRequest = { guideSource = null },
            title = {
                Text(
                    stringResource(
                        if (source == WechatBillParser.SOURCE) R.string.dialog_wechat_export_title
                        else R.string.dialog_alipay_export_title,
                    ),
                )
            },
            text = { Text(guideText(source)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingSource = source
                    importLauncher.launch(arrayOf("*/*"))
                    guideSource = null
                }) { Text(stringResource(R.string.action_choose_file)) }
            },
            dismissButton = { TextButton(onClick = { guideSource = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** 外观分组：主题模式单选 */
@Composable
private fun AppearanceSection(vm: SettingsViewModel) {
    CollapsibleSection(title = stringResource(R.string.section_appearance), emoji = "🎨") {
        Column(Modifier.padding(vertical = 4.dp)) {
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = stringResource(mode.labelRes),
                    selected = vm.themeMode == mode,
                    onClick = { vm.updateThemeMode(mode) },
                )
            }
        }
    }
}

/** 自动记账分组：总开关、权限引导 */
@Composable
private fun AutoRecordSection(vm: SettingsViewModel) {
    val context = LocalContext.current
    ToggleRow(
        title = stringResource(R.string.toggle_auto_record_title),
        subtitle = stringResource(R.string.toggle_auto_record_subtitle),
        checked = vm.autoRecord,
        onChecked = vm::updateAutoRecord,
    )

    if (!vm.accessibilityEnabled) {
        Text(
            stringResource(R.string.warning_accessibility_disabled),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = { context.startActivity(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.action_open_accessibility)) }
    }

    if (vm.accessibilityEnabled && !vm.overlayPermissionEnabled) {
        Text(
            stringResource(R.string.hint_overlay_permission),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(
                        SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    ),
                )
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.action_grant_overlay)) }
    }

    if (vm.accessibilityEnabled && !vm.notificationAccessEnabled) {
        Text(
            stringResource(R.string.hint_notification_access),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.action_grant_notification_access)) }
    }
}

/** 监听范围单选 */
@Composable
private fun ListenScopeSection(vm: SettingsViewModel) {
    Column(Modifier.padding(vertical = 4.dp)) {
        ListenScope.entries.forEach { scope ->
            RadioRow(
                label = stringResource(scope.labelRes),
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
            stringResource(R.string.bill_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ActionButtonRow {
            SettingsActionButton(stringResource(R.string.action_import_wechat), onImportWechat)
            SettingsActionButton(stringResource(R.string.action_import_alipay), onImportAlipay)
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
            SettingsActionButton(stringResource(R.string.action_manage_rules), onManage)
            SettingsActionButton(
                text = stringResource(if (reclassifying) R.string.action_reclassifying else R.string.action_reclassify),
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
            stringResource(R.string.backup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ActionButtonRow {
            SettingsActionButton(stringResource(R.string.action_export_backup), onExport)
            SettingsActionButton(stringResource(R.string.action_import_backup), onImport)
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
        Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = onCheckUpdate, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(R.string.action_check_update))
    }
}

@Composable
private fun guideText(source: String): String = if (source == WechatBillParser.SOURCE) {
    stringResource(R.string.dialog_wechat_export_steps)
} else {
    stringResource(R.string.dialog_alipay_export_steps)
}
