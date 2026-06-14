package com.mymc.launcher.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymc.launcher.service.account.AccountManager
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.ui.navigation.Screen
import com.mymc.launcher.service.version.VersionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主页 ViewModel —— 管理版本列表、账号列表、内存设置等状态。
 */
class HomeViewModel : ViewModel() {

    /** 已安装版本列表 */
    private val _installedVersions = MutableStateFlow<List<String>>(emptyList())
    val installedVersions: StateFlow<List<String>> = _installedVersions.asStateFlow()

    /** 已登录账号列表 */
    private val _loggedInAccounts = MutableStateFlow<List<String>>(emptyList())
    val loggedInAccounts: StateFlow<List<String>> = _loggedInAccounts.asStateFlow()

    /** 当前选中的版本 */
    private val _selectedVersion = MutableStateFlow("")
    val selectedVersion: StateFlow<String> = _selectedVersion.asStateFlow()

    /** 当前选中的账号 */
    private val _selectedAccount = MutableStateFlow("")
    val selectedAccount: StateFlow<String> = _selectedAccount.asStateFlow()

    /** 内存分配大小 (MB) */
    private val _memoryMb = MutableStateFlow(2048f)
    val memoryMb: StateFlow<Float> = _memoryMb.asStateFlow()

    init {
        refreshData()
    }

    /** 刷新版本与账号数据 */
    fun refreshData() {
        viewModelScope.launch {
            _installedVersions.value = VersionManager.getInstalledVersions()
            _loggedInAccounts.value = AccountManager.getLoggedInAccounts()
        }
    }

    fun selectVersion(version: String) {
        _selectedVersion.value = version
    }

    fun selectAccount(account: String) {
        _selectedAccount.value = account
    }

    fun updateMemory(memory: Float) {
        _memoryMb.value = memory
    }
}

/**
 * 主界面 Composable。
 * 顶部标题、启动游戏按钮、版本/账号选择器、内存滑块、底部导航栏。
 *
 * @param onNavigate      全局导航回调，用于页面间跳转
 * @param currentRoute    当前路由，供底部导航栏高亮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: HomeViewModel = remember { HomeViewModel() }
) {
    val installedVersions by viewModel.installedVersions.collectAsState()
    val loggedInAccounts by viewModel.loggedInAccounts.collectAsState()
    val selectedVersion by viewModel.selectedVersion.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val memoryMb by viewModel.memoryMb.collectAsState()

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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // 顶部标题
            Text(
                text = "YCL启动器",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 启动游戏按钮
            Button(
                onClick = {
                    val version = selectedVersion
                    if (version.isNotBlank()) {
                        onNavigate(Screen.LaunchGame.createRoute(version))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "启动游戏",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 版本选择器
            VersionSelector(
                versions = installedVersions,
                selectedVersion = selectedVersion,
                onVersionSelected = { viewModel.selectVersion(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 账号选择器
            AccountSelector(
                accounts = loggedInAccounts,
                selectedAccount = selectedAccount,
                onAccountSelected = { viewModel.selectAccount(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 内存滑块
            MemorySlider(
                memoryMb = memoryMb,
                onMemoryChanged = { viewModel.updateMemory(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 版本下拉选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSelector(
    versions: List<String>,
    selectedVersion: String,
    onVersionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedVersion.ifBlank { "选择已安装版本" },
            onValueChange = {},
            readOnly = true,
            label = { Text("游戏版本") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version) },
                    onClick = {
                        onVersionSelected(version)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 账号下拉选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<String>,
    selectedAccount: String,
    onAccountSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedAccount.ifBlank { "选择已登录账号" },
            onValueChange = {},
            readOnly = true,
            label = { Text("账号") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account) },
                    onClick = {
                        onAccountSelected(account)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 内存分配滑块组件。
 * 范围 512MB - 8192MB，默认 2048MB。
 */
@Composable
private fun MemorySlider(
    memoryMb: Float,
    onMemoryChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "内存分配",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${memoryMb.toInt()} MB",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = memoryMb,
            onValueChange = onMemoryChanged,
            valueRange = 512f..8192f,
            steps = 15,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "512 MB", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = "8192 MB", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}