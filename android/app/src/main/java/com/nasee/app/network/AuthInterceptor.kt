package com.nasee.app.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 拦截器：自动注入认证头 `X-NASee-Key`。
 *
 * 密码通过 [getPassword] 闭包动态获取，支持运行时更新密码（如用户修改连接配置后）。
 *
 * @property getPassword 返回当前密码的闭包
 */
class AuthInterceptor(
    private val getPassword: () -> String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val password = getPassword()
        val request = chain.request().newBuilder()
            .addHeader(HEADER_KEY, password)
            .build()
        return chain.proceed(request)
    }

    /**
     * 获取当前密码（供 ExoPlayer DataSource 使用）。
     */
    fun getPassword(): String = getPassword.invoke()

    companion object {
        /** 认证请求头名称 */
        const val HEADER_KEY = "X-NASee-Key"
    }
}
