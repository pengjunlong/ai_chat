package com.pengjunlong.app.ui.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.example.framework.ui.base.BaseFragment
import com.example.framework.ui.ext.toast
import com.pengjunlong.app.R
import com.pengjunlong.app.data.model.AiSite
import com.pengjunlong.app.data.model.AiSiteList
import com.pengjunlong.app.databinding.FragmentWebBinding
import com.pengjunlong.app.ui.web.WebFragment.Companion.newInstance

/**
 * WebFragment：单个 AI Chat 网页的容器
 *
 * 功能：
 * 1. 使用 PC User-Agent 加载网站，绕过移动端跳转限制
 * 2. 顶部进度条显示加载进度
 * 3. 网络错误时显示重试界面
 * 4. 右下角 FAB 一键清除缓存（Cookie + LocalStorage + Cache），重置 Session
 *
 * @param siteId 对应 [AiSiteList] 中 [AiSite.id]，通过 [newInstance] 创建
 */
class WebFragment : BaseFragment<FragmentWebBinding>(FragmentWebBinding::inflate) {

    companion object {
        private const val ARG_SITE_ID = "site_id"

        /**
         * PC 模式 User-Agent（Chrome 最新版 Windows）
         * 用于绕过各 AI 工具的移动端限制/跳转
         */
        private const val PC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

        fun newInstance(siteId: Int) = WebFragment().apply {
            arguments = Bundle().apply { putInt(ARG_SITE_ID, siteId) }
        }
    }

    private lateinit var site: AiSite

    // 是否发生了加载错误（用于区分正常加载完成和错误页）
    private var hasLoadError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val siteId = requireArguments().getInt(ARG_SITE_ID)
        site = AiSiteList.findById(siteId)
            ?: error("Unknown site id: $siteId")
    }

    override fun initViews() {
        setupWebView()
        setupFab()
        setupReloadButton()

        // 首次加载
        if (binding.webView.url == null) {
            loadUrl(site.url)
        }
    }

    // ── WebView 配置 ──────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                // 启用 JavaScript（AI 聊天网站必须）
                javaScriptEnabled = true

                // PC 模式 User-Agent，绕过移动端检测
                userAgentString = PC_USER_AGENT

                // 让网站以桌面模式渲染
                useWideViewPort = true
                loadWithOverviewMode = true

                // 支持缩放
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // 缓存策略：正常加载使用缓存，清除后重新从网络获取
                cacheMode = WebSettings.LOAD_DEFAULT

                // DOM Storage（大多数 AI 工具用 LocalStorage 存 Session）
                domStorageEnabled = true

                // 数据库存储（部分工具使用 IndexedDB）
                @Suppress("DEPRECATION")
                databaseEnabled = true

                // 允许混合内容（部分资源可能走 http）
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // 媒体自动播放（静音模式）
                mediaPlaybackRequiresUserGesture = false
            }

            webViewClient = AiWebViewClient()
            webChromeClient = AiWebChromeClient()
        }
    }

    private fun setupFab() {
        binding.fabClearCache.setOnClickListener {
            showClearCacheConfirmDialog()
        }
    }

    private fun setupReloadButton() {
        binding.btnReload.setOnClickListener {
            showErrorLayout(false)
            loadUrl(site.url)
        }
    }

    // ── 清除缓存逻辑 ──────────────────────────────────────────────────────────

    /**
     * 弹出确认对话框，防止误操作
     */
    private fun showClearCacheConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_cache))
            .setMessage(getString(R.string.clear_cache_confirm))
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> clearCacheAndReload() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 清除所有客户端存储，重置 Session：
     * 1. WebView 缓存（磁盘缓存）
     * 2. Cookie（服务器 Session Token）
     * 3. DOM Storage / LocalStorage（匿名 UID、计数器）
     * 4. 历史记录（防止 back 回到旧页面）
     */
    private fun clearCacheAndReload() {
        binding.webView.apply {
            // 1. 清除 HTTP 缓存
            clearCache(true)

            // 2. 清除 Cookie（最关键：服务端 Session Token 存在这里）
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }

            // 3. 清除 WebView 内存数据（包含 LocalStorage、SessionStorage）
            clearHistory()
            clearFormData()

            // 4. 清除 LocalStorage / IndexedDB（通过加载 javascript: 清除）
            evaluateJavascript(
                """
                (function() {
                    try { localStorage.clear(); } catch(e) {}
                    try { sessionStorage.clear(); } catch(e) {}
                    try {
                        indexedDB.databases().then(function(dbs) {
                            dbs.forEach(function(db) { indexedDB.deleteDatabase(db.name); });
                        });
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }

        // 5. 清除 WebView 应用数据目录中的数据库文件
        try {
            val webviewDir = requireContext().getDir("webview", android.content.Context.MODE_PRIVATE)
            webviewDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            // 忽略，不影响主流程
        }

        // 重新加载主页
        showErrorLayout(false)
        loadUrl(site.url)

        toast(site.clearCacheNote)
    }

    // ── 辅助方法 ─────────────────────────────────────────────────────────────

    private fun loadUrl(url: String) {
        hasLoadError = false
        binding.webView.loadUrl(url)
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.isVisible = show
    }

    private fun showErrorLayout(show: Boolean) {
        binding.errorLayout.isVisible = show
        binding.webView.isVisible = !show
    }

    // ── WebViewClient ─────────────────────────────────────────────────────────

    private inner class AiWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            hasLoadError = false
            showProgress(true)
            showErrorLayout(false)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            showProgress(false)
            if (hasLoadError) {
                showErrorLayout(true)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // 只处理主框架加载失败（忽略资源加载错误）
            if (request.isForMainFrame) {
                hasLoadError = true
                showProgress(false)
                showErrorLayout(true)
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            // 所有跳转都在 WebView 内打开，不调起外部浏览器
            return false
        }
    }

    // ── WebChromeClient ───────────────────────────────────────────────────────

    private inner class AiWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.progress = newProgress
            showProgress(newProgress < 100)
        }
    }

    // ── 返回键处理（由 MainActivity 调用） ───────────────────────────────────

    /**
     * 如果 WebView 有历史可以返回，执行返回并返回 true；
     * 否则返回 false（交给 Activity 处理）。
     */
    fun onBackPressed(): Boolean {
        return if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            true
        } else {
            false
        }
    }
}

