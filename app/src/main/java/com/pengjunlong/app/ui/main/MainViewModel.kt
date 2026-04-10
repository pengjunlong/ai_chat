package com.pengjunlong.app.ui.main

import com.pengjunlong.app.data.model.Post
import com.pengjunlong.app.data.repository.PostRepository
import com.example.framework.crash.CrashReporter
import com.example.framework.storage.KVStore
import com.example.framework.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 示例 ViewModel
 *
 * 演示：
 * - 通过 [request] 发起网络请求（自动 loading / error 处理）
 * - 通过 [KVStore] 读写本地存储
 * - 通过 [CrashReporter.putCustomData] 附加用户信息到崩溃报告
 */
class MainViewModel : BaseViewModel() {

    private val repo = PostRepository()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    init {
        loadPosts()
        // 示例：附加用户 ID 到崩溃报告，方便定位问题
        val userId = KVStore.getString("user_id", "anonymous")
        CrashReporter.putCustomData("user_id", userId)
    }

    fun loadPosts() = request(
        block = { repo.fetchPosts() },
        onSuccess = { posts ->
            _posts.value = posts
            KVStore.putInt("last_posts_count", posts.size)
        },
    )

    fun refresh() = loadPosts()
}

