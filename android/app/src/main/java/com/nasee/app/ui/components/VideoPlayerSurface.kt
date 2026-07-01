package com.nasee.app.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 视频播放器 Surface。
 *
 * 使用 AndroidView 包裹 Media3 [PlayerView]，配置保持原始比例的缩放模式。
 *
 * 修复横屏视频裁剪问题：
 * - 使用 RESIZE_MODE_FIT 保持视频原始比例
 * - 横屏视频会在上下留黑边（letterbox）的方式全屏显示
 * - 避免视频被裁剪
 *
 * @param player ExoPlayer 实例
 * @param modifier Compose 修饰符
 */
@UnstableApi
@Composable
fun VideoPlayerSurface(
    player: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    if (player == null) {
        // 无 Player 时显示黑色占位
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                // 使用 FIT 模式保持视频原始比例，避免裁剪
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            view.player = player
        },
        modifier = modifier.fillMaxSize()
    )
}
