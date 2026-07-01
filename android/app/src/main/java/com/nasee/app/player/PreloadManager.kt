package com.nasee.app.player

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.nasee.app.data.model.Video

/**
 * 预加载协调器。
 *
 * 与 [VideoPlayerManager] 配合，管理视频预加载策略：
 * - 当前视频播放时，预创建并 prepare 相邻视频（±1）
 * - 释放距离当前超过 1 的 Player 以回收资源
 *
 * 这样滑动切换时目标视频已预加载，实现秒开。
 *
 * @property playerManager Player 管理器
 */
@UnstableApi
class PreloadManager(
    private val playerManager: VideoPlayerManager
) {
    companion object {
        private const val TAG = "PreloadManager"
        /** 预加载范围：当前 ± PRELOAD_RANGE */
        private const val PRELOAD_RANGE = 1
    }

    /**
     * 预加载当前视频相邻的视频。
     *
     * 对 videos[currentIndex ± 1] 调用 [VideoPlayerManager.getOrCreatePlayer]，
     * 触发 ExoPlayer 创建 + prepare()（不 play）。
     *
     * @param videos 视频列表
     * @param currentIndex 当前播放索引
     */
    @Synchronized
    fun preload(videos: List<Video>, currentIndex: Int) {
        if (videos.isEmpty() || currentIndex !in videos.indices) return

        val keepIds = mutableSetOf<Long>()

        // 预加载当前视频
        val currentVideo = videos[currentIndex]
        keepIds.add(currentVideo.id)
        playerManager.getOrCreatePlayer(currentVideo)

        // 预加载前一个
        val prevIndex = currentIndex - PRELOAD_RANGE
        if (prevIndex >= 0) {
            val prevVideo = videos[prevIndex]
            keepIds.add(prevVideo.id)
            playerManager.getOrCreatePlayer(prevVideo)
            Log.d(TAG, "Preloaded prev video at index $prevIndex (id: ${prevVideo.id})")
        }

        // 预加载后一个
        val nextIndex = currentIndex + PRELOAD_RANGE
        if (nextIndex < videos.size) {
            val nextVideo = videos[nextIndex]
            keepIds.add(nextVideo.id)
            playerManager.getOrCreatePlayer(nextVideo)
            Log.d(TAG, "Preloaded next video at index $nextIndex (id: ${nextVideo.id})")
        }

        // 释放远端 Player
        playerManager.releaseDistant(keepIds)
    }

    /**
     * 释放所有预加载的 Player。
     */
    fun releaseAll() {
        playerManager.releaseAll()
    }
}
