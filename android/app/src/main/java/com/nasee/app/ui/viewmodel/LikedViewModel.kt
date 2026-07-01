package com.nasee.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nasee.app.NASeeApplication
import com.nasee.app.data.model.Video
import com.nasee.app.data.remote.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 点赞列表 ViewModel。
 *
 * 职责：
 * - 分页加载点赞视频列表
 * - 取消点赞并刷新列表
 */
class LikedViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LikedViewModel"
        private const val PAGE_SIZE = 20
    }

    private val app = application as NASeeApplication
    private val container = app.appContainer

    /** UI 状态 */
    private val _uiState = MutableStateFlow(LikedUiState())
    val uiState: StateFlow<LikedUiState> = _uiState.asStateFlow()

    init {
        loadLikedVideos()
    }

    /**
     * 加载点赞视频列表（第一页）。
     */
    fun loadLikedVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val api = container.getApiService()
                val response = api.getLikedVideos(page = 1, pageSize = PAGE_SIZE)

                if (response.isSuccess() && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        videos = response.data.videos,
                        total = response.data.total,
                        currentPage = 1,
                        isLoading = false,
                        hasMore = response.data.videos.size < response.data.total
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.message.ifBlank { "加载失败" }
                    )
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Load liked videos failed (API)", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (e.errorCode == 401) "认证失败，请重新连接" else "加载失败：${e.message}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Load liked videos failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误：${e.localizedMessage ?: "未知错误"}"
                )
            }
        }
    }

    /**
     * 加载更多。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val api = container.getApiService()
                val nextPage = state.currentPage + 1
                val response = api.getLikedVideos(page = nextPage, pageSize = PAGE_SIZE)

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
                Log.e(TAG, "Load more liked videos failed", e)
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    /**
     * 取消点赞（optimistic update + 回滚）。
     *
     * @param videoId 视频 ID
     */
    fun unlike(videoId: Long) {
        val previousVideos = _uiState.value.videos
        // Optimistic update: 立即移除
        _uiState.value = _uiState.value.copy(
            videos = previousVideos.filterNot { it.id == videoId },
            total = (_uiState.value.total - 1).coerceAtLeast(0)
        )

        viewModelScope.launch {
            try {
                val api = container.getApiService()
                val response = api.toggleLike(videoId)
                if (!response.isSuccess()) {
                    // 回滚
                    _uiState.value = _uiState.value.copy(
                        videos = previousVideos,
                        total = previousVideos.size,
                        error = "取消点赞失败"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unlike failed", e)
                // 回滚
                _uiState.value = _uiState.value.copy(
                    videos = previousVideos,
                    total = previousVideos.size,
                    error = "网络错误，取消点赞失败"
                )
            }
        }
    }

    /** 清除错误 */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 点赞列表 UI 状态。
 */
data class LikedUiState(
    val videos: List<Video> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)
