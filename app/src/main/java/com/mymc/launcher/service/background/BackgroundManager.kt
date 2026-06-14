package com.mymc.launcher.service.background

import android.content.Context
import android.net.Uri
import com.mymc.launcher.util.LogUtil
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 背景图片管理器（单例）
 *
 * 负责管理应用背景图片的存取，解决 Android content:// URI 直接访问的问题。
 * 将用户选择的背景图片从 content:// URI 拷贝到应用内部存储，
 * 后续统一从内部存储读取，避免 URI 权限过期或无法直接读取的问题。
 */
class BackgroundManager private constructor() {

    companion object {
        private const val TAG = "BackgroundManager"

        /** 内部存储中背景图片的存放目录名 */
        private const val BACKGROUNDS_DIR = "backgrounds"

        /** IO 缓冲区大小：8KB */
        private const val BUFFER_SIZE = 8192

        @Volatile
        private var instance: BackgroundManager? = null

        /**
         * 获取 BackgroundManager 单例实例
         *
         * @return BackgroundManager 实例
         */
        fun getInstance(): BackgroundManager {
            return instance ?: synchronized(this) {
                instance ?: BackgroundManager().also { instance = it }
            }
        }
    }

    /**
     * 获取背景图片存放目录的 File 对象
     *
     * @param context Android 上下文
     * @return backgrounds 目录的 File
     */
    fun getBackgroundsDir(context: Context): File {
        val dir = File(context.filesDir, BACKGROUNDS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 从 content:// URI 拷贝图片到应用内部存储
     *
     * 通过 ContentResolver 打开 URI 的输入流，将内容写入到
     * filesDir/backgrounds/{targetFileName} 文件中。
     *
     * @param context        Android 上下文
     * @param uri            图片的 content:// URI
     * @param targetFileName 保存到内部存储的目标文件名
     * @return 拷贝成功返回目标文件的 File 对象，失败返回 null
     */
    suspend fun copyUriToInternal(
        context: Context,
        uri: Uri,
        targetFileName: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val backgroundsDir = getBackgroundsDir(context)
            val targetFile = File(backgroundsDir, targetFileName)

            // 如果目标文件已存在，先删除
            if (targetFile.exists()) {
                targetFile.delete()
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            } ?: run {
                LogUtil.error(TAG, "无法打开 URI 输入流: $uri")
                return@withContext null
            }

            LogUtil.info(TAG, "背景图片已拷贝到内部存储: ${targetFile.absolutePath}")
            targetFile
        } catch (e: Exception) {
            LogUtil.error(TAG, "拷贝背景图片失败: $uri -> $targetFileName", e)
            null
        }
    }

    /**
     * 获取背景图片文件
     *
     * 优先检查应用内部存储中是否存在，如果内部存储不存在，
     * 则尝试通过 content:// URI 读取并自动拷贝到内部存储。
     *
     * 调用逻辑：
     *   1. 如果 path 是应用内部存储路径且文件存在，直接返回
     *   2. 如果 path 是 content:// URI：
     *      a. 先检查内部存储是否有对应的缓存文件
     *      b. 如果有则直接返回缓存文件
     *      c. 如果没有则拷贝到内部存储后返回
     *
     * @param context Android 上下文
     * @param path    背景图片的路径字符串，可以是内部存储绝对路径或 content:// URI
     * @return 可用的背景图片 File 对象，无法获取则返回 null
     */
    suspend fun getBackgroundFile(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) {
            LogUtil.warn(TAG, "背景图片路径为空")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // 情况1：已经是内部存储的绝对路径
                if (!path.startsWith("content://")) {
                    val file = File(path)
                    if (file.exists() && file.isFile && file.canRead()) {
                        LogUtil.info(TAG, "从内部路径加载背景图片: $path")
                        return@withContext file
                    }
                    LogUtil.warn(TAG, "内部路径背景图片不存在或不可读: $path")
                    return@withContext null
                }

                // 情况2：content:// URI
                val uri = Uri.parse(path)
                val uriString = uri.toString()

                // 生成基于 URI 的缓存文件名（使用 URI 的 hashCode 避免文件名冲突）
                val cacheFileName = "bg_${uriString.hashCode().toUInt()}.img"
                val cacheFile = File(getBackgroundsDir(context), cacheFileName)

                // 优先使用内部存储的缓存文件
                if (cacheFile.exists() && cacheFile.isFile && cacheFile.canRead()) {
                    LogUtil.info(TAG, "从缓存加载背景图片: ${cacheFile.absolutePath}")
                    return@withContext cacheFile
                }

                // 缓存不存在，从 URI 拷贝到内部存储
                LogUtil.info(TAG, "缓存未命中，从 URI 拷贝背景图片...")
                val copiedFile = copyUriToInternal(context, uri, cacheFileName)
                if (copiedFile != null) {
                    LogUtil.info(TAG, "URI 背景图片拷贝成功: ${copiedFile.absolutePath}")
                }
                copiedFile
            } catch (e: Exception) {
                LogUtil.error(TAG, "获取背景图片失败: $path", e)
                null
            }
        }
    }

    /**
     * 删除指定的背景图片
     *
     * @param context Android 上下文
     * @param fileName 要删除的背景图片文件名
     * @return 删除成功返回 true
     */
    fun deleteBackground(context: Context, fileName: String): Boolean {
        val file = File(getBackgroundsDir(context), fileName)
        if (!file.exists()) {
            LogUtil.info(TAG, "背景图片不存在，无需删除: $fileName")
            return true
        }
        val deleted = file.delete()
        if (deleted) {
            LogUtil.info(TAG, "背景图片已删除: $fileName")
        } else {
            LogUtil.error(TAG, "背景图片删除失败: $fileName")
        }
        return deleted
    }

    /**
     * 清空所有缓存的背景图片
     *
     * @param context Android 上下文
     * @return 删除成功返回 true
     */
    fun clearAllBackgrounds(context: Context): Boolean {
        val dir = getBackgroundsDir(context)
        return try {
            dir.listFiles()?.forEach { file ->
                file.delete()
            }
            LogUtil.info(TAG, "所有背景图片已清空")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "清空背景图片失败", e)
            false
        }
    }

    /**
     * 获取已缓存背景图片的数量
     *
     * @param context Android 上下文
     * @return 缓存图片数量
     */
    fun getBackgroundCount(context: Context): Int {
        val dir = getBackgroundsDir(context)
        return dir.listFiles()?.size ?: 0
    }
}