package com.mymc.launcher.service.theme

import android.content.Context
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主题管理器单例
 *
 * 管理应用的主题状态，包括：
 * - 暗色模式开关（isDarkMode: null = 跟随系统，true = 强制暗色，false = 强制亮色）
 * - 主题色（themeColor: 十六进制颜色字符串）
 * - 自定义背景图片路径（backgroundPath）
 *
 * 所有状态以 StateFlow 形式暴露，与 Compose UI 无缝集成，
 * 同时与 PreferencesManager 联动实现持久化存储。
 */
object ThemeManager {

    private const val TAG = "ThemeManager"

    /** 协程作用域，用于异步持久化操作 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** PreferencesManager 实例 */
    private lateinit var preferencesManager: PreferencesManager

    /** 是否已初始化 */
    private var initialized = false

    // ==================== 状态流 ====================

    /** 暗色模式状态（null = 跟随系统） */
    private val _isDarkMode = MutableStateFlow<Boolean?>(null)

    /** 主题色状态（十六进制颜色字符串） */
    private val _themeColor = MutableStateFlow(PreferencesManager.DEFAULT_THEME_COLOR)

    /** 自定义背景图片路径状态 */
    private val _backgroundPath = MutableStateFlow(PreferencesManager.DEFAULT_BACKGROUND_PATH)

    /**
     * 暗色模式状态流（只读）
     *
     * null: 跟随系统设置
     * true: 强制暗色模式
     * false: 强制亮色模式
     */
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    /**
     * 主题色状态流（只读）
     *
     * 十六进制颜色字符串，如 "#FF6750A4"
     */
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    /**
     * 自定义背景图片路径状态流（只读）
     *
     * 空字符串表示无自定义背景
     */
    val backgroundPath: StateFlow<String> = _backgroundPath.asStateFlow()

    // ==================== 初始化 ====================

    /**
     * 初始化主题管理器
     *
     * 必须在 Application 或主 Activity 中调用一次。
     * 从 DataStore 加载持久化的主题设置并同步到 StateFlow。
     *
     * @param context Android 上下文
     */
    fun init(context: Context) {
        if (initialized) {
            LogUtil.warn(TAG, "主题管理器已初始化，跳过重复初始化")
            return
        }

        preferencesManager = PreferencesManager.getInstance(context)
        initialized = true

        // 从 DataStore 加载并同步主题状态
        scope.launch {
            // 监听暗色模式变化
            launch {
                preferencesManager.darkModeFlow.collect { modeStr ->
                    val darkMode = when (modeStr) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                    _isDarkMode.value = darkMode
                    LogUtil.info(TAG, "暗色模式已同步: $darkMode")
                }
            }

            // 监听主题色变化
            launch {
                preferencesManager.themeColorFlow.collect { color ->
                    _themeColor.value = color
                    LogUtil.info(TAG, "主题色已同步: $color")
                }
            }

            // 监听背景路径变化
            launch {
                preferencesManager.backgroundPathFlow.collect { path ->
                    _backgroundPath.value = path
                    LogUtil.info(TAG, "背景路径已同步: $path")
                }
            }
        }

        LogUtil.info(TAG, "主题管理器初始化完成")
    }

    // ==================== 主题设置方法 ====================

    /**
     * 设置暗色模式
     *
     * @param darkMode null = 跟随系统, true = 强制暗色, false = 强制亮色
     */
    fun setDarkMode(darkMode: Boolean?) {
        _isDarkMode.value = darkMode
        scope.launch {
            val modeStr = when (darkMode) {
                true -> "true"
                false -> "false"
                null -> null
            }
            preferencesManager.setDarkMode(modeStr)
        }
        LogUtil.info(TAG, "暗色模式已设置: $darkMode")
    }

    /**
     * 切换暗色模式（在跟随系统 -> 暗色 -> 亮色 -> 跟随系统之间循环）
     */
    fun toggleDarkMode() {
        val nextMode = when (_isDarkMode.value) {
            null -> true
            true -> false
            false -> null
        }
        setDarkMode(nextMode)
    }

    /**
     * 设置主题色
     *
     * @param color 十六进制颜色字符串，如 "#FF6200EE"
     */
    fun setThemeColor(color: String) {
        _themeColor.value = color
        scope.launch {
            preferencesManager.setThemeColor(color)
        }
        LogUtil.info(TAG, "主题色已设置: $color")
    }

    /**
     * 设置自定义背景图片路径
     *
     * @param path 背景图片的绝对路径，空字符串表示清除自定义背景
     */
    fun setBackgroundPath(path: String) {
        _backgroundPath.value = path
        scope.launch {
            preferencesManager.setBackgroundPath(path)
        }
        LogUtil.info(TAG, "背景路径已设置: $path")
    }

    /**
     * 清除自定义背景图片
     */
    fun clearBackground() {
        setBackgroundPath("")
    }

    // ==================== 查询方法 ====================

    /**
     * 获取当前暗色模式设置
     *
     * @return null = 跟随系统, true = 强制暗色, false = 强制亮色
     */
    fun getCurrentDarkMode(): Boolean? = _isDarkMode.value

    /**
     * 获取当前主题色
     *
     * @return 十六进制颜色字符串
     */
    fun getCurrentThemeColor(): String = _themeColor.value

    /**
     * 获取当前背景图片路径
     *
     * @return 背景图片路径，空字符串表示无自定义背景
     */
    fun getCurrentBackgroundPath(): String = _backgroundPath.value

    /**
     * 检查是否有自定义背景
     *
     * @return 有自定义背景返回 true
     */
    fun hasCustomBackground(): Boolean = _backgroundPath.value.isNotEmpty()
}