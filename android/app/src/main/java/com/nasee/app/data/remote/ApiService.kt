package com.nasee.app.data.remote

import com.nasee.app.data.model.FolderListResponse
import com.nasee.app.data.model.HealthResponse
import com.nasee.app.data.model.LikeResponse
import com.nasee.app.data.model.ScanResponse
import com.nasee.app.data.model.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 统一 API 响应包装类，对应服务端 ApiResponse。
 *
 * @param T 业务数据类型
 * @property code 状态码（0 = 成功，非 0 = 错误）
 * @property data 业务数据，错误时为 null
 * @property message 描述信息
 */
data class ApiResponse<T>(
    val code: Int,
    val `data`: T?,
    val message: String
) {
    /** 判断响应是否成功 */
    fun isSuccess(): Boolean = code == 0

    /** 获取数据，若失败则抛出异常 */
    fun requireData(): T = data ?: throw ApiException(code, message)
}

/**
 * API 异常类。
 */
class ApiException(val errorCode: Int, override val message: String) : Exception(message)

/**
 * Retrofit API 接口定义。
 *
 * 所有方法均为 suspend 协程函数，对应服务端 RESTful 端点。
 * 认证头由 [com.nasee.app.network.AuthInterceptor] 自动注入。
 */
interface ApiService {

    /**
     * 健康检查（免认证）。
     * GET /health
     */
    @GET("health")
    suspend fun health(): ApiResponse<HealthResponse>

    /**
     * 获取视频列表（分页/筛选/排序）。
     * GET /api/v1/videos
     *
     * @param page 页码，默认 1
     * @param pageSize 每页数量，默认 20
     * @param folder 文件夹筛选（空 = 全部）
     * @param sort 排序字段：name / mod_time / file_size / duration
     * @param order 排序方向：asc / desc
     * @param likedOnly 仅返回点赞视频
     */
    @GET("api/v1/videos")
    suspend fun getVideos(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("folder") folder: String? = null,
        @Query("sort") sort: String = "mod_time",
        @Query("order") order: String = "desc",
        @Query("liked_only") likedOnly: Boolean = false
    ): ApiResponse<VideoListResponse>

    /**
     * 获取文件夹树。
     * GET /api/v1/folders
     */
    @GET("api/v1/folders")
    suspend fun getFolders(): ApiResponse<FolderListResponse>

    /**
     * 获取视频点赞状态。
     * GET /api/v1/videos/{id}/like
     */
    @GET("api/v1/videos/{id}/like")
    suspend fun getLikeStatus(
        @Path("id") id: Long
    ): ApiResponse<LikeResponse>

    /**
     * 切换点赞状态。
     * POST /api/v1/videos/{id}/like
     */
    @POST("api/v1/videos/{id}/like")
    suspend fun toggleLike(
        @Path("id") id: Long
    ): ApiResponse<LikeResponse>

    /**
     * 获取点赞视频列表。
     * GET /api/v1/videos/liked
     */
    @GET("api/v1/videos/liked")
    suspend fun getLikedVideos(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): ApiResponse<VideoListResponse>

    /**
     * 手动触发增量扫描。
     * POST /api/v1/scan
     */
    @POST("api/v1/scan")
    suspend fun triggerScan(): ApiResponse<ScanResponse>
}
