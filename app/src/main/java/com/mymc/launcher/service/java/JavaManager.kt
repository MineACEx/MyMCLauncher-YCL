package com.mymc.launcher.service.java

import android.content.Context
import com.mymc.launcher.domain.model.JavaVersion
import com.mymc.launcher.util.FileUtil
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Java 版本管理器单例
 *
 * 管理 Java 8 / 17 / 21 / 25 的 arm64 OpenJDK 下载、安装与版本匹配。
 * 所有 Java 运行时安装到应用内部存储的 java/ 目录下，按版本号隔离。
 *
 * MC 版本与 Java 版本自动匹配规则：
 *   MC 1.8  ~ 1.16  -> Java 8
 *   MC 1.17          -> Java 17
 *   MC 1.18 ~ 1.20   -> Java 17
 *   MC 1.21+         -> Java 21
 */
class JavaManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "JavaManager"

        /** 应用内部存储中 Java 安装的根目录 */
        private const val JAVA_DIR = "java"

        /** 各 Java 版本的默认 arm64 OpenJDK 下载地址（Adoptium/Temurin） */
        private const val JAVA_8_URL  = "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u432-b06/OpenJDK8U-jdk_aarch64_linux_hotspot_8u432b06.tar.gz"
        private const val JAVA_17_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.13_11.tar.gz"
        private const val JAVA_21_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.5_11.tar.gz"
        private const val JAVA_25_URL = "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25%2B36/OpenJDK25U-jdk_aarch64_linux_hotspot_25_36.tar.gz"

        @Volatile
        private var instance: JavaManager? = null

        /**
         * 获取 JavaManager 单例实例
         *
         * @param context Android 上下文
         * @return JavaManager 实例
         */
        fun getInstance(context: Context): JavaManager {
            return instance ?: synchronized(this) {
                instance ?: JavaManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /** Java 安装根目录 File 对象 */
    private val javaRootDir: File
        get() = File(context.filesDir, JAVA_DIR)

    /**
     * 获取所有可用的 Java 版本列表
     *
     * 检查本地已安装状态，返回每个版本对应的 JavaVersion 模型。
     *
     * @return 所有支持 Java 版本的列表
     */
    fun getAvailableJavaVersions(): List<JavaVersion> {
        val versions = listOf(
            JavaVersion(
                id = "java8",
                version = "8",
                downloadUrl = JAVA_8_URL,
                architecture = "arm64",
                fileSize = 0
            ),
            JavaVersion(
                id = "java17",
                version = "17",
                downloadUrl = JAVA_17_URL,
                architecture = "arm64",
                fileSize = 0
            ),
            JavaVersion(
                id = "java21",
                version = "21",
                downloadUrl = JAVA_21_URL,
                architecture = "arm64",
                fileSize = 0
            ),
            JavaVersion(
                id = "java25",
                version = "25",
                downloadUrl = JAVA_25_URL,
                architecture = "arm64",
                fileSize = 0
            )
        )
        return versions.map { jv ->
            val installPath = getJavaInstallDir(jv.version)
            val installed = isJavaInstalled(jv.version)
            jv.copy(
                installPath = installPath.absolutePath,
                installed = installed
            )
        }
    }

    /**
     * 下载并安装指定版本的 Java 运行时
     *
     * 下载 tar.gz 压缩包到临时目录，解压到 java/{version}/ 目录。
     * 通过 onProgress 回调报告下载进度（0.0 ~ 1.0）。
     *
     * @param version    Java 版本号，如 "8"、"17"、"21"、"25"
     * @param onProgress 下载及解压进度回调，Float 范围 0.0~1.0
     * @return 安装成功返回 true，失败返回 false
     */
    suspend fun downloadJava(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = getDownloadUrl(version) ?: run {
            LogUtil.error(TAG, "不支持的 Java 版本: $version")
            return@withContext false
        }

        val installDir = getJavaInstallDir(version)
        // 如果已安装则先清理旧文件
        if (installDir.exists()) {
            LogUtil.info(TAG, "已存在 Java $version 安装目录，清理后重新下载")
            FileUtil.deleteRecursively(installDir)
        }
        installDir.mkdirs()

        // 创建临时下载目录
        val tempDir = File(context.cacheDir, "java_download")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "java${version}.tar.gz")

        LogUtil.info(TAG, "开始下载 Java $version: $downloadUrl")
        onProgress(0f)

        // 下载文件（占用 80% 的进度）
        val downloadSuccess = FileUtil.downloadWithResume(
            downloadUrl = downloadUrl,
            targetFile = tempFile,
            onProgress = { downloaded, total ->
                val progress = if (total > 0) {
                    0.8f * (downloaded.toFloat() / total.toFloat())
                } else {
                    0f
                }
                onProgress(progress)
            }
        )

        if (!downloadSuccess) {
            LogUtil.error(TAG, "Java $version 下载失败")
            onProgress(0f)
            return@withContext false
        }

        onProgress(0.8f)
        LogUtil.info(TAG, "Java $version 下载完成，开始解压...")

        // 解压 tar.gz 到安装目录（使用 GZIP + Tar 解压）
        val unzipSuccess = FileUtil.unTarGzWithProgress(
            tarGzFile = tempFile,
            destDir = installDir,
            onProgress = { entryName, completedCount ->
                // 解压进度：80%～100%
                val progress = 0.8f + 0.2f * (completedCount.toFloat() / (completedCount + 100f).coerceAtMost(500f))
                onProgress(progress.coerceAtMost(1f))
            }
        )

        if (!unzipSuccess) {
            LogUtil.error(TAG, "Java $version 解压失败")
            onProgress(0f)
            return@withContext false
        }

        // 清理临时文件
        tempFile.delete()

        // 验证安装
        val verified = verifyJavaInstallation(version)
        if (verified) {
            onProgress(1f)
            LogUtil.info(TAG, "Java $version 安装完成: ${installDir.absolutePath}")
        } else {
            LogUtil.error(TAG, "Java $version 安装验证失败")
            onProgress(0f)
        }
        verified
    }

    /**
     * 获取指定版本 Java 可执行文件的路径
     *
     * 在 tar.gz 解压后的目录结构中查找 bin/java 可执行文件。
     *
     * @param version Java 版本号
     * @return Java 可执行文件的绝对路径，未安装返回 null
     */
    fun getJavaPath(version: String): String? {
        val installDir = getJavaInstallDir(version)
        if (!installDir.exists()) return null

        // 在安装目录中搜索 java 可执行文件
        // tar.gz 解压后通常有一个顶层目录，例如 jdk8u432-b06/
        val javaExecutable = findJavaExecutable(installDir)
        return javaExecutable?.absolutePath
    }

    /**
     * 根据 Minecraft 版本自动匹配对应的 Java 版本
     *
     * 匹配规则：
     *   - MC 1.8  ~ 1.16  -> Java 8
     *   - MC 1.17          -> Java 17
     *   - MC 1.18 ~ 1.20   -> Java 17
     *   - MC 1.21+         -> Java 21
     *
     * @param mcVersion Minecraft 版本号，如 "1.21"
     * @return 推荐的 Java 版本号，如 "8"、"17"、"21"
     */
    fun autoMatchJava(mcVersion: String): String {
        // 移除可能的加载器后缀（如 "1.21-forge-..."）
        val cleanVersion = mcVersion
            .substringBefore("-forge")
            .substringBefore("-fabric")
            .trim()

        return try {
            // 将版本号拆分为主版本号和次版本号
            val parts = cleanVersion.split(".")
            if (parts.size < 2) {
                LogUtil.warn(TAG, "无法解析 MC 版本号: $mcVersion，默认使用 Java 21")
                return "21"
            }

            val major = parts[0].toIntOrNull() ?: 1
            val minor = parts[1].toIntOrNull() ?: 21

            when {
                major < 1 -> "8"
                major == 1 && minor <= 16 -> "8"
                major == 1 && minor == 17 -> "17"
                major == 1 && minor in 18..20 -> "17"
                major == 1 && minor >= 21 -> "21"
                major >= 2 -> "21"
                else -> "21"
            }
        } catch (e: Exception) {
            LogUtil.error(TAG, "自动匹配 Java 版本失败: $mcVersion，默认使用 Java 21", e)
            "21"
        }
    }

    /**
     * 验证指定版本的 Java 安装是否完整
     *
     * 检查 bin/java 可执行文件是否存在且可执行。
     *
     * @param version Java 版本号
     * @return 安装完整返回 true，否则返回 false
     */
    fun verifyJavaInstallation(version: String): Boolean {
        val installDir = getJavaInstallDir(version)
        if (!installDir.exists() || !installDir.isDirectory) {
            LogUtil.warn(TAG, "Java $version 安装目录不存在")
            return false
        }

        val javaExecutable = findJavaExecutable(installDir)
        if (javaExecutable == null) {
            LogUtil.warn(TAG, "Java $version 可执行文件未找到")
            return false
        }

        if (!javaExecutable.canExecute()) {
            // 尝试设置可执行权限
            javaExecutable.setExecutable(true)
        }

        LogUtil.info(TAG, "Java $version 安装验证通过: ${javaExecutable.absolutePath}")
        return true
    }

    // ==================== 私有方法 ====================

    /**
     * 获取指定版本的下载地址
     */
    private fun getDownloadUrl(version: String): String? {
        return when (version) {
            "8"  -> JAVA_8_URL
            "17" -> JAVA_17_URL
            "21" -> JAVA_21_URL
            "25" -> JAVA_25_URL
            else -> null
        }
    }

    /**
     * 获取指定版本 Java 的安装目录
     */
    private fun getJavaInstallDir(version: String): File {
        return File(javaRootDir, version)
    }

    /**
     * 判断指定版本 Java 是否已安装
     */
    private fun isJavaInstalled(version: String): Boolean {
        return verifyJavaInstallation(version)
    }

    /**
     * 在安装目录中递归查找 java 可执行文件
     *
     * tar.gz 解压后的目录结构通常为:
     *   java/{version}/jdk8u432-b06/bin/java
     *
     * @param installDir Java 安装的根目录
     * @return 找到的 java 可执行文件 File 对象，未找到返回 null
     */
    private fun findJavaExecutable(installDir: File): File? {
        if (!installDir.exists() || !installDir.isDirectory) return null

        // 先尝试在 bin/ 子目录下直接查找
        val directJava = File(installDir, "bin/java")
        if (directJava.exists() && directJava.isFile) return directJava

        // 递归搜索（深度优先，限制最大深度以避免性能问题）
        return findFileRecursive(installDir, "java", maxDepth = 4)
    }

    /**
     * 在目录中递归搜索指定名称的文件
     *
     * @param dir      起始目录
     * @param fileName 目标文件名
     * @param maxDepth 最大递归深度
     * @return 找到的文件 File 对象，未找到返回 null
     */
    private fun findFileRecursive(dir: File, fileName: String, maxDepth: Int): File? {
        if (maxDepth < 0) return null
        val files = dir.listFiles() ?: return null
        for (file in files) {
            if (file.isFile && file.name == fileName) {
                return file
            }
            if (file.isDirectory) {
                val found = findFileRecursive(file, fileName, maxDepth - 1)
                if (found != null) return found
            }
        }
        return null
    }
}