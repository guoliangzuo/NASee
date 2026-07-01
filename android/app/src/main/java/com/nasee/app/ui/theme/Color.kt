package com.nasee.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// NASee 液态玻璃配色方案
// ============================================================
// 设计理念：深色背景 + 半透明叠加层 + 柔和强调色
// 营造类似 iOS 液态玻璃（Liquid Glass）的视觉风格
// ============================================================

// --- 主色调 ---
val NASeePrimary = Color(0xFF6C8EEF)       // 柔和蓝
val NASeePrimaryVariant = Color(0xFF4A6FCC) // 深蓝
val NASeeSecondary = Color(0xFFFF6B9D)      // 粉色（点赞/强调）
val NASeeTertiary = Color(0xFF5AC8B0)       // 青绿（辅助）

// --- 背景色 ---
val NASeeDarkBackground = Color(0xFF0A0A0F)  // 近黑
val NASeeSurface = Color(0xFF1A1A24)          // 深灰
val NASeeSurfaceVariant = Color(0xFF2A2A35)   // 中灰

// --- 半透明叠加层（液态玻璃核心） ---
val GlassWhite = Color.White.copy(alpha = 0.15f)    // 通用半透明白
val GlassWhiteStrong = Color.White.copy(alpha = 0.25f) // 强半透明白
val GlassBlack = Color.Black.copy(alpha = 0.30f)     // 半透明黑
val GlassBlackLight = Color.Black.copy(alpha = 0.15f) // 浅半透明黑

// --- 文字色 ---
val TextPrimary = Color.White
val TextSecondary = Color.White.copy(alpha = 0.70f)
val TextTertiary = Color.White.copy(alpha = 0.45f)

// --- 功能色 ---
val ErrorRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF4CAF50)
val WarningAmber = Color(0xFFFFC107)

// --- 渐变 ---
val GradientTop = Color.Black.copy(alpha = 0.6f)
val GradientBottom = Color.Black.copy(alpha = 0.85f)
