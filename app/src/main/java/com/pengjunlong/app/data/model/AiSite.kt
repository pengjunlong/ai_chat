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
 * @param id         唯一标识（与底部导航菜单项 id 对应）
 * @param label      显示名称
 * @param url        网站地址
 * @param iconRes    底部导航图标资源
 * @param clearCacheNote  清缓存效果说明（用于 Snackbar 提示）
 */
data class AiSite(
    @IdRes val id: Int,
    val label: String,
    val url: String,
    @DrawableRes val iconRes: Int,
    val clearCacheNote: String = "缓存已清除，Session 已重置",
)

/**
 * 无需登录的 AI Chat 网站列表
 *
 * 入选标准：
 * 1. 无需注册/登录可直接对话
 * 2. 次数限制基于客户端存储（Cookie / LocalStorage），清缓存可有效重置
 *
 * PC User-Agent 在 [com.pengjunlong.app.ui.web.WebFragment] 中统一注入，绕过移动端跳转限制。
 */
object AiSiteList {

    val sites: List<AiSite> = listOf(
        AiSite(
            id = R.id.nav_qianwen,
            label = "通义千问",
            url = "https://tongyi.aliyun.com/qianwen/",
            iconRes = R.drawable.ic_nav_qianwen,
            clearCacheNote = "Session 已重置，阿里系宽松，次数已刷新 ✓",
        ),
        AiSite(
            id = R.id.nav_tiangong,
            label = "天工 AI",
            url = "https://www.tiangong.cn",
            iconRes = R.drawable.ic_nav_tiangong,
            clearCacheNote = "Session 已重置，天工以客户端计数，次数已刷新 ✓",
        ),
        AiSite(
            id = R.id.nav_kimi,
            label = "Kimi",
            url = "https://kimi.moonshot.cn",
            iconRes = R.drawable.ic_nav_kimi,
            clearCacheNote = "Session 已重置，请稍等片刻再使用以避免 IP 限速",
        ),
        AiSite(
            id = R.id.nav_doubao,
            label = "豆包",
            url = "https://www.doubao.com/chat/",
            iconRes = R.drawable.ic_nav_doubao,
            clearCacheNote = "Session 已重置，已同步刷新 User-Agent 以规避指纹识别",
        ),
        AiSite(
            id = R.id.nav_chatglm,
            label = "智谱清言",
            url = "https://chatglm.cn",
            iconRes = R.drawable.ic_nav_chatglm,
            clearCacheNote = "Session 已重置，请控制重置频率以避免触发 IP 速率限制",
        ),
    )

    fun findById(id: Int): AiSite? = sites.firstOrNull { it.id == id }
}

