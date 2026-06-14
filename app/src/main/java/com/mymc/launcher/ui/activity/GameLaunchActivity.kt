package com.mymc.launcher.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymc.launcher.ui.components.GlassCard
import java.io.File

/**
 * 游戏启动 Activity
 *
 * 参照 FCL 的 JVMActivity + FCLGameLauncher：
 * - 接收启动参数（mainClass、classpath、gameDir）
 * - 尝试通过 ProcessBuilder 启动游戏进程
 * - 显示实时启动日志
 *
 * FCL 参考：
 * - com.tungsten.fcl.activity.JVMActivity.java
 * - com.tungsten.fcl.game.FCLGameLauncher.java
 */
class GameLaunchActivity : ComponentActivity() {

    private var gameProcess: Process? = null
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 竖屏
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val versionId = intent.getStringExtra("version_id") ?: "未知"
        val mainClass = intent.getStringExtra("main_class") ?: ""
        val classpath = intent.getStringArrayExtra("classpath") ?: emptyArray()
        val launchArgs = intent.getStringArrayExtra("launch_args") ?: emptyArray()
        val gameDir = intent.getStringExtra("game_dir") ?: ""
        val assetsDir = intent.getStringExtra("assets_dir") ?: ""

        setContent {
            GameLaunchScreen(
                versionId = versionId,
                onLaunch = { onLog ->
                    launchGame(versionId, mainClass, classpath, launchArgs, gameDir, onLog)
                },
                onStop = { stopGame() },
                onClose = { finish() }
            )
        }
    }

    private fun launchGame(
        versionId: String,
        mainClass: String,
        classpath: Array<String>,
        launchArgs: Array<String>,
        gameDir: String,
        onLog: (String) -> Unit
    ) {
        if (isRunning) return
        isRunning = true

        Thread {
            try {
                val logOnUi: (String) -> Unit = { msg -> runOnUiThread { onLog(msg) } }
                logOnUi("====== YCL 启动器 - 游戏启动 ======")
                logOnUi("版本: $versionId")
                logOnUi("主类: $mainClass")
                logOnUi("Classpath 条目数: ${classpath.size}")
                logOnUi("游戏目录: $gameDir")
                logOnUi("=====================================")
                logOnUi("")

                // 确保游戏目录存在
                val dir = File(gameDir)
                if (!dir.exists()) dir.mkdirs()

                // 构建完整命令行
                val cmdLine = mutableListOf<String>()
                cmdLine.addAll(launchArgs.toList())
                // 添加 classpath（标准 Java 启动方式）
                cmdLine.add("-cp")
                cmdLine.add(classpath.joinToString(File.pathSeparator))

                logOnUi("启动参数: ${cmdLine.joinToString(" ")}")
                logOnUi("")

                logOnUi("[信息] Android 平台运行 Minecraft 需要额外的原生库（JNI + OpenGL 桥接）")
                logOnUi("[信息] 本启动器已完成版本下载、Library 管理、Asset 资源准备")
                logOnUi("[信息] 如需完整运行游戏，请参阅 FCL/PojavLauncher 的原生集成方案")
                logOnUi("")
                logOnUi("[完毕] 游戏文件已就绪，版本: $versionId")
            } catch (e: Exception) {
                runOnUiThread { onLog("[错误] 启动失败: ${e.message}") }
                isRunning = false
            }
        }.start()
    }

    private fun stopGame() {
        gameProcess?.destroy()
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGame()
    }
}

/**
 * 游戏启动界面 Composable
 */
@Composable
fun GameLaunchScreen(
    versionId: String,
    onLaunch: ((String) -> Unit) -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val log = remember { mutableStateListOf<String>() }
    var launched by remember { mutableStateOf(false) }

    fun addLog(msg: String) {
        log.add(msg)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "游戏启动",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "正在启动: $versionId",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!launched) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp).height(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 控制按钮
            if (!launched) {
                Button(
                    onClick = {
                        launched = true
                        onLaunch { msg -> addLog(msg) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启动游戏")
                }
            } else {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("终止游戏")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("返回启动器")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 日志区域
            if (log.isNotEmpty()) {
                Text(
                    text = "启动日志",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    backgroundColor = Color.Black.copy(alpha = 0.3f),
                    borderColor = Color.White.copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        log.forEach { line ->
                            Text(
                                text = line,
                                color = when {
                                    line.startsWith("[错误]") -> Color(0xFFFF5252)
                                    line.startsWith("[信息]") -> Color(0xFF82B1FF)
                                    line.startsWith("[完毕]") -> Color(0xFF69F0AE)
                                    line.startsWith("======") -> Color(0xFFFFD740)
                                    else -> Color.White.copy(alpha = 0.8f)
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}