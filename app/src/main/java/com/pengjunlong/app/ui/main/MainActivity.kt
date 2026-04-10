package com.pengjunlong.app.ui.main

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.commit
import com.example.framework.ui.base.BaseActivity
import com.pengjunlong.app.BuildConfig
import com.pengjunlong.app.R
import com.pengjunlong.app.data.model.AiSiteList
import com.pengjunlong.app.databinding.ActivityMainBinding
import com.pengjunlong.app.ui.web.WebFragment

/**
 * 主界面 Activity
 *
 * 功能：
 * - 底部导航栏（5 个 AI 工具快速切换）
 * - Fragment 切换时 hide/show 保活 WebView，避免重复加载
 * - 返回键优先交给当前 WebFragment 处理（WebView 内页返回）
 */
class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    override val updateRepoOwner = BuildConfig.UPDATE_REPO_OWNER
    override val updateRepoName  = BuildConfig.UPDATE_REPO_NAME

    /** 当前选中的 Tab 对应的菜单 id */
    private var currentItemId: Int = -1

    override fun initViews() {
        setupBottomNavigation()
        setupBackHandler()

        // 初始化时选中第一个 Tab
        val firstSite = AiSiteList.sites.first()
        binding.bottomNavigation.selectedItemId = firstSite.id
    }

    override fun initObservers() {
        // 无全局数据观察，各 Fragment 内部自行管理
    }

    // ── 底部导航 ──────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId != currentItemId) {
                switchToSite(item.itemId)
            }
            true
        }
    }

    /**
     * 切换到指定 Tab 对应的 Fragment。
     *
     * 策略：**hide/show 保活**（而非 replace）：
     * - Fragment 切换时不销毁 WebView，避免页面重新加载
     * - 首次切换到某个 Tab 时才创建对应的 WebFragment
     */
    private fun switchToSite(siteId: Int) {
        val tag = "web_$siteId"
        val fm = supportFragmentManager

        fm.commit {
            setReorderingAllowed(true)

            // 隐藏当前 Fragment
            fm.findFragmentByTag("web_$currentItemId")?.let { hide(it) }

            // 查找或创建目标 Fragment
            val target = fm.findFragmentByTag(tag)
            if (target == null) {
                add(R.id.fragmentContainer, WebFragment.newInstance(siteId), tag)
            } else {
                show(target)
            }
        }

        currentItemId = siteId
    }

    // ── 返回键处理 ────────────────────────────────────────────────────────────

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentFragment = currentWebFragment()
                    if (currentFragment?.onBackPressed() == true) {
                        // WebView 内页后退，已处理
                        return
                    }
                    // 交回系统处理（最终退出 App）
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun currentWebFragment(): WebFragment? {
        return supportFragmentManager
            .findFragmentByTag("web_$currentItemId") as? WebFragment
    }
}

