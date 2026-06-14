package com.mymc.launcher.ui.navigation

/**
 * YCL启动器 — 导航页面定义
 * 使用 sealed class 统一定义所有页面的路由字符串和参数
 */
sealed class Screen(val route: String) {

    /** 主界面 */
    data object Home : Screen("home")

    /** Java 环境管理 */
    data object Java : Screen("java")

    /** 游戏版本管理 */
    data object Version : Screen("version")

    /** 版本设置（JVM 参数） */
    data object VersionSettings : Screen("version_settings/{versionId}") {
        const val ARG_VERSION_ID = "versionId"
        fun createRoute(versionId: String): String = "version_settings/$versionId"
    }

    /** 资源中心 */
    data object Resource : Screen("resource")

    /** 资源详情 */
    data object ResourceDetail : Screen("resource_detail/{resourceId}") {
        const val ARG_RESOURCE_ID = "resourceId"
        fun createRoute(resourceId: String): String = "resource_detail/$resourceId"
    }

    /** 账号管理 */
    data object Account : Screen("account")

    /** 设置 */
    data object Settings : Screen("settings")

    /** 启动游戏 */
    data object LaunchGame : Screen("launch_game/{versionId}") {
        const val ARG_VERSION_ID = "versionId"
        fun createRoute(versionId: String): String = "launch_game/$versionId"
    }

    companion object {
        /** 底部导航栏页面（6个主要页面） */
        val bottomNavScreens = listOf(Home, Java, Version, Resource, Account, Settings)

        /** 所有注册页面 */
        val allScreens = bottomNavScreens + listOf(VersionSettings, ResourceDetail, LaunchGame)
    }
}