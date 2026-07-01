package com.nasee.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 非敏感偏好存储。
 *
 * 使用 Jetpack DataStore Preferences 存储用户偏好设置：
 * - 排序方式和方向
 * - 上次选中的文件夹
 * - 仅点赞模式开关
 * - 内/外网切换状态
 *
 * 注意：敏感数据（地址、密码）不在此存储，使用 [EncryptedConfigStore]。
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nasee_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        // 排序字段
        val SORT_FIELD = stringPreferencesKey("sort_field")
        // 排序方向
        val SORT_ORDER = stringPreferencesKey("sort_order")
        // 上次选中的文件夹
        val SELECTED_FOLDER = stringPreferencesKey("selected_folder")
        // 仅点赞模式
        val LIKED_ONLY = booleanPreferencesKey("liked_only")
        // 使用外网地址
        val USE_EXTERNAL = booleanPreferencesKey("use_external")

        // 默认值
        const val DEFAULT_SORT_FIELD = "mod_time"
        const val DEFAULT_SORT_ORDER = "desc"
        const val DEFAULT_FOLDER = ""
    }

    /** 排序字段 Flow */
    val sortFieldFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SORT_FIELD] ?: DEFAULT_SORT_FIELD
    }

    /** 排序方向 Flow */
    val sortOrderFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SORT_ORDER] ?: DEFAULT_SORT_ORDER
    }

    /** 选中文件夹 Flow */
    val selectedFolderFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_FOLDER] ?: DEFAULT_FOLDER
    }

    /** 仅点赞模式 Flow */
    val likedOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LIKED_ONLY] ?: false
    }

    /** 使用外网地址 Flow */
    val useExternalFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_EXTERNAL] ?: false
    }

    /** 设置排序字段 */
    suspend fun setSortField(field: String) {
        context.dataStore.edit { it[SORT_FIELD] = field }
    }

    /** 设置排序方向 */
    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { it[SORT_ORDER] = order }
    }

    /** 设置选中文件夹 */
    suspend fun setSelectedFolder(folder: String) {
        context.dataStore.edit { it[SELECTED_FOLDER] = folder }
    }

    /** 设置仅点赞模式 */
    suspend fun setLikedOnly(likedOnly: Boolean) {
        context.dataStore.edit { it[LIKED_ONLY] = likedOnly }
    }

    /** 设置使用外网地址 */
    suspend fun setUseExternal(useExternal: Boolean) {
        context.dataStore.edit { it[USE_EXTERNAL] = useExternal }
    }
}
