package com.mymc.launcher.ui.screens.account

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymc.launcher.domain.model.AccountType
import com.mymc.launcher.service.account.AccountManager
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.ui.components.FadeInContent
import com.mymc.launcher.ui.components.GlassCard
import com.mymc.launcher.ui.components.scaleOnClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CurrentAccountInfo(
    val username: String = "",
    val uuid: String = "",
    val type: String = ""
)

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val accountManager = AccountManager.getInstance(application)

    private val _offlineUsername = MutableStateFlow("")
    val offlineUsername: StateFlow<String> = _offlineUsername.asStateFlow()

    private val _microsoftDeviceCode = MutableStateFlow("")
    val microsoftDeviceCode: StateFlow<String> = _microsoftDeviceCode.asStateFlow()

    private val _microsoftVerifyUrl = MutableStateFlow("")
    val microsoftVerifyUrl: StateFlow<String> = _microsoftVerifyUrl.asStateFlow()

    private val _microsoftLoginMessage = MutableStateFlow("")
    val microsoftLoginMessage: StateFlow<String> = _microsoftLoginMessage.asStateFlow()

    private val _littleSkinUsername = MutableStateFlow("")
    val littleSkinUsername: StateFlow<String> = _littleSkinUsername.asStateFlow()

    private val _littleSkinPassword = MutableStateFlow("")
    val littleSkinPassword: StateFlow<String> = _littleSkinPassword.asStateFlow()

    private val _littleSkinLoginMessage = MutableStateFlow("")
    val littleSkinLoginMessage: StateFlow<String> = _littleSkinLoginMessage.asStateFlow()

    private val _currentAccount = MutableStateFlow(CurrentAccountInfo())
    val currentAccount: StateFlow<CurrentAccountInfo> = _currentAccount.asStateFlow()

    init {
        loadCurrentAccount()
    }

    private fun loadCurrentAccount() {
        viewModelScope.launch {
            accountManager.currentAccount.collect { account ->
                _currentAccount.value = if (account != null) {
                    CurrentAccountInfo(
                        username = account.username,
                        uuid = account.uuid,
                        type = account.accountType.name
                    )
                } else {
                    CurrentAccountInfo()
                }
            }
        }
    }

    fun updateOfflineUsername(name: String) {
        _offlineUsername.value = name
    }

    fun loginOffline() {
        viewModelScope.launch {
            val name = _offlineUsername.value.trim()
            if (name.isBlank()) return@launch
            accountManager.login(
                type = AccountType.OFFLINE,
                credentials = mapOf("username" to name)
            ) { _, _ -> }
        }
    }

    fun startMicrosoftLogin() {
        viewModelScope.launch {
            _microsoftLoginMessage.value = "正在请求设备码..."
            accountManager.login(
                type = AccountType.MICROSOFT,
                credentials = emptyMap()
            ) { account, errorMsg ->
                if (account != null) {
                    _microsoftLoginMessage.value = "登录成功: ${account.username}"
                    _microsoftDeviceCode.value = ""
                    _microsoftVerifyUrl.value = ""
                } else if (errorMsg != null) {
                    _microsoftLoginMessage.value = errorMsg
                    parseDeviceCodeFromMessage(errorMsg)
                }
            }
        }
    }

    private fun parseDeviceCodeFromMessage(message: String) {
        val urlRegex = Regex("""(https?://[^\s]+)""")
        val codeRegex = Regex("""验证码\s*([A-Z0-9]+)""")

        urlRegex.find(message)?.value?.let { url ->
            _microsoftVerifyUrl.value = url
        }
        codeRegex.find(message)?.value?.let { fullMatch ->
            _microsoftDeviceCode.value = fullMatch.removePrefix("验证码").trim()
        } ?: run {
            codeRegex.find(message)?.groupValues?.getOrNull(1)?.let { code ->
                _microsoftDeviceCode.value = code
            }
        }
    }

    fun updateLittleSkinUsername(name: String) {
        _littleSkinUsername.value = name
    }

    fun updateLittleSkinPassword(password: String) {
        _littleSkinPassword.value = password
    }

    fun loginLittleSkin() {
        viewModelScope.launch {
            val name = _littleSkinUsername.value.trim()
            val password = _littleSkinPassword.value.trim()
            if (name.isBlank() || password.isBlank()) {
                _littleSkinLoginMessage.value = "用户名和密码不能为空"
                return@launch
            }
            accountManager.login(
                type = AccountType.LITTLESKIN,
                credentials = mapOf("username" to name, "password" to password)
            ) { account, errorMsg ->
                if (account != null) {
                    _littleSkinLoginMessage.value = "LittleSkin 登录成功"
                } else {
                    _littleSkinLoginMessage.value = errorMsg ?: "LittleSkin 登录失败"
                }
            }
        }
    }

    fun logout() {
        accountManager.logout()
        _currentAccount.value = CurrentAccountInfo()
    }
}

@Composable
fun AccountScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: AccountViewModel = viewModel()
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
        FadeInContent {
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

            // 三个登录方式卡片（竖屏纵向排列）
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 离线账号卡片
                OfflineLoginCard(
                    username = offlineUsername,
                    onUsernameChange = { viewModel.updateOfflineUsername(it) },
                    onLogin = { viewModel.loginOffline() }
                )

                // 微软账号卡片
                MicrosoftLoginCard(
                    deviceCode = microsoftDeviceCode,
                    verifyUrl = microsoftVerifyUrl,
                    loginMessage = microsoftLoginMessage,
                    onLogin = { viewModel.startMicrosoftLogin() }
                )

                // LittleSkin 账号卡片
                LittleSkinLoginCard(
                    username = littleSkinUsername,
                    password = littleSkinPassword,
                    loginMessage = littleSkinLoginMessage,
                    onUsernameChange = { viewModel.updateLittleSkinUsername(it) },
                    onPasswordChange = { viewModel.updateLittleSkinPassword(it) },
                    onLogin = { viewModel.loginLittleSkin() }
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
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                    modifier = Modifier.fillMaxWidth().scaleOnClick()
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
}

@Composable
private fun OfflineLoginCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth().scaleOnClick()
            ) {
                Text("登录")
            }
        }
    }
}

@Composable
private fun MicrosoftLoginCard(
    deviceCode: String,
    verifyUrl: String,
    loginMessage: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth().scaleOnClick()
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
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth().scaleOnClick()
            ) {
                Text("登录")
            }
        }
    }
}

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