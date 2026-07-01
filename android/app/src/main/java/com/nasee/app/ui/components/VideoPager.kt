package com.nasee.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nasee.app.data.model.Video
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 抖音式竖屏滑动 Pager。
 *
 * 使用 Compose [VerticalPager] 实现上下滑动切换视频。
 * 每页渲染 [VideoPlayerSurface]，滑动 settle 后通知 ViewModel 切换播放。
 *
 * @param videos 视频列表
 * @param getPlayer 获取指定视频的 ExoPlayer（可能为 null，表示未预加载）
 * @param onPageChanged 页面切换回调
 * @param isPlaying 当前是否在播放（用于手势层）
 * @param onSingleTap 单击回调
 * @param onDoubleTapLeft 双击左半屏回调
 * @param onDoubleTapRight 双击右半屏回调
 * @param onDoubleTapCenter 双击中间回调
 * @param modifier 修饰符
 */
@UnstableApi
@Composable
fun VideoPager(
    videos: List<Video>,
    getPlayer: (Long) -> ExoPlayer?,
    onPageChanged: (Int) -> Unit,
    isPlaying: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { videos.size }
    )

    // 监听页面变化
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onPageChanged(page)
            }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1 // 预渲染相邻页面
    ) { pageIndex ->
        val video = videos[pageIndex]
        val player = getPlayer(video.id)

        Box(modifier = Modifier.fillMaxSize()) {
            // 视频播放器
            VideoPlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize()
            )

            // 手势覆盖层（仅当前页面响应手势）
            if (pageIndex == pagerState.settledPage) {
                GestureOverlay(
                    isPlaying = isPlaying,
                    onSingleTap = onSingleTap,
                    onDoubleTapLeft = onDoubleTapLeft,
                    onDoubleTapRight = onDoubleTapRight,
                    onDoubleTapCenter = onDoubleTapCenter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
