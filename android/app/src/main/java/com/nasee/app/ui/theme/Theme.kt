package com.nasee.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * NASee 深色配色方案。
 *
 * 始终使用深色主题——视频播放场景需要深色背景以减少视觉干扰。
 */
private val NASeeDarkColorScheme = darkColorScheme(
    primary = NASeePrimary,
    onPrimary = Color.White,
    primaryContainer = NASeePrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = NASeeSecondary,
    onSecondary = Color.White,
    tertiary = NASeeTertiary,
    onTertiary = Color.White,
    background = NASeeDarkBackground,
    onBackground = TextPrimary,
    surface = NASeeSurface,
    onSurface = TextPrimary,
    surfaceVariant = NASeeSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White
)

/**
 * NASee 主题入口。
 *
 * 强制使用深色配色方案，并配置系统状态栏为透明、沉浸式。
 */
@Composable
fun NASeeTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true, // 强制深色
    content: @Composable () -> Unit
) {
    val colorScheme = NASeeDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏透明 + 深色图标
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false // 深色背景用浅色图标
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NASeeTypography,
        shapes = NASeeShapes,
        content = content
    )
}
