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
 * 1. 按站点配置选择 PC / 移动端 User-Agent（支持完全自定义 UA）
 * 2. 按站点配置注入额外请求头（如 Referer、Origin）
 * 3. 页面加载完成后注入站点专属 JS（修复 viewport / 渲染问题）
 * 4. 顶部进度条显示加载进度
 * 5. 网络错误时显示重试界面
 * 6. 右下角 FAB 一键清除缓存（Cookie + LocalStorage + Cache），重置 Session
 *
 * @param siteId 对应 [AiSiteList] 中 [AiSite.id]，通过 [newInstance] 创建
 */
class WebFragment : BaseFragment<FragmentWebBinding>(FragmentWebBinding::inflate) {

    companion object {
        private const val ARG_SITE_ID = "site_id"

        fun newInstance(siteId: Int) = WebFragment().apply {
            arguments = Bundle().apply { putInt(ARG_SITE_ID, siteId) }
        }
    }

    private lateinit var site: AiSite

    /** 是否发生了加载错误（用于区分正常完成和错误页） */
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
                // JavaScript（AI 聊天网站必须开启）
                javaScriptEnabled = true

                // User-Agent 选择优先级：
                //   overrideUserAgent（完全自定义）> useDesktopMode 决定 PC/移动 UA
                userAgentString = site.overrideUserAgent
                    ?: if (site.useDesktopMode) AiSiteList.UA_DESKTOP else AiSiteList.UA_MOBILE

                // 桌面宽视口：PC UA 站点使用网站设计宽度渲染（天工/清言再靠 jsInjection 修复缩放）
                useWideViewPort = site.useDesktopMode
                loadWithOverviewMode = site.useDesktopMode

                // 支持缩放（桌面版页面较宽，需要捏合缩放）
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // 缓存：正常使用缓存；清缓存后会重新从网络获取
                cacheMode = WebSettings.LOAD_DEFAULT

                // DOM Storage（LocalStorage / SessionStorage）
                // 大多数 AI 工具用它存匿名 Session Token 和使用计数
                domStorageEnabled = true

                // IndexedDB（智谱清言等工具的状态存储）
                @Suppress("DEPRECATION")
                databaseEnabled = true

                // 混合内容：允许 HTTPS 页面加载 HTTP 子资源
                // 默认 MIXED_CONTENT_NEVER_ALLOW 会导致部分静态资源 404，造成白屏
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // 媒体：不要求用户手势才能播放（部分工具有音频反馈）
                mediaPlaybackRequiresUserGesture = false
            }

            webViewClient = AiWebViewClient()
            webChromeClient = AiWebChromeClient()

            // 启用 Cookie（部分 WebView 默认关闭第三方 Cookie）
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
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

    private fun showClearCacheConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_cache))
            .setMessage(getString(R.string.clear_cache_confirm))
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> clearCacheAndReload() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 清除所有客户端存储，重置匿名 Session：
     * 1. HTTP 磁盘缓存
     * 2. Cookie（服务端 Session Token 存在这里，清除后服务端视为新访客）
     * 3. WebView 内存数据（历史、表单）
     * 4. JS 清除 localStorage / sessionStorage / IndexedDB
     * 5. 重新加载首页
     */
    private fun clearCacheAndReload() {
        binding.webView.apply {
            // 1. HTTP 缓存
            clearCache(true)

            // 2. Cookie
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }

            // 3. 历史 & 表单
            clearHistory()
            clearFormData()

            // 4. localStorage / sessionStorage / IndexedDB
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

        // 5. 重新加载
        showErrorLayout(false)
        loadUrl(site.url)
        toast(site.clearCacheNote)
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────────

    /**
     * 加载 URL，同时附加站点专属请求头（如通义的 Referer / Origin）。
     */
    private fun loadUrl(url: String) {
        hasLoadError = false
        if (site.extraHeaders.isNotEmpty()) {
            binding.webView.loadUrl(url, site.extraHeaders)
        } else {
            binding.webView.loadUrl(url)
        }
    }

    /**
     * 在页面加载完成后注入站点专属 JS。
     *
     * 用途举例：
     * - 智谱清言：修复 viewport meta，避免 WebView 以 980px 宽渲染导致内容缩小不可见
     * - 天工 AI：调整 viewport 缩放比例，使桌面版布局适配手机屏宽
     */
    private fun injectJsIfNeeded() {
        val js = site.jsInjection ?: return
        binding.webView.evaluateJavascript(js.trimIndent(), null)
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
            } else {
                // 页面正常加载完成后注入站点专属 JS（修复 viewport / 渲染问题）
                injectJsIfNeeded()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // 只处理主框架错误（忽略图片/JS 等资源加载失败）
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

    // ── 返回键（由 MainActivity 调用） ───────────────────────────────────────

    /**
     * WebView 有历史则后退并返回 true，否则返回 false 交由 Activity 处理。
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

