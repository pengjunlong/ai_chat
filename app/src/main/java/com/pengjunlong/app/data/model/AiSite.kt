package com.pengjunlong.app.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.pengjunlong.app.R

/**
 * AI 网站配置数据模型
 *
 * 只收录**无需登录**即可使用的 AI Chat 网页版。
 * 次数限制主要依赖客户端 Cookie / LocalStorage，清除缓存可重置 Session。
 *
 * @param id                唯一标识（与底部导航菜单项 id 对应）
 * @param label             显示名称
 * @param url               网站首页地址
 * @param iconRes           底部导航图标资源
 * @param clearCacheNote    清缓存后的 Toast 提示文字
 * @param useDesktopMode    true=PC UA + 宽视口（默认），false=移动 UA + 窄视口
 * @param overrideUserAgent 完全自定义 UA，不为 null 时优先级高于 useDesktopMode
 * @param extraHeaders      加载首页时附加的额外请求头（如 Referer/Origin）
 * @param jsInjection       页面加载完成后注入执行的 JS，用于修复 viewport / 渲染问题
 */
data class AiSite(
    @IdRes val id: Int,
    val label: String,
    val url: String,
    @DrawableRes val iconRes: Int,
    val clearCacheNote: String = "缓存已清除，Session 已重置",
    val useDesktopMode: Boolean = true,
    val overrideUserAgent: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val jsInjection: String? = null,
)

/**
 * 无需登录的 AI Chat 网站列表
 *
 * 入选标准：
 * 1. 无需注册/登录可直接对话
 * 2. 次数限制基于客户端存储（Cookie / LocalStorage），清缓存可有效重置
 */
object AiSiteList {

    /** PC User-Agent（Windows Chrome），绕过"请用电脑访问"的跳转 */
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

    /** 移动 User-Agent（Android Chrome），用于原生响应式界面的站点 */
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.6367.82 Mobile Safari/537.36"

    /**
     * 天工 AI / 智谱清言 viewport 修复 JS：
     *
     * 问题：这两个站点的页面 <meta viewport> 不含 initial-scale，
     * Android WebView 默认以 980px 虚拟宽度渲染桌面版，内容被缩小到几乎不可见。
     *
     * 修复：强制将 viewport 设为设备宽度、初始缩放 1:1，
     * 同时保留用户可缩放（对桌面版页面有实用价值）。
     */
    private const val JS_FIX_VIEWPORT = """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
            }
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
        })();
    """

    val sites: List<AiSite> = listOf(

        // ── 通义千问 ──────────────────────────────────────────────────────────
        // 官方 PC 聊天入口已迁移至 qianwen.com，tongyi.aliyun.com 会被阿里云 WAF 拦截
        AiSite(
            id = R.id.nav_qianwen,
            label = "通义千问",
            url = "https://www.qianwen.com/",
            iconRes = R.drawable.ic_nav_qianwen,
            clearCacheNote = "Session 已重置，次数已刷新 ✓",
            useDesktopMode = true,
            extraHeaders = mapOf(
                "Referer" to "https://www.qianwen.com/",
                "Origin"  to "https://www.qianwen.com",
            ),
        ),

        // ── 天工 AI ───────────────────────────────────────────────────────────
        // 天工只有一套 Web UI（响应式），移动 UA 会弹"下载 APP"悬浮层；
        // 使用 PC UA + useWideViewPort 加载，再用 JS 调整 viewport 适配手机屏宽。
        AiSite(
            id = R.id.nav_tiangong,
            label = "天工 AI",
            url = "https://www.tiangong.cn/chat",
            iconRes = R.drawable.ic_nav_tiangong,
            clearCacheNote = "Session 已重置，次数已刷新 ✓",
            useDesktopMode = true,
            jsInjection = JS_FIX_VIEWPORT,
        ),

        // ── Kimi ──────────────────────────────────────────────────────────────
        AiSite(
            id = R.id.nav_kimi,
            label = "Kimi",
            url = "https://kimi.moonshot.cn",
            iconRes = R.drawable.ic_nav_kimi,
            clearCacheNote = "Session 已重置，请稍等片刻再使用以避免 IP 限速",
            useDesktopMode = true,
        ),

        // ── 豆包 ──────────────────────────────────────────────────────────────
        AiSite(
            id = R.id.nav_doubao,
            label = "豆包",
            url = "https://www.doubao.com/chat/",
            iconRes = R.drawable.ic_nav_doubao,
            clearCacheNote = "Session 已重置，已同步刷新 User-Agent 以规避指纹识别",
            useDesktopMode = true,
        ),

        // ── 智谱清言 ──────────────────────────────────────────────────────────
        // /main/alltoolsdetail 被阿里云 WAF (CF_APP_WAF) 拦截 → 白屏
        // /main/detail 无 WAF 保护，可正常加载；注入 viewport JS 防止 980px 渲染问题
        AiSite(
            id = R.id.nav_chatglm,
            label = "智谱清言",
            url = "https://chatglm.cn/main/detail",
            iconRes = R.drawable.ic_nav_chatglm,
            clearCacheNote = "Session 已重置，请控制重置频率以避免触发 IP 速率限制",
            useDesktopMode = true,
            jsInjection = JS_FIX_VIEWPORT,
        ),
    )

    fun findById(id: Int): AiSite? = sites.firstOrNull { it.id == id }
}

