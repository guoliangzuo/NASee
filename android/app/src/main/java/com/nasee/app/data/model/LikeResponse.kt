package com.nasee.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 点赞响应数据，对应服务端 LikeResponse。
 *
 * @property videoId 视频 ID
 * @property liked 当前点赞状态
 */
data class LikeResponse(
    @SerializedName("video_id")
    val videoId: Long,

    @SerializedName("liked")
    val liked: Boolean
)
