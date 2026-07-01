package com.nasee.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 视频数据模型，对应服务端 VideoDTO。
 *
 * @property id 视频 ID
 * @property title 文件名（含扩展名）
 * @property duration 时长（秒）
 * @property size 文件大小（字节）
 * @property path 相对路径
 * @property folder 父目录相对路径
 * @property width 视频宽度（像素）
 * @property height 视频高度（像素）
 * @property liked 是否已点赞
 * @property streamUrl 流地址相对路径（如 /api/v1/videos/1/stream）
 * @property modTime 文件修改时间（Unix 时间戳）
 */
data class Video(
    @SerializedName("id")
    val id: Long,

    @SerializedName("title")
    val title: String,

    @SerializedName("duration")
    val duration: Double,

    @SerializedName("size")
    val size: Long,

    @SerializedName("path")
    val path: String,

    @SerializedName("folder")
    val folder: String,

    @SerializedName("width")
    val width: Int,

    @SerializedName("height")
    val height: Int,

    @SerializedName("liked")
    val liked: Boolean,

    @SerializedName("stream_url")
    val streamUrl: String,

    @SerializedName("mod_time")
    val modTime: Long
) {
    /** 判断视频是否为竖屏 */
    val isPortrait: Boolean get() = height > width

    /** 格式化时长为 mm:ss 或 h:mm:ss */
    fun formattedDuration(): String {
        val totalSeconds = duration.toLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /** 格式化文件大小 */
    fun formattedSize(): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var sizeValue = size.toDouble()
        var unitIndex = 0
        while (sizeValue >= 1024 && unitIndex < units.lastIndex) {
            sizeValue /= 1024
            unitIndex++
        }
        return String.format("%.1f %s", sizeValue, units[unitIndex])
    }
}

/**
 * 分页视频列表响应数据。
 */
data class VideoListResponse(
    @SerializedName("videos")
    val videos: List<Video> = emptyList(),

    @SerializedName("total")
    val total: Int = 0,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("page_size")
    val pageSize: Int = 20
)

/**
 * 健康检查响应数据。
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String = ""
)

/**
 * 扫描触发响应数据。
 */
data class ScanResponse(
    @SerializedName("status")
    val status: String = ""
)
