# ============================================================
# YCL启动器 — R8 混淆规则
# ============================================================

# ---- 保留项 ----

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Zip4j
-dontwarn net.lingala.zip4j.**
-keep class net.lingala.zip4j.** { *; }

# Apache Commons
-dontwarn org.apache.commons.**
-keep class org.apache.commons.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keep class androidx.datastore.** { *; }

# ---- 项目代码保留 ----
# 保留所有数据类（DTO / Model）
-keep class com.mymc.launcher.data.remote.dto.** { *; }
-keep class com.mymc.launcher.domain.model.** { *; }

# 保留 Retrofit API 接口
-keep interface com.mymc.launcher.data.remote.api.** { *; }

# ---- 通用优化 ----
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses com.mymc.launcher