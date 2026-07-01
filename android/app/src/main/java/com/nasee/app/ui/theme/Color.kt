package com.nasee.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// NASee 黄色马卡龙配色方案
// ============================================================
// 设计理念：深色背景 + 黄色马卡龙强调色 + 半透明叠加层
// 参考抖音风格，但更加优雅和温暖
// ============================================================

// --- 主色调（黄色马卡龙色系）---
val NASeePrimary = Color(0xFFFFD700)       // 温暖黄色（马卡龙黄）
val NASeePrimaryVariant = Color(0xFFFFC107) // 深黄
val NASeeSecondary = Color(0xFFFF6B9D)      // 粉色（收藏/强调）
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

// --- 黄色马卡龙专用色 ---
val MacaronYellow = Color(0xFFFFD700)       // 主黄色
val MacaronYellowLight = Color(0xFFFFE44D)  // 浅黄色
val MacaronYellowDark = Color(0xFFFFC107)   // 深黄色
val MacaronGold = Color(0xFFFFD700)         // 金色
