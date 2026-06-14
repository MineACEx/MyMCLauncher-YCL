package com.mymc.launcher.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 应用上下文扩展属性：DataStore 实例
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ycl_launcher_preferences"
)

/**
 * DataStore 偏好设置管理器
 *
 * 管理应用的所有持久化偏好设置，包括：
 * - 主题色（十六进制颜色字符串）
 * - 暗色模式开关（null 表示跟随系统）
 * - 版本隔离开关
 * - JVM 启动参数
 * - 自定义背景图片路径
 * 
 * 使用 Preferences DataStore 替代 SharedPreferences，
 * 提供类型安全和协程友好的数据访问方式。
 */
class PreferencesManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        /**
         * 获取 PreferencesManager 单例实例
         */
        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        // ==================== 默认值常量 ====================

        /** 默认主题色（深海蓝） */
        const val DEFAULT_THEME_COLOR = "#FF0D47A1"

        /** 暗色模式默认值（null = 跟随系统） */
        val DARK_MODE_DEFAULT: String? = null

        /** 默认启用版本隔离 */
        const val DEFAULT_VERSION_ISOLATION = true

        /** 默认 JVM 参数 */
        const val DEFAULT_JVM_ARGS = "-Xmx2G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC"

        /** 默认背景路径（空字符串表示无自定义背景） */
        const val DEFAULT_BACKGROUND_PATH = ""

        /** 默认 DPI（0 表示自动/系统默认） */
        const val DEFAULT_DPI = 0

        /** DPI 最小值 */
        const val MIN_DPI = 72

        /** DPI 最大值 */
        const val MAX_DPI = 640

        /** 默认字体粗细（400 = Normal） */
        const val DEFAULT_FONT_WEIGHT = 400
    }

    // ==================== 偏好设置键定义 ====================

    private object Keys {
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val VERSION_ISOLATION = booleanPreferencesKey("version_isolation")
        val JVM_ARGS = stringPreferencesKey("jvm_args")
        val BACKGROUND_PATH = stringPreferencesKey("background_path")
        val DPI = stringPreferencesKey("dpi")
        val FONT_WEIGHT = stringPreferencesKey("font_weight")
    }

    // ==================== 数据流 ====================

    /**
     * 主题色数据流
     *
     * @return Flow<String> 十六进制颜色字符串
     */
    val themeColorFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取主题色失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[Keys.THEME_COLOR] ?: DEFAULT_THEME_COLOR
        }

    /**
     * 暗色模式数据流
     *
     * @return Flow<String?> "true" = 强制暗色, "false" = 强制亮色, null = 跟随系统
     */
    val darkModeFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取暗色模式失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[Keys.DARK_MODE]
        }

    /**
     * 版本隔离开关数据流
     *
     * @return Flow<Boolean> 是否启用版本隔离
     */
    val versionIsolationFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取版本隔离开关失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[Keys.VERSION_ISOLATION] ?: DEFAULT_VERSION_ISOLATION
        }

    /**
     * JVM 参数数据流
     *
     * @return Flow<String> JVM 启动参数字符串
     */
    val jvmArgsFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取 JVM 参数失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[Keys.JVM_ARGS] ?: DEFAULT_JVM_ARGS
        }

    /**
     * 自定义背景路径数据流
     *
     * @return Flow<String> 背景图片文件路径，空字符串表示无自定义背景
     */
    val backgroundPathFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取背景路径失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[Keys.BACKGROUND_PATH] ?: DEFAULT_BACKGROUND_PATH
        }

    /**
     * 自定义 DPI 数据流
     *
     * @return Flow<Int> DPI 值，0 表示自动
     */
    val dpiFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取 DPI 失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            (preferences[Keys.DPI] ?: DEFAULT_DPI.toString()).toIntOrNull() ?: DEFAULT_DPI
        }

    /**
     * 字体粗细数据流
     *
     * @return Flow<Int> 字体粗细值，如 400 (Normal), 700 (Bold)
     */
    val fontWeightFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            LogUtil.error("PreferencesManager", "读取字体粗细失败", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            (preferences[Keys.FONT_WEIGHT] ?: DEFAULT_FONT_WEIGHT.toString()).toIntOrNull() ?: DEFAULT_FONT_WEIGHT
        }

    // ==================== 写入方法 ====================

    /**
     * 设置主题色
     *
     * @param color 十六进制颜色字符串，如 "#FF6200EE"
     */
    suspend fun setThemeColor(color: String) {
        try {
            context.dataStore.edit { preferences ->
                preferences[Keys.THEME_COLOR] = color
            }
            LogUtil.info("PreferencesManager", "主题色已更新: $color")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存主题色失败", e)
        }
    }

    /**
     * 设置暗色模式
     *
     * @param mode "true" = 强制暗色, "false" = 强制亮色, null = 跟随系统
     */
    suspend fun setDarkMode(mode: String?) {
        try {
            context.dataStore.edit { preferences ->
                if (mode != null) {
                    preferences[Keys.DARK_MODE] = mode
                } else {
                    preferences.remove(Keys.DARK_MODE)
                }
            }
            LogUtil.info("PreferencesManager", "暗色模式已更新: $mode")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存暗色模式失败", e)
        }
    }

    /**
     * 设置版本隔离开关
     *
     * @param enabled 是否启用版本隔离
     */
    suspend fun setVersionIsolation(enabled: Boolean) {
        try {
            context.dataStore.edit { preferences ->
                preferences[Keys.VERSION_ISOLATION] = enabled
            }
            LogUtil.info("PreferencesManager", "版本隔离开关已更新: $enabled")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存版本隔离开关失败", e)
        }
    }

    /**
     * 设置 JVM 启动参数
     *
     * @param args JVM 参数字符串
     */
    suspend fun setJvmArgs(args: String) {
        try {
            context.dataStore.edit { preferences ->
                preferences[Keys.JVM_ARGS] = args
            }
            LogUtil.info("PreferencesManager", "JVM 参数已更新: $args")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存 JVM 参数失败", e)
        }
    }

    /**
     * 设置自定义背景图片路径
     *
     * @param path 背景图片的绝对路径，空字符串表示清除自定义背景
     */
    suspend fun setBackgroundPath(path: String) {
        try {
            context.dataStore.edit { preferences ->
                if (path.isNotEmpty()) {
                    preferences[Keys.BACKGROUND_PATH] = path
                } else {
                    preferences.remove(Keys.BACKGROUND_PATH)
                }
            }
            LogUtil.info("PreferencesManager", "背景路径已更新: $path")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存背景路径失败", e)
        }
    }

    /**
     * 设置自定义 DPI
     *
     * @param dpi DPI 值，0 表示自动
     */
    suspend fun setDpi(dpi: Int) {
        try {
            context.dataStore.edit { preferences ->
                if (dpi > 0) {
                    preferences[Keys.DPI] = dpi.toString()
                } else {
                    preferences.remove(Keys.DPI)
                }
            }
            LogUtil.info("PreferencesManager", "DPI 已更新: $dpi")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存 DPI 失败", e)
        }
    }

    /**
     * 设置字体粗细
     *
     * @param weight 字体粗细值，如 400 (Normal), 700 (Bold)
     */
    suspend fun setFontWeight(weight: Int) {
        try {
            context.dataStore.edit { preferences ->
                preferences[Keys.FONT_WEIGHT] = weight.toString()
            }
            LogUtil.info("PreferencesManager", "字体粗细已更新: $weight")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "保存字体粗细失败", e)
        }
    }

    /**
     * 重置所有偏好设置为默认值
     */
    suspend fun resetAll() {
        try {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
            LogUtil.info("PreferencesManager", "所有偏好设置已重置为默认值")
        } catch (e: IOException) {
            LogUtil.error("PreferencesManager", "重置偏好设置失败", e)
        }
    }

    /**
     * 生成空的 Preferences 对象（用于 catch 块中的默认发射）
     */
    private fun emptyPreferences(): Preferences {
        return androidx.datastore.preferences.core.emptyPreferences()
    }
}