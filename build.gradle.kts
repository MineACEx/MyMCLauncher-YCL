// ============================================================
// YCL启动器 — 根目录构建配置
// 使用 Kotlin DSL + 版本目录统一管理依赖
// ============================================================

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}