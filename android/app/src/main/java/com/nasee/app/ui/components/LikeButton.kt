package com.nasee.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 点赞按钮。
 *
 * 特性：
 * - 心形图标，点赞时缩放弹跳动画
 * - 点赞时触发爱心粒子飘散效果（Canvas 绘制）
 * - 已点赞/未点赞状态切换
 *
 * @param isLiked 是否已点赞
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun LikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaleAnim = remember { Animatable(1f) }
    val particles = remember { mutableStateListOf<HeartParticle>() }

    // 点赞时触发弹跳动画
    LaunchedEffect(isLiked) {
        if (isLiked) {
            scaleAnim.animateTo(
                targetValue = 1.3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))

            // 生成粒子
            repeat(8) {
                particles.add(
                    HeartParticle(
                        angle = Random.nextFloat() * 360f,
                        distance = 40f + Random.nextFloat() * 60f,
                        scale = 0.5f + Random.nextFloat() * 0.5f
                    )
                )
            }
            delay(1000)
            particles.clear()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 粒子效果
        if (particles.isNotEmpty()) {
            Canvas(modifier = Modifier.size(80.dp)) {
                particles.forEach { particle ->
                    val radians = Math.toRadians(particle.angle.toDouble())
                    val dx = (Math.cos(radians) * particle.distance).toFloat()
                    val dy = (Math.sin(radians) * particle.distance).toFloat()
                    val px = size.width / 2 + dx
                    val py = size.height / 2 + dy

                    drawCircle(
                        color = Color(0xFFFF6B9D).copy(alpha = 0.6f),
                        radius = 4f * particle.scale,
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )
                }
            }
        }

        // 心形图标
        Icon(
            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "取消点赞" else "点赞",
            tint = if (isLiked) Color(0xFFFF6B9D) else Color.White,
            modifier = Modifier
                .size(32.dp)
                .scale(scaleAnim.value)
        )
    }
}

/** 爱心粒子数据 */
data class HeartParticle(
    val angle: Float,
    val distance: Float,
    val scale: Float
)
