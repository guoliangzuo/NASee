package com.nasee.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.nasee.app.NASeeApplication
import com.nasee.app.data.model.FolderNode
import com.nasee.app.data.model.Video
import com.nasee.app.data.remote.ApiException
import com.nasee.app.player.PreloadManager
import com.nasee.app.player.PlayerFactory
import com.nasee.app.player.VideoPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放页 ViewModel。
 *
 * 核心职责：
 * - 加载视频列表（分页）
 * - 管理当前播放索引和页面切换
 * - 协调 ExoPlayer 实例池预加载
 * - 点赞状态管理（optimistic update + 回滚）
 * - 文件夹筛选和排序
 *
 * 依赖 [VideoPlayerManager] 和 [PreloadManager] 管理播放器生命周期。
 */
@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val PAGE_SIZE = 20
    }

    private val app = application as NASeeApplication
    private val container = app.appContainer
    private val settingsDataStore = container.settingsDataStore

    /** Player 管理器 */
    private var playerManager: VideoPlayerManager? = null
    private var preloadManager: PreloadManager? = null

    /** UI 状态 */
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** 文件夹列表 */
    private val _folders = MutableStateFlow<List<FolderNode>>(emptyList())
    val folders: StateFlow<List<FolderNode>> = _folders.asStateFlow()

    /** 当前播放进度（ms） */
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    /** 视频总时长（ms） */
    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    /** 是否显示文件夹弹窗 */
    private val _showFolderSheet = MutableStateFlow(false)
    val showFolderSheet: StateFlow<Boolean> = _showFolderSheet.asStateFlow()

    /** 是否显示排序菜单 */
    private val _showSortMenu = MutableStateFlow(false)
    val showSortMenu: StateFlow<Boolean> = _showSortMenu.asStateFlow()

    init {
        initPlayerManager()
        loadSettings()
    }

    /**
     * 初始化 Player 管理器。
     */
    private fun initPlayerManager() {
        try {
            val config = container.encryptedConfigStore.loadConfig() ?: return
            val baseUrl = config.activeBaseUrl()
            val factory = PlayerFactory(getApplication(), baseUrl, config.password)
            val manager = VideoPlayerManager(getApplication(), factory)
            playerManager = manager
            preloadManager = PreloadManager(manager)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init player manager", e)
        }
    }

    /**
     * 从 DataStore 加载用户偏好设置。
     */
    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.sortFieldFlow.collectLatest { field ->
                _uiState.value = _uiState.value.copy(sortField = field)
            }
        }
        viewModelScope.launch {
            settingsDataStore.sortOrderFlow.collectLatest { order ->
                _uiState.value = _uiState.value.copy(sortOrder = order)
            }
        }
        viewModelScope.launch {
            settingsDataStore.selectedFolderFlow.collectLatest { folder ->
                _uiState.value = _uiState.value.copy(selectedFolder = folder)
            }
        }
        viewModelScope.launch {
            settingsDataStore.likedOnlyFlow.collectLatest { likedOnly ->
                _uiState.value = _uiState.value.copy(likedOnly = likedOnly)
            }
        }
    }

    /**
     * 加载视频列表（第一页）。
     */
    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val api = container.getApiService()
                val state = _uiState.value
                val response = api.getVideos(
                    page = 1,
                    pageSize = PAGE_SIZE,
                    folder = state.selectedFolder.ifBlank { null },
                    sort = state.sortField,
                    order = state.sortOrder,
                    likedOnly = state.likedOnly
                )

                if (response.isSuccess() && response.data != null) {
                    val videos = response.data.videos
                    _uiState.value = _uiState.value.copy(
                        videos = videos,
                        currentPage = 1,
                        total = response.data.total,
                        isLoading = false,
                        hasMore = videos.size < response.data.total,
                        currentIndex = if (videos.isNotEmpty()) 0 else -1
                    )
                    // 预加载第一个视频
                    if (videos.isNotEmpty()) {
                        onVideoChanged(0)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.message.ifBlank { "加载视频失败" }
                    )
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Load videos failed (API)", e)
                val error = if (e.errorCode == 401) {
                    "认证失败，请重新连接"
                } else {
                    "加载失败：${e.message}"
                }
                _uiState.value = _uiState.value.copy(isLoading = false, error = error)
            } catch (e: Exception) {
                Log.e(TAG, "Load videos failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误：${e.localizedMessage ?: "未知错误"}"
                )
            }
        }
    }

    /**
     * 加载更多视频（下一页）。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val api = container.getApiService()
                val nextPage = state.currentPage + 1
                val response = api.getVideos(
                    page = nextPage,
                    pageSize = PAGE_SIZE,
                    folder = state.selectedFolder.ifBlank { null },
                    sort = state.sortField,
                    order = state.sortOrder,
                    likedOnly = state.likedOnly
                )

                if (response.isSuccess() && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        videos = state.videos + response.data.videos,
                        currentPage = nextPage,
                        isLoadingMore = false,
                        hasMore = (state.videos.size + response.data.videos.size) < response.data.total
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    /**
     * 页面切换回调（VerticalPager 滑动）。
     *
     * @param index 新的页面索引
     */
    fun onPageChanged(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
        onVideoChanged(index)

        // 接近底部时加载更多
        val videos = _uiState.value.videos
        if (index >= videos.size - 3 && _uiState.value.hasMore) {
            loadMore()
        }
    }

    /**
     * 视频切换处理：更新播放器状态 + 预加载。
     */
    private fun onVideoChanged(index: Int) {
        val videos = _uiState.value.videos
        if (index !in videos.indices) return

        val video = videos[index]
        playerManager?.setCurrent(video.id)
        preloadManager?.preload(videos, index)

        // 更新点赞状态
        _uiState.value = _uiState.value.copy(
            currentVideo = video,
            isLiked = video.liked
        )
    }

    /**
     * 切换点赞（optimistic update）。
     *
     * UI 立即翻转状态 → 调用 API → 失败则回滚。
     */
    fun toggleLike() {
        val video = _uiState.value.currentVideo ?: return
        val previousLiked = _uiState.value.isLiked

        // Optimistic update
        _uiState.value = _uiState.value.copy(isLiked = !previousLiked)

        viewModelScope.launch {
            try {
                val api = container.getApiService()
                val response = api.toggleLike(video.id)
                if (response.isSuccess() && response.data != null) {
                    // 更新列表中的 liked 状态
                    val updatedVideos = _uiState.value.videos.map { v ->
                        if (v.id == video.id) v.copy(liked = response.data.liked) else v
                    }
                    _uiState.value = _uiState.value.copy(
                        videos = updatedVideos,
                        currentVideo = updatedVideos.find { it.id == video.id },
                        isLiked = response.data.liked
                    )
                    Log.d(TAG, "Like toggled: video ${video.id} → ${response.data.liked}")
                } else {
                    // 回滚
                    _uiState.value = _uiState.value.copy(isLiked = previousLiked)
                    _uiState.value = _uiState.value.copy(error = "点赞操作失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Toggle like failed", e)
                // 回滚
                _uiState.value = _uiState.value.copy(
                    isLiked = previousLiked,
                    error = "网络错误，点赞失败"
                )
            }
        }
    }

    /**
     * 双击点赞（直接设为已点赞）。
     */
    fun doubleTapLike() {
        if (!_uiState.value.isLiked) {
            toggleLike()
        }
    }

    /**
     * Seek 到指定位置。
     *
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        val video = _uiState.value.currentVideo ?: return
        playerManager?.getPlayer(video.id)?.seekTo(positionMs)
    }

    /**
     * 更新播放进度（由 UI 定时回调）。
     */
    fun updateProgress(positionMs: Long, durationMs: Long) {
        _currentPosition.value = positionMs
        _totalDuration.value = durationMs
    }

    /**
     * 加载文件夹列表。
     */
    fun loadFolders() {
        viewModelScope.launch {
            try {
                val api = container.getApiService()
                val response = api.getFolders()
                if (response.isSuccess() && response.data != null) {
                    _folders.value = response.data.folders
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load folders failed", e)
            }
        }
    }

    /**
     * 选择文件夹筛选。
     */
    fun selectFolder(folder: String) {
        viewModelScope.launch {
            settingsDataStore.setSelectedFolder(folder)
            _uiState.value = _uiState.value.copy(selectedFolder = folder)
            _showFolderSheet.value = false
            loadVideos()
        }
    }

    /**
     * 设置排序方式。
     */
    fun setSort(field: String, order: String) {
        viewModelScope.launch {
            settingsDataStore.setSortField(field)
            settingsDataStore.setSortOrder(order)
            _uiState.value = _uiState.value.copy(sortField = field, sortOrder = order)
            _showSortMenu.value = false
            loadVideos()
        }
    }

    /**
     * 切换仅点赞模式。
     */
    fun toggleLikedOnly() {
        viewModelScope.launch {
            val newValue = !_uiState.value.likedOnly
            settingsDataStore.setLikedOnly(newValue)
            _uiState.value = _uiState.value.copy(likedOnly = newValue)
            loadVideos()
        }
    }

    /** 显示/隐藏文件夹弹窗 */
    fun showFolderSheet(show: Boolean) { _showFolderSheet.value = show }
    fun showSortMenu(show: Boolean) { _showSortMenu.value = show }

    /** 清除错误 */
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /**
     * 获取当前视频的 Player（供 UI Surface 使用）。
     */
    fun getCurrentPlayer() = _uiState.value.currentVideo?.let {
        playerManager?.getPlayer(it.id)
    }

    /**
     * 获取指定视频 ID 的 Player（供 VideoPager 各页面使用）。
     *
     * @param videoId 视频 ID
     * @return 对应的 ExoPlayer（若不在缓存池中返回 null）
     */
    fun getPlayerByVideoId(videoId: Long) = playerManager?.getPlayer(videoId)

    override fun onCleared() {
        super.onCleared()
        preloadManager?.releaseAll()
    }
}

/**
 * 播放页 UI 状态。
 */
data class PlayerUiState(
    /** 视频列表 */
    val videos: List<Video> = emptyList(),
    /** 当前播放索引 */
    val currentIndex: Int = -1,
    /** 当前视频 */
    val currentVideo: Video? = null,
    /** 是否点赞 */
    val isLiked: Boolean = false,
    /** 总数 */
    val total: Int = 0,
    /** 当前页码 */
    val currentPage: Int = 1,
    /** 是否加载中 */
    val isLoading: Boolean = false,
    /** 是否加载更多中 */
    val isLoadingMore: Boolean = false,
    /** 是否有更多 */
    val hasMore: Boolean = false,
    /** 选中文件夹 */
    val selectedFolder: String = "",
    /** 排序字段 */
    val sortField: String = "mod_time",
    /** 排序方向 */
    val sortOrder: String = "desc",
    /** 仅点赞 */
    val likedOnly: Boolean = false,
    /** 错误信息 */
    val error: String? = null,
    /** 是否显示暂停图标 */
    val showPauseIcon: Boolean = false
)
