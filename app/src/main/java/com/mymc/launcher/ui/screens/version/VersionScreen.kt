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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.util.FileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val isInstalled: Boolean
)

/**
 * 游戏版本管理页面 ViewModel。
 * 使用 AndroidViewModel 获取 Application 上下文以访问 VersionManager 单例。
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
        // 监听版本隔离开关变化
        viewModelScope.launch {
            preferencesManager.versionIsolationFlow.collect { enabled ->
                _versionIsolationEnabled.value = enabled
            }
        }
        refreshVersions()
    }

    /** 刷新版本列表 */
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
                        isInstalled = version.installed
                    )
                }
            }
        }
    }

    /** 扫描本地版本 */
    fun scanLocalVersions() {
        viewModelScope.launch {
            val localVersions = versionManager.scanLocalVersions()
            // 将扫描到的本地版本合并到列表中
            val existingIds = _versionList.value.map { it.versionId }.toSet()
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
                        isInstalled = version.installed
                    )
                }
            _versionList.value = _versionList.value + newLocalVersions
            // 同时更新已安装状态
            val updatedList = _versionList.value.map { item ->
                val localVersion = localVersions.find { it.versionId == item.versionId }
                if (localVersion != null) item.copy(isInstalled = true) else item
            }
            _versionList.value = updatedList
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

    /** 获取筛选后的版本列表 */
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
    val filteredVersions = viewModel.getFilteredVersions()

    Scaffold(
        bottomBar = {
            BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
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
                text = "游戏版本管理",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 顶部筛选栏
            FilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 版本列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredVersions) { item ->
                    VersionCard(
                        item = item,
                        onClick = { onVersionClick(item.versionId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 扫描本地版本按钮
            Button(
                onClick = { viewModel.scanLocalVersions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("扫描本地版本")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 版本隔离开关
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
 * 单个版本卡片组件。
 */
@Composable
private fun VersionCard(
    item: GameVersionItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                text = if (item.isInstalled) "已安装" else "未安装",
                color = if (item.isInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                fontWeight = FontWeight.Medium
            )
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

    Card(
        colors = CardDefaults.cardColors(containerColor = tagColor.copy(alpha = 0.15f))
    ) {
        Text(
            text = type,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = tagColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}