package com.nonen.Bookkeeping.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/** 系统开启「移除动画」（ANIMATOR_DURATION_SCALE = 0）时的降级开关 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** 通用动效弹簧：临界阻尼、约 0.32s 响应（流畅界面的默认手感，无过冲） */
fun <T> motionSpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

/**
 * iOS 风格按压反馈：按下瞬间缩放（snap），松开弹簧回落；无 ripple。
 * 与 clickable(interactionSource = 返回的 source, indication = null) 搭配使用，
 * 缩放与点击共用同一个 interactionSource，反馈发生在按下而非松开。
 */
@Composable
fun rememberPressScale(pressedScale: Float = 0.97f): Pair<MutableInteractionSource, Modifier> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            motionSpring()
        },
        label = "pressScale",
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return interactionSource to modifier
}
