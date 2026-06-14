package com.mymc.launcher.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.service.background.BackgroundManager
import com.mymc.launcher.service.theme.ThemeManager
import com.mymc.launcher.ui.components.BottomNavBar
import com.mymc.launcher.ui.components.FadeInContent
import com.mymc.launcher.ui.theme.PresetThemeColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// =============================================================================
// HSV 颜色转换工具函数
// =============================================================================

/**
 * 将 HSV 颜色转换为 Compose Color。
 *
 * @param hue        色相，范围 0..360
 * @param saturation 饱和度，范围 0f..1f
 * @param value      明度，范围 0f..1f
 * @param alpha      透明度，范围 0f..1f
 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    val h = hue / 60f
    val i = h.toInt()
    val f = h - i
    val p = value * (1f - saturation)
    val q = value * (1f - saturation * f)
    val t = value * (1f - saturation * (1f - f))

    val (r, g, b) = when (i % 6) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }

    return Color(r, g, b, alpha)
}

/**
 * 将 Compose Color 转换为 HSV 数组。
 *
 * @return FloatArray 包含 [hue(0..360), saturation(0..1), value(0..1)]
 */
private fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue

    val maxVal = max(r, max(g, b))
    val minVal = min(r, min(g, b))
    val delta = maxVal - minVal

    val hue = when {
        delta == 0f -> 0f
        maxVal == r -> 60f * (((g - b) / delta) % 6f)
        maxVal == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (maxVal == 0f) 0f else delta / maxVal
    val value = maxVal

    return floatArrayOf(hue, saturation, value)
}

// =============================================================================
// SettingsViewModel
// =============================================================================

/**
 * 设置页面 ViewModel。
 * 管理深色模式、主题色、背景图片、版本隔离、字体粗细等设置状态。
 */
class SettingsViewModel : ViewModel() {

    private val context = com.mymc.launcher.YCLApplication.instance
    private val preferencesManager = PreferencesManager.getInstance(context)

    /** 深色模式开关 */
    private val _isDarkMode = MutableStateFlow(ThemeManager.isDarkMode.value ?: false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /** 当前主题色 */
    private val _currentThemeColor = MutableStateFlow(ThemeManager.themeColor.value)
    val currentThemeColor: StateFlow<String> = _currentThemeColor.asStateFlow()

    /** 背景图片路径 */
    private val _backgroundPath = MutableStateFlow(ThemeManager.getCurrentBackgroundPath())
    val backgroundPath: StateFlow<String> = _backgroundPath.asStateFlow()

    /** 版本隔离开关 */
    private val _versionIsolationEnabled = MutableStateFlow(true)
    val versionIsolationEnabled: StateFlow<Boolean> = _versionIsolationEnabled.asStateFlow()

    /** 应用版本号 */
    private val _appVersion = MutableStateFlow("1.0.0")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    /** 自定义 DPI */
    private val _customDpi = MutableStateFlow(PreferencesManager.DEFAULT_DPI)
    val customDpi: StateFlow<Int> = _customDpi.asStateFlow()

    /** 字体粗细 */
    private val _fontWeight = MutableStateFlow(400)
    val fontWeight: StateFlow<Int> = _fontWeight.asStateFlow()

    /** 是否使用中国镜像下载 Java（默认 true） */
    private val _useMirror = MutableStateFlow(true)
    val useMirror: StateFlow<Boolean> = _useMirror.asStateFlow()

    /** 颜色选择器弹窗 */
    private val _showColorPicker = MutableStateFlow(false)
    val showColorPicker: StateFlow<Boolean> = _showColorPicker.asStateFlow()

    init {
        // 从 DataStore 读取版本隔离状态
        viewModelScope.launch {
            preferencesManager.versionIsolationFlow.collect { enabled ->
                _versionIsolationEnabled.value = enabled
            }
        }
        // 从 DataStore 读取 DPI 设置
        viewModelScope.launch {
            preferencesManager.dpiFlow.collect { dpi ->
                _customDpi.value = dpi
            }
        }
        // 从 DataStore 读取字体粗细
        viewModelScope.launch {
            preferencesManager.fontWeightFlow.collect { weight ->
                _fontWeight.value = weight
            }
        }
        // 从 DataStore 读取下载源偏好
        viewModelScope.launch {
            preferencesManager.useMirrorFlow.collect { mirror ->
                _useMirror.value = mirror
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        ThemeManager.setDarkMode(enabled)
    }

    fun selectThemeColor(colorHex: String) {
        _currentThemeColor.value = colorHex
        ThemeManager.setThemeColor(colorHex)
    }

    /** 从自定义颜色选择器设置主题色 */
    fun selectCustomColor(colorHex: String) {
        _currentThemeColor.value = colorHex
        ThemeManager.setThemeColor(colorHex)
    }

    fun setBackgroundImage(uri: Uri?) {
        if (uri != null) {
            viewModelScope.launch {
                val file = BackgroundManager.getInstance().copyUriToInternal(
                    context, uri, "custom_background.jpg"
                )
                if (file != null) {
                    val path = file.absolutePath
                    _backgroundPath.value = path
                    ThemeManager.setBackgroundPath(path)
                }
            }
        }
    }

    fun resetBackgroundImage() {
        _backgroundPath.value = ""
        ThemeManager.setBackgroundPath("")
        BackgroundManager.getInstance().clearAllBackgrounds(context)
    }

    fun toggleVersionIsolation(enabled: Boolean) {
        _versionIsolationEnabled.value = enabled
        viewModelScope.launch {
            preferencesManager.setVersionIsolation(enabled)
        }
    }

    fun setCustomDpi(dpi: Int) {
        _customDpi.value = dpi
        viewModelScope.launch {
            preferencesManager.setDpi(dpi)
        }
    }

    fun setFontWeight(weight: Int) {
        _fontWeight.value = weight
        ThemeManager.setFontWeight(weight)
    }

    fun toggleUseMirror(enabled: Boolean) {
        _useMirror.value = enabled
        viewModelScope.launch {
            preferencesManager.setUseMirror(enabled)
        }
    }

    fun openColorPicker() {
        _showColorPicker.value = true
    }

    fun closeColorPicker() {
        _showColorPicker.value = false
    }
}

// =============================================================================
// SettingsScreen
// =============================================================================

/**
 * 设置页面 Composable。
 * 深色/亮色模式切换、主题色选择、自定义颜色、背景图片、
 * 字体粗细、版本隔离、关于信息。
 *
 * @param onNavigate    全局导航回调
 * @param currentRoute  当前路由
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SettingsViewModel = viewModel()
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val currentThemeColor by viewModel.currentThemeColor.collectAsState()
    val backgroundPath by viewModel.backgroundPath.collectAsState()
    val versionIsolationEnabled by viewModel.versionIsolationEnabled.collectAsState()
    val appVersion by viewModel.appVersion.collectAsState()
    val customDpi by viewModel.customDpi.collectAsState()
    val fontWeight by viewModel.fontWeight.collectAsState()
    val useMirror by viewModel.useMirror.collectAsState()
    val showColorPicker by viewModel.showColorPicker.collectAsState()

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setBackgroundImage(uri)
    }

    // 自定义颜色选择器弹窗
    if (showColorPicker) {
        val initialColor = try {
            Color(android.graphics.Color.parseColor(currentThemeColor))
        } catch (_: Exception) {
            Color(0xFF0D47A1)
        }

        HsvColorPickerDialog(
            initialColor = initialColor,
            onColorSelected = { color ->
                val hex = String.format("#%08X", color.toArgb())
                viewModel.selectCustomColor(hex)
                viewModel.closeColorPicker()
            },
            onDismiss = { viewModel.closeColorPicker() }
        )
    }

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
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 深色/亮色模式切换
            SettingsToggleRow(
                title = "深色模式",
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 主题色选择
            Text(
                text = "主题色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetThemeColors.forEach { (name, themeColor) ->
                    val colorHex = String.format("#%08X", themeColor.toArgb())
                    ColorCircle(
                        color = themeColor,
                        isSelected = currentThemeColor.equals(colorHex, ignoreCase = true),
                        onClick = { viewModel.selectThemeColor(colorHex) }
                    )
                }
                // 自定义颜色按钮
                CustomColorButton(
                    onClick = { viewModel.openColorPicker() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 字体粗细
            Text(
                text = "字体粗细",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val fontWeightLabel = when {
                fontWeight <= 200 -> "Thin (200)"
                fontWeight <= 300 -> "Light (${fontWeight})"
                fontWeight <= 400 -> "Normal (${fontWeight})"
                fontWeight <= 500 -> "Medium (${fontWeight})"
                fontWeight <= 600 -> "SemiBold (${fontWeight})"
                fontWeight <= 700 -> "Bold (${fontWeight})"
                fontWeight <= 800 -> "ExtraBold (${fontWeight})"
                else -> "Black (${fontWeight})"
            }

            Text(
                text = fontWeightLabel,
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "200",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Slider(
                    value = fontWeight.toFloat(),
                    onValueChange = { viewModel.setFontWeight(it.toInt()) },
                    valueRange = 200f..900f,
                    steps = 13,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "900",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = { viewModel.setFontWeight(400) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("重置为默认 (400)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 背景图片
            Text(
                text = "背景图片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (backgroundPath.isNotBlank()) {
                Text(
                    text = "已设置: $backgroundPath",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = "未设置背景图片",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("选择本地图片")
                }
                Button(
                    onClick = { viewModel.resetBackgroundImage() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("重置")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 版本隔离开关
            SettingsToggleRow(
                title = "版本隔离",
                checked = versionIsolationEnabled,
                onCheckedChange = { viewModel.toggleVersionIsolation(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 自定义 DPI
            Text(
                text = "自定义 DPI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (customDpi == 0) "自动（系统默认）" else "${customDpi} DPI",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${PreferencesManager.MIN_DPI}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Slider(
                    value = customDpi.toFloat(),
                    onValueChange = { viewModel.setCustomDpi(it.toInt()) },
                    valueRange = PreferencesManager.MIN_DPI.toFloat()..PreferencesManager.MAX_DPI.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${PreferencesManager.MAX_DPI}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = { viewModel.setCustomDpi(0) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("重置为自动")
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ── Java 下载源切换 ──
            Text(
                text = "Java 下载源",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggleRow(
                title = "使用中国镜像（推荐）",
                checked = useMirror,
                onCheckedChange = { viewModel.toggleUseMirror(it) }
            )
            Text(
                text = if (useMirror)
                    "当前：BMCLAPI 镜像源（国内高速）→ 官方源备用"
                else
                    "当前：GitHub Adoptium 官方源",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 关于
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "应用版本", color = Color.Gray)
                Text(
                    text = appVersion,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    } // FadeInContent
    }
}

// =============================================================================
// HSV 颜色选择器弹窗
// =============================================================================

/**
 * HSV 颜色选择器弹窗。
 *
 * 包含：
 * - 饱和度/明度 2D 面板
 * - 色相条（横向滑动选择色相）
 * - 预览色块
 * - 确认按钮
 *
 * @param initialColor    初始颜色
 * @param onColorSelected 颜色确认回调
 * @param onDismiss       弹窗关闭回调
 */
@Composable
private fun HsvColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember { colorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = hsvToColor(hue, saturation, value)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "自定义颜色",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 饱和度/明度 2D 面板
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // 背景：水平饱和度渐变 垂直明度渐变
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(hue) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    saturation = (offset.x / w).coerceIn(0f, 1f)
                                    value = (1f - offset.y / h).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        for (y in 0 until h.toInt()) {
                            val v = 1f - y / h
                            val startColor = hsvToColor(hue, 0f, v)
                            val endColor = hsvToColor(hue, 1f, v)
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(startColor, endColor)
                                ),
                                start = Offset(0f, y.toFloat()),
                                end = Offset(w, y.toFloat()),
                                strokeWidth = 1f
                            )
                        }

                        // 当前选择指示器
                        val indicatorX = saturation * w
                        val indicatorY = (1f - value) * h
                        drawCircle(
                            color = Color.White,
                            radius = 8f,
                            center = Offset(indicatorX, indicatorY)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 6f,
                            center = Offset(indicatorX, indicatorY)
                        )
                        drawCircle(
                            color = currentColor,
                            radius = 5f,
                            center = Offset(indicatorX, indicatorY)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 色相条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        // 绘制色相渐变
                        for (x in 0 until w.toInt()) {
                            val hueAtX = x / w * 360f
                            drawLine(
                                color = hsvToColor(hueAtX, 1f, 1f),
                                start = Offset(x.toFloat(), 0f),
                                end = Offset(x.toFloat(), h),
                                strokeWidth = 1f
                            )
                        }

                        // 色相指示器
                        val indicatorX = hue / 360f * w
                        drawCircle(
                            color = Color.White,
                            radius = 10f,
                            center = Offset(indicatorX, h / 2f)
                        )
                        drawCircle(
                            color = hsvToColor(hue, 1f, 1f),
                            radius = 8f,
                            center = Offset(indicatorX, h / 2f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 预览色块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "预览:",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                            .background(currentColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = String.format("#%08X", currentColor.toArgb()),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 确认按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = { onColorSelected(currentColor) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Reusable Components
// =============================================================================

/**
 * 设置开关行组件。
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 主题色选择圆圈组件。
 */
@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color.White else Color.Transparent
    val borderWidth = if (isSelected) 3.dp else 0.dp

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .background(color, CircleShape)
            .clickable { onClick() }
    )
}

/**
 * 自定义颜色按钮组件。
 * 显示在预设颜色圆圈后面，点击后打开 HSV 颜色选择器。
 */
@Composable
private fun CustomColorButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
            .background(Color.Transparent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
    }
}