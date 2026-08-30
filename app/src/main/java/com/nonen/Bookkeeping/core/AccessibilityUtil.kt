package com.nonen.Bookkeeping.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.google.android.accessibility.selecttospeak.SelectToSpeakService

object AccessibilityUtil {

    /** 判断本应用的无障碍自动记账服务是否已在系统设置中开启。 */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = ComponentName(context, SelectToSpeakService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
