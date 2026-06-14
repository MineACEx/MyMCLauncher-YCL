package com.mymc.launcher.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.ui.theme.PresetThemeColors
import com.mymc.launcher.service.theme.ThemeManager
import com.mymc.launcher.ui.components.BottomNavBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页面 ViewModel。
 * 管理深色模式、主题色、背景图片、版本隔离等设置状态。
 */
class SettingsViewModel : ViewModel() {

    private val preferencesManager = PreferencesManager.getInstance(
        com.mymc.launcher.YCLApplication.instance
    )

    /** 深色模式开关 */
    private val _isDarkMode = MutableStateFlow(ThemeManager.isDarkMode.value ?: false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /** 当前主题色 */
    private val _currentThemeColor = MutableStateFlow(ThemeManager.themeColor.value)
    val currentThemeColor: StateFlow<String> = _currentThemeColor.asStateFlow()

    /** 背景图片路径 */
    private val _backgroundPath = MutableStateFlow(ThemeManager.getCurrentBackgroundPath())
    val backgroundPath: StateFlow<String> = _backgroundPath.asStateFlow()

    /** 版本隔离开关 */
    private val _versionIsolationEnabled = MutableStateFlow(true)
    val versionIsolationEnabled: StateFlow<Boolean> = _versionIsolationEnabled.asStateFlow()

    /** 应用版本号 */
    private val _appVersion = MutableStateFlow("1.0.0")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    init {
        // 从 DataStore 读取版本隔离状态
        kotlinx.coroutines.MainScope().launch {
            preferencesManager.versionIsolationFlow.collect { enabled ->
                _versionIsolationEnabled.value = enabled
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        ThemeManager.setDarkMode(enabled)
    }

    fun selectThemeColor(colorHex: String) {
        _currentThemeColor.value = colorHex
        ThemeManager.setThemeColor(colorHex)
    }

    fun setBackgroundImage(uri: Uri?) {
        if (uri != null) {
            _backgroundPath.value = uri.toString()
            ThemeManager.setBackgroundPath(uri.toString())
        }
    }

    fun resetBackgroundImage() {
        _backgroundPath.value = ""
        ThemeManager.setBackgroundPath("")
    }

    fun toggleVersionIsolation(enabled: Boolean) {
        _versionIsolationEnabled.value = enabled
        kotlinx.coroutines.MainScope().launch {
            preferencesManager.setVersionIsolation(enabled)
        }
    }
}

/**
 * 设置页面 Composable。
 * 深色/亮色模式切换、主题色选择、背景图片、版本隔离、关于信息。
 *
 * @param onNavigate    全局导航回调
 * @param currentRoute  当前路由
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SettingsViewModel = remember { SettingsViewModel() }
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val currentThemeColor by viewModel.currentThemeColor.collectAsState()
    val backgroundPath by viewModel.backgroundPath.collectAsState()
    val versionIsolationEnabled by viewModel.versionIsolationEnabled.collectAsState()
    val appVersion by viewModel.appVersion.collectAsState()

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setBackgroundImage(uri)
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
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

            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 深色/亮色模式切换
            SettingsToggleRow(
                title = "深色模式",
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 主题色选择
            Text(
                text = "主题色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetThemeColors.forEach { (name, themeColor) ->
                    val colorHex = String.format("#%08X", themeColor.toArgb())
                    ColorCircle(
                        color = themeColor,
                        isSelected = currentThemeColor.equals(colorHex, ignoreCase = true),
                        onClick = { viewModel.selectThemeColor(colorHex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 背景图片
            Text(
                text = "背景图片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (backgroundPath.isNotBlank()) {
                Text(
                    text = "已设置: $backgroundPath",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = "未设置背景图片",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("选择本地图片")
                }
                Button(
                    onClick = { viewModel.resetBackgroundImage() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("重置")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 版本隔离开关
            SettingsToggleRow(
                title = "版本隔离",
                checked = versionIsolationEnabled,
                onCheckedChange = { viewModel.toggleVersionIsolation(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 关于
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "应用版本", color = Color.Gray)
                Text(
                    text = appVersion,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 设置开关行组件。
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 主题色选择圆圈组件。
 */
@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color.White else Color.Transparent
    val borderWidth = if (isSelected) 3.dp else 0.dp

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .background(color, CircleShape)
            .clickable { onClick() }
    )
}