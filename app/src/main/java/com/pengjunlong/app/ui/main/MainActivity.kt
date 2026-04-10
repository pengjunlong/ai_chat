package com.pengjunlong.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.framework.ui.base.BaseActivity
import com.example.framework.ui.ext.toast
import com.pengjunlong.app.BuildConfig
import com.pengjunlong.app.databinding.ActivityMainBinding

/**
 * 示例 MainActivity
 *
 * 演示：
 * - 继承 [BaseActivity]，通过 ViewBinding 访问 View
 * - 通过 [launchWhenStarted] 安全订阅 Flow
 * - 统一处理 loading / error 状态
 * - 检查更新入口由 [BaseActivity] 统一放在 ActionBar 溢出菜单（⋮）中
 */
class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    private val viewModel: MainViewModel by viewModels()

    // ── 检查更新：从 BuildConfig 读取仓库信息（在 app/build.gradle.kts 中配置）──
    override val updateRepoOwner = BuildConfig.UPDATE_REPO_OWNER
    override val updateRepoName  = BuildConfig.UPDATE_REPO_NAME

    /** 请求通知权限（Android 13+，崩溃通知需要） */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) toast("⚠️ 通知权限被拒绝，崩溃上报通知将无法显示")
    }

    override fun initViews() {
        // ActionBar 标题自动读取 AndroidManifest / strings.xml 中的 app_name，无需手动设置

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        // Android 13+ 请求通知权限
        requestNotificationPermissionIfNeeded()
    }

    override fun initObservers() {
        launchWhenStarted {
            viewModel.posts.collect { posts ->
                binding.tvContent.text = buildString {
                    appendLine("✅ 已加载 ${posts.size} 条数据")
                    appendLine()
                    posts.take(5).forEachIndexed { index, post ->
                        appendLine("${index + 1}. ${post.title}")
                    }
                    if (posts.size > 5) appendLine("... 共 ${posts.size} 条")
                }
            }
        }

        launchWhenStarted {
            viewModel.isLoading.collect { loading ->
                showLoading(loading)
            }
        }

        launchWhenStarted {
            viewModel.errorEvent.collect { message ->
                showError(message)
            }
        }
    }

    override fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun showError(message: String) {
        toast("请求失败：$message")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
}

