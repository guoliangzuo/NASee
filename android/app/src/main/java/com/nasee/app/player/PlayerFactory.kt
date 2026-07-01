package com.nasee.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

/**
 * ExoPlayer 工厂类。
 *
 * 负责创建配置好的 [ExoPlayer] 实例：
 * - 配置 HTTP DataSource 注入认证头 `X-NASee-Key`
 * - 优化缓冲参数实现内网快速起播
 * - 支持可选的 SimpleCache 实现视频预加载缓存
 *
 * @property context 应用上下文
 * @property baseUrl 服务端 base URL
 * @property password 访问密码
 * @property cache 可选的 SimpleCache（预加载缓存）
 */
@UnstableApi
class PlayerFactory(
    private val context: Context,
    private val baseUrl: String,
    private val password: String,
    private val cache: SimpleCache? = null
) {
    /**
     * 创建一个配置好的 ExoPlayer 实例。
     *
     * @return 配置好的 ExoPlayer（未设置 MediaItem，需调用方 setMediaItem + prepare）
     */
    fun createPlayer(): ExoPlayer {
        // HTTP DataSource —— 注入认证头
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("X-NASee-Key" to password))

        // 包装为 DefaultDataSource（支持混合协议）
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // 如果有缓存，包装为 CacheDataSource
        val finalDataSourceFactory = if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(dataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            dataSourceFactory
        }

        // MediaSource 工厂
        val mediaSourceFactory = DefaultMediaSourceFactory(finalDataSourceFactory)

        // 缓冲控制 —— 内网快速起播
        // minBufferMs: 最小缓冲（1秒即可起播）
        // maxBufferMs: 最大缓冲（5秒，避免占用过多内存）
        // bufferForPlaybackMs: 起播所需缓冲（1秒）
        // bufferForPlaybackAfterRebufferMs: 重新缓冲后起播所需（1秒）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1000,
                /* maxBufferMs = */ 5000,
                /* bufferForPlaybackMs = */ 1000,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(context))
            .build()
    }

    /**
     * 构建视频流完整 URL。
     *
     * @param streamUrl 服务端返回的相对路径（如 /api/v1/videos/1/stream）
     * @return 完整 URL
     */
    fun buildStreamUrl(streamUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return base + streamUrl
    }
}
