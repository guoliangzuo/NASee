package com.nasee.app

import android.app.Application
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.nasee.app.data.local.EncryptedConfigStore
import com.nasee.app.data.local.SettingsDataStore
import com.nasee.app.data.remote.ApiClient
import com.nasee.app.data.remote.ApiService
import com.nasee.app.network.AuthInterceptor

/**
 * NASee 全局 Application 类。
 *
 * 采用手动依赖注入（Manual DI）：在 Application 中持有所有单例依赖，
 * 通过 [appContainer] 供 ViewModel 和 UI 层访问。
 *
 * 避免引入 Hilt/KSP 以降低构建复杂度——项目规模小，手动管理即可。
 */
class NASeeApplication : Application() {

    companion object {
        private const val TAG = "NASeeApplication"
    }

    /** 全局依赖注入容器 */
    lateinit var appContainer: AppContainer
        private set

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        Log.i(TAG, "NASee Application initialized")
    }
}

/**
 * 手动 DI 容器，持有所有单例依赖。
 *
 * 延迟初始化策略：
 * - [encryptedConfigStore] 和 [settingsDataStore] 在容器创建时即初始化（轻量）
 * - [apiClient] 和 [apiService] 在首次调用 [ensureApiClient] 时创建（需要 ServerConfig）
 */
@UnstableApi
class AppContainer(private val context: android.content.Context) {

    /** 加密存储——保存服务端地址和密码 */
    val encryptedConfigStore: EncryptedConfigStore by lazy {
        EncryptedConfigStore(context)
    }

    /** 偏好存储——排序方式、文件夹选择等非敏感配置 */
    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(context)
    }

    /** 当前认证拦截器（密码可动态更新） */
    private var authInterceptor: AuthInterceptor? = null

    /** Retrofit API 客户端 */
    private var apiClient: ApiClient? = null

    /** 当前 API 服务实例 */
    @Volatile
    private var _apiService: ApiService? = null

    /**
     * 获取当前 ApiService 实例。
     *
     * 必须先调用 [updateConfig] 设置服务端配置。
     *
     * @return 当前生效的 ApiService
     * @throws IllegalStateException 如果尚未配置服务端连接
     */
    fun getApiService(): ApiService {
        return _apiService ?: throw IllegalStateException(
            "ApiService 未初始化，请先配置服务端连接"
        )
    }

    /**
     * 更新服务端配置并重建 ApiService。
     *
     * 当用户在连接页保存新配置时调用。
     *
     * @param baseUrl 服务端 base URL（如 http://192.168.1.100:8080/）
     * @param password 访问密码
     */
    @Synchronized
    fun updateConfig(baseUrl: String, password: String) {
        authInterceptor = AuthInterceptor { password }

        apiClient = ApiClient(
            baseUrl = baseUrl,
            authInterceptor = authInterceptor!!,
            isDebug = BuildConfig.DEBUG
        )
        _apiService = apiClient!!.createApiService()
    }

    /**
     * 获取当前 AuthInterceptor 提供的密码（供 ExoPlayer DataSource 使用）。
     */
    fun getPassword(): String {
        return authInterceptor?.getPassword() ?: ""
    }

    /**
     * 判断 ApiService 是否已初始化。
     */
    fun isConfigured(): Boolean = _apiService != null
}
