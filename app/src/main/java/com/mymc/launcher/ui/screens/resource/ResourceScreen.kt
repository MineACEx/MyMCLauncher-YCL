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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import com.mymc.launcher.ui.components.FadeInContent
import com.mymc.launcher.ui.components.GlassCard
import com.mymc.launcher.ui.components.scaleOnClick
import com.mymc.launcher.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

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
 * 资源下载状态。
 */
enum class ResourceDownloadStatus {
    NOT_STARTED,
    DOWNLOADING,
    COMPLETED,
    FAILED
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
    val iconUrl: String = "",
    val downloadStatus: ResourceDownloadStatus = ResourceDownloadStatus.NOT_STARTED,
    val downloadProgress: Float = 0f
)

/**
 * 资源下载页面 ViewModel。
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
                        val body = response.body()
                        if (body != null) {
                            _resourceList.value = body.hits.map { hit ->
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
                    }

                    ResourceTypeFilter.MODPACK -> {
                        val response = RetrofitClient.modrinthApi.search(
                            query = "",
                            facets = """[["versions:$version"],["project_type:modpack"]]""",
                            limit = 20
                        )
                        val body = response.body()
                        if (body != null) {
                            _resourceList.value = body.hits.map { hit ->
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
                        val body = response.body()
                        if (body != null) {
                            _resourceList.value = body.hits.map { hit ->
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
                    }

                    ResourceTypeFilter.ALL -> {
                        val response = RetrofitClient.modrinthApi.search(
                            query = "",
                            facets = """[["versions:$version"]]""",
                            limit = 20
                        )
                        val body = response.body()
                        if (body != null) {
                            _resourceList.value = body.hits.map { hit ->
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
                }
            } catch (e: Exception) {
                _resourceList.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 下载指定资源 */
    fun downloadResource(resource: ResourceItem) {
        viewModelScope.launch {
            updateResourceStatus(resource.id, ResourceDownloadStatus.DOWNLOADING, 0f)
            try {
                val downloadUrl = fetchDownloadUrl(resource.id)
                if (downloadUrl == null) {
                    updateResourceStatus(resource.id, ResourceDownloadStatus.FAILED, 0f)
                    return@launch
                }

                val app = getApplication<Application>()
                val downloadsDir = File(app.filesDir, "downloads")
                downloadsDir.mkdirs()
                val fileName = "${resource.name}.jar"
                val targetFile = File(downloadsDir, fileName)

                val success = withContext(Dispatchers.IO) {
                    FileUtil.downloadWithResume(
                        downloadUrl = downloadUrl,
                        targetFile = targetFile,
                        onProgress = { downloaded, total ->
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            viewModelScope.launch {
                                updateResourceStatus(resource.id, ResourceDownloadStatus.DOWNLOADING, progress)
                            }
                        }
                    )
                }

                if (success) {
                    updateResourceStatus(resource.id, ResourceDownloadStatus.COMPLETED, 1f)
                } else {
                    updateResourceStatus(resource.id, ResourceDownloadStatus.FAILED, 0f)
                }
            } catch (e: Exception) {
                updateResourceStatus(resource.id, ResourceDownloadStatus.FAILED, 0f)
            }
        }
    }

    /** 从 Modrinth API 获取项目最新版本的下载 URL */
    private suspend fun fetchDownloadUrl(projectId: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.modrinthApi.getProjectVersions(projectId)
            if (response.isSuccessful && response.body() != null) {
                val versions = response.body()!!
                if (versions.isNotEmpty()) {
                    val latestVersion = versions.first()
                    if (latestVersion.files.isNotEmpty()) {
                        return@withContext latestVersion.files.first().url
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun updateResourceStatus(id: String, status: ResourceDownloadStatus, progress: Float) {
        _resourceList.value = _resourceList.value.map { item ->
            if (item.id == id) item.copy(downloadStatus = status, downloadProgress = progress) else item
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
        FadeInContent {
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

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredResources) { resource ->
                    ResourceCard(
                        resource = resource,
                        onDownload = { viewModel.downloadResource(resource) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

/**
 * 单个资源卡片组件（毛玻璃风格 + 下载面板）。
 */
@Composable
private fun ResourceCard(
    resource: ResourceItem,
    onDownload: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 下载面板
            when (resource.downloadStatus) {
                ResourceDownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { resource.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(resource.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ResourceDownloadStatus.COMPLETED -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("下载完成 ✓")
                    }
                }
                ResourceDownloadStatus.FAILED -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("重新下载")
                    }
                }
                ResourceDownloadStatus.NOT_STARTED -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("下载")
                    }
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