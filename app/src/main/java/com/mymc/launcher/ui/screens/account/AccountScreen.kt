package com.mymc.launcher.ui.screens.account

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymc.launcher.service.account.AccountManager
import com.mymc.launcher.ui.components.BottomNavBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 当前登录账号信息数据模型。
 */
data class CurrentAccountInfo(
    val username: String = "",
    val uuid: String = "",
    val type: String = ""
)

/**
 * 账号管理页面 ViewModel。
 */
class AccountViewModel : ViewModel() {

    // 离线账号输入
    private val _offlineUsername = MutableStateFlow("")
    val offlineUsername: StateFlow<String> = _offlineUsername.asStateFlow()

    // 微软 OAuth 状态
    private val _microsoftDeviceCode = MutableStateFlow("")
    val microsoftDeviceCode: StateFlow<String> = _microsoftDeviceCode.asStateFlow()

    private val _microsoftVerifyUrl = MutableStateFlow("")
    val microsoftVerifyUrl: StateFlow<String> = _microsoftVerifyUrl.asStateFlow()

    private val _microsoftLoginMessage = MutableStateFlow("")
    val microsoftLoginMessage: StateFlow<String> = _microsoftLoginMessage.asStateFlow()

    // LittleSkin 账号输入
    private val _littleSkinUsername = MutableStateFlow("")
    val littleSkinUsername: StateFlow<String> = _littleSkinUsername.asStateFlow()

    private val _littleSkinPassword = MutableStateFlow("")
    val littleSkinPassword: StateFlow<String> = _littleSkinPassword.asStateFlow()

    private val _littleSkinLoginMessage = MutableStateFlow("")
    val littleSkinLoginMessage: StateFlow<String> = _littleSkinLoginMessage.asStateFlow()

    // 当前登录账号信息
    private val _currentAccount = MutableStateFlow(CurrentAccountInfo())
    val currentAccount: StateFlow<CurrentAccountInfo> = _currentAccount.asStateFlow()

    init {
        loadCurrentAccount()
    }

    /** 加载当前登录账号信息 */
    private fun loadCurrentAccount() {
        viewModelScope.launch {
            val account = AccountManager.getCurrentAccount()
            _currentAccount.value = if (account != null) {
                CurrentAccountInfo(
                    username = account.username,
                    uuid = account.uuid,
                    type = account.type
                )
            } else {
                CurrentAccountInfo()
            }
        }
    }

    fun updateOfflineUsername(name: String) {
        _offlineUsername.value = name
    }

    /** 离线账号登录 */
    fun loginOffline() {
        viewModelScope.launch {
            val name = _offlineUsername.value.trim()
            if (name.isBlank()) return@launch
            AccountManager.loginOffline(name)
            loadCurrentAccount()
        }
    }

    /** 微软账号 OAuth 设备码流程 */
    fun startMicrosoftLogin() {
        viewModelScope.launch {
            val result = AccountManager.startMicrosoftOAuth()
            if (result != null) {
                _microsoftDeviceCode.value = result.deviceCode
                _microsoftVerifyUrl.value = result.verificationUri
                _microsoftLoginMessage.value = "请在浏览器中打开以下链接并输入设备代码完成验证"
            }
        }
    }

    fun updateLittleSkinUsername(name: String) {
        _littleSkinUsername.value = name
    }

    fun updateLittleSkinPassword(password: String) {
        _littleSkinPassword.value = password
    }

    /** LittleSkin 账号登录 */
    fun loginLittleSkin() {
        viewModelScope.launch {
            val name = _littleSkinUsername.value.trim()
            val password = _littleSkinPassword.value.trim()
            if (name.isBlank() || password.isBlank()) {
                _littleSkinLoginMessage.value = "用户名和密码不能为空"
                return@launch
            }
            val success = AccountManager.loginLittleSkin(name, password)
            if (success) {
                _littleSkinLoginMessage.value = "LittleSkin 登录成功"
                loadCurrentAccount()
            } else {
                _littleSkinLoginMessage.value = "LittleSkin 登录失败，请检查用户名和密码"
            }
        }
    }

    /** 退出登录 */
    fun logout() {
        viewModelScope.launch {
            AccountManager.logout()
            _currentAccount.value = CurrentAccountInfo()
        }
    }
}

/**
 * 账号管理页面 Composable。
 * 三个登录方式卡片（离线、微软、LittleSkin）+ 当前账号信息。
 *
 * @param onNavigate    全局导航回调
 * @param currentRoute  当前路由
 */
@Composable
fun AccountScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: AccountViewModel = remember { AccountViewModel() }
) {
    val offlineUsername by viewModel.offlineUsername.collectAsState()
    val microsoftDeviceCode by viewModel.microsoftDeviceCode.collectAsState()
    val microsoftVerifyUrl by viewModel.microsoftVerifyUrl.collectAsState()
    val microsoftLoginMessage by viewModel.microsoftLoginMessage.collectAsState()
    val littleSkinUsername by viewModel.littleSkinUsername.collectAsState()
    val littleSkinPassword by viewModel.littleSkinPassword.collectAsState()
    val littleSkinLoginMessage by viewModel.littleSkinLoginMessage.collectAsState()
    val currentAccount by viewModel.currentAccount.collectAsState()

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
                text = "账号管理",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 三个登录方式卡片（横向排列适配横屏）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 离线账号卡片
                OfflineLoginCard(
                    username = offlineUsername,
                    onUsernameChange = { viewModel.updateOfflineUsername(it) },
                    onLogin = { viewModel.loginOffline() },
                    modifier = Modifier.weight(1f)
                )

                // 微软账号卡片
                MicrosoftLoginCard(
                    deviceCode = microsoftDeviceCode,
                    verifyUrl = microsoftVerifyUrl,
                    loginMessage = microsoftLoginMessage,
                    onLogin = { viewModel.startMicrosoftLogin() },
                    modifier = Modifier.weight(1f)
                )

                // LittleSkin 账号卡片
                LittleSkinLoginCard(
                    username = littleSkinUsername,
                    password = littleSkinPassword,
                    loginMessage = littleSkinLoginMessage,
                    onUsernameChange = { viewModel.updateLittleSkinUsername(it) },
                    onPasswordChange = { viewModel.updateLittleSkinPassword(it) },
                    onLogin = { viewModel.loginLittleSkin() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // 当前登录账号信息
            Text(
                text = "当前登录账号",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (currentAccount.username.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AccountInfoRow(label = "用户名", value = currentAccount.username)
                        AccountInfoRow(label = "UUID", value = currentAccount.uuid)
                        AccountInfoRow(label = "类型", value = currentAccount.type)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("退出登录")
                }
            } else {
                Text(
                    text = "暂无登录账号",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 离线账号登录卡片。
 */
@Composable
private fun OfflineLoginCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "离线账号",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("登录")
            }
        }
    }
}

/**
 * 微软账号 OAuth 登录卡片。
 */
@Composable
private fun MicrosoftLoginCard(
    deviceCode: String,
    verifyUrl: String,
    loginMessage: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "微软账号",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("登录")
            }

            if (deviceCode.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = loginMessage,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "设备代码: $deviceCode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = verifyUrl,
                    fontSize = 11.sp,
                    color = Color(0xFF2196F3),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * LittleSkin 外置登录卡片。
 */
@Composable
private fun LittleSkinLoginCard(
    username: String,
    password: String,
    loginMessage: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LittleSkin",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (loginMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = loginMessage,
                    fontSize = 12.sp,
                    color = if (loginMessage.contains("成功")) Color(0xFF4CAF50) else Color(0xFFE53935),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("登录")
            }
        }
    }
}

/**
 * 账号信息行组件。
 */
@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium
        )
    }
}