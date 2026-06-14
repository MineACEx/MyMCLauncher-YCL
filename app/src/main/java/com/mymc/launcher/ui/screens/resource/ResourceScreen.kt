package com.mymc.launcher.ui.screens.resource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mymc.launcher.data.remote.api.CurseForgeApi
import com.mymc.launcher.data.remote.api.ModrinthApi
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.service.version.VersionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 资源分类 Tab 枚举。
 */
enum class ResourceTab(val label: String, val apiCategory: String) {
    MOD("Mod", "mod"),
    SHADER("光影", "shader"),
    TEXTURE("材质", "resourcepack")
}

/**
 * 资源数据模型。
 */
data class ResourceItem(
    val id: String,
    val name: String,
    val author: String,
    val iconUrl: String,
    val downloadCount: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean = true
)

/**
 * 资源中心页面 ViewModel。
 */
class ResourceViewModel : ViewModel() {

    private val _selectedTab = MutableStateFlow(ResourceTab.MOD)
    val selectedTab: StateFlow<ResourceTab> = _selectedTab.asStateFlow()

    private val _resourceList = MutableStateFlow<List<ResourceItem>>(emptyList())
    val resourceList: StateFlow<List<ResourceItem>> = _resourceList.asStateFlow()

    private val _selectedGameVersion = MutableStateFlow("")
    val selectedGameVersion: StateFlow<String> = _selectedGameVersion.asStateFlow()

    private val _selectedLoader = MutableStateFlow("")
    val selectedLoader: StateFlow<String> = _selectedLoader.asStateFlow()

    private val _gameVersions = MutableStateFlow<List<String>>(emptyList())
    val gameVersions: StateFlow<List<String>> = _gameVersions.asStateFlow()

    private val loaders = listOf("全部", "Forge", "Fabric", "Quilt")

    init {
        loadGameVersions()
        searchResources()
    }

    private fun loadGameVersions() {
        viewModelScope.launch {
            _gameVersions.value = VersionManager.getInstalledVersions()
        }
    }

    fun selectTab(tab: ResourceTab) {
        _selectedTab.value = tab
        searchResources()
    }

    fun selectGameVersion(version: String) {
        _selectedGameVersion.value = version
        searchResources()
    }

    fun selectLoader(loader: String) {
        _selectedLoader.value = loader
        searchResources()
    }

    /** 从 Modrinth 和 CurseForge 搜索资源 */
    fun searchResources() {
        viewModelScope.launch {
            val tab = _selectedTab.value
            try {
                val modrinthResults = ModrinthApi.search(
                    category = tab.apiCategory,
                    gameVersion = _selectedGameVersion.value.ifBlank { null }
                )
                val curseforgeResults = CurseForgeApi.search(
                    category = tab.apiCategory,
                    gameVersion = _selectedGameVersion.value.ifBlank { null }
                )
                val merged = mergeResults(modrinthResults, curseforgeResults)
                _resourceList.value = merged
            } catch (e: Exception) {
                // 搜索失败保持现有列表
            }
        }
    }

    /** 合并两个 API 的搜索结果，去重 */
    private fun mergeResults(
        modrinth: List<ResourceItem>,
        curseforge: List<ResourceItem>
    ): List<ResourceItem> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<ResourceItem>()
        for (item in modrinth + curseforge) {
            if (seen.add(item.id)) {
                merged.add(item)
            }
        }
        return merged
    }

    fun toggleResourceEnabled(resourceId: String) {
        val updated = _resourceList.value.map {
            if (it.id == resourceId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _resourceList.value = updated
    }

    fun deleteResource(resourceId: String) {
        _resourceList.value = _resourceList.value.filter { it.id != resourceId }
    }
}

/**
 * 资源中心页面 Composable。
 * 顶部 Tab 切换 Mod / 光影 / 材质，筛选行，资源网格列表。
 *
 * @param onNavigate       全局导航回调
 * @param currentRoute     当前路由
 * @param onResourceClick  资源详情点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    onResourceClick: (String) -> Unit = {},
    viewModel: ResourceViewModel = remember { ResourceViewModel() }
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val resourceList by viewModel.resourceList.collectAsState()
    val selectedGameVersion by viewModel.selectedGameVersion.collectAsState()
    val selectedLoader by viewModel.selectedLoader.collectAsState()
    val gameVersions by viewModel.gameVersions.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部 Tab 栏
            ScrollableTabRow(
                selectedTabIndex = ResourceTab.entries.indexOf(selectedTab),
                modifier = Modifier.fillMaxWidth()
            ) {
                ResourceTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }

            // 筛选器行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 游戏版本下拉
                FilterDropdown(
                    label = "游戏版本",
                    selectedValue = selectedGameVersion.ifBlank { "全部" },
                    options = listOf("全部") + gameVersions,
                    onOptionSelected = { viewModel.selectGameVersion(if (it == "全部") "" else it) },
                    modifier = Modifier.weight(1f)
                )

                // 加载器类型下拉
                FilterDropdown(
                    label = "加载器",
                    selectedValue = selectedLoader.ifBlank { "全部" },
                    options = listOf("全部", "Forge", "Fabric", "Quilt"),
                    onOptionSelected = { viewModel.selectLoader(if (it == "全部") "" else it) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 资源网格列表
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(resourceList) { item ->
                    ResourceCard(
                        item = item,
                        onClick = { onResourceClick(item.id) },
                        onToggleEnabled = { viewModel.toggleResourceEnabled(item.id) },
                        onDelete = { viewModel.deleteResource(item.id) }
                    )
                }
            }
        }
    }
}

/**
 * 筛选器下拉组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = modifier.menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 单个资源卡片组件。
 */
@Composable
private fun ResourceCard(
    item: ResourceItem,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
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
        Column(modifier = Modifier.padding(8.dp)) {
            // 资源图标
            AsyncImage(
                model = item.iconUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 名称
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 作者
            Text(
                text = item.author,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 下载量
            Text(
                text = "下载: ${item.downloadCount}",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 本地已安装资源的控制按钮
            if (item.isInstalled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (item.isEnabled) "启用" else "禁用",
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = item.isEnabled,
                            onCheckedChange = { onToggleEnabled() }
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = Color(0xFFE53935)
                        )
                    }
                }
            }
        }
    }
}