package com.nasee.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃容器组件。
 *
 * 实现液态玻璃（Liquid Glass）视觉效果：
 * - 背景：半透明白色叠加
 * - 圆角：24dp 大圆角
 * - 阴影：柔和阴影
 *
 * 供 ConnectionScreen、BottomSheet 等需要玻璃质感的容器使用。
 *
 * @param modifier 修饰符
 * @param cornerRadius 圆角半径（默认 24dp）
 * @param alpha 背景透明度（默认 0.15f）
 * @param content 内容
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.15f,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.White.copy(alpha = alpha),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.background(Color.White.copy(alpha = alpha * 0.3f)),
            content = content
        )
    }
}
