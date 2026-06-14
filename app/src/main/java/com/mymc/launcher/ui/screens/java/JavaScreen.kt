package com.mymc.launcher.ui.screens.java

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymc.launcher.service.java.JavaManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Java 版本安装状态枚举。
 */
enum class JavaInstallStatus {
    /** 未安装 */
    NOT_INSTALLED,
    /** 正在下载 */
    DOWNLOADING,
    /** 已安装 */
    INSTALLED
}

/**
 * Java 版本数据模型。
 */
data class JavaVersionItem(
    val version: String,
    val status: JavaInstallStatus,
    val fileSize: String,
    val progress: Float = 0f
)

/**
 * Java 环境管理页面 ViewModel。
 * 使用 AndroidViewModel 获取 Application 上下文以访问 JavaManager 单例。
 */
class JavaViewModel(application: Application) : AndroidViewModel(application) {

    private val javaManager = JavaManager.getInstance(application)

    private val _javaVersions = MutableStateFlow(
        listOf(
            JavaVersionItem(version = "Java 8", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "120 MB"),
            JavaVersionItem(version = "Java 17", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "180 MB"),
            JavaVersionItem(version = "Java 21", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "200 MB"),
            JavaVersionItem(version = "Java 25", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "210 MB")
        )
    )
    val javaVersions: StateFlow<List<JavaVersionItem>> = _javaVersions.asStateFlow()

    /** 自动匹配开关状态 */
    private val _autoMatchEnabled = MutableStateFlow(false)
    val autoMatchEnabled: StateFlow<Boolean> = _autoMatchEnabled.asStateFlow()

    init {
        refreshStatus()
    }

    /** 从 JavaManager 刷新所有版本安装状态 */
    fun refreshStatus() {
        viewModelScope.launch {
            val current = _javaVersions.value.toMutableList()
            for (i in current.indices) {
                val versionNum = current[i].version.removePrefix("Java ")
                val installed = javaManager.verifyJavaInstallation(versionNum)
                if (installed) {
                    current[i] = current[i].copy(status = JavaInstallStatus.INSTALLED)
                }
            }
            _javaVersions.value = current
        }
    }

    /** 开始下载指定 Java 版本 */
    fun startDownload(version: String) {
        viewModelScope.launch {
            updateStatus(version, JavaInstallStatus.DOWNLOADING, 0f)
            val versionNum = version.removePrefix("Java ")
            javaManager.downloadJava(versionNum) { progress ->
                viewModelScope.launch {
                    if (progress >= 1f) {
                        updateStatus(version, JavaInstallStatus.INSTALLED, 1f)
                    } else {
                        updateStatus(version, JavaInstallStatus.DOWNLOADING, progress)
                    }
                }
            }
        }
    }

    /** 重新下载指定 Java 版本 */
    fun reDownload(version: String) {
        startDownload(version)
    }

    fun toggleAutoMatch(enabled: Boolean) {
        _autoMatchEnabled.value = enabled
    }

    private fun updateStatus(version: String, status: JavaInstallStatus, progress: Float) {
        val list = _javaVersions.value.map { item ->
            if (item.version == version) item.copy(status = status, progress = progress) else item
        }
        _javaVersions.value = list
    }
}

/**
 * Java 环境管理页面 Composable。
 */
@Composable
fun JavaScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: JavaViewModel = viewModel()
) {
    val javaVersions by viewModel.javaVersions.collectAsState()
    val autoMatchEnabled by viewModel.autoMatchEnabled.collectAsState()

    Scaffold(
        bottomBar = {
            com.mymc.launcher.ui.components.BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Java 环境管理",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(javaVersions) { item ->
                    JavaVersionCard(
                        item = item,
                        onDownload = { viewModel.startDownload(item.version) },
                        onReDownload = { viewModel.reDownload(item.version) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 自动匹配开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自动匹配",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = autoMatchEnabled,
                    onCheckedChange = { viewModel.toggleAutoMatch(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 单个 Java 版本卡片组件。
 */
@Composable
private fun JavaVersionCard(
    item: JavaVersionItem,
    onDownload: () -> Unit,
    onReDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.version,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "大小: ${item.fileSize}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (item.status) {
                JavaInstallStatus.INSTALLED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已安装 ✓",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = onReDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("重新下载")
                        }
                    }
                }

                JavaInstallStatus.NOT_INSTALLED -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("下载")
                    }
                }

                JavaInstallStatus.DOWNLOADING -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 安装状态标签徽章。
 */
@Composable
private fun StatusBadge(status: JavaInstallStatus) {
    val (text, color) = when (status) {
        JavaInstallStatus.INSTALLED -> "已安装" to Color(0xFF4CAF50)
        JavaInstallStatus.NOT_INSTALLED -> "未安装" to Color(0xFFFF9800)
        JavaInstallStatus.DOWNLOADING -> "下载中" to Color(0xFF2196F3)
    }

    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
}