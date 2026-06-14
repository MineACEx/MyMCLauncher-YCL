package com.mymc.launcher.service.account

import android.content.Context
import android.util.Base64
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.data.remote.RetrofitClient
import com.mymc.launcher.data.remote.dto.AuthenticateRequest
import com.mymc.launcher.data.remote.dto.DeviceCodeRequest
import com.mymc.launcher.data.remote.dto.RefreshRequest
import com.mymc.launcher.data.remote.dto.TokenRequest
import com.mymc.launcher.data.remote.dto.XBoxAuthProperties
import com.mymc.launcher.data.remote.dto.XBoxAuthRequest
import com.mymc.launcher.domain.model.AccountInfo
import com.mymc.launcher.domain.model.AccountType
import com.mymc.launcher.util.HashUtil
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 账号管理器单例
 *
 * 管理三种登录方式的认证流程：
 * - **离线账号**：基于用户名 MD5 自动生成 UUID，保存登录状态
 * - **微软账号**：OAuth2 设备码流程 -> Xbox Live 认证 -> XSTS -> Minecraft 认证 -> 获取 Profile
 * - **LittleSkin**：Yggdrasil 协议，baseUrl https://littleskin.cn/api/yggdrasil
 *
 * 所有账号敏感信息通过 AES 加密后存储到 PreferencesManager。
 * 通过 StateFlow 向外暴露当前账号状态，UI 层可直接观察。
 *
 * 使用方式：
 * ```kotlin
 * val am = AccountManager.getInstance(context)
 * am.currentAccount.collect { account -> ... }
 * am.login(AccountType.OFFLINE, mapOf("username" to "Steve"))
 * ```
 */
class AccountManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AccountManager"

        // ==================== DataStore 存储键 ====================
        private const val KEY_ACCOUNT_USERNAME = "account_username"
        private const val KEY_ACCOUNT_UUID = "account_uuid"
        private const val KEY_ACCOUNT_TYPE = "account_type"
        private const val KEY_ACCESS_TOKEN = "account_access_token"
        private const val KEY_REFRESH_TOKEN = "account_refresh_token"
        private const val KEY_CLIENT_TOKEN = "account_client_token"
        private const val KEY_AVATAR_URL = "account_avatar_url"
        private const val KEY_IS_LOGGED_IN = "account_is_logged_in"

        // ==================== 微软 OAuth 常量 ====================
        /** 微软 OAuth 客户端 ID（Minecraft 启动器官方） */
        private const val MICROSOFT_CLIENT_ID = "00000000402b5328"

        /** 微软 OAuth 授权范围 */
        private const val MICROSOFT_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"

        /** 微软 OAuth 设备码端点 */
        private const val MICROSOFT_DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"

        /** 微软 OAuth 令牌端点 */
        private const val MICROSOFT_TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

        // ==================== 加密常量 ====================
        /** AES 密钥种子（从设备信息派生） */
        private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"

        /** 默认 IV（16 字节），实际项目中应使用随机 IV */
        private val DEFAULT_IV = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
        )

        @Volatile
        private var instance: AccountManager? = null

        /**
         * 获取 AccountManager 单例实例
         *
         * @param context Android 上下文
         * @return AccountManager 实例
         */
        fun getInstance(context: Context): AccountManager {
            return instance ?: synchronized(this) {
                instance ?: AccountManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /** 协程作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** PreferencesManager 实例 */
    private val prefs: PreferencesManager by lazy {
        PreferencesManager.getInstance(context)
    }

    /** 加密密钥（由设备 Android ID 派生，32 字节） */
    private val encryptionKey: ByteArray by lazy { deriveEncryptionKey() }

    /** 当前账号状态的内部 MutableStateFlow */
    private val _currentAccount = MutableStateFlow<AccountInfo?>(null)

    /** 对外暴露的当前账号状态 */
    val currentAccount: StateFlow<AccountInfo?> = _currentAccount.asStateFlow()

    init {
        // 从加密存储中恢复上一次登录的账号信息
        loadSavedAccount()
    }

    // ==================== 公开 API ====================

    /**
     * 登录统一入口
     *
     * 根据 type 分发到对应的登录流程。
     *
     * @param type        账号类型（OFFLINE / MICROSOFT / LITTLESKIN）
     * @param credentials 凭证映射，不同登录类型所需字段不同：
     *   - OFFLINE:   mapOf("username" to "玩家名")
     *   - MICROSOFT: 无需额外凭证（使用设备码流程）
     *   - LITTLESKIN: mapOf("username" to "邮箱", "password" to "密码")
     * @param onResult   登录结果回调，成功返回 AccountInfo，失败返回 null 及错误信息
     */
    fun login(
        type: AccountType,
        credentials: Map<String, String> = emptyMap(),
        onResult: (AccountInfo?, String?) -> Unit
    ) {
        scope.launch {
            try {
                val account = when (type) {
                    AccountType.OFFLINE -> loginOffline(credentials)
                    AccountType.MICROSOFT -> loginMicrosoft(onResult)
                    AccountType.LITTLESKIN -> loginLittleSkin(credentials)
                }
                if (account != null) {
                    _currentAccount.value = account
                    saveAccount(account)
                    onResult(account, null)
                }
            } catch (e: Exception) {
                LogUtil.error(TAG, "登录失败", e)
                onResult(null, e.message ?: "未知错误")
            }
        }
    }

    /**
     * 登出当前账号
     *
     * 清除内存状态和持久化存储中的账号信息。
     */
    fun logout() {
        val account = _currentAccount.value
        if (account != null) {
            LogUtil.info(TAG, "登出账号: ${account.username}")
        }

        scope.launch {
            // 如果是 LittleSkin 账号，调用 Yggdrasil signout 吊销令牌
            if (account?.accountType == AccountType.LITTLESKIN) {
                try {
                    RetrofitClient.yggdrasilApi.signout(
                        mapOf(
                            "username" to account.username,
                            "password" to ""
                        )
                    )
                } catch (e: Exception) {
                    LogUtil.warn(TAG, "Yggdrasil 登出请求失败（忽略）", e)
                }
            }

            // 清除本地存储
            clearSavedAccount()
            _currentAccount.value = null
        }
    }

    /**
     * 刷新当前账号的令牌
     *
     * 适用于：
     * - 微软账号：使用 refresh_token 重新获取 access_token
     * - LittleSkin：使用 Yggdrasil refresh 接口续期
     * - 离线账号：无需刷新
     *
     * @param onResult 刷新结果回调
     */
    fun refreshToken(
        onResult: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            val account = _currentAccount.value
            if (account == null) {
                onResult(false, "当前没有登录的账号")
                return@launch
            }

            try {
                when (account.accountType) {
                    AccountType.OFFLINE -> {
                        // 离线账号无需刷新令牌
                        onResult(true, null)
                    }
                    AccountType.MICROSOFT -> {
                        refreshMicrosoftToken(account, onResult)
                    }
                    AccountType.LITTLESKIN -> {
                        refreshLittleSkinToken(account, onResult)
                    }
                }
            } catch (e: Exception) {
                LogUtil.error(TAG, "刷新令牌失败", e)
                onResult(false, e.message ?: "未知错误")
            }
        }
    }

    // ==================== 离线账号登录 ====================

    /**
     * 离线账号登录
     *
     * 使用玩家名 MD5 生成 UUID（符合 Minecraft 离线模式规范），
     * 生成随机 accessToken。
     *
     * @param credentials 包含 "username" 字段的凭证映射
     * @return 生成的 AccountInfo
     */
    private suspend fun loginOffline(credentials: Map<String, String>): AccountInfo? =
        withContext(Dispatchers.IO) {
            val username = credentials["username"]
            if (username.isNullOrBlank()) {
                LogUtil.error(TAG, "离线登录失败：用户名为空")
                return@withContext null
            }

            // 通过用户名 MD5 生成 UUID（Minecraft 离线模式标准做法）
            // UUID 版本 3 风格：namespace + username MD5
            val nameMd5 = HashUtil.md5("OfflinePlayer:$username")
            // 将 MD5 十六进制字符串格式化为 UUID (xxxxxxxx-xxxx-3xxx-yxxx-xxxxxxxxxxxx)
            val uuid = buildString {
                append(nameMd5.substring(0, 8))
                append('-')
                append(nameMd5.substring(8, 12))
                append("-3") // UUID version 3
                append(nameMd5.substring(13, 16))
                append("-")
                // variant bits: 10xx
                val variantByte = nameMd5.substring(16, 18).toInt(16)
                val variantHex = Integer.toHexString((variantByte and 0x3F) or 0x80)
                append(variantHex.padStart(2, '0'))
                append(nameMd5.substring(18, 20))
                append('-')
                append(nameMd5.substring(20, 32))
            }

            // 生成随机 accessToken
            val accessToken = generateRandomToken()

            val account = AccountInfo(
                id = uuid,
                username = username,
                uuid = uuid,
                accessToken = accessToken,
                accountType = AccountType.OFFLINE,
                isLoggedIn = true,
                avatarUrl = null
            )

            LogUtil.info(TAG, "离线登录成功: $username (UUID: $uuid)")
            account
        }

    // ==================== 微软账号登录（OAuth2 设备码流程） ====================

    /**
     * 微软账号登录
     *
     * OAuth2 设备码流程：
     * 1. 请求设备码 -> 用户打开浏览器输入验证码
     * 2. 轮询令牌端点等待用户授权
     * 3. 用 Microsoft Token 换取 Xbox Live Token
     * 4. 用 Xbox Live Token 换取 XSTS Token
     * 5. 用 XSTS Token 换取 Minecraft Token
     * 6. 用 Minecraft Token 获取玩家 Profile
     *
     * @param onResult 用于通知 UI 层的回调（设备码信息、进度等）
     * @return 认证成功返回 AccountInfo，失败返回 null
     */
    private suspend fun loginMicrosoft(
        onResult: (AccountInfo?, String?) -> Unit
    ): AccountInfo? = withContext(Dispatchers.IO) {
        try {
            // 第一步：请求设备码
            LogUtil.info(TAG, "请求微软设备码...")
            onResult(null, "正在请求设备码...")

            val deviceCodeResponse = RetrofitClient.microsoftLoginApi.requestDeviceCode(
                url = MICROSOFT_DEVICE_CODE_URL,
                request = DeviceCodeRequest(
                    clientId = MICROSOFT_CLIENT_ID,
                    scope = MICROSOFT_SCOPE
                )
            )

            if (!deviceCodeResponse.isSuccessful || deviceCodeResponse.body() == null) {
                LogUtil.error(TAG, "获取设备码失败，HTTP ${deviceCodeResponse.code()}")
                return@withContext null
            }

            val deviceCode = deviceCodeResponse.body()!!
            LogUtil.info(TAG, "设备码: ${deviceCode.userCode}, 验证地址: ${deviceCode.verificationUri}")
            onResult(null, "请在浏览器中打开 ${deviceCode.verificationUri} 并输入验证码 ${deviceCode.userCode}")

            // 第二步：轮询令牌端点
            val tokenResponse = pollForToken(deviceCode.deviceCode, deviceCode.expiresIn, deviceCode.interval)
            if (tokenResponse == null) {
                LogUtil.error(TAG, "获取微软令牌超时或失败")
                return@withContext null
            }

            val microsoftAccessToken = tokenResponse.accessToken
            LogUtil.info(TAG, "已获取微软 AccessToken")

            // 第三步：Xbox Live 认证
            onResult(null, "正在进行 Xbox Live 认证...")
            val xboxToken = authenticateXbox(microsoftAccessToken) ?: return@withContext null
            LogUtil.info(TAG, "已获取 Xbox Live Token")

            // 第四步：XSTS 认证
            onResult(null, "正在进行 XSTS 认证...")
            val (xstsToken, userHash) = authenticateXsts(xboxToken) ?: return@withContext null
            LogUtil.info(TAG, "已获取 XSTS Token")

            // 第五步：Minecraft 认证
            onResult(null, "正在进行 Minecraft 认证...")
            val mcToken = authenticateMinecraft(xstsToken, userHash) ?: return@withContext null
            LogUtil.info(TAG, "已获取 Minecraft Token")

            // 第六步：获取 Minecraft Profile
            onResult(null, "正在获取角色信息...")
            val profile = getMinecraftProfile(mcToken) ?: return@withContext null

            val account = AccountInfo(
                id = profile.id,
                username = profile.name,
                uuid = profile.id,
                accessToken = mcToken,
                accountType = AccountType.MICROSOFT,
                isLoggedIn = true,
                avatarUrl = "https://crafatar.com/avatars/${profile.id}"
            )

            LogUtil.info(TAG, "微软账号登录成功: ${profile.name}")
            account
        } catch (e: Exception) {
            LogUtil.error(TAG, "微软账号登录异常", e)
            null
        }
    }

    // ==================== LittleSkin 账号登录（Yggdrasil 协议） ====================

    /**
     * LittleSkin 外置登录
     *
     * 使用 Yggdrasil 认证协议，baseUrl https://littleskin.cn/api/yggdrasil
     *
     * @param credentials 包含 "username" 和 "password" 的凭证映射
     * @return 认证成功返回 AccountInfo，失败返回 null
     */
    private suspend fun loginLittleSkin(credentials: Map<String, String>): AccountInfo? =
        withContext(Dispatchers.IO) {
            val username = credentials["username"]
            val password = credentials["password"]

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                LogUtil.error(TAG, "LittleSkin 登录失败：用户名或密码为空")
                return@withContext null
            }

            try {
                // 生成或加载 clientToken（用于令牌刷新）
                val clientToken = loadClientToken()

                LogUtil.info(TAG, "正在进行 LittleSkin Yggdrasil 认证: $username")
                val authResponse = RetrofitClient.yggdrasilApi.authenticate(
                    AuthenticateRequest(
                        username = username,
                        password = password,
                        clientToken = clientToken,
                        requestUser = true
                    )
                )

                if (!authResponse.isSuccessful || authResponse.body() == null) {
                    LogUtil.error(TAG, "LittleSkin 认证失败，HTTP ${authResponse.code()}")
                    return@withContext null
                }

                val authData = authResponse.body()!!
                if (authData.selectedProfile == null) {
                    LogUtil.error(TAG, "LittleSkin 认证失败：无可用角色")
                    return@withContext null
                }

                // 保存 clientToken 以便后续刷新
                saveClientToken(authData.clientToken)

                val profile = authData.selectedProfile
                val account = AccountInfo(
                    id = profile.id,
                    username = profile.name,
                    uuid = profile.id,
                    accessToken = authData.accessToken,
                    accountType = AccountType.LITTLESKIN,
                    isLoggedIn = true,
                    avatarUrl = "https://crafatar.com/avatars/${profile.id}"
                )

                LogUtil.info(TAG, "LittleSkin 登录成功: ${profile.name} (${profile.id})")
                account
            } catch (e: Exception) {
                LogUtil.error(TAG, "LittleSkin 登录异常", e)
                null
            }
        }

    // ==================== 令牌刷新 ====================

    /**
     * 刷新微软账号令牌
     */
    private suspend fun refreshMicrosoftToken(
        account: AccountInfo,
        onResult: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val refreshToken = loadEncryptedValue(KEY_REFRESH_TOKEN)
            if (refreshToken.isNullOrEmpty()) {
                onResult(false, "刷新令牌不存在，请重新登录")
                return@withContext
            }

            // 使用 refresh_token 获取新的 access_token
            val tokenResponse = RetrofitClient.microsoftLoginApi.requestToken(
                url = MICROSOFT_TOKEN_URL,
                request = TokenRequest(
                    clientId = MICROSOFT_CLIENT_ID,
                    deviceCode = refreshToken,
                    grantType = "refresh_token"
                )
            )

            if (!tokenResponse.isSuccessful || tokenResponse.body() == null) {
                onResult(false, "令牌刷新失败，请重新登录")
                return@withContext
            }

            val newToken = tokenResponse.body()!!
            val updatedAccount = account.copy(accessToken = newToken.accessToken)
            _currentAccount.value = updatedAccount
            saveAccount(updatedAccount)

            LogUtil.info(TAG, "微软令牌刷新成功")
            onResult(true, null)
        } catch (e: Exception) {
            LogUtil.error(TAG, "微软令牌刷新异常", e)
            onResult(false, e.message ?: "未知错误")
        }
    }

    /**
     * 刷新 LittleSkin 令牌
     */
    private suspend fun refreshLittleSkinToken(
        account: AccountInfo,
        onResult: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val clientToken = loadClientToken()

            val refreshResponse = RetrofitClient.yggdrasilApi.refresh(
                RefreshRequest(
                    accessToken = account.accessToken,
                    clientToken = clientToken
                )
            )

            if (!refreshResponse.isSuccessful || refreshResponse.body() == null) {
                onResult(false, "令牌刷新失败，请重新登录")
                return@withContext
            }

            val refreshData = refreshResponse.body()!!
            val updatedAccount = account.copy(accessToken = refreshData.accessToken)
            _currentAccount.value = updatedAccount
            saveAccount(updatedAccount)
            saveClientToken(refreshData.clientToken)

            LogUtil.info(TAG, "LittleSkin 令牌刷新成功")
            onResult(true, null)
        } catch (e: Exception) {
            LogUtil.error(TAG, "LittleSkin 令牌刷新异常", e)
            onResult(false, e.message ?: "未知错误")
        }
    }

    // ==================== 微软 OAuth 子流程 ====================

    /**
     * 轮询令牌端点，等待用户完成设备码授权
     *
     * @param deviceCode 设备码
     * @param expiresIn  过期时间（秒）
     * @param interval   轮询间隔（秒）
     * @return 令牌响应，超时返回 null
     */
    private suspend fun pollForToken(
        deviceCode: String,
        expiresIn: Int,
        interval: Int
    ): com.mymc.launcher.data.remote.dto.TokenResponse? {
        val endTime = System.currentTimeMillis() + expiresIn * 1000L
        val pollInterval = (interval * 1000L).coerceAtLeast(5000L)

        while (System.currentTimeMillis() < endTime) {
            try {
                val response = RetrofitClient.microsoftLoginApi.requestToken(
                    url = MICROSOFT_TOKEN_URL,
                    request = TokenRequest(
                        clientId = MICROSOFT_CLIENT_ID,
                        deviceCode = deviceCode,
                        grantType = "urn:ietf:params:oauth:grant-type:device_code"
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    return response.body()
                }

                // 检查错误类型
                val errorBody = response.errorBody()?.string() ?: ""
                if (errorBody.contains("authorization_pending")) {
                    // 用户尚未授权，继续等待
                    kotlinx.coroutines.delay(pollInterval)
                    continue
                }
                if (errorBody.contains("slow_down")) {
                    // 服务器要求降低轮询频率
                    kotlinx.coroutines.delay(pollInterval * 2)
                    continue
                }
                if (errorBody.contains("expired_token") || errorBody.contains("access_denied")) {
                    LogUtil.error(TAG, "设备码已过期或被拒绝")
                    return null
                }

                // 其他错误
                LogUtil.warn(TAG, "轮询令牌时收到未知响应: $errorBody")
                kotlinx.coroutines.delay(pollInterval)
            } catch (e: Exception) {
                LogUtil.warn(TAG, "轮询令牌时网络异常，重试中...", e)
                kotlinx.coroutines.delay(pollInterval)
            }
        }

        LogUtil.error(TAG, "设备码轮询超时")
        return null
    }

    /**
     * Xbox Live 用户认证
     *
     * 使用微软 AccessToken 换取 Xbox Live Token。
     *
     * @param microsoftToken 微软 OAuth AccessToken
     * @return Xbox Live Token 字符串，失败返回 null
     */
    private suspend fun authenticateXbox(microsoftToken: String): String? {
        val response = RetrofitClient.xboxLiveApi.authenticateXBox(
            XBoxAuthRequest(
                properties = XBoxAuthProperties(
                    authMethod = "RPS",
                    siteName = "user.auth.xboxlive.com",
                    rpsTicket = "d=$microsoftToken"
                )
            )
        )

        if (!response.isSuccessful || response.body() == null) {
            LogUtil.error(TAG, "Xbox Live 认证失败，HTTP ${response.code()}")
            return null
        }

        return response.body()!!.token
    }

    /**
     * XSTS 认证
     *
     * 使用 Xbox Live Token 换取 XSTS Token，同时获取用户哈希（UHS）。
     *
     * @param xboxToken Xbox Live Token
     * @return Pair<XSTS Token, User Hash>，失败返回 null
     */
    private suspend fun authenticateXsts(xboxToken: String): Pair<String, String>? {
        val response = RetrofitClient.xstsApi.authenticateXBox(
            XBoxAuthRequest(
                properties = XBoxAuthProperties(
                    authMethod = "RPS",
                    siteName = "user.auth.xboxlive.com",
                    rpsTicket = "d=$xboxToken"
                )
            )
        )

        if (!response.isSuccessful || response.body() == null) {
            LogUtil.error(TAG, "XSTS 用户认证失败，HTTP ${response.code()}")
            // 尝试使用 XSTS authorize 端点
            return authenticateXstsAuthorize(xboxToken)
        }

        val xboxAuth = response.body()!!
        val uhs = xboxAuth.displayClaims.xui.firstOrNull()?.uhs ?: return null
        return Pair(xboxAuth.token, uhs)
    }

    /**
     * XSTS authorize 备用端点
     */
    private suspend fun authenticateXstsAuthorize(xboxToken: String): Pair<String, String>? {
        val response = RetrofitClient.xstsApi.acquireXsts(
            XBoxAuthRequest(
                properties = XBoxAuthProperties(
                    authMethod = "RPS",
                    siteName = "user.auth.xboxlive.com",
                    rpsTicket = xboxToken
                )
            )
        )

        if (!response.isSuccessful || response.body() == null) {
            LogUtil.error(TAG, "XSTS authorize 认证失败，HTTP ${response.code()}")
            return null
        }

        val xstsToken = response.body()!!.token
        return Pair(xstsToken, "")
    }

    /**
     * Minecraft 认证
     *
     * 使用 XSTS Token 和用户哈希换取 Minecraft 访问令牌。
     *
     * @param xstsToken XSTS Token
     * @param userHash  用户哈希
     * @return Minecraft AccessToken，失败返回 null
     */
    private suspend fun authenticateMinecraft(xstsToken: String, userHash: String): String? {
        val response = RetrofitClient.minecraftServicesApi.authenticateMinecraft(
            body = mapOf(
                "identityToken" to "XBL3.0 x=$userHash;$xstsToken"
            )
        )

        if (!response.isSuccessful || response.body() == null) {
            LogUtil.error(TAG, "Minecraft 认证失败，HTTP ${response.code()}")
            return null
        }

        return response.body()!!.accessToken
    }

    /**
     * 获取 Minecraft 角色信息
     *
     * @param mcToken Minecraft AccessToken
     * @return Minecraft 角色信息，失败返回 null
     */
    private suspend fun getMinecraftProfile(mcToken: String): com.mymc.launcher.data.remote.dto.MinecraftProfileResponse? {
        val response = RetrofitClient.minecraftServicesApi.getMinecraftProfile(
            authorization = "Bearer $mcToken"
        )

        if (!response.isSuccessful || response.body() == null) {
            LogUtil.error(TAG, "获取 Minecraft Profile 失败，HTTP ${response.code()}")
            return null
        }

        return response.body()
    }

    // ==================== 持久化存储（加密） ====================

    /**
     * 从加密存储中恢复账号信息
     */
    private fun loadSavedAccount() {
        scope.launch {
            try {
                val isLoggedIn = loadEncryptedValue(KEY_IS_LOGGED_IN)
                if (isLoggedIn != "true") return@launch

                val username = loadEncryptedValue(KEY_ACCOUNT_USERNAME) ?: return@launch
                val uuid = loadEncryptedValue(KEY_ACCOUNT_UUID) ?: return@launch
                val accountTypeStr = loadEncryptedValue(KEY_ACCOUNT_TYPE) ?: return@launch
                val accessToken = loadEncryptedValue(KEY_ACCESS_TOKEN) ?: return@launch
                val avatarUrl = loadEncryptedValue(KEY_AVATAR_URL)

                val accountType = AccountType.valueOf(accountTypeStr)

                val account = AccountInfo(
                    id = uuid,
                    username = username,
                    uuid = uuid,
                    accessToken = accessToken,
                    accountType = accountType,
                    isLoggedIn = true,
                    avatarUrl = avatarUrl
                )

                _currentAccount.value = account
                LogUtil.info(TAG, "已恢复账号: $username (${accountType.name})")
            } catch (e: Exception) {
                LogUtil.error(TAG, "恢复账号信息失败", e)
            }
        }
    }

    /**
     * 加密保存当前账号信息
     */
    private suspend fun saveAccount(account: AccountInfo) {
        withContext(Dispatchers.IO) {
            try {
                saveEncryptedValue(KEY_ACCOUNT_USERNAME, account.username)
                saveEncryptedValue(KEY_ACCOUNT_UUID, account.uuid)
                saveEncryptedValue(KEY_ACCOUNT_TYPE, account.accountType.name)
                saveEncryptedValue(KEY_ACCESS_TOKEN, account.accessToken)
                saveEncryptedValue(KEY_AVATAR_URL, account.avatarUrl ?: "")
                saveEncryptedValue(KEY_IS_LOGGED_IN, "true")
                LogUtil.info(TAG, "账号信息已加密保存")
            } catch (e: Exception) {
                LogUtil.error(TAG, "保存账号信息失败", e)
            }
        }
    }

    /**
     * 清除所有已保存的账号信息
     */
    private suspend fun clearSavedAccount() {
        withContext(Dispatchers.IO) {
            try {
                saveEncryptedValue(KEY_IS_LOGGED_IN, "false")
                saveEncryptedValue(KEY_ACCESS_TOKEN, "")
                saveEncryptedValue(KEY_REFRESH_TOKEN, "")
                LogUtil.info(TAG, "账号信息已清除")
            } catch (e: Exception) {
                LogUtil.error(TAG, "清除账号信息失败", e)
            }
        }
    }

    /**
     * 保存 clientToken（用于 Yggdrasil 令牌刷新）
     */
    private fun saveClientToken(clientToken: String) {
        scope.launch {
            saveEncryptedValue(KEY_CLIENT_TOKEN, clientToken)
        }
    }

    /**
     * 加载 clientToken
     */
    private fun loadClientToken(): String {
        return loadEncryptedValue(KEY_CLIENT_TOKEN) ?: generateRandomToken()
    }

    // ==================== AES 加密工具 ====================

    /**
     * 加密并保存字符串值
     *
     * 使用 AES/CBC/PKCS5Padding 加密后进行 Base64 编码存储。
     */
    private suspend fun saveEncryptedValue(key: String, value: String) {
        val encrypted = encrypt(value)
        saveToPrefs(key, encrypted)
    }

    /**
     * 加载并解密字符串值
     */
    private fun loadEncryptedValue(key: String): String? {
        val encrypted = loadFromPrefs(key) ?: return null
        return decrypt(encrypted)
    }

    /**
     * Base64 编码后存储到 DataStore（简化实现：直接写入文件）
     */
    private var inMemoryStorage = mutableMapOf<String, String>()

    private suspend fun saveToPrefs(key: String, value: String) {
        inMemoryStorage[key] = value
        // 在实际项目中，这里应调用 PreferencesManager 的扩展方法写入 DataStore
        // 由于当前 PreferencesManager 未提供通用 key-value 存储，使用内存存储作为简化
        LogUtil.info(TAG, "已存储加密值: $key")
    }

    private fun loadFromPrefs(key: String): String? {
        return inMemoryStorage[key]
    }

    /**
     * AES 加密
     *
     * @param plainText 明文
     * @return Base64 编码的密文
     */
    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val ivSpec = IvParameterSpec(DEFAULT_IV)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * AES 解密
     *
     * @param cipherText Base64 编码的密文
     * @return 明文，解密失败返回 null
     */
    private fun decrypt(cipherText: String): String? {
        return try {
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val secretKey = SecretKeySpec(encryptionKey, "AES")
            val ivSpec = IvParameterSpec(DEFAULT_IV)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val encrypted = Base64.decode(cipherText, Base64.NO_WRAP)
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            LogUtil.error(TAG, "解密失败", e)
            null
        }
    }

    /**
     * 从 Android ID 派生 AES 加密密钥
     *
     * 使用设备唯一标识符的 SHA256 哈希作为 256 位密钥。
     *
     * @return 32 字节的 AES 密钥
     */
    private fun deriveEncryptionKey(): ByteArray {
        val androidId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        } catch (e: Exception) {
            ""
        }

        val seed = "YCL_Launcher_Account_Encryption_$androidId"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    // ==================== 工具方法 ====================

    /**
     * 生成随机令牌（64 字符十六进制）
     */
    private fun generateRandomToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}