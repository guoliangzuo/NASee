package com.nasee.app.data.remote

import com.google.gson.GsonBuilder
import com.nasee.app.network.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit + OkHttp 客户端工厂。
 *
 * 负责创建配置好的 [ApiService] 实例：
 * - OkHttp：注入 [AuthInterceptor] + 日志拦截器（debug 模式）
 * - Retrofit：Gson 转换器 + 动态 baseUrl
 *
 * @property baseUrl 服务端 base URL（必须以 / 结尾）
 * @property authInterceptor 认证拦截器
 * @property isDebug 是否为 debug 构建（控制日志输出）
 */
class ApiClient(
    private val baseUrl: String,
    private val authInterceptor: AuthInterceptor,
    private val isDebug: Boolean = false
) {
    /** Gson 实例（配置宽松解析） */
    private val gson = GsonBuilder()
        .setLenient()
        .create()

    /** OkHttp 客户端（超时配置针对内网优化） */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Retrofit 实例 */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * 创建 ApiService 实例。
     *
     * @return 配置好的 [ApiService]
     */
    fun createApiService(): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    /**
     * 获取底层 OkHttpClient（供 ExoPlayer OkHttpDataSource 复用）。
     */
    fun okhttpClient(): OkHttpClient = okHttpClient

    companion object {
        /**
         * 标准化 base URL，确保以 / 结尾。
         */
        fun normalizeBaseUrl(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
