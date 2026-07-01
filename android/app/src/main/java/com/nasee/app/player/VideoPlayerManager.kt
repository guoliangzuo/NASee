package com.nasee.app.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nasee.app.data.model.Video

/**
 * ExoPlayer 实例池管理器。
 *
 * 维护 [ExoPlayer] 实例缓存池，实现抖音式竖屏滑动的秒开体验：
 * - 当前视频播放时，预加载相邻视频（prev / next）
 * - 切换时已预加载的视频秒开
 * - 释放远端 Player 以回收资源
 *
 * 缓存策略：以 videoId 为 key 维护 Map，最多同时保留 3 个 Player 实例。
 *
 * @property context 应用上下文
 * @property playerFactory Player 工厂
 */
@UnstableApi
class VideoPlayerManager(
    private val context: Context,
    private val playerFactory: PlayerFactory
) {
    companion object {
        private const val TAG = "VideoPlayerManager"
        /** 最大同时存活的 Player 数量 */
        private const val MAX_PLAYERS = 3
    }

    /** Player 缓存池：videoId → ExoPlayer */
    private val playerPool = mutableMapOf<Long, ExoPlayer>()

    /** 当前正在播放的 videoId */
    @Volatile
    private var currentVideoId: Long? = null

    /**
     * 获取或创建指定视频的 Player。
     *
     * 若缓存命中，返回已预加载的 Player（秒开）；
     * 否则创建新 Player，设置 MediaItem 并 prepare()。
     *
     * @param video 视频信息
     * @return 配置好的 ExoPlayer（已 prepare，未 play）
     */
    @Synchronized
    fun getOrCreatePlayer(video: Video): ExoPlayer {
        // 缓存命中
        playerPool[video.id]?.let { cached ->
            Log.d(TAG, "Player cache hit for video ${video.id}")
            return cached
        }

        // 创建新 Player
        Log.d(TAG, "Creating new player for video ${video.id}")
        val player = playerFactory.createPlayer()
        val streamUrl = playerFactory.buildStreamUrl(video.streamUrl)
        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.repeatMode = Player.REPEAT_MODE_ONE // 循环播放

        playerPool[video.id] = player
        return player
    }

    /**
     * 设置当前播放视频。
     *
     * 暂停其他所有 Player，播放指定视频。
     *
     * @param videoId 要播放的视频 ID
     */
    @Synchronized
    fun setCurrent(videoId: Long) {
        if (currentVideoId == videoId) return

        // 暂停其他 Player
        playerPool.forEach { (id, player) ->
            if (id != videoId) {
                player.pause()
            }
        }

        // 播放当前
        playerPool[videoId]?.let { player ->
            player.play()
        }

        currentVideoId = videoId
        Log.d(TAG, "Current video set to $videoId, pool size: ${playerPool.size}")
    }

    /**
     * 暂停当前播放。
     */
    @Synchronized
    fun pauseCurrent() {
        currentVideoId?.let { id ->
            playerPool[id]?.pause()
        }
    }

    /**
     * 恢复播放。
     */
    @Synchronized
    fun resumeCurrent() {
        currentVideoId?.let { id ->
            playerPool[id]?.play()
        }
    }

    /**
     * 获取指定视频的 Player（如果存在于池中）。
     */
    @Synchronized
    fun getPlayer(videoId: Long): ExoPlayer? = playerPool[videoId]

    /**
     * 释放指定视频的 Player。
     *
     * @param videoId 视频 ID
     */
    @Synchronized
    fun releasePlayer(videoId: Long) {
        playerPool.remove(videoId)?.let { player ->
            player.release()
            Log.d(TAG, "Released player for video $videoId")
        }
        if (currentVideoId == videoId) {
            currentVideoId = null
        }
    }

    /**
     * 释放所有不在指定范围内的 Player。
     *
     * 用于滑动切换后清理远端缓存。
     *
     * @param keepVideoIds 需要保留的 videoId 集合
     */
    @Synchronized
    fun releaseDistant(keepVideoIds: Set<Long>) {
        val toRelease = playerPool.keys.filter { it !in keepVideoIds }
        toRelease.forEach { id ->
            releasePlayer(id)
        }
        Log.d(TAG, "Released ${toRelease.size} distant players, kept ${playerPool.size}")
    }

    /**
     * 释放所有 Player 并清空缓存池。
     */
    @Synchronized
    fun releaseAll() {
        playerPool.values.forEach { it.release() }
        playerPool.clear()
        currentVideoId = null
        Log.d(TAG, "All players released")
    }

    /**
     * 获取当前播放视频 ID。
     */
    fun getCurrentVideoId(): Long? = currentVideoId

    /**
     * 获取当前缓存池大小。
     */
    fun getPoolSize(): Int = playerPool.size
}
