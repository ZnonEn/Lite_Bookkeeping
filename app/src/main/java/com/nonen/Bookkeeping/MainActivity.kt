package com.nonen.Bookkeeping

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nonen.Bookkeeping.data.prefs.ThemeMode
import com.nonen.Bookkeeping.ui.AppNavHost
import com.nonen.Bookkeeping.ui.theme.BookkeepingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as BookkeepingApp
            val mode by app.container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            BookkeepingTheme(darkTheme = darkTheme) {
                RequestNotificationPermissionIfNeeded()
                AppNavHost()
            }
        }
    }
}

@Composable
private fun RequestNotificationPermissionIfNeeded() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
