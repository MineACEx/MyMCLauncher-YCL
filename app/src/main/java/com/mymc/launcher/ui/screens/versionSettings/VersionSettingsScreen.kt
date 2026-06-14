package com.mymc.launcher.ui.screens.versionSettings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.domain.model.GameVersion
import com.mymc.launcher.domain.model.GameVersionType
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.game.GameLauncher
import com.mymc.launcher.ui.components.GlassCard
import com.mymc.launcher.ui.components.scaleOnClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 版本设置页面 ViewModel
 *
 * 参照 FCL 的 VersionListPage + VersionSettingsPage：
 * - 版本信息展示
 * - JVM 参数配置
 * - Forge/Fabric 模组加载器安装
 * - 游戏启动
 */
class VersionSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager.getInstance(application)
    private val versionManager = VersionManager.getInstance(application)

    private val _versionName = MutableStateFlow("")
    val versionName: StateFlow<String> = _versionName.asStateFlow()

    private val _versionType = MutableStateFlow("")
    val versionType: StateFlow<String> = _versionType.asStateFlow()

    private val _jvmArgs = MutableStateFlow("-Xmx2G -XX:+UseG1GC")
    val jvmArgs: StateFlow<String> = _jvmArgs.asStateFlow()

    private val _savedJvmArgs = MutableStateFlow("-Xmx2G -XX:+UseG1GC")

    /** 加载器安装状态 */
    private val _installLoaderProgress = MutableStateFlow(0f)
    val installLoaderProgress: StateFlow<Float> = _installLoaderProgress.asStateFlow()

    private val _installLoaderMessage = MutableStateFlow("")
    val installLoaderMessage: StateFlow<String> = _installLoaderMessage.asStateFlow()

    private val _isInstallingLoader = MutableStateFlow(false)
    val isInstallingLoader: StateFlow<Boolean> = _isInstallingLoader.asStateFlow()

    private var _currentVersion: GameVersion? = null

    init {
        viewModelScope.launch {
            preferencesManager.jvmArgsFlow.collect { args ->
                _jvmArgs.value = args
                _savedJvmArgs.value = args
            }
        }
    }

    fun loadVersionInfo(versionId: String) {
        viewModelScope.launch {
            _versionName.value = versionId
            _versionType.value = "未知"
            val localVersions = versionManager.scanLocalVersions()
            val version = localVersions.find { it.versionId == versionId }
            if (version != null) {
                _versionName.value = version.versionId
                _versionType.value = when (version.type) {
                    GameVersionType.VANILLA -> "原版"
                    GameVersionType.FORGE -> "Forge"
                    GameVersionType.FABRIC -> "Fabric"
                }
                _currentVersion = version
            }
        }
    }

    /** 启动游戏 */
    fun launchGame(versionId: String) {
        val version = _currentVersion ?: GameVersion(
            id = "vanilla_$versionId",
            versionId = versionId,
            type = GameVersionType.VANILLA,
            downloadUrl = "",
            fileSize = 0,
            installed = true
        )
        viewModelScope.launch {
            GameLauncher.launch(getApplication(), version)
        }
    }

    /** 安装 Fabric 加载器 */
    fun installFabric() {
        if (_isInstallingLoader.value) return
        _isInstallingLoader.value = true
        _installLoaderMessage.value = "正在安装 Fabric 加载器..."
        _installLoaderProgress.value = 0f

        viewModelScope.launch {
            val versionId = _versionName.value
            val success = versionManager.installFabric(versionId) { progress ->
                _installLoaderProgress.value = progress
            }
            if (success) {
                _installLoaderMessage.value = "Fabric 安装成功！请重新扫描版本以查看"
                _installLoaderProgress.value = 1f
            } else {
                _installLoaderMessage.value = "Fabric 安装失败，请检查网络连接"
                _installLoaderProgress.value = 0f
            }
            _isInstallingLoader.value = false
        }
    }

    /** 安装 Forge 加载器 */
    fun installForge() {
        if (_isInstallingLoader.value) return
        _isInstallingLoader.value = true
        _installLoaderMessage.value = "正在安装 Forge 加载器..."
        _installLoaderProgress.value = 0f

        viewModelScope.launch {
            val versionId = _versionName.value
            val success = versionManager.installForge(versionId) { progress ->
                _installLoaderProgress.value = progress
            }
            if (success) {
                _installLoaderMessage.value = "Forge 安装成功！请重新扫描版本以查看"
                _installLoaderProgress.value = 1f
            } else {
                _installLoaderMessage.value = "Forge 安装失败，请检查网络连接"
                _installLoaderProgress.value = 0f
            }
            _isInstallingLoader.value = false
        }
    }

    fun updateJvmArgs(args: String) {
        _jvmArgs.value = args
    }

    fun saveJvmArgs() {
        _savedJvmArgs.value = _jvmArgs.value
        viewModelScope.launch {
            preferencesManager.setJvmArgs(_jvmArgs.value)
        }
    }

    fun hasUnsavedChanges(): Boolean = _jvmArgs.value != _savedJvmArgs.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSettingsScreen(
    versionId: String,
    onBack: () -> Unit,
    viewModel: VersionSettingsViewModel = viewModel()
) {
    LaunchedEffect(versionId) {
        viewModel.loadVersionInfo(versionId)
    }

    val versionName by viewModel.versionName.collectAsState()
    val versionType by viewModel.versionType.collectAsState()
    val jvmArgs by viewModel.jvmArgs.collectAsState()
    val installLoaderProgress by viewModel.installLoaderProgress.collectAsState()
    val installLoaderMessage by viewModel.installLoaderMessage.collectAsState()
    val isInstallingLoader by viewModel.isInstallingLoader.collectAsState()

    // 是否显示加载器安装 UI（仅原版版本显示）
    val showLoaderInstall = versionType == "原版" || versionType == "未知"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("版本设置") },
                navigationIcon = {
                    Text(
                        text = "‹ 返回",
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { onBack() },
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 启动游戏按钮（参照 FCL MainActivity.start 按钮）
            Button(
                onClick = { viewModel.launchGame(versionId) },
                modifier = Modifier.fillMaxWidth().scaleOnClick(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                enabled = versionName.isNotBlank()
            ) {
                Text(
                    text = "▶ 启动游戏",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 版本信息卡片（毛玻璃风格）
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "版本信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    VersionInfoRow(label = "版本名称", value = versionName)
                    VersionInfoRow(label = "版本类型", value = versionType)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 模组加载器安装（参照 FCL 的 InstallerPage）──
            if (showLoaderInstall && versionName.isNotBlank()) {
                Text(
                    text = "加载器安装",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "安装模组加载器（Forge / Fabric）",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Forge + Fabric 安装按钮并排
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.installForge() },
                                modifier = Modifier.weight(1f),
                                enabled = !isInstallingLoader,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD81B60)
                                )
                            ) {
                                Text("安装 Forge", fontSize = 14.sp)
                            }
                            Button(
                                onClick = { viewModel.installFabric() },
                                modifier = Modifier.weight(1f),
                                enabled = !isInstallingLoader,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFC107)
                                )
                            ) {
                                Text("安装 Fabric", fontSize = 14.sp)
                            }
                        }

                        // 安装进度
                        if (installLoaderMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isInstallingLoader) {
                                LinearProgressIndicator(
                                    progress = { installLoaderProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${(installLoaderProgress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = installLoaderMessage,
                                fontSize = 13.sp,
                                color = if (installLoaderMessage.contains("成功")) {
                                    Color(0xFF4CAF50)
                                } else if (installLoaderMessage.contains("失败")) {
                                    Color(0xFFE53935)
                                } else {
                                    Color.Gray
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // JVM 参数
            Text(
                text = "JVM 参数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = jvmArgs,
                onValueChange = { viewModel.updateJvmArgs(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("JVM 启动参数") },
                minLines = 3,
                maxLines = 6
            )

            if (viewModel.hasUnsavedChanges()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveJvmArgs() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存 JVM 参数")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "游戏设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsRow(
                label = "全屏",
                description = "以全屏模式启动游戏"
            ) {
                Switch(
                    checked = true,
                    onCheckedChange = { }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VersionInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    description: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        content()
    }
}