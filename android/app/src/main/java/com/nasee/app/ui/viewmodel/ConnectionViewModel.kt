package com.nasee.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nasee.app.NASeeApplication
import com.nasee.app.data.local.SettingsDataStore
import com.nasee.app.data.model.ServerConfig
import com.nasee.app.data.remote.ApiClient
import com.nasee.app.data.remote.ApiException
import com.nasee.app.data.remote.ApiService
import com.nasee.app.network.AuthInterceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 连接页 ViewModel。
 *
 * 职责：
 * - 加载已保存的服务端配置
 * - 测试服务端连接（调用 /health）
 * - 保存配置到加密存储
 * - 初始化 ApiService 并更新 DI 容器
 *
 * 仅支持局域网连接，已移除外网连接相关功能。
 */
class ConnectionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ConnectionViewModel"
    }

    private val app = application as NASeeApplication
    private val container = app.appContainer

    /** UI 状态 */
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        loadSavedConfig()
    }

    /**
     * 加载已保存的配置（用于预填表单）。
     */
    fun loadSavedConfig() {
        viewModelScope.launch {
            val config = container.encryptedConfigStore.loadConfig()
            if (config != null) {
                _uiState.value = _uiState.value.copy(
                    address = config.address,
                    password = config.password,
                    hasSavedConfig = true
                )
            }
        }
    }

    /**
     * 更新地址输入。
     */
    fun updateAddress(address: String) {
        _uiState.value = _uiState.value.copy(address = address)
    }

    /**
     * 更新密码输入。
     */
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    /**
     * 测试连接。
     *
     * 创建临时 ApiService 调用 /health 端点验证连通性。
     * 成功后保存配置并初始化正式 ApiService。
     */
    fun testConnection() {
        val state = _uiState.value
        if (state.address.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(
                error = "请填写服务端地址和密码"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isTesting = true, error = null)

            try {
                val baseUrl = ApiClient.normalizeBaseUrl(state.address)
                val authInterceptor = AuthInterceptor { state.password }

                val tempApiClient = ApiClient(
                    baseUrl = baseUrl,
                    authInterceptor = authInterceptor,
                    isDebug = com.nasee.app.BuildConfig.DEBUG
                )
                val apiService = tempApiClient.createApiService()

                // 调用 /health 验证连通性
                val response = apiService.health()
                if (response.isSuccess() && response.data?.status == "ok") {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        isConnectionSuccess = true,
                        error = null
                    )
                    Log.i(TAG, "Connection test successful to $baseUrl")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        error = response.message.ifBlank { "连接失败：服务端返回异常" }
                    )
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Connection test failed (API error)", e)
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    error = when (e.errorCode) {
                        401 -> "密码错误，认证失败"
                        else -> "服务端错误：${e.message}"
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    error = "无法连接到服务端：${e.localizedMessage ?: "网络错误"}"
                )
            }
        }
    }

    /**
     * 保存配置并完成连接。
     *
     * 保存到加密存储 → 更新 DI 容器 → 通知 UI 导航。
     */
    fun saveAndConnect() {
        val state = _uiState.value
        if (state.address.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "请填写服务端地址和密码")
            return
        }

        viewModelScope.launch {
            val config = ServerConfig(
                address = state.address.trim(),
                password = state.password
            )

            // 保存到加密存储
            container.encryptedConfigStore.saveConfig(config)

            // 更新 DI 容器
            val baseUrl = ApiClient.normalizeBaseUrl(config.address)
            container.updateConfig(baseUrl, config.password)

            _uiState.value = _uiState.value.copy(isConnected = true)
            Log.i(TAG, "Config saved and connected")
        }
    }

    /**
     * 清除错误状态。
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 连接页 UI 状态。
 */
data class ConnectionUiState(
    /** 服务端地址 */
    val address: String = "",
    /** 密码 */
    val password: String = "",
    /** 是否正在测试连接 */
    val isTesting: Boolean = false,
    /** 连接测试是否成功 */
    val isConnectionSuccess: Boolean = false,
    /** 是否已连接（保存配置完成） */
    val isConnected: Boolean = false,
    /** 是否有已保存的配置 */
    val hasSavedConfig: Boolean = false,
    /** 错误信息 */
    val error: String? = null
)
