package com.example.framework.ui.base

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.example.framework.logger.L
import com.example.framework.network.update.UpdateChecker
import com.example.framework.network.update.UpdateInfo
import com.example.framework.ui.R
import kotlinx.coroutines.launch

/**
 * Activity 基类（ViewBinding + ViewModel 模式）
 *
 * 提供：
 * - ViewBinding 自动绑定（通过泛型 + [inflate] lambda）
 * - 统一 loading / error 处理钩子
 * - [launchWhenStarted] / [launchWhenResumed] 生命周期安全协程
 * - ActionBar 溢出菜单（⋮）内置「检查更新」入口（自动读取 GitHub Release）
 *
 * ### 使用示例
 * ```kotlin
 * class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
 *
 *     private val viewModel: MainViewModel by viewModels()
 *
 *     override fun initViews() {
 *         binding.button.setOnClickListener { viewModel.loadData() }
 *     }
 *
 *     override fun initObservers() {
 *         launchWhenStarted {
 *             viewModel.data.collect { showData(it) }
 *         }
 *     }
 * }
 * ```
 *
 * ### 检查更新配置
 * 子类重写 [updateRepoOwner] 和 [updateRepoName] 即可启用；返回 null 则隐藏菜单项：
 * ```kotlin
 * override val updateRepoOwner = "your-org"
 * override val updateRepoName  = "your-app"
 * ```
 */
abstract class BaseActivity<VB : ViewBinding>(
    private val inflate: (LayoutInflater) -> VB
) : AppCompatActivity() {

    private var _binding: VB? = null

    /** ViewBinding 实例，仅在 [onCreate] ~ [onDestroy] 期间有效 */
    protected val binding: VB
        get() = _binding ?: error("Binding is accessed after onDestroy or before onCreate.")

    // ── 检查更新配置（子类按需重写）──────────────────────────────────────────────

    /** GitHub 仓库 owner，null 则不显示「检查更新」菜单项 */
    protected open val updateRepoOwner: String? = null

    /** GitHub 仓库名 */
    protected open val updateRepoName: String? = null

    /** 是否包含预发布版本（默认 false） */
    protected open val updateIncludePreRelease: Boolean = false

    private var updateChecker: UpdateChecker? = null
    private var isCheckingUpdate = false

    // ── 生命周期 ─────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflate(layoutInflater)
        setContentView(binding.root)
        L.d("${this::class.java.simpleName} onCreate")

        // 初始化 UpdateChecker（如果子类配置了仓库信息）
        val owner = updateRepoOwner
        val repo  = updateRepoName
        if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
            updateChecker = UpdateChecker(
                repoOwner           = owner,
                repoName            = repo,
                includePreRelease   = updateIncludePreRelease,
            )
        }

        initViews()
        initObservers()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        L.d("${this::class.java.simpleName} onDestroy")
    }

    // ── ActionBar 菜单 ────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (updateChecker != null) {
            menuInflater.inflate(R.menu.menu_base_activity, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_check_update) {
            triggerCheckUpdate()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ── 检查更新内部实现 ──────────────────────────────────────────────────────────

    private fun triggerCheckUpdate() {
        val checker = updateChecker ?: return
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        invalidateOptionsMenu()   // 让菜单项视觉上感知到"正在检查"

        lifecycleScope.launch {
            when (val result = checker.checkUpdate(this@BaseActivity)) {
                is com.example.framework.network.ApiResult.Success -> {
                    isCheckingUpdate = false
                    invalidateOptionsMenu()
                    val info = result.data
                    if (info.hasUpdate) {
                        showUpdateDialog(info)
                    } else {
                        onNoUpdate(info.currentVersion)
                    }
                }
                is com.example.framework.network.ApiResult.Error -> {
                    isCheckingUpdate = false
                    invalidateOptionsMenu()
                    onUpdateCheckFailed(result.message)
                }
                else -> {
                    isCheckingUpdate = false
                    invalidateOptionsMenu()
                }
            }
        }
    }

    /**
     * 展示更新弹窗（子类可重写以自定义样式）。
     * - 有 APK 下载链接 → 「立即下载」打开浏览器
     * - 无下载链接 → 「查看详情」跳转 GitHub Release 页面
     * - 强制更新 → 隐藏「以后再说」
     */
    protected open fun showUpdateDialog(info: UpdateInfo) {
        val positiveLabel = if (info.downloadUrl != null) "立即下载" else "查看详情"
        val targetUrl     = info.downloadUrl ?: info.releasePageUrl

        val builder = AlertDialog.Builder(this)
            .setTitle("发现新版本  v${info.latestVersion}")
            .setMessage(buildString {
                appendLine("当前版本：v${info.currentVersion}")
                appendLine("最新版本：v${info.latestVersion}")
                appendLine("发布时间：${info.publishedAt.take(10)}")
                if (info.releaseNotes.isNotBlank()) {
                    appendLine()
                    appendLine("更新内容：")
                    appendLine(info.releaseNotes.replace(Regex("#{1,6}\\s*"), "").take(300))
                }
            })
            .setPositiveButton(positiveLabel) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            }
            .setCancelable(!info.isForceUpdate)

        if (!info.isForceUpdate) {
            builder.setNegativeButton("以后再说", null)
        }

        builder.show()
    }

    /**
     * 已是最新版本时的回调（子类可重写，默认 Toast）
     */
    protected open fun onNoUpdate(currentVersion: String) {
        showToast("当前已是最新版本（$currentVersion）")
    }

    /**
     * 检查更新失败时的回调（子类可重写，默认 Toast）
     */
    protected open fun onUpdateCheckFailed(message: String) {
        L.w("Update check failed: $message")
        showToast("检查更新失败：$message")
    }

    // ── 子类钩子 ─────────────────────────────────────────────────────────────────

    /**
     * 初始化 View（设置点击事件、Adapter 等），此时 [binding] 已可用
     */
    protected open fun initViews() {}

    /**
     * 初始化数据观察（订阅 ViewModel 的 Flow/LiveData）
     */
    protected open fun initObservers() {}

    /**
     * 展示 loading（子类按需重写）
     */
    protected open fun showLoading(show: Boolean) {}

    /**
     * 展示错误信息（子类按需重写）
     */
    protected open fun showError(message: String) {
        L.w("${this::class.java.simpleName} error: $message")
    }

    /**
     * 在 [Lifecycle.State.STARTED] 状态下安全收集 Flow（页面不可见时自动暂停）
     */
    protected fun launchWhenStarted(block: suspend () -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                block()
            }
        }
    }

    /**
     * 在 [Lifecycle.State.RESUMED] 状态下安全收集 Flow
     */
    protected fun launchWhenResumed(block: suspend () -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                block()
            }
        }
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}

