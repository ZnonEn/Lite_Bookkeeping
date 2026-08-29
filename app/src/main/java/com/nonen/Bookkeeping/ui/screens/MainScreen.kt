package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nonen.Bookkeeping.AppContainer
import com.nonen.Bookkeeping.ui.theme.InkPrimary
import com.nonen.Bookkeeping.ui.theme.UnselectedTabDark
import com.nonen.Bookkeeping.ui.theme.UnselectedTabLight
import com.nonen.Bookkeeping.ui.vmFactory
import kotlinx.coroutines.launch

/**
 * 主界面：首页 / 统计 / 设置 三个页面用 HorizontalPager 承载，
 * 底栏点击滚动切页、手势可水平滑动（参照墨麒麟 MainScreen 的做法）。
 * 记一笔 / 搜索 / 规则管理仍以 iOS 风格滑动推入的子页面打开。
 */
@Composable
fun MainScreen(
    container: AppContainer,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSearch: () -> Unit,
    onRules: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()
    val selectedTab by remember { derivedStateOf { pagerState.currentPage } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                onAdd = onAdd,
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> {
                    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(container.transactionRepository) })
                    HomeScreen(vm = vm, onSearch = onSearch, onEdit = onEdit)
                }
                1 -> {
                    val vm: StatsViewModel = viewModel(factory = vmFactory { StatsViewModel(container.transactionRepository) })
                    StatisticsScreen(vm = vm)
                }
                else -> {
                    val vm: SettingsViewModel = viewModel(factory = vmFactory { SettingsViewModel(container) })
                    SettingsScreen(vm = vm, onRules = onRules)
                }
            }
        }
    }
}

/** Apple Music 风格浮动胶囊底栏 + 圆形记账 FAB */
@Composable
private fun MainBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAdd: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val barColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.65f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
    val pillColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(barColor)
                .border(0.5.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(4.dp),
        ) {
            MainTab(Icons.Default.Home, "首页", selectedTab == 0, pillColor, isDark, Modifier.weight(1f)) { onTabSelected(0) }
            MainTab(Icons.Default.List, "统计", selectedTab == 1, pillColor, isDark, Modifier.weight(1f)) { onTabSelected(1) }
            MainTab(Icons.Default.Settings, "设置", selectedTab == 2, pillColor, isDark, Modifier.weight(1f)) { onTabSelected(2) }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(InkPrimary)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "记一笔",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MainTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    pillColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) {
        if (isDark) Color.White else Color(0xFF1D1D1F)
    } else {
        if (isDark) UnselectedTabDark else UnselectedTabLight
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) pillColor else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(1.dp))
            Text(text = label, fontSize = 9.sp, color = tint)
        }
    }
}
