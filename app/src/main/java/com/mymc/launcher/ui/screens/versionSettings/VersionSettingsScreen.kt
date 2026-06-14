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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.mymc.launcher.domain.model.GameVersionType
import com.mymc.launcher.service.version.VersionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 版本设置页面 ViewModel。
 * 使用 AndroidViewModel 获取 Application 上下文以访问版本设置相关服务。
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

    init {
        // 从 DataStore 读取已保存的 JVM 参数
        viewModelScope.launch {
            preferencesManager.jvmArgsFlow.collect { args ->
                _jvmArgs.value = args
                _savedJvmArgs.value = args
            }
        }
    }

    /** 加载版本基本信息 */
    fun loadVersionInfo(versionId: String) {
        viewModelScope.launch {
            _versionName.value = versionId
            _versionType.value = "未知"
            // 尝试从本地版本中获取版本信息
            val localVersions = versionManager.scanLocalVersions()
            val version = localVersions.find { it.versionId == versionId }
            if (version != null) {
                _versionName.value = version.versionId
                _versionType.value = when (version.type) {
                    GameVersionType.VANILLA -> "原版"
                    GameVersionType.FORGE -> "Forge"
                    GameVersionType.FABRIC -> "Fabric"
                }
            }
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

/**
 * 版本设置页面 Composable。
 */
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 版本信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

            // JVM 参数设置
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

            // 游戏设置
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

/**
 * 版本信息行。
 */
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

/**
 * 设置开关行。
 */
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