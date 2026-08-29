package com.nonen.Bookkeeping.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nonen.Bookkeeping.ui.motion.motionSpring
import com.nonen.Bookkeeping.ui.motion.rememberReducedMotion

/**
 * 滑块式分段控件：选中的色块用弹簧滑动到目标段（可中断）。
 * 注意：不能用 BoxWithConstraints + IntrinsicSize（SubcomposeLayout 不支持
 * intrinsic 测量，组合即崩溃），这里用 matchParentSize + onSizeChanged 取宽度。
 */
@Composable
fun AnimatedSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    corner: Dp = 14.dp,
    thumbCorner: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    fontSize: TextUnit = 13.sp,
) {
    val reduced = rememberReducedMotion()
    val xSpec: FiniteAnimationSpec<Dp> = if (reduced) snap() else motionSpring()
    val colorSpec: FiniteAnimationSpec<Color> = if (reduced) snap() else motionSpring()
    val density = LocalDensity.current
    var rowWidthPx by remember { mutableStateOf(0) }

    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        // 滑块：matchParentSize 不参与外层 Box 的尺寸计算（画在文字下层）
        Box(Modifier.matchParentSize()) {
            val segWidthPx = rowWidthPx.toFloat() / options.size
            if (segWidthPx > 0f) {
                val animatedThumbColor by animateColorAsState(
                    targetValue = thumbColor,
                    animationSpec = colorSpec,
                    label = "segmentedThumbColor",
                )
                val thumbX by animateDpAsState(
                    targetValue = with(density) { (segWidthPx * selectedIndex).toDp() },
                    animationSpec = xSpec,
                    label = "segmentedThumbX",
                )
                Box(
                    Modifier
                        .offset(x = thumbX)
                        .width(with(density) { segWidthPx.toDp() })
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(thumbCorner))
                        .background(animatedThumbColor),
                )
            }
        }
        // 文字行：唯一的占尺寸子级，决定整个控件的高度
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width },
        ) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(index) }
                        .padding(vertical = verticalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = fontSize,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selectedIndex) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
