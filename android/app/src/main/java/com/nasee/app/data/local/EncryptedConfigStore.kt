package com.nasee.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.nasee.app.data.model.ServerConfig

/**
 * 加密配置存储。
 *
 * 使用 Android Keystore 主密钥 + [EncryptedSharedPreferences] 加密存储服务端连接配置
 * （地址和密码），root 设备也无法直接读取明文。
 *
 * 配置以 JSON 格式序列化后存储在 SharedPreferences 中。
 *
 * @property context 应用上下文
 */
class EncryptedConfigStore(private val context: Context) {

    companion object {
        private const val TAG = "EncryptedConfigStore"
        private const val PREFS_NAME = "nasee_config"
        private const val KEY_CONFIG = "server_config_json"
    }

    private val gson = Gson()

    /** 主密钥（基于 Android Keystore，AES256-GCM） */
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /** 加密的 SharedPreferences 实例 */
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 保存服务端配置（加密存储）。
     *
     * @param config 服务端连接配置
     */
    fun saveConfig(config: ServerConfig) {
        try {
            val json = gson.toJson(config)
            encryptedPrefs.edit().putString(KEY_CONFIG, json).apply()
            Log.i(TAG, "Server config saved (address: ${config.address})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
        }
    }

    /**
     * 加载已保存的服务端配置。
     *
     * @return 配置对象，若未保存或读取失败返回 null
     */
    fun loadConfig(): ServerConfig? {
        return try {
            val json = encryptedPrefs.getString(KEY_CONFIG, null)
            if (json.isNullOrBlank()) {
                null
            } else {
                gson.fromJson(json, ServerConfig::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config", e)
            null
        }
    }

    /**
     * 清除已保存的配置。
     */
    fun clearConfig() {
        try {
            encryptedPrefs.edit().clear().apply()
            Log.i(TAG, "Server config cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear config", e)
        }
    }

    /**
     * 判断是否已保存配置。
     */
    fun hasConfig(): Boolean {
        return encryptedPrefs.contains(KEY_CONFIG)
    }
}
