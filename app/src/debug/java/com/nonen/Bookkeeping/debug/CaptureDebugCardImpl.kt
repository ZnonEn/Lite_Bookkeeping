package com.nonen.Bookkeeping.debug

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nonen.Bookkeeping.ui.screens.SectionCard
import com.nonen.Bookkeeping.ui.screens.ToggleRow
import kotlinx.coroutines.delay

/**
 * 「抓取调试」设置面板（src/debug 源集，仅 test 分支的 debug 构建出现）。
 * 开启时每 1.5 秒刷新一次诊断报告，操作完切回设置页可直接看到最新抓取结论。
 */
@Composable
fun CaptureDebugCard() {
    val context = LocalContext.current
    val enabled by CaptureDebug.enabled.collectAsState()
    val report by CaptureDebug.report.collectAsState()
    LaunchedEffect(enabled) {
        while (enabled) {
            CaptureDebug.refresh(context)
            delay(1500)
        }
    }
    SectionCard {
        ToggleRow(
            title = "抓取调试（排查自动记账）",
            subtitle = "记录无障碍抓到的页面文本与解析结论，用于定位抓不到的原因",
            checked = enabled,
            onChecked = { CaptureDebug.setEnabled(context, it) },
        )
        report?.let { r ->
            Text(
                r,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("capture_debug", r))
                }) { Text("复制诊断信息") }
                TextButton(onClick = { CaptureDebug.clear(context) }) { Text("清空") }
            }
        }
    }
}
