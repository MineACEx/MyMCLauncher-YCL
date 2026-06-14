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
import com.mymc.launcher.service.java.JavaEnvironmentManager
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.ui.components.FadeInContent
import com.mymc.launcher.ui.components.GlassCard
import com.mymc.launcher.ui.components.scaleOnClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Java 版本安装状态枚举。
 */
enum class JavaInstallStatus {
    NOT_INSTALLED,
    DOWNLOADING,
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
 */
class JavaViewModel(application: Application) : AndroidViewModel(application) {

    private val javaManager = JavaManager.getInstance(application)
    private val javaEnvManager = JavaEnvironmentManager.getInstance(application)

    private val _javaVersions = MutableStateFlow(
        listOf(
            JavaVersionItem(version = "Java 8", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "120 MB"),
            JavaVersionItem(version = "Java 17", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "180 MB"),
            JavaVersionItem(version = "Java 21", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "200 MB"),
            JavaVersionItem(version = "Java 25", status = JavaInstallStatus.NOT_INSTALLED, fileSize = "210 MB")
        )
    )
    val javaVersions: StateFlow<List<JavaVersionItem>> = _javaVersions.asStateFlow()

    private val _autoMatchEnabled = MutableStateFlow(false)
    val autoMatchEnabled: StateFlow<Boolean> = _autoMatchEnabled.asStateFlow()

    /** 是否正在从 APK 提取 Java */
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    /** 提取进度消息 */
    private val _extractMessage = MutableStateFlow("")
    val extractMessage: StateFlow<String> = _extractMessage.asStateFlow()

    init {
        // 启动时自动检测已安装的 Java 环境
        refreshStatus()
        // 检测嵌入式 Java 是否需要提取
        checkEmbeddedJava()
    }

    /**
     * 检测嵌入式 Java 运行时环境
     * 参照 FCL：第一次启动时从 APK assets 中提取嵌入式 JRE
     */
    private fun checkEmbeddedJava() {
        viewModelScope.launch {
            val current = _javaVersions.value.toMutableList()
            for (i in current.indices) {
                val versionNum = current[i].version.removePrefix("Java ")
                // 检查是否已通过嵌入式安装
                val installed = javaEnvManager.isJavaInstalled(versionNum)
                if (installed) {
                    current[i] = current[i].copy(status = JavaInstallStatus.INSTALLED)
                }
            }
            _javaVersions.value = current
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val current = _javaVersions.value.toMutableList()
            for (i in current.indices) {
                val versionNum = current[i].version.removePrefix("Java ")
                // 检查嵌入式安装
                val embeddedInstalled = javaEnvManager.isJavaInstalled(versionNum)
                // 检查下载安装
                val downloadedInstalled = javaManager.verifyJavaInstallation(versionNum)
                if (embeddedInstalled || downloadedInstalled) {
                    current[i] = current[i].copy(status = JavaInstallStatus.INSTALLED)
                }
            }
            _javaVersions.value = current
        }
    }

    /**
     * 从 APK 嵌入式资源提取 Java 运行时
     * 优先使用嵌入式 JRE（卡刷包方式），失败则回退到网络下载
     */
    fun extractFromAssets(version: String) {
        if (_isExtracting.value) return
        _isExtracting.value = true
        _extractMessage.value = "正在从 APK 提取 Java $version 运行时..."

        viewModelScope.launch {
            updateStatus(version, JavaInstallStatus.DOWNLOADING, 0f)
            val versionNum = version.removePrefix("Java ")
            try {
                val success = javaEnvManager.extractJavaRuntime(versionNum) { _, completedCount ->
                    // extractJavaRuntime 回调签名为 (entryName: String, completedCount: Int)
                    // 将完成数量归一化为 0.0~1.0 进度（最多估算 500 个文件条目）
                    val progress = (completedCount.toFloat() / 500f).coerceIn(0f, 1f)
                    updateStatus(version, JavaInstallStatus.DOWNLOADING, progress)
                    _extractMessage.value = "解压中... ${(progress * 100).toInt()}%"
                }
                if (success) {
                    updateStatus(version, JavaInstallStatus.INSTALLED, 1f)
                    _extractMessage.value = "Java $versionNum 提取完成！"
                } else {
                    // 嵌入式提取失败，回退到网络下载
                    _extractMessage.value = "内嵌资源不可用，尝试网络下载..."
                    startDownload(version)
                }
            } catch (e: Exception) {
                _extractMessage.value = "提取失败: ${e.message}，尝试网络下载..."
                startDownload(version)
            }
            _isExtracting.value = false
        }
    }

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
    val isExtracting by viewModel.isExtracting.collectAsState()
    val extractMessage by viewModel.extractMessage.collectAsState()

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

            Text(
                text = "Java 环境管理",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 提取状态提示
            if (extractMessage.isNotBlank()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF1B5E20).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = extractMessage,
                        fontSize = 13.sp,
                        color = Color(0xFF81C784)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(javaVersions) { item ->
                    JavaVersionCard(
                        item = item,
                        onDownload = { viewModel.startDownload(item.version) },
                        onReDownload = { viewModel.reDownload(item.version) },
                        onExtract = { viewModel.extractFromAssets(item.version) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
}

/**
 * 单个 Java 版本卡片组件（毛玻璃风格）。
 */
@Composable
private fun JavaVersionCard(
    item: JavaVersionItem,
    onDownload: () -> Unit,
    onReDownload: () -> Unit,
    onExtract: () -> Unit = {}
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExtract,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B5E20)
                            )
                        ) {
                            Text("从APK提取")
                        }
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("从网络下载")
                        }
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