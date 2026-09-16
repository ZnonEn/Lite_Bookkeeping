package com.nonen.Bookkeeping.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.AppContainer
import com.nonen.Bookkeeping.core.AccessibilityUtil
import com.nonen.Bookkeeping.core.HashUtil
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.prefs.ListenScope
import com.nonen.Bookkeeping.data.prefs.ThemeMode
import com.nonen.Bookkeeping.export.BackupExporter
import com.nonen.Bookkeeping.parse.AlipayBillParser
import com.nonen.Bookkeeping.parse.BackupExcelParser
import com.nonen.Bookkeeping.parse.WechatBillParser
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 设置页状态与业务动作。
 * 与 UI 解耦：所有耗时/IO 操作都在这里，Composable 只读状态、发事件。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settings

    var autoRecord by mutableStateOf(true)
    var listenScope by mutableStateOf(ListenScope.ALL)
    var notifyOnRecord by mutableStateOf(true)
    var learnOnEdit by mutableStateOf(true)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var importing by mutableStateOf(false)
        private set
    /** -1 = 解析文件中（不定态进度），0..1 = 逐行导入进度 */
    var importProgress by mutableStateOf(-1f)
        private set
    var statusMessage by mutableStateOf<String?>(null)
    var accessibilityEnabled by mutableStateOf(false)
        private set
    var notificationAccessEnabled by mutableStateOf(false)
        private set
    var overlayPermissionEnabled by mutableStateOf(false)
        private set
    var versionName by mutableStateOf("")
        private set
    var reclassifying by mutableStateOf(false)
        private set
    var reclassifyResult by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val s = settings.snapshot()
            autoRecord = s.autoRecordEnabled
            listenScope = s.listenScope
            notifyOnRecord = s.notifyOnRecord
            learnOnEdit = s.learnOnEdit
            themeMode = s.themeMode
        }
        refreshRuntimeState()
        versionName = runCatching {
            container.appContext.packageManager
                .getPackageInfo(container.appContext.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    /** 重新读取权限与服务的实时状态（从系统设置返回时调用） */
    fun refreshRuntimeState() {
        accessibilityEnabled = AccessibilityUtil.isServiceEnabled(container.appContext)
        notificationAccessEnabled = androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(container.appContext)
            .contains(container.appContext.packageName)
        overlayPermissionEnabled = android.provider.Settings.canDrawOverlays(container.appContext)
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

    /** 按当前分类规则重算全部历史账单的分类（覆盖手动改过的分类，学习规则优先） */
    fun reclassifyAll() {
        if (reclassifying) return
        viewModelScope.launch {
            reclassifying = true
            val changed = container.transactionRepository.reclassifyAll()
            reclassifying = false
            reclassifyResult = "已按当前规则重算，更新 $changed 条"
        }
    }

    /** 导入微信/支付宝官方账单文件 */
    fun importBillFile(uri: Uri, source: String) {
        viewModelScope.launch {
            importing = true
            importProgress = -1f
            statusMessage = runCatching {
                val bytes = readBytesOrThrow(uri)
                val rows = when (source) {
                    WechatBillParser.SOURCE -> WechatBillParser.parse(bytes)
                    else -> AlipayBillParser.parse(bytes)
                }
                if (rows.none { !it.skipped }) {
                    "未从文件中解析到有效账单记录，请确认选择了正确的账单文件"
                } else {
                    importProgress = 0f
                    val total = rows.size
                    val r = container.billImporter.import(rows, source, onProgress = { done, _ ->
                        importProgress = done.toFloat() / total
                    })
                    importProgress = 1f
                    "导入完成：成功 ${r.success} 条，重复 ${r.duplicates} 条，失败 ${r.failed} 条，忽略 ${r.skipped} 条"
                }
            }.getOrElse { "导入失败：${it.message}" }
            importing = false
        }
    }

    /** 导出本应用专属 Excel 备份 */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            statusMessage = runCatching {
                val all = container.transactionRepository.getAll()
                val bytes = BackupExporter.buildXlsx(all)
                container.appContext.contentResolver.openOutputStream(uri)
                    ?.use { it.write(bytes) }
                    ?: error("无法写入所选文件")
                "备份导出成功：共 ${all.size} 条记录（Excel）"
            }.getOrElse { "导出失败：${it.message}" }
        }
    }

    /** 导入本应用导出的 Excel 备份：按校验码原样回灌，重复导入自动去重 */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            importing = true
            importProgress = -1f
            statusMessage = runCatching {
                val bytes = readBytesOrThrow(uri)
                val rows = BackupExcelParser.parse(bytes)
                    ?: error("不是本应用导出的 Excel 备份格式")
                if (rows.isEmpty()) error("备份中没有账单记录")
                importProgress = 0f
                val total = rows.size
                var success = 0
                var duplicates = 0
                var failed = 0
                rows.forEachIndexed { index, row ->
                    when {
                        !row.typeValid || row.timestamp == null || row.amount == null -> failed++
                        else -> {
                            val signed = if (row.isIncome) row.amount!! else -row.amount!!
                            val source = row.source ?: "excel"
                            val entity = TransactionEntity(
                                amount = signed,
                                category = row.category
                                    ?: container.ruleEngine.categorize(
                                        listOfNotNull(row.merchant, row.note).joinToString(" "),
                                        row.isIncome,
                                    ),
                                note = row.note,
                                merchant = row.merchant,
                                timestamp = row.timestamp!!,
                                source = source,
                                hash = row.hash
                                    ?: HashUtil.transactionHash(row.timestamp!!, signed, row.merchant, source),
                            )
                            if (container.transactionRepository.insertIfNew(entity)) success++ else duplicates++
                        }
                    }
                    importProgress = (index + 1).toFloat() / total
                }
                importProgress = 1f
                "备份导入完成：成功 $success 条，重复 $duplicates 条，失败 $failed 条"
            }.getOrElse { "导入失败：${it.message}" }
            importing = false
        }
    }

    private fun readBytesOrThrow(uri: Uri): ByteArray =
        container.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选文件")
}
