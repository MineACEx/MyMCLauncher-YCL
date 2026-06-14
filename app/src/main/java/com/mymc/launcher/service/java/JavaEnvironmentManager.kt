package com.mymc.launcher.service.java

import android.content.Context
import android.content.res.AssetManager
import com.mymc.launcher.util.FileUtil
import com.mymc.launcher.util.LogUtil
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Java 运行时环境管理器（单例）
 *
 * 负责管理嵌入在 APK assets 中的 Java 运行时（JRE）。
 * 支持从 APK assets 中提取预置的 JRE tar.xz 包到应用私有目录，
 * 并提供运行时检测、版本路径查询、设备架构自动识别等功能。
 *
 * 参照 FCL（Fold Craft Launcher）的 Java 环境管理设计。
 *
 * Assets 中的 JRE 包路径：
 *   - assets/java/Jre8-arm64.tar.xz   (Java 8 运行时, arm64)
 *   - assets/java/Jre17-arm64.tar.xz  (Java 17 运行时, arm64)
 *   - assets/java/Jre21-arm64.tar.xz  (Java 21 运行时, arm64)
 *   - assets/java/Jre25-arm64.tar.xz  (Java 25 运行时, arm64)
 *
 * 提取目标目录：{app私有目录}/java/{version}/
 */
class JavaEnvironmentManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "JavaEnvironmentManager"

        /** 应用内部存储中 Java 安装的根目录 */
        private const val JAVA_ROOT_DIR = "java"

        /** Assets 中 JRE tar.xz 包的基础路径 */
        private const val ASSETS_JAVA_BASE = "java"

        /** 支持的 Java 版本列表及其对应的 assets 包名 */
        private val SUPPORTED_VERSIONS = mapOf(
            "8"  to "Jre8-arm64.tar.xz",
            "17" to "Jre17-arm64.tar.xz",
            "21" to "Jre21-arm64.tar.xz",
            "25" to "Jre25-arm64.tar.xz"
        )

        @Volatile
        private var instance: JavaEnvironmentManager? = null

        /**
         * 获取 JavaEnvironmentManager 单例实例
         *
         * @param context Android 上下文
         * @return JavaEnvironmentManager 实例
         */
        fun getInstance(context: Context): JavaEnvironmentManager {
            return instance ?: synchronized(this) {
                instance ?: JavaEnvironmentManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /** Java 安装根目录的 File 对象 */
    private val javaRootDir: File
        get() = File(context.filesDir, JAVA_ROOT_DIR)

    /**
     * 获取当前设备的 CPU 架构（ABI）
     *
     * 返回例如 "arm64-v8a"、"armeabi-v7a"、"x86_64"、"x86" 等。
     *
     * @return 设备 ABI 字符串
     */
    fun getDeviceArchitecture(): String {
        return try {
            val abis = android.os.Build.SUPPORTED_ABIS
            if (abis.isNotEmpty()) abis[0] else "arm64-v8a"
        } catch (e: Exception) {
            LogUtil.error(TAG, "获取设备架构失败，默认使用 arm64-v8a", e)
            "arm64-v8a"
        }
    }

    /**
     * 检测指定版本的 Java 是否已安装
     *
     * 检查 app 私有目录下 java/{version}/ 文件夹是否存在，
     * 并且其中包含可用的 Java 可执行文件。
     *
     * @param version Java 版本号，如 "8"、"17"、"21"、"25"
     * @return 已安装返回 true，否则返回 false
     */
    fun isJavaInstalled(version: String): Boolean {
        val installDir = getJavaInstallDir(version)
        if (!installDir.exists() || !installDir.isDirectory) {
            return false
        }
        val javaExe = findJavaExecutable(installDir)
        if (javaExe == null) {
            LogUtil.warn(TAG, "Java $version 安装目录存在但未找到可执行文件")
            return false
        }
        if (!javaExe.canExecute()) {
            javaExe.setExecutable(true)
        }
        return javaExe.exists() && javaExe.canExecute()
    }

    /**
     * 检测所有支持版本的 Java 是否已安装
     *
     * @return 已安装的版本号列表
     */
    fun getInstalledVersions(): List<String> {
        return SUPPORTED_VERSIONS.keys.filter { isJavaInstalled(it) }
    }

    /**
     * 从 APK assets 中提取嵌入式 Java 运行时
     *
     * 将 assets/java/{Jre tar.xz} 解压到 app 私有目录 java/{version}/ 下。
     * 使用 FileUtil.unTarXzWithProgress 进行解压，并通过 onProgress 回调报告进度。
     *
     * 提取流程：
     *   1. 检查 assets 中是否存在对应的 JRE tar.xz 包
     *   2. 复制 assets 中的 tar.xz 到临时缓存目录
     *   3. 解压 tar.xz 到 java/{version}/ 目录
     *   4. 验证安装完整性
     *   5. 清理临时文件
     *
     * @param version    Java 版本号，如 "8"、"17"、"21"、"25"
     * @param onProgress 解压进度回调，参数为 (已解压条目名称, 已解压条目数)
     * @return 提取成功返回 true，失败返回 false
     */
    suspend fun extractJavaRuntime(
        version: String,
        onProgress: (entryName: String, completedCount: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val assetFileName = SUPPORTED_VERSIONS[version]
        if (assetFileName == null) {
            LogUtil.error(TAG, "不支持的 Java 版本: $version，支持的版本: ${SUPPORTED_VERSIONS.keys}")
            return@withContext false
        }

        val assetPath = "$ASSETS_JAVA_BASE/$assetFileName"
        LogUtil.info(TAG, "开始从 assets 提取 Java $version: $assetPath")

        // 检查 assets 中是否存在该文件
        val assetManager: AssetManager = context.assets
        val assetExists = try {
            assetManager.open(assetPath).use { true }
        } catch (e: Exception) {
            LogUtil.error(TAG, "assets 中未找到 JRE 包: $assetPath", e)
            false
        }

        if (!assetExists) {
            LogUtil.error(TAG, "Assets 中不存在 JRE 包: $assetPath")
            return@withContext false
        }

        // 如果已安装，先清理旧文件
        val installDir = getJavaInstallDir(version)
        if (installDir.exists()) {
            LogUtil.info(TAG, "已存在 Java $version 安装目录，清理后重新提取")
            FileUtil.deleteRecursively(installDir)
        }
        installDir.mkdirs()

        // 将 assets 中的 tar.xz 复制到临时缓存目录
        val tempDir = File(context.cacheDir, "java_extract")
        tempDir.mkdirs()
        val tempTarXzFile = File(tempDir, assetFileName)

        LogUtil.info(TAG, "正在将 assets 中 JRE 包复制到临时目录...")
        try {
            assetManager.open(assetPath).use { input ->
                FileOutputStream(tempTarXzFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            LogUtil.error(TAG, "复制 assets JRE 包到临时目录失败: $assetPath", e)
            return@withContext false
        }

        LogUtil.info(TAG, "开始解压 JRE 包到安装目录: ${installDir.absolutePath}")
        onProgress("", 0)

        // 使用 FileUtil.unTarXzWithProgress 解压 tar.xz
        val unzipSuccess = FileUtil.unTarXzWithProgress(
            tarXzFile = tempTarXzFile,
            destDir = installDir,
            onProgress = { entryName, completedCount ->
                onProgress(entryName, completedCount)
            }
        )

        // 无论解压是否成功，都清理临时文件
        tempTarXzFile.delete()

        if (!unzipSuccess) {
            LogUtil.error(TAG, "Java $version 解压失败")
            return@withContext false
        }

        // 解压完成后，对安装目录内所有 bin/ 子目录递归设置可执行权限
        setExecutableRecursively(installDir)

        // 验证安装
        val verified = verifyJavaInstallation(version)
        if (verified) {
            LogUtil.info(TAG, "Java $version 提取并安装完成: ${installDir.absolutePath}")
        } else {
            LogUtil.error(TAG, "Java $version 提取后验证失败")
        }
        verified
    }

    /**
     * 获取指定版本 Java 可执行文件的路径
     *
     * 在 java/{version}/ 目录中搜索 java 可执行文件。
     * tar.xz 解压后通常包含 bin/java 目录结构。
     *
     * @param version Java 版本号，如 "8"、"17"、"21"、"25"
     * @return Java 可执行文件的绝对路径，未安装返回 null
     */
    fun getJavaPath(version: String): String? {
        val installDir = getJavaInstallDir(version)
        if (!installDir.exists() || !installDir.isDirectory) {
            LogUtil.warn(TAG, "Java $version 安装目录不存在: ${installDir.absolutePath}")
            return null
        }

        val javaExecutable = findJavaExecutable(installDir)
        if (javaExecutable == null) {
            LogUtil.warn(TAG, "在安装目录中未找到 Java $version 可执行文件")
            return null
        }

        if (!javaExecutable.canExecute()) {
            javaExecutable.setExecutable(true)
        }

        return javaExecutable.absolutePath
    }

    /**
     * 验证指定版本的 Java 安装是否完整
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
            javaExecutable.setExecutable(true)
        }

        LogUtil.info(TAG, "Java $version 安装验证通过: ${javaExecutable.absolutePath}")
        return true
    }

    /**
     * 获取 Java 安装根目录的绝对路径
     *
     * @return Java 根目录的绝对路径字符串
     */
    fun getJavaRootPath(): String {
        return javaRootDir.absolutePath
    }

    /**
     * 获取指定版本 Java 的安装目录 File 对象
     *
     * @param version Java 版本号
     * @return 安装目录 File
     */
    fun getJavaInstallDir(version: String): File {
        return File(javaRootDir, version)
    }

    /**
     * 删除指定版本的 Java 运行时
     *
     * @param version Java 版本号
     * @return 删除成功返回 true
     */
    fun deleteJavaRuntime(version: String): Boolean {
        val installDir = getJavaInstallDir(version)
        if (!installDir.exists()) {
            LogUtil.info(TAG, "Java $version 未安装，无需删除")
            return true
        }
        val result = FileUtil.deleteRecursively(installDir)
        if (result) {
            LogUtil.info(TAG, "Java $version 运行时已删除")
        } else {
            LogUtil.error(TAG, "Java $version 运行时删除失败")
        }
        return result
    }

    /**
     * 获取已安装 Java 版本占用的磁盘空间（字节）
     *
     * @param version Java 版本号
     * @return 磁盘占用字节数，未安装返回 0
     */
    fun getJavaSize(version: String): Long {
        val installDir = getJavaInstallDir(version)
        if (!installDir.exists()) return 0L
        return FileUtil.calculateSize(installDir)
    }

    // ==================== 私有方法 ====================

    /**
     * 在安装目录中查找 Java 可执行文件
     *
     * tar.xz 解压后的目录通常为:
     *   java/{version}/bin/java
     * 也可能包含一层额外目录，如:
     *   java/{version}/jre8/bin/java
     *
     * @param installDir Java 安装目录
     * @return Java 可执行文件 File，未找到返回 null
     */
    private fun findJavaExecutable(installDir: File): File? {
        if (!installDir.exists() || !installDir.isDirectory) return null

        // 先尝试直接在 bin/ 下查找
        val directJava = File(installDir, "bin/java")
        if (directJava.exists() && directJava.isFile) return directJava

        // 递归搜索（深度优先，限制最大深度）
        return findFileRecursive(installDir, "java", maxDepth = 4)
    }

    /**
     * 在目录中递归搜索指定名称的文件
     *
     * @param dir      起始目录
     * @param fileName 目标文件名
     * @param maxDepth 最大递归深度
     * @return 找到的文件 File，未找到返回 null
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

    /**
     * 递归为安装目录内所有 bin/ 子目录中的文件设置可执行权限
     *
     * tar 提取后某些系统无法通过 entry.mode 还原权限，
     * 此处在提取完成后对所有 bin/ 目录内文件补充 setExecutable。
     *
     * @param rootDir Java 安装根目录
     */
    private fun setExecutableRecursively(rootDir: File) {
        if (!rootDir.exists() || !rootDir.isDirectory) return
        rootDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val inBin = file.parentFile?.name == "bin" ||
                            file.absolutePath.contains("/bin/")
                if (inBin || !file.name.endsWith(".jar")) {
                    // bin 目录下所有文件，以及非 jar 的其他可执行体都赋予执行权限
                    file.setExecutable(true, false)
                }
            }
        }
        LogUtil.info(TAG, "已为 ${rootDir.absolutePath} 内所有 bin/ 文件设置可执行权限")
    }
}