package com.nonen.Bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.stats.CategoryStat
import com.nonen.Bookkeeping.stats.StatsBucket
import com.nonen.Bookkeeping.ui.components.formatCompactAmount
import com.nonen.Bookkeeping.ui.components.formatPlainAmount
import com.nonen.Bookkeeping.ui.theme.ChartColors
import java.util.Locale

/**
 * 统计页图表与卡片组件。
 * 全部为无状态组件（数据入参 + 回调），可独立预览与复用。
 */

/** 可横向滚动的下钻筛选条（全部 / 第N周 / 各月…） */
@Composable
internal fun SubFilterBar(options: List<String>, selectedIndex: Int?, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        options.forEachIndexed { idx, label ->
            val selected = selectedIndex != null && (if (idx == 0) selectedIndex == null else selectedIndex == idx)
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 日期胶囊（自定义区间用） */
@Composable
internal fun DateChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 总额卡：标题 + 大字金额 + 笔数 */
@Composable
internal fun TotalCard(title: String, total: Double, count: Int) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                "¥${formatPlainAmount(total)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "共 $count 笔记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 环比与日均卡 */
@Composable
internal fun CompareCard(
    prevTotal: Double,
    total: Double,
    dailyAvg: Double,
    typeNoun: String,
    accentUp: Color,
    accentDown: Color,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("环比上期", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prevTotal > 0 || total > 0) {
                        val pct = when {
                            prevTotal > 0 -> (total - prevTotal) / prevTotal * 100
                            total > 0 -> 100.0
                            else -> 0.0
                        }
                        val up = pct >= 0
                        Icon(
                            if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (up) accentUp else accentDown,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            String.format(Locale.US, "%+.1f%%", pct),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (up) accentUp else accentDown,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "上期 ¥${formatPlainAmount(prevTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("日均$typeNoun", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "¥${formatPlainAmount(dailyAvg)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 胶囊柱状趋势图：≤12 个桶平铺整行（周视图铺满宽度），更多时横向滚动 */
@Composable
internal fun BarChart(buckets: List<StatsBucket>, accent: Color) {
    if (buckets.isEmpty() || buckets.all { it.value <= 0.0 }) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val maxV = buckets.maxOf { it.value }.coerceAtLeast(1.0)

    if (buckets.size <= 12) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            buckets.forEach { b ->
                BarBucket(b, maxV, accent, modifier = Modifier.weight(1f))
            }
        }
    } else {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(buckets) { b ->
                BarBucket(b, maxV, accent, modifier = Modifier.width(26.dp))
            }
        }
    }
}

/**
 * 单个柱位：柱体 + 数值文字锁在固定高度（120dp）的绘图区内底部对齐，
 * 标签行独立在绘图区之外——柱子再高也不可能越过基线压住标签。
 */
@Composable
private fun BarBucket(b: StatsBucket, maxV: Double, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(120.dp), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (b.value > 0) {
                    Text(formatCompactAmount(b.value), fontSize = 10.sp, color = accent)
                    Spacer(Modifier.height(4.dp))
                }
                // 最高 100dp：文字 13 + 间距 4 + 柱 100 = 117 ≤ 绘图区 120
                val h = if (b.value <= 0) 4.dp else (16 + 84 * (b.value / maxV)).dp
                Box(
                    Modifier
                        .width(22.dp)
                        .height(h)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (b.value <= 0) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.9f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            b.label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 占比环图 + 图例 */
@Composable
internal fun DonutSection(categories: List<CategoryStat>, total: Double) {
    val slices = categories.filter { it.amount > 0 }.take(12)
    if (slices.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(180.dp)) {
                val stroke = 24.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                var startAngle = -90f
                slices.forEachIndexed { i, c ->
                    val sweep = (c.percent * 360f).coerceAtLeast(0.5f)
                    drawArc(
                        color = ChartColors[i % ChartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("总计", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "¥${formatCompactAmount(total)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        slices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ChartColors[slices.indexOf(c) % ChartColors.size]),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${c.category} ${(c.percent * 100).toString().take(4)}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 分类排行卡：emoji + 分类名 + 金额 + 占比进度条 */
@Composable
internal fun RankCard(c: CategoryStat, color: Color) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = Categories.emoji(c.category), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.category,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "¥${formatPlainAmount(c.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(10.dp))
            val progress by animateFloatAsState(
                targetValue = c.percent.coerceIn(0.02f, 1f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
                label = "rankProgress",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${"%.1f".format(Locale.US, c.percent * 100)}% · ${c.count} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
