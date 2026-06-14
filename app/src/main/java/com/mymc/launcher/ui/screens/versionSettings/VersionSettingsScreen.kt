package com.mymc.launcher.ui.screens.versionSettings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.service.version.VersionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 默认 JVM 启动参数。
 */
private const val DEFAULT_JVM_ARGS = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+ZGenerational"

/**
 * 版本设置页面 ViewModel。
 * 管理当前版本的 JVM 参数读写。
 */
class VersionSettingsViewModel(versionId: String) : ViewModel() {

    /** 当前版本 ID */
    private val currentVersionId: String = versionId

    /** 版本号显示名称 */
    private val _versionName = MutableStateFlow(versionId)
    val versionName: StateFlow<String> = _versionName.asStateFlow()

    /** 版本类型 */
    private val _versionType = MutableStateFlow("原版")
    val versionType: StateFlow<String> = _versionType.asStateFlow()

    /** JVM 参数文本 */
    private val _jvmArgs = MutableStateFlow("")
    val jvmArgs: StateFlow<String> = _jvmArgs.asStateFlow()

    /** 保存结果提示 */
    private val _saveMessage = MutableStateFlow("")
    val saveMessage: StateFlow<String> = _saveMessage.asStateFlow()

    /** Preferences 存储键 */
    private val prefsKey = "jvm_args_$versionId"

    init {
        loadVersionInfo()
        loadJvmArgs()
    }

    /** 加载版本基本信息 */
    private fun loadVersionInfo() {
        val version = VersionManager.getVersionInfo(currentVersionId)
        if (version != null) {
            _versionName.value = version.name
            _versionType.value = version.type
        }
    }

    /** 从 PreferencesManager 读取该版本的 JVM 参数 */
    private fun loadJvmArgs() {
        val saved = PreferencesManager.getString(prefsKey, "")
        _jvmArgs.value = saved.ifBlank { DEFAULT_JVM_ARGS }
    }

    /** 更新 JVM 参数文本 */
    fun updateJvmArgs(newArgs: String) {
        _jvmArgs.value = newArgs
    }

    /** 保存 JVM 参数到 PreferencesManager */
    fun saveJvmArgs() {
        PreferencesManager.putString(prefsKey, _jvmArgs.value)
        _saveMessage.value = "JVM 参数已保存"
    }

    /** 重置为默认 JVM 参数 */
    fun resetToDefault() {
        _jvmArgs.value = DEFAULT_JVM_ARGS
        PreferencesManager.putString(prefsKey, DEFAULT_JVM_ARGS)
        _saveMessage.value = "已重置为默认参数"
    }
}

/**
 * 版本设置页面 Composable。
 * 显示当前版本信息，提供自定义 JVM 参数编辑与保存。
 *
 * @param versionId   从导航路由获取的版本 ID
 * @param onBack      返回上一页回调
 */
@Composable
fun VersionSettingsScreen(
    versionId: String,
    onBack: () -> Unit,
    viewModel: VersionSettingsViewModel = remember(versionId) {
        VersionSettingsViewModel(versionId)
    }
) {
    val versionName by viewModel.versionName.collectAsState()
    val versionType by viewModel.versionType.collectAsState()
    val jvmArgs by viewModel.jvmArgs.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 返回按钮
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("返回")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 当前版本信息
            Text(
                text = "版本设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "版本: $versionName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "类型: $versionType",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // JVM 参数输入
            Text(
                text = "自定义 JVM 参数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = jvmArgs,
                onValueChange = { viewModel.updateJvmArgs(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = {
                    Text("-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions")
                },
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.saveJvmArgs() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.resetToDefault() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("重置为默认参数")
                }
            }

            // 保存结果提示
            if (saveMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = saveMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}