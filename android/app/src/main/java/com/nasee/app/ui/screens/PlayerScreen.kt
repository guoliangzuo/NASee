package com.nasee.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.nasee.app.NASeeApplication
import com.nasee.app.ui.components.FolderBottomSheet
import com.nasee.app.ui.components.ProgressBar
import com.nasee.app.ui.components.SideActionRail
import com.nasee.app.ui.components.SortMenu
import com.nasee.app.ui.components.VideoInfoBar
import com.nasee.app.ui.components.VideoPager
import com.nasee.app.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * 播放页（核心界面）。
 *
 * 叠加层结构（从底到顶）：
 * 1. VideoPager —— 视频播放器 + 竖屏滑动
 * 2. 半透明渐变遮罩 —— 底部信息可读性
 * 3. VideoInfoBar —— 底部标题/路径信息
 * 4. SideActionRail —— 右侧操作栏
 * 5. ProgressBar —— 底部进度条
 * 6. SnackbarHost —— 错误提示
 *
 * @param onNavigateToLiked 导航到点赞列表
 * @param onDisconnect 断开连接
 */
@UnstableApi
@Composable
fun PlayerScreen(
    onNavigateToLiked: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        factory = androidx.lifecycle.viewmodel.initializer {
            PlayerViewModel(context.applicationContext as NASeeApplication)
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val showFolderSheet by viewModel.showFolderSheet.collectAsState()
    val showSortMenu by viewModel.showSortMenu.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 初始加载
    LaunchedEffect(Unit) {
        viewModel.loadVideos()
        viewModel.loadFolders()
    }

    // 错误提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // 定时更新播放进度
    LaunchedEffect(uiState.currentIndex) {
        while (true) {
            val player = viewModel.getCurrentPlayer()
            if (player != null && player.isPlaying) {
                viewModel.updateProgress(
                    positionMs = player.currentPosition,
                    durationMs = player.duration
                )
            }
            delay(500)
        }
    }

    // 播放/暂停状态
    val currentPlayer = viewModel.getCurrentPlayer()
    val isPlaying = currentPlayer?.isPlaying == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. 视频 Pager
        VideoPager(
            videos = uiState.videos,
            getPlayer = { id -> viewModel.getPlayerByVideoId(id) },
            onPageChanged = { index -> viewModel.onPageChanged(index) },
            isPlaying = isPlaying,
            onSingleTap = {
                val player = viewModel.getCurrentPlayer()
                if (player != null) {
                    if (player.isPlaying) player.pause() else player.play()
                }
            },
            onDoubleTapLeft = {
                val player = viewModel.getCurrentPlayer()
                player?.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
            },
            onDoubleTapRight = {
                val player = viewModel.getCurrentPlayer()
                player?.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration))
            },
            onDoubleTapCenter = { viewModel.doubleTapLike() },
            modifier = Modifier.fillMaxSize()
        )

        // 2. 底部渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // 3. 底部信息栏
        uiState.currentVideo?.let { video ->
            VideoInfoBar(
                title = video.title,
                folder = video.folder,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // 4. 右侧操作栏
        SideActionRail(
            isLiked = uiState.isLiked,
            likedCount = uiState.total,
            onLikeClick = { viewModel.toggleLike() },
            onFolderClick = { viewModel.showFolderSheet(true) },
            onSortClick = { viewModel.showSortMenu(true) },
            onLikedVideosClick = onNavigateToLiked,
            onDisconnect = onDisconnect,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .statusBarsPadding()
        )

        // 5. 进度条
        ProgressBar(
            currentPosition = currentPosition,
            duration = totalDuration,
            visible = true,
            onSeek = { pos -> viewModel.seekTo(pos) },
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // 6. Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 7. 文件夹弹窗
        if (showFolderSheet) {
            FolderBottomSheet(
                folders = folders,
                selectedFolder = uiState.selectedFolder,
                onSelect = { folder -> viewModel.selectFolder(folder) },
                onDismiss = { viewModel.showFolderSheet(false) }
            )
        }

        // 8. 排序菜单
        if (showSortMenu) {
            SortMenu(
                currentSort = uiState.sortField,
                currentOrder = uiState.sortOrder,
                onSortSelected = { field, order -> viewModel.setSort(field, order) },
                onDismiss = { viewModel.showSortMenu(false) }
            )
        }
    }
}
