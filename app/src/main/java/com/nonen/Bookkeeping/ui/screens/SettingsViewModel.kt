package com.nonen.Bookkeeping.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonen.Bookkeeping.AppContainer
import com.nonen.Bookkeeping.R
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 设置页状态与业务动作。
 * 与 UI 解耦：所有耗时/IO 操作都在这里，Composable 只读状态、发事件。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settings

    /**
     * Application context：仅用于读取字符串资源与 ContentResolver。
     * lint 的 StaticFieldLeak 针对 Activity/Fragment context，Application 实例与进程同生命周期，
     * 不构成泄漏。
     */
    @Suppress("StaticFieldLeak")
    private val ctx = container.appContext

    var autoRecord by mutableStateOf(true)
    var listenScope by mutableStateOf(ListenScope.ALL)
    var notifyOnRecord by mutableStateOf(true)
    var learnOnEdit by mutableStateOf(true)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var importing by mutableStateOf(false)
        private set
    /** -1 = 解析文件中（不定态进度），0..1 = 逐行导入进度 */
    var importProgress by mutableFloatStateOf(-1f)
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
            reclassifyResult = ctx.getString(R.string.status_reclassified, changed)
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
                    ctx.getString(R.string.status_import_empty)
                } else {
                    importProgress = 0f
                    val total = rows.size
                    val r = container.billImporter.import(rows, source, onProgress = { done, _ ->
                        importProgress = done.toFloat() / total
                    })
                    importProgress = 1f
                    ctx.getString(R.string.status_import_done, r.success, r.duplicates, r.failed, r.skipped)
                }
            }.getOrElse { ctx.getString(R.string.status_import_failed, it.message.orEmpty()) }
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
                    ?: error(ctx.getString(R.string.error_cannot_write_file))
                ctx.getString(R.string.status_backup_exported, all.size)
            }.getOrElse { ctx.getString(R.string.status_backup_export_failed, it.message.orEmpty()) }
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
                    ?: error(ctx.getString(R.string.error_not_backup_format))
                if (rows.isEmpty()) error(ctx.getString(R.string.error_backup_empty))
                importProgress = 0f
                val total = rows.size
                var success = 0
                var duplicates = 0
                var failed = 0
                rows.forEachIndexed { index, row ->
                    val timestamp = row.timestamp
                    val amount = row.amount
                    when {
                        !row.typeValid || timestamp == null || amount == null -> failed++
                        else -> {
                            val signed = if (row.isIncome) amount else -amount
                            val source = row.source ?: "excel"
                            val entity = TransactionEntity(
                                amount = signed,
                                category = row.category
                                    ?: container.ruleEngine.categorize(
                                        text = listOfNotNull(row.merchant, row.note).joinToString(" "),
                                        isIncome = row.isIncome,
                                        merchant = row.merchant,
                                    ),
                                note = row.note,
                                merchant = row.merchant,
                                timestamp = timestamp,
                                source = source,
                                hash = row.hash
                                    ?: HashUtil.transactionHash(timestamp, signed, row.merchant, source),
                            )
                            if (container.transactionRepository.insertIfNew(entity)) success++ else duplicates++
                        }
                    }
                    importProgress = (index + 1).toFloat() / total
                }
                importProgress = 1f
                ctx.getString(R.string.status_backup_imported, success, duplicates, failed)
            }.getOrElse { ctx.getString(R.string.status_import_failed, it.message.orEmpty()) }
            importing = false
        }
    }

    private fun readBytesOrThrow(uri: Uri): ByteArray =
        container.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error(ctx.getString(R.string.error_cannot_read_file))
}
