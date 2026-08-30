package com.nonen.Bookkeeping.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object Packages {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
}

enum class ListenScope(val label: String, val packages: Set<String>) {
    ALL("全部（微信 + 支付宝）", setOf(Packages.WECHAT, Packages.ALIPAY)),
    WECHAT_ONLY("仅微信", setOf(Packages.WECHAT)),
    ALIPAY_ONLY("仅支付宝", setOf(Packages.ALIPAY));

    companion object {
        fun from(name: String?): ListenScope = entries.firstOrNull { it.name == name } ?: ALL
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色模式"), DARK("深色模式");

    companion object {
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class SettingsSnapshot(
    val autoRecordEnabled: Boolean,
    val listenScope: ListenScope,
    val notifyOnRecord: Boolean,
    val learnOnEdit: Boolean,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val captureDebug: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private val store = context.settingsDataStore

    private val KEY_AUTO_RECORD = booleanPreferencesKey("auto_record_enabled")
    private val KEY_LISTEN_SCOPE = stringPreferencesKey("listen_scope")
    private val KEY_NOTIFY = booleanPreferencesKey("notify_on_record")
    private val KEY_LEARN = booleanPreferencesKey("learn_on_edit")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_CAPTURE_DEBUG = booleanPreferencesKey("capture_debug")

    val autoRecordEnabled: Flow<Boolean> = store.data.map { it[KEY_AUTO_RECORD] ?: true }
    val listenScope: Flow<ListenScope> = store.data.map { ListenScope.from(it[KEY_LISTEN_SCOPE]) }
    val notifyOnRecord: Flow<Boolean> = store.data.map { it[KEY_NOTIFY] ?: true }
    val learnOnEdit: Flow<Boolean> = store.data.map { it[KEY_LEARN] ?: true }
    val themeMode: Flow<ThemeMode> = store.data.map { ThemeMode.from(it[KEY_THEME_MODE]) }
    val captureDebug: Flow<Boolean> = store.data.map { it[KEY_CAPTURE_DEBUG] ?: false }

    suspend fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        autoRecordEnabled = autoRecordEnabled.first(),
        listenScope = listenScope.first(),
        notifyOnRecord = notifyOnRecord.first(),
        learnOnEdit = learnOnEdit.first(),
        themeMode = themeMode.first(),
        captureDebug = captureDebug.first(),
    )

    suspend fun setCaptureDebug(value: Boolean) = store.edit { it[KEY_CAPTURE_DEBUG] = value }

    suspend fun setAutoRecordEnabled(value: Boolean) = store.edit { it[KEY_AUTO_RECORD] = value }
    suspend fun setListenScope(value: ListenScope) = store.edit { it[KEY_LISTEN_SCOPE] = value.name }
    suspend fun setNotifyOnRecord(value: Boolean) = store.edit { it[KEY_NOTIFY] = value }
    suspend fun setLearnOnEdit(value: Boolean) = store.edit { it[KEY_LEARN] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[KEY_THEME_MODE] = value.name }
}
