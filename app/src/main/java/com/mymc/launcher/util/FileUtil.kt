package com.mymc.launcher.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/**
 * 文件处理工具类
 *
 * 提供文件/目录的复制、删除、大小计算、Zip 解压以及断点续传辅助方法。
 */
object FileUtil {

    /** IO 缓冲区大小：8KB */
    private const val BUFFER_SIZE = 8192

    /**
     * 复制单个文件到目标路径
     *
     * @param source 源文件
     * @param target 目标文件
     * @return 复制成功返回 true，否则返回 false
     */
    fun copyFile(source: File, target: File): Boolean {
        if (!source.exists() || !source.isFile) {
            LogUtil.warn("FileUtil", "源文件不存在或不是文件: ${source.absolutePath}")
            return false
        }
        return try {
            ensureParentDir(target)
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            true
        } catch (e: IOException) {
            LogUtil.error("FileUtil", "文件复制失败: ${source.absolutePath} -> ${target.absolutePath}", e)
            false
        }
    }

    /**
     * 递归复制目录（包含所有子目录和文件）
     *
     * @param sourceDir  源目录
     * @param targetDir  目标目录
     * @return 复制成功返回 true，否则返回 false
     */
    fun copyDirectory(sourceDir: File, targetDir: File): Boolean {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            LogUtil.warn("FileUtil", "源目录不存在或不是目录: ${sourceDir.absolutePath}")
            return false
        }
        return try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            sourceDir.listFiles()?.forEach { child ->
                val targetChild = File(targetDir, child.name)
                if (child.isDirectory) {
                    copyDirectory(child, targetChild)
                } else {
                    copyFile(child, targetChild)
                }
            }
            true
        } catch (e: Exception) {
            LogUtil.error("FileUtil", "目录复制失败: ${sourceDir.absolutePath} -> ${targetDir.absolutePath}", e)
            false
        }
    }

    /**
     * 递归删除文件或目录
     *
     * @param file 要删除的文件或目录
     * @return 删除成功返回 true，否则返回 false
     */
    fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deleteRecursively(child)
                }
            }
            file.delete()
        } catch (e: Exception) {
            LogUtil.error("FileUtil", "删除失败: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * 计算文件或目录的总大小（字节）
     *
     * @param file 目标文件或目录
     * @return 总字节数
     */
    fun calculateSize(file: File): Long {
        if (!file.exists()) return 0L
        return if (file.isDirectory) {
            var totalSize = 0L
            file.listFiles()?.forEach { child ->
                totalSize += calculateSize(child)
            }
            totalSize
        } else {
            file.length()
        }
    }

    /**
     * 将字节数格式化为可读的字符串（KB/MB/GB）
     *
     * @param bytes 字节数
     * @return 格式化后的字符串，如 "1.50 MB"
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val size = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "%.2f %s".format(size, units[digitGroups.coerceAtMost(units.size - 1)])
    }

    /**
     * 带进度回调的解压 tar.gz 文件（使用 GZIP + Tar）
     *
     * @param tarGzFile     tar.gz 文件
     * @param destDir       目标解压目录
     * @param onProgress    进度回调 (entryName, completedCount)
     * @return 解压成功返回 true
     */
    fun unTarGzWithProgress(
        tarGzFile: File,
        destDir: File,
        onProgress: (entryName: String, completedCount: Int) -> Unit
    ): Boolean {
        if (!tarGzFile.exists() || !tarGzFile.isFile) {
            LogUtil.warn("FileUtil", "tar.gz 文件不存在: ${tarGzFile.absolutePath}")
            return false
        }
        if (!destDir.exists()) destDir.mkdirs()

        return try {
            var completedCount = 0
            FileInputStream(tarGzFile).use { fis ->
                GZIPInputStream(fis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry
                        while (entry != null) {
                            val entryFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                entryFile.mkdirs()
                            } else {
                                ensureParentDir(entryFile)
                                FileOutputStream(entryFile).use { fos ->
                                    tarIn.copyTo(fos, BUFFER_SIZE)
                                }
                            }
                            completedCount++
                            onProgress(entry.name, completedCount)
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
            LogUtil.info("FileUtil", "tar.gz 解压完成（共 $completedCount 个条目）: ${tarGzFile.name}")
            true
        } catch (e: Exception) {
            LogUtil.error("FileUtil", "tar.gz 解压失败: ${tarGzFile.absolutePath}", e)
            false
        }
    }

    /**
     * 带进度回调的解压 tar.xz 文件（使用 XZ + Tar）
     *
     * @param tarXzFile     tar.xz 文件
     * @param destDir       目标解压目录
     * @param onProgress    进度回调 (entryName, completedCount)
     * @return 解压成功返回 true
     */
    fun unTarXzWithProgress(
        tarXzFile: File,
        destDir: File,
        onProgress: (entryName: String, completedCount: Int) -> Unit
    ): Boolean {
        if (!tarXzFile.exists() || !tarXzFile.isFile) {
            LogUtil.warn("FileUtil", "tar.xz 文件不存在: ${tarXzFile.absolutePath}")
            return false
        }
        if (!destDir.exists()) destDir.mkdirs()

        return try {
            var completedCount = 0
            FileInputStream(tarXzFile).use { fis ->
                XZCompressorInputStream(fis).use { xzis ->
                    TarArchiveInputStream(xzis).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry
                        while (entry != null) {
                            val entryFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                entryFile.mkdirs()
                            } else {
                                ensureParentDir(entryFile)
                                FileOutputStream(entryFile).use { fos ->
                                    tarIn.copyTo(fos, BUFFER_SIZE)
                                }
                            }
                            completedCount++
                            onProgress(entry.name, completedCount)
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
            LogUtil.info("FileUtil", "tar.xz 解压完成（共 $completedCount 个条目）: ${tarXzFile.name}")
            true
        } catch (e: Exception) {
            LogUtil.error("FileUtil", "tar.xz 解压失败: ${tarXzFile.absolutePath}", e)
            false
        }
    }

    /**
     * 解压 Zip 文件到目标目录
     *
     * @param zipFile     Zip 文件
     * @param destDir     目标解压目录
     * @return 解压成功返回 true，否则返回 false
     */
    fun unzipFile(zipFile: File, destDir: File): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) {
            LogUtil.warn("FileUtil", "Zip 文件不存在: ${zipFile.absolutePath}")
            return false
        }
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        return try {
            FileInputStream(zipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            ensureParentDir(entryFile)
                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos, BUFFER_SIZE)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            LogUtil.info("FileUtil", "Zip 解压完成: ${zipFile.name} -> ${destDir.absolutePath}")
            true
        } catch (e: IOException) {
            LogUtil.error("FileUtil", "Zip 解压失败: ${zipFile.absolutePath}", e)
            false
        }
    }

    /**
     * 带进度回调的解压 Zip 文件
     *
     * @param zipFile        Zip 文件
     * @param destDir        目标解压目录
     * @param onProgress     进度回调，参数为当前条目名称和已解压条目数
     * @return 解压成功返回 true，否则返回 false
     */
    fun unzipFileWithProgress(
        zipFile: File,
        destDir: File,
        onProgress: (entryName: String, completedCount: Int) -> Unit
    ): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) {
            LogUtil.warn("FileUtil", "Zip 文件不存在: ${zipFile.absolutePath}")
            return false
        }
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        return try {
            var completedCount = 0
            FileInputStream(zipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            ensureParentDir(entryFile)
                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos, BUFFER_SIZE)
                            }
                        }
                        completedCount++
                        onProgress(entry.name, completedCount)
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            LogUtil.info("FileUtil", "Zip 解压完成（共 $completedCount 个条目）: ${zipFile.name}")
            true
        } catch (e: IOException) {
            LogUtil.error("FileUtil", "Zip 解压失败: ${zipFile.absolutePath}", e)
            false
        }
    }

    /**
     * 断点续传下载文件
     *
     * 如果目标文件已部分下载，则从已下载字节数继续下载。
     *
     * @param downloadUrl  下载地址
     * @param targetFile   保存的目标文件
     * @param onProgress   下载进度回调，参数为 (已下载字节数, 总字节数)
     * @return 下载成功返回 true，否则返回 false
     */
    fun downloadWithResume(
        downloadUrl: String,
        targetFile: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean {
        ensureParentDir(targetFile)
        val existingLength = if (targetFile.exists()) targetFile.length() else 0L
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // 如果已有部分文件，设置 Range 请求头进行断点续传
            if (existingLength > 0) {
                connection.setRequestProperty("Range", "bytes=$existingLength-")
            }

            connection.connect()

            val responseCode = connection.responseCode
            val totalLength: Long

            // 处理服务器返回的状态码
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // 206 表示服务器支持断点续传
                val contentRange = connection.getHeaderField("Content-Range")
                totalLength = if (contentRange != null) {
                    // 从 Content-Range 头部解析总文件大小
                    val parts = contentRange.split("/")
                    if (parts.size >= 2) parts.last().toLongOrNull() ?: -1L else -1L
                } else {
                    connection.contentLength.toLong() + existingLength
                }
                LogUtil.info("FileUtil", "断点续传: 从 $existingLength 字节处继续下载")
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                // 200 表示服务器不支持断点续传，从头下载
                totalLength = connection.contentLength.toLong()
                if (existingLength > 0) {
                    targetFile.delete()
                    LogUtil.info("FileUtil", "服务器不支持断点续传，重新下载")
                }
            } else {
                LogUtil.error("FileUtil", "下载失败，HTTP 响应码: $responseCode")
                return false
            }

            // 根据是否续传选择写入模式
            val appendMode = responseCode == HttpURLConnection.HTTP_PARTIAL
            val inputStream: InputStream = connection.inputStream
            FileOutputStream(targetFile, appendMode).use { fos ->
                inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = if (appendMode) existingLength else 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress?.invoke(downloaded, totalLength)
                    }
                }
            }
            LogUtil.info("FileUtil", "下载完成: $downloadUrl -> ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            LogUtil.error("FileUtil", "下载失败: $downloadUrl", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 获取文件扩展名（小写，不含点）
     *
     * @param file 目标文件
     * @return 扩展名字符串，如 "zip"、"jar"，无扩展名时返回空字符串
     */
    fun getFileExtension(file: File): String {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex >= 0 && dotIndex < name.length - 1) {
            name.substring(dotIndex + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * 判断给定路径是否为目录
     */
    fun isDirectory(path: String): Boolean {
        return File(path).isDirectory
    }

    /**
     * 判断给定路径是否为文件
     */
    fun isFile(path: String): Boolean {
        return File(path).isFile
    }

    /**
     * 确保目标文件的父目录存在，不存在则自动创建
     */
    fun ensureParentDir(file: File): Boolean {
        val parent = file.parentFile ?: return false
        if (!parent.exists()) {
            return parent.mkdirs()
        }
        return true
    }

    /**
     * 列出目录下的所有文件（非递归）
     *
     * @param dir 目标目录
     * @return 文件列表，若目录不存在或为空返回空列表
     */
    fun listFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    /**
     * 列出目录下的所有子目录（非递归）
     *
     * @param dir 目标目录
     * @return 子目录列表，若目录不存在或为空返回空列表
     */
    fun listDirectories(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
    }
}