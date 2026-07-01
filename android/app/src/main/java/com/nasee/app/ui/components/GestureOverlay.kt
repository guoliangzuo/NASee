package com.nasee.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 手势覆盖层。
 *
 * 检测以下手势：
 * - 单击：toggle play/pause + 显示暂停/播放图标淡入淡出
 * - 双击左半屏：seek -10s
 * - 双击右半屏：seek +10s
 * - 双击中间：点赞
 *
 * @param isPlaying 当前是否在播放
 * @param onSingleTap 单击回调（toggle play/pause）
 * @param onDoubleTapLeft 双击左半屏回调（seek -10s）
 * @param onDoubleTapRight 双击右半屏回调（seek +10s）
 * @param onDoubleTapCenter 双击中间回调（点赞）
 * @param modifier 修饰符
 */
@Composable
fun GestureOverlay(
    isPlaying: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showIcon by remember { mutableStateOf(false) }
    var iconType by remember { mutableStateOf(IconType.PAUSE) }
    var rippleEffect by remember { mutableStateOf<RippleData?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // 单击：toggle play/pause + 显示图标
                        onSingleTap()
                        iconType = if (isPlaying) IconType.PAUSE else IconType.PLAY
                        showIcon = true
                        scope.launch {
                            delay(800)
                            showIcon = false
                        }
                    },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        val thirdWidth = screenWidth / 3

                        when {
                            offset.x < thirdWidth -> {
                                // 双击左半屏：快退
                                onDoubleTapLeft()
                                rippleEffect = RippleData(
                                    position = offset,
                                    isForward = false
                                )
                            }
                            offset.x > thirdWidth * 2 -> {
                                // 双击右半屏：快进
                                onDoubleTapRight()
                                rippleEffect = RippleData(
                                    position = offset,
                                    isForward = true
                                )
                            }
                            else -> {
                                // 双击中间：点赞
                                onDoubleTapCenter()
                                rippleEffect = RippleData(
                                    position = offset,
                                    isLike = true
                                )
                            }
                        }
                        scope.launch {
                            delay(600)
                            rippleEffect = null
                        }
                    }
                )
            }
    ) {
        // 暂停/播放图标淡入淡出
        if (showIcon) {
            val iconAlpha = remember { Animatable(0f) }
            LaunchedEffect(showIcon) {
                iconAlpha.animateTo(0.8f, tween(200))
            }

            Icon(
                imageVector = if (iconType == IconType.PAUSE) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (iconType == IconType.PAUSE) "暂停" else "播放",
                tint = Color.White.copy(alpha = iconAlpha.value),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
            )
        }

        // 涟漪动画
        rippleEffect?.let { ripple ->
            RippleAnimation(
                ripple = ripple,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 图标类型 */
private enum class IconType { PLAY, PAUSE }

/** 涟漪数据 */
data class RippleData(
    val position: Offset,
    val isForward: Boolean = false,
    val isLike: Boolean = false
)

/**
 * 涟漪动画——双击时在触点位置显示扩散圆圈。
 */
@Composable
private fun RippleAnimation(
    ripple: RippleData,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(ripple) {
        scale.animateTo(2f, tween(600))
        alpha.animateTo(0f, tween(600))
    }

    Canvas(modifier = modifier) {
        val baseRadius = 60f * scale.value
        val center = Offset(ripple.position.x, ripple.position.y)

        val color = when {
            ripple.isLike -> Color(0xFFFF6B9D)
            ripple.isForward -> Color.White
            else -> Color.White
        }

        drawCircle(
            color = color.copy(alpha = alpha.value * 0.4f),
            radius = baseRadius,
            center = center
        )

        // 爱心或箭头
        if (ripple.isLike) {
            drawCircle(
                color = Color(0xFFFF6B9D).copy(alpha = alpha.value),
                radius = baseRadius * 0.5f,
                center = center
            )
        }
    }
}
