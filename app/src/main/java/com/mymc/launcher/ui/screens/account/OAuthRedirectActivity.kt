package com.mymc.launcher.ui.screens.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.mymc.launcher.service.account.AccountManager

/**
 * OAuth 重定向 Activity。
 * 处理微软账号 OAuth 登录回调，从 Intent 中提取授权码（auth code），
 * 然后通知 AccountManager 完成 token 兑换与账号绑定。
 *
 * 此 Activity 注册在 AndroidManifest.xml 中作为自定义 scheme 的接收者，
 * 例如：mymc-launcher://oauth/callback
 */
class OAuthRedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从 Intent 中提取回调 URI，解析授权码
        val data: Uri? = intent?.data
        if (data != null) {
            handleOAuthCallback(data)
        } else {
            // 无回调数据时直接结束
            finish()
        }
    }

    /**
     * 处理 OAuth 回调 URI，提取 auth code 并通知 AccountManager。
     *
     * @param uri 包含授权码的回调 URI
     */
    private fun handleOAuthCallback(uri: Uri) {
        // 获取 AccountManager 实例
        val accountManager = AccountManager.getInstance(applicationContext)

        // 尝试从 query 参数中提取 code
        val authCode = uri.getQueryParameter("code")

        if (!authCode.isNullOrBlank()) {
            // 通知 AccountManager 完成微软 OAuth 登录流程
            accountManager.handleMicrosoftOAuthCode(authCode)
        } else {
            // 可能从 fragment 中获取（隐式流程）
            val fragment = uri.fragment
            if (fragment != null) {
                val fragmentUri = Uri.parse("?$fragment")
                val fragmentCode = fragmentUri.getQueryParameter("code")
                if (!fragmentCode.isNullOrBlank()) {
                    accountManager.handleMicrosoftOAuthCode(fragmentCode)
                }
            }
        }

        // 处理完成后关闭 Activity
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理单实例模式下新的回调请求
        intent.data?.let { handleOAuthCallback(it) }
    }
}