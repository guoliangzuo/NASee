package com.nasee.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 服务端连接配置。
 *
 * 存储内网地址、外网地址（FN Connect 反向代理）和访问密码。
 * 内网地址优先使用；当内网不可达时可手动切换到外网地址。
 *
 * @property address 内网地址，如 http://192.168.1.100:8080
 * @property externalAddress 外网地址（FN Connect），可选
 * @property password 访问密码
 */
data class ServerConfig(
    @SerializedName("address")
    val address: String,

    @SerializedName("external_address")
    val externalAddress: String? = null,

    @SerializedName("password")
    val password: String
) {
    /**
     * 获取当前生效的 base URL。
     *
     * 内网优先；当 [useExternal] 为 true 且外网地址非空时使用外网地址。
     * 保证返回的 URL 以 `/` 结尾（Retrofit baseUrl 要求）。
     *
     * @param useExternal 是否使用外网地址
     * @return 标准化的 base URL
     */
    fun activeBaseUrl(useExternal: Boolean = false): String {
        val url = if (useExternal && !externalAddress.isNullOrBlank()) {
            externalAddress!!
        } else {
            address
        }
        return if (url.endsWith("/")) url else "$url/"
    }

    /**
     * 拼接完整的流地址。
     *
     * @param streamUrl 服务端返回的相对路径（如 /api/v1/videos/1/stream）
     * @param useExternal 是否使用外网地址
     * @return 完整的流 URL
     */
    fun fullStreamUrl(streamUrl: String, useExternal: Boolean = false): String {
        return activeBaseUrl(useExternal).trimEnd('/') + streamUrl
    }
}
