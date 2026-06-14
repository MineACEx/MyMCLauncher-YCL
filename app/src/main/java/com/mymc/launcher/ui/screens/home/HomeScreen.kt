package com.mymc.launcher.ui.screens.home

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymc.launcher.service.account.AccountManager
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.ui.components.FadeInContent
import com.mymc.launcher.ui.components.GlassCard
import com.mymc.launcher.ui.components.scaleOnClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页数据模型。
 */
data class HomeData(
    val installedVersions: List<String> = emptyList(),
    val loggedInAccount: String = "",
    val accountType: String = ""
)

/**
 * 首页 ViewModel。
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val versionManager = VersionManager.getInstance(application)
    private val accountManager = AccountManager.getInstance(application)

    private val _homeData = MutableStateFlow(HomeData())
    val homeData: StateFlow<HomeData> = _homeData.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            val installed = versionManager.scanLocalVersions().map { it.versionId }
            val account = accountManager.currentAccount.value
            _homeData.value = HomeData(
                installedVersions = installed,
                loggedInAccount = account?.username ?: "",
                accountType = account?.accountType?.name ?: ""
            )
        }
    }
}

/**
 * 首页 Composable。
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: HomeViewModel = viewModel()
) {
    val homeData by viewModel.homeData.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
        }
    ) { innerPadding ->
        FadeInContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 欢迎横幅
            WelcomeBanner(
                accountName = homeData.loggedInAccount,
                accountType = homeData.accountType
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 已安装版本
            Text(
                text = "已安装版本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (homeData.installedVersions.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无已安装版本",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigate("version") }
                        ) {
                            Text("去下载")
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(homeData.installedVersions) { versionId ->
                        VersionChip(
                            versionId = versionId,
                            onClick = { onNavigate("version_settings/$versionId") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 快速操作
            Text(
                text = "快速操作",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "版本管理",
                    description = "下载和管理游戏版本",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("version") }
                )
                QuickActionCard(
                    title = "Java 环境",
                    description = "配置 Java 运行时",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("java") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "账号管理",
                    description = "登录和管理账号",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("account") }
                )
                QuickActionCard(
                    title = "资源下载",
                    description = "Mod/光影/材质",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("resource") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

/**
 * 欢迎横幅组件（毛玻璃风格）。
 */
@Composable
private fun WelcomeBanner(
    accountName: String,
    accountType: String
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "欢迎使用 YCL 启动器",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (accountName.isNotBlank()) {
                Text(
                    text = "当前账号: $accountName ($accountType)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = "尚未登录账号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 版本标签芯片（毛玻璃风格）。
 */
@Composable
private fun VersionChip(
    versionId: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        cornerRadius = 12.dp,
        blurRadius = 6.dp,
        backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
        borderColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentPadding = 10.dp,
        enableClickAnimation = false
    ) {
        Text(
            text = versionId,
            modifier = Modifier.padding(horizontal = 6.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 快速操作卡片（毛玻璃风格）。
 */
@Composable
private fun QuickActionCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        cornerRadius = 12.dp,
        blurRadius = 8.dp,
        contentPadding = 16.dp,
        enableClickAnimation = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}