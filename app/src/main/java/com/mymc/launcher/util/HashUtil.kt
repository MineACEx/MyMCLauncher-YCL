package com.mymc.launcher.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 哈希校验工具类
 *
 * 提供 SHA256、MD5、SHA1 三种哈希算法的计算功能，
 * 支持字符串哈希计算、文件哈希计算以及文件比对校验。
 */
object HashUtil {

    /** 缓冲区大小：8KB */
    private const val BUFFER_SIZE = 8192

    /**
     * 计算字符串的 SHA256 哈希值
     *
     * @param input 输入字符串
     * @return 十六进制小写哈希字符串
     */
    fun sha256(input: String): String {
        return hashString(input, "SHA-256")
    }

    /**
     * 计算字符串的 MD5 哈希值
     *
     * @param input 输入字符串
     * @return 十六进制小写哈希字符串
     */
    fun md5(input: String): String {
        return hashString(input, "MD5")
    }

    /**
     * 计算字符串的 SHA1 哈希值
     *
     * @param input 输入字符串
     * @return 十六进制小写哈希字符串
     */
    fun sha1(input: String): String {
        return hashString(input, "SHA-1")
    }

    /**
     * 计算文件的 SHA256 哈希值
     *
     * @param file 目标文件
     * @return 十六进制小写哈希字符串，若文件不存在或读取失败返回空字符串
     */
    fun sha256File(file: File): String {
        return hashFile(file, "SHA-256")
    }

    /**
     * 计算文件的 MD5 哈希值
     *
     * @param file 目标文件
     * @return 十六进制小写哈希字符串，若文件不存在或读取失败返回空字符串
     */
    fun md5File(file: File): String {
        return hashFile(file, "MD5")
    }

    /**
     * 计算文件的 SHA1 哈希值
     *
     * @param file 目标文件
     * @return 十六进制小写哈希字符串，若文件不存在或读取失败返回空字符串
     */
    fun sha1File(file: File): String {
        return hashFile(file, "SHA-1")
    }

    /**
     * 根据算法名称计算字符串的哈希值
     *
     * @param input     输入字符串
     * @param algorithm 哈希算法名称，如 "SHA-256"、"MD5"、"SHA-1"
     * @return 十六进制小写哈希字符串
     */
    fun hashString(input: String, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    /**
     * 根据算法名称计算文件的哈希值
     *
     * @param file      目标文件
     * @param algorithm 哈希算法名称
     * @return 十六进制小写哈希字符串，若文件不存在或读取失败返回空字符串
     */
    fun hashFile(file: File, algorithm: String): String {
        if (!file.exists() || !file.isFile) return ""
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            LogUtil.error("HashUtil", "文件哈希计算失败: ${file.absolutePath}", e)
            ""
        }
    }

    /**
     * 比对两个文件内容是否一致（基于 SHA256 哈希比较）
     *
     * @param file1 第一个文件
     * @param file2 第二个文件
     * @return 两个文件哈希值相同返回 true，否则返回 false
     */
    fun compareFiles(file1: File, file2: File): Boolean {
        val hash1 = sha256File(file1)
        val hash2 = sha256File(file2)
        if (hash1.isEmpty() || hash2.isEmpty()) return false
        return hash1 == hash2
    }

    /**
     * 校验文件哈希值是否与期望值一致
     *
     * @param file           目标文件
     * @param expectedHash   期望的哈希值（十六进制小写）
     * @param algorithm      使用的哈希算法，默认 "SHA-256"
     * @return 哈希值匹配返回 true，否则返回 false
     */
    fun verifyFileHash(file: File, expectedHash: String, algorithm: String = "SHA-256"): Boolean {
        val actualHash = hashFile(file, algorithm)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * 将字节数组转换为十六进制小写字符串
     *
     * @param bytes 字节数组
     * @return 十六进制小写字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            result.append(hexChars[value ushr 4])
            result.append(hexChars[value and 0x0F])
        }
        return result.toString()
    }
}