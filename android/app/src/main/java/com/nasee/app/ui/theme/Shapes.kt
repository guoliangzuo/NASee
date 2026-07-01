package com.nasee.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * NASee 形状方案。
 *
 * 大圆角体现液态玻璃风格：
 * - 小组件（按钮、标签）：12dp
 * - 中组件（卡片、输入框）：16dp
 * - 大组件（底部弹窗、面板）：24dp
 */
val NASeeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
