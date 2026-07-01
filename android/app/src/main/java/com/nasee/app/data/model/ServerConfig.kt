package com.nasee.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 服务端连接配置。
 *
 * 存储局域网地址和访问密码。
 * 仅支持内网连接，移除外网连接相关功能。
 *
 * @property address 局域网地址，如 http://192.168.1.100:8080
 * @property password 访问密码
 */
data class ServerConfig(
    @SerializedName("address")
    val address: String,

    @SerializedName("password")
    val password: String
) {
    /**
     * 获取 base URL。
     *
     * 保证返回的 URL 以 `/` 结尾（Retrofit baseUrl 要求）。
     *
     * @return 标准化的 base URL
     */
    fun activeBaseUrl(): String {
        return if (address.endsWith("/")) address else "$address/"
    }

    /**
     * 拼接完整的流地址。
     *
     * @param streamUrl 服务端返回的相对路径（如 /api/v1/videos/1/stream）
     * @return 完整的流 URL
     */
    fun fullStreamUrl(streamUrl: String): String {
        return activeBaseUrl().trimEnd('/') + streamUrl
    }
}
