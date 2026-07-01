# ============================================================
# NASee ProGuard Rules
# ============================================================

# --- 通用 Android 规则 ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn javax.annotation.**

# --- Kotlin Coroutines ---
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Compose ---
# Compose compiler自带混淆规则，无需额外配置。

# --- Retrofit2 ---
-keepattributes Signature, InnerClasses, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# --- Gson ---
# 保留数据模型类（用于 JSON 序列化/反序列化）
-keep class com.nasee.app.data.model.** { *; }
-keep class com.nasee.app.data.remote.** { *; }
-keepattributes Signature
-dontwarn com.google.gson.**

# --- Media3 / ExoPlayer ---
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# --- EncryptedSharedPreferences / AndroidX Security ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# --- Coil ---
-dontwarn coil.**

# --- DataStore ---
-keep class androidx.datastore.** { *; }
