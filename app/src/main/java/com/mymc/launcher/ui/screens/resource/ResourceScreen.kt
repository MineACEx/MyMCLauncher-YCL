package com.mymc.launcher.ui.screens.resource

import android.app.Application
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.mymc.launcher.data.remote.RetrofitClient
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.ui.components.BottomNavBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 资源类型枚举，用于筛选栏。
 */
enum class ResourceTypeFilter(val label: String) {
    ALL("全部"),
    MOD("Mod"),
    RESOURCE_PACK("材质包"),
    SHADER("光影"),
    MODPACK("整合包"),
    WORLD("地图")
}

/**
 * 资源数据模型。
 */
data class ResourceItem(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val downloads: String,
    val iconUrl: String = ""
)

/**
 * 资源下载页面 ViewModel。
 * 使用 AndroidViewModel 获取 Application 上下文以访问服务。
 */
class ResourceViewModel(application: Application) : AndroidViewModel(application) {

    private val versionManager = VersionManager.getInstance(application)

    private val _resourceList = MutableStateFlow<List<ResourceItem>>(emptyList())
    val resourceList: StateFlow<List<ResourceItem>> = _resourceList.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ResourceTypeFilter.ALL)
    val selectedFilter: StateFlow<ResourceTypeFilter> = _selectedFilter.asStateFlow()

    private val _gameVersions = MutableStateFlow<List<String>>(emptyList())
    val gameVersions: StateFlow<List<String>> = _gameVersions.asStateFlow()

    private val _selectedVersion = MutableStateFlow("")
    val selectedVersion: StateFlow<String> = _selectedVersion.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadGameVersions()
    }

    private fun loadGameVersions() {
        viewModelScope.launch {
            val versions = versionManager.scanLocalVersions().map { it.versionId }
            _gameVersions.value = versions
            if (versions.isNotEmpty() && _selectedVersion.value.isEmpty()) {
                _selectedVersion.value = versions.first()
            }
        }
    }

    fun selectFilter(filter: ResourceTypeFilter) {
        _selectedFilter.value = filter
    }

    fun selectVersion(version: String) {
        _selectedVersion.value = version
        searchResources()
    }

    fun searchResources() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val filter = _selectedFilter.value
                val version = _selectedVersion.value

                when (filter) {
                    ResourceTypeFilter.MOD -> {
                        val response = RetrofitClient.modrinthApi.search(
                            query = "",
                            facets = """[["versions:$version"],["project_type:mod"]]""",
                            limit = 20
                        )
                        _resourceList.value = response.hits.map { hit ->
                            ResourceItem(
                                id = hit.projectId,
                                name = hit.title,
                                description = hit.description,
                                type = "Mod",
                                downloads = "${hit.downloads}",
                                iconUrl = hit.iconUrl ?: ""
                            )
                        }
                    }

                    ResourceTypeFilter.MODPACK -> {
                        val response = RetrofitClient.modrinthApi.search(
                            query = "",
                            facets = """[["versions:$version"],["project_type:modpack"]]""",
                            limit = 20
                        )
                        _resourceList.value = response.hits.map { hit ->
                            ResourceItem(
                                id = hit.projectId,
                                name = hit.title,
                                description = hit.description,
                                type = "整合包",
                                downloads = "${hit.downloads}",
                                iconUrl = hit.iconUrl ?: ""
                            )
                        }
                    }

                    ResourceTypeFilter.RESOURCE_PACK,
                    ResourceTypeFilter.SHADER -> {
                        val curseForgeType = when (filter) {
                            ResourceTypeFilter.RESOURCE_PACK -> "resourcepack"
                            ResourceTypeFilter.SHADER -> "shader"
                            else -> "mod"
                        }
                        val response = RetrofitClient.curseForgeApi.searchMods(
                            searchFilter = curseForgeType,
                            index = 0,
                            pageSize = 20
                        )
                        if (response.isSuccessful && response.body() != null) {
                            _resourceList.value = response.body()!!.data.map { mod ->
                                val fileSize = mod.latestFiles.firstOrNull()?.fileLength ?: 0L
                                ResourceItem(
                                    id = "${mod.id}",
                                    name = mod.name,
                                    description = mod.summary,
                                    type = filter.label,
                                    downloads = if (fileSize > 0) "${fileSize / 1024} KB" else "未知",
                                    iconUrl = mod.logo?.url ?: ""
                                )
                            }
                        }
                    }

                    ResourceTypeFilter.WORLD -> {
                        val response = RetrofitClient.modrinthApi.search(
                            query = "",
                            facets = """[["versions:$version"],["categories:world"]]""",
                            limit = 20
                        )
                        _resourceList.value = response.hits.map { hit ->
                            ResourceItem(
                                id = hit.projectId,
                                name = hit.title,
                                description = hit.description,
                                type = "地图",
                                downloads = "${hit.downloads}",
                                iconUrl = hit.iconUrl ?: ""
                            )
                        }
                    }

                    ResourceTypeFilter.ALL -> {
                        val response = RetrofitClient.modrinthApi.search(
                            query = "",
                            facets = """[["versions:$version"]]""",
                            limit = 20
                        )
                        _resourceList.value = response.hits.map { hit ->
                            ResourceItem(
                                id = hit.projectId,
                                name = hit.title,
                                description = hit.description,
                                type = "通用",
                                downloads = "${hit.downloads}",
                                iconUrl = hit.iconUrl ?: ""
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _resourceList.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getFilteredResources(): List<ResourceItem> {
        val all = _resourceList.value
        val filter = _selectedFilter.value
        if (filter == ResourceTypeFilter.ALL) return all
        return all.filter { it.type.equals(filter.label, ignoreCase = true) }
    }
}

/**
 * 资源下载页面 Composable。
 */
@Composable
fun ResourceScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: ResourceViewModel = viewModel()
) {
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val gameVersions by viewModel.gameVersions.collectAsState()
    val selectedVersion by viewModel.selectedVersion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val filteredResources = viewModel.getFilteredResources()

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
                text = "资源下载",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 资源类型筛选栏
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ResourceTypeFilter.entries.toList()) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.selectFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 版本选择
            if (gameVersions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gameVersions) { version ->
                        FilterChip(
                            selected = selectedVersion == version,
                            onClick = { viewModel.selectVersion(version) },
                            label = { Text(version) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 资源列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredResources) { resource ->
                    ResourceCard(resource = resource)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 单个资源卡片组件。
 */
@Composable
private fun ResourceCard(resource: ResourceItem) {
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
                    text = resource.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                ResourceTypeTag(type = resource.type)
            }

            if (resource.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resource.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "下载量: ${resource.downloads}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Button(
                    onClick = { /* TODO: 下载资源 */ }
                ) {
                    Text("下载")
                }
            }
        }
    }
}

/**
 * 资源类型标签。
 */
@Composable
private fun ResourceTypeTag(type: String) {
    val tagColor = when (type.lowercase()) {
        "mod" -> Color(0xFFD81B60)
        "材质包" -> Color(0xFF4CAF50)
        "光影" -> Color(0xFFFFC107)
        "整合包" -> Color(0xFF2196F3)
        "地图" -> Color(0xFF9C27B0)
        else -> Color(0xFF607D8B)
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