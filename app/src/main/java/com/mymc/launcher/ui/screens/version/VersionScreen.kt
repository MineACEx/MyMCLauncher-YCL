package com.mymc.launcher.ui.screens.version

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.domain.model.GameVersionType
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.ui.components.FadeInContent
import com.mymc.launcher.ui.components.GlassCard
import com.mymc.launcher.ui.components.scaleOnClick
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.util.FileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 版本下载状态枚举。
 */
enum class VersionDownloadStatus {
    /** 未安装 */
    NOT_INSTALLED,
    /** 正在下载 */
    DOWNLOADING,
    /** 已安装 */
    INSTALLED,
    /** 下载失败 */
    FAILED
}

/**
 * 版本类型枚举，用于筛选栏。
 */
enum class VersionTypeFilter(val label: String) {
    ALL("全部"),
    VANILLA("原版"),
    FORGE("Forge"),
    FABRIC("Fabric")
}

/**
 * 游戏版本数据模型。
 */
data class GameVersionItem(
    val versionId: String,
    val versionName: String,
    val type: String,
    val size: String,
    val isInstalled: Boolean,
    val downloadStatus: VersionDownloadStatus = if (isInstalled) VersionDownloadStatus.INSTALLED else VersionDownloadStatus.NOT_INSTALLED,
    val downloadProgress: Float = 0f
)

/**
 * 游戏版本管理页面 ViewModel。
 */
class VersionViewModel(application: Application) : AndroidViewModel(application) {

    private val versionManager = VersionManager.getInstance(application)
    private val preferencesManager = PreferencesManager.getInstance(application)

    private val _versionList = MutableStateFlow<List<GameVersionItem>>(emptyList())
    val versionList: StateFlow<List<GameVersionItem>> = _versionList.asStateFlow()

    private val _selectedFilter = MutableStateFlow(VersionTypeFilter.ALL)
    val selectedFilter: StateFlow<VersionTypeFilter> = _selectedFilter.asStateFlow()

    private val _versionIsolationEnabled = MutableStateFlow(
        PreferencesManager.DEFAULT_VERSION_ISOLATION
    )
    val versionIsolationEnabled: StateFlow<Boolean> = _versionIsolationEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.versionIsolationFlow.collect { enabled ->
                _versionIsolationEnabled.value = enabled
            }
        }
        refreshVersions()
    }

    fun refreshVersions() {
        viewModelScope.launch {
            versionManager.fetchVersionList { versions ->
                _versionList.value = versions.map { version ->
                    GameVersionItem(
                        versionId = version.versionId,
                        versionName = "${version.type.name.lowercase().replaceFirstChar { it.uppercase() }} ${version.versionId}",
                        type = when (version.type) {
                            GameVersionType.VANILLA -> "原版"
                            GameVersionType.FORGE -> "Forge"
                            GameVersionType.FABRIC -> "Fabric"
                        },
                        size = if (version.fileSize > 0) FileUtil.formatFileSize(version.fileSize) else "",
                        isInstalled = version.installed,
                        downloadStatus = if (version.installed) VersionDownloadStatus.INSTALLED else VersionDownloadStatus.NOT_INSTALLED
                    )
                }
            }
        }
    }

    fun scanLocalVersions() {
        viewModelScope.launch {
            val localVersions = versionManager.scanLocalVersions()
            val updatedList = _versionList.value.map { item ->
                val localVersion = localVersions.find { it.versionId == item.versionId }
                if (localVersion != null) {
                    item.copy(isInstalled = true, downloadStatus = VersionDownloadStatus.INSTALLED)
                } else {
                    item
                }
            }
            // 添加本地有但远程列表中没有的版本
            val existingIds = updatedList.map { it.versionId }.toSet()
            val newLocalVersions = localVersions
                .filter { it.versionId !in existingIds }
                .map { version ->
                    GameVersionItem(
                        versionId = version.versionId,
                        versionName = "${version.type.name.lowercase().replaceFirstChar { it.uppercase() }} ${version.versionId}",
                        type = when (version.type) {
                            GameVersionType.VANILLA -> "原版"
                            GameVersionType.FORGE -> "Forge"
                            GameVersionType.FABRIC -> "Fabric"
                        },
                        size = if (version.fileSize > 0) FileUtil.formatFileSize(version.fileSize) else "",
                        isInstalled = true,
                        downloadStatus = VersionDownloadStatus.INSTALLED
                    )
                }
            _versionList.value = updatedList + newLocalVersions
        }
    }

    fun selectFilter(filter: VersionTypeFilter) {
        _selectedFilter.value = filter
    }

    fun toggleVersionIsolation(enabled: Boolean) {
        _versionIsolationEnabled.value = enabled
        viewModelScope.launch {
            preferencesManager.setVersionIsolation(enabled)
        }
    }

    /** 下载/安装指定版本 */
    fun downloadVersion(versionId: String, versionType: String = "原版") {
        viewModelScope.launch {
            // 更新状态为下载中
            updateDownloadStatus(versionId, VersionDownloadStatus.DOWNLOADING, 0f)

            val success = when {
                versionType.contains("Fabric", ignoreCase = true) -> {
                    // 先下载原版，再安装 Fabric
                    val baseOk = versionManager.downloadVersion(versionId) { progress ->
                        viewModelScope.launch {
                            updateDownloadStatus(versionId, VersionDownloadStatus.DOWNLOADING, progress * 0.5f)
                        }
                    }
                    if (!baseOk) false
                    else versionManager.installFabric(versionId) { progress ->
                        viewModelScope.launch {
                            updateDownloadStatus(versionId, VersionDownloadStatus.DOWNLOADING, 0.5f + progress * 0.5f)
                        }
                    }
                }
                versionType.contains("Forge", ignoreCase = true) -> {
                    // 先下载原版，再安装 Forge
                    val baseOk = versionManager.downloadVersion(versionId) { progress ->
                        viewModelScope.launch {
                            updateDownloadStatus(versionId, VersionDownloadStatus.DOWNLOADING, progress * 0.5f)
                        }
                    }
                    if (!baseOk) false
                    else versionManager.installForge(versionId) { progress ->
                        viewModelScope.launch {
                            updateDownloadStatus(versionId, VersionDownloadStatus.DOWNLOADING, 0.5f + progress * 0.5f)
                        }
                    }
                }
                else -> {
                    versionManager.downloadVersion(versionId) { progress ->
                        viewModelScope.launch {
                            updateDownloadStatus(versionId, VersionDownloadStatus.DOWNLOADING, progress)
                        }
                    }
                }
            }

            if (success) {
                updateDownloadStatus(versionId, VersionDownloadStatus.INSTALLED, 1f)
            } else {
                updateDownloadStatus(versionId, VersionDownloadStatus.FAILED, 0f)
            }
        }
    }

    private fun updateDownloadStatus(versionId: String, status: VersionDownloadStatus, progress: Float) {
        _versionList.value = _versionList.value.map { item ->
            if (item.versionId == versionId) {
                item.copy(
                    downloadStatus = status,
                    downloadProgress = progress,
                    isInstalled = status == VersionDownloadStatus.INSTALLED
                )
            } else item
        }
    }

    fun getFilteredVersions(): List<GameVersionItem> {
        val all = _versionList.value
        val filter = _selectedFilter.value
        if (filter == VersionTypeFilter.ALL) return all
        return all.filter { it.type.equals(filter.label, ignoreCase = true) }
    }
}

/**
 * 游戏版本管理页面 Composable。
 */
@Composable
fun VersionScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    onVersionClick: (String) -> Unit = {},
    viewModel: VersionViewModel = viewModel()
) {
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val versionIsolationEnabled by viewModel.versionIsolationEnabled.collectAsState()
    val versionList by viewModel.versionList.collectAsState()
    val filteredVersions = viewModel.getFilteredVersions()

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
                text = "游戏版本管理",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredVersions) { item ->
                    VersionCard(
                        item = item,
                        onClick = { onVersionClick(item.versionId) },
                        onDownload = { viewModel.downloadVersion(item.versionId, item.type) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.scanLocalVersions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("扫描本地版本")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "版本隔离",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = versionIsolationEnabled,
                    onCheckedChange = { viewModel.toggleVersionIsolation(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

/**
 * 筛选栏。
 */
@Composable
private fun FilterBar(
    selectedFilter: VersionTypeFilter,
    onFilterSelected: (VersionTypeFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VersionTypeFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

/**
 * 单个版本卡片组件（毛玻璃风格 + 下载功能）。
 */
@Composable
private fun VersionCard(
    item: GameVersionItem,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.versionName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VersionTypeTag(type = item.type)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.size,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Text(
                    text = when (item.downloadStatus) {
                        VersionDownloadStatus.INSTALLED -> "已安装"
                        VersionDownloadStatus.DOWNLOADING -> "下载中"
                        VersionDownloadStatus.FAILED -> "失败"
                        VersionDownloadStatus.NOT_INSTALLED -> "未安装"
                    },
                    color = when (item.downloadStatus) {
                        VersionDownloadStatus.INSTALLED -> Color(0xFF4CAF50)
                        VersionDownloadStatus.DOWNLOADING -> Color(0xFF2196F3)
                        VersionDownloadStatus.FAILED -> Color(0xFFE53935)
                        VersionDownloadStatus.NOT_INSTALLED -> Color(0xFFFF9800)
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            // 下载进度条和按钮
            when (item.downloadStatus) {
                VersionDownloadStatus.DOWNLOADING -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { item.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(item.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                VersionDownloadStatus.NOT_INSTALLED, VersionDownloadStatus.FAILED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (item.downloadStatus == VersionDownloadStatus.FAILED)
                            ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                        else
                            ButtonDefaults.buttonColors()
                    ) {
                        Text(
                            if (item.downloadStatus == VersionDownloadStatus.FAILED) "重新下载" else "下载安装"
                        )
                    }
                }
                VersionDownloadStatus.INSTALLED -> {
                    // 已安装：不显示额外按钮，点击卡片进入版本设置
                }
            }
        }
    }
}

/**
 * 版本类型标签。
 */
@Composable
private fun VersionTypeTag(type: String) {
    val tagColor = when (type.lowercase()) {
        "forge" -> Color(0xFFD81B60)
        "fabric" -> Color(0xFFFFC107)
        else -> Color(0xFF2196F3)
    }

    GlassCard(
        cornerRadius = 8.dp,
        blurRadius = 4.dp,
        backgroundColor = tagColor.copy(alpha = 0.15f),
        borderColor = tagColor.copy(alpha = 0.3f),
        contentPadding = 4.dp,
        enableClickAnimation = false
    ) {
        Text(
            text = type,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = tagColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}