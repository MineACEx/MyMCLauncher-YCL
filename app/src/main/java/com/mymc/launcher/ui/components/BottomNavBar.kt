package com.mymc.launcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航栏目标数据类。
 * @param label  导航项文字标签
 * @param icon   导航项图标
 * @param route  导航目标路由
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * 底部导航栏组件。
 * 包含 6 个导航项：主页、Java、版本、资源、账号、设置。
 * 使用 Material3 NavigationBar + NavigationBarItem 构建。
 * 当前选中项高亮为主题色。
 *
 * @param currentRoute  当前激活的路由，用于高亮对应导航项
 * @param onNavigate    导航回调，传入目标路由
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem(label = "主页", icon = Icons.Filled.Home, route = "home"),
        BottomNavItem(label = "Java", icon = Icons.Filled.Coffee, route = "java"),
        BottomNavItem(label = "版本", icon = Icons.Filled.Archive, route = "version"),
        BottomNavItem(label = "资源", icon = Icons.Filled.Folder, route = "resource"),
        BottomNavItem(label = "账号", icon = Icons.Filled.Person, route = "account"),
        BottomNavItem(label = "设置", icon = Icons.Filled.Settings, route = "settings")
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(text = item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}