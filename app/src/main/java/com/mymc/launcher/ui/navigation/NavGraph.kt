package com.mymc.launcher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mymc.launcher.ui.screens.home.HomeScreen
import com.mymc.launcher.ui.screens.java.JavaScreen
import com.mymc.launcher.ui.screens.version.VersionScreen
import com.mymc.launcher.ui.screens.versionSettings.VersionSettingsScreen
import com.mymc.launcher.ui.screens.resource.ResourceScreen
import com.mymc.launcher.ui.screens.account.AccountScreen
import com.mymc.launcher.ui.screens.settings.SettingsScreen

/**
 * YCL启动器 — Compose Navigation 导航图
 */
@Composable
fun YCLNavGraph(navController: NavHostController) {
    val context = LocalContext.current

    val onNavigate: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // ──────────── 主界面 ────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = onNavigate,
                currentRoute = Screen.Home.route
            )
        }

        // ──────────── Java 环境管理 ────────────
        composable(Screen.Java.route) {
            JavaScreen(
                onNavigate = onNavigate,
                currentRoute = Screen.Java.route
            )
        }

        // ──────────── 游戏版本管理 ────────────
        composable(Screen.Version.route) {
            VersionScreen(
                onNavigate = onNavigate,
                currentRoute = Screen.Version.route,
                onVersionClick = { versionId ->
                    navController.navigate(Screen.VersionSettings.createRoute(versionId))
                }
            )
        }

        // ──────────── 版本设置（JVM 参数） ────────────
        composable(
            route = Screen.VersionSettings.route,
            arguments = listOf(
                navArgument(Screen.VersionSettings.ARG_VERSION_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val versionId =
                backStackEntry.arguments?.getString(Screen.VersionSettings.ARG_VERSION_ID) ?: ""
            VersionSettingsScreen(
                versionId = versionId,
                onBack = { navController.popBackStack() }
            )
        }

        // ──────────── 资源中心 ────────────
        composable(Screen.Resource.route) {
            ResourceScreen(
                onNavigate = onNavigate,
                currentRoute = Screen.Resource.route
            )
        }

        // ──────────── 账号管理 ────────────
        composable(Screen.Account.route) {
            AccountScreen(
                onNavigate = onNavigate,
                currentRoute = Screen.Account.route
            )
        }

        // ──────────── 设置 ────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigate = onNavigate,
                currentRoute = Screen.Settings.route
            )
        }

        // ──────────── 启动游戏（路由入口占位） ────────────
        composable(
            route = Screen.LaunchGame.route,
            arguments = listOf(
                navArgument(Screen.LaunchGame.ARG_VERSION_ID) {
                    type = NavType.StringType
                }
            )
        ) { /* 实际启动逻辑由 HomeScreen 触发 */ }
    }
}