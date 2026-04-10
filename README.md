# AI Chat

一个基于 **Kotlin + WebView** 的 Android 应用，将多个**无需登录**的网页版 AI Chat 工具整合进一个 APK，底部导航快速切换，PC User-Agent 绕过移动端限制，内置清除缓存功能帮助重置 Session 使用次数。

## 目录

- [功能特性](#功能特性)
- [收录工具](#收录工具)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [框架模块说明](#框架模块说明)
- [开发指南](#开发指南)
- [构建与发布](#构建与发布)

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 🗂️ **底部导航** | 5 个 AI 工具一键切换，Fragment hide/show 保活 WebView，切换时不重载页面 |
| 🖥️ **PC 模式** | 统一注入 `Chrome/124 Windows NT 10.0` User-Agent，绕过各平台移动端跳转限制 |
| 🗑️ **清除缓存** | 右下角 FAB 一键清除 Cookie + LocalStorage + IndexedDB + HTTP 缓存，重置匿名 Session |
| ↩️ **返回键** | 优先 WebView 内页后退，无历史时退出 App |
| ❌ **错误重试** | 网络异常时显示错误页，一键重新加载 |
| 📶 **加载进度** | 顶部细线进度条实时显示页面加载进度 |
| 🔄 **检查更新** | ActionBar 溢出菜单内置检查更新，自动读取 GitHub Release |

---

## 收录工具

只收录**无需注册/登录**即可直接对话、且次数限制基于客户端存储（可通过清缓存重置）的工具：

| 工具 | URL | 清缓存效果 | 说明 |
|------|-----|----------|------|
| **通义千问** | tongyi.aliyun.com | ✅ 最佳 | 阿里系宽松，清除后重新分配免费额度 |
| **天工 AI** | tiangong.cn | ✅ 最佳 | 以客户端 LocalStorage 计数，清除即重置 |
| **Kimi** | kimi.moonshot.cn | ⚠️ 有效 | 清 Cookie 可重置 Session，注意控制频率避免 IP 限速 |
| **豆包** | doubao.com | ⚠️ 有效 | 配合 PC UA 规避字节系设备指纹识别 |
| **智谱清言** | chatglm.cn | ⚠️ 有效 | 清 Cookie 有效，避免短时间内频繁重置 |

> **为什么不收录 ChatGPT / Claude / Gemini / DeepSeek？**
> 这些工具均强制要求账号登录，无游客模式，清缓存无意义。

---

## 技术栈

| 类别 | 库 | 版本 |
|------|----|------|
| 语言 | Kotlin | 2.1.0 |
| 异步 | Kotlin Coroutines | 1.9.0 |
| UI | AndroidX + Material Components | — |
| 崩溃上报 | ACRA | 5.13.1 |
| 日志 | Timber | 5.0.1 |
| 网络（检查更新） | OkHttp + Retrofit + Gson | 4.12.0 / 2.11.0 |
| 存储 | MMKV | 2.2.2 |
| 构建 | AGP + Version Catalog | 8.7.3 |

---

## 项目结构

```
ai_chat/
├── gradle/
│   └── libs.versions.toml          # 统一版本目录（Version Catalog）
├── settings.gradle.kts             # 模块注册
├── build.gradle.kts                # 根构建
│
├── app/                            # 主应用模块
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/pengjunlong/app/
│       │   ├── SampleApplication.kt          # Application，框架初始化
│       │   ├── data/
│       │   │   └── model/
│       │   │       └── AiSite.kt             # AI 网站数据模型 + AiSiteList
│       │   └── ui/
│       │       ├── main/
│       │       │   └── MainActivity.kt       # 底部导航 + Fragment 切换
│       │       └── web/
│       │           └── WebFragment.kt        # WebView + PC UA + 清缓存
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml         # CoordinatorLayout + BottomNavigationView
│           │   └── fragment_web.xml          # WebView + 进度条 + 错误页 + FAB
│           ├── menu/
│           │   └── bottom_nav_menu.xml       # 底部导航菜单（5 个 AI 工具）
│           ├── color/
│           │   └── bottom_nav_item_color.xml # 导航栏图标选中/未选中颜色
│           ├── drawable/
│           │   ├── ic_nav_qianwen.xml        # 通义千问图标
│           │   ├── ic_nav_tiangong.xml       # 天工 AI 图标
│           │   ├── ic_nav_kimi.xml           # Kimi 图标
│           │   ├── ic_nav_doubao.xml         # 豆包图标
│           │   ├── ic_nav_chatglm.xml        # 智谱清言图标
│           │   └── ic_clear_cache.xml        # 清除缓存 FAB 图标
│           └── values/
│               ├── strings.xml
│               ├── colors.xml                # 深色主题配色
│               └── themes.xml               # NoActionBar 深色主题
│
├── framework-core/                 # 基础核心库
├── framework-crash/                # 异常上报（ACRA）
├── framework-logger/               # 日志（Timber）
├── framework-network/              # 网络 + 检查更新
├── framework-storage/              # 存储（MMKV）
└── framework-ui/                   # UI 基类（BaseActivity / BaseFragment）
```

---

## 框架模块说明

本项目复用了一套多模块框架基础库，各模块职责如下：

### framework-core

所有模块的基础依赖，提供全局 `Context`、Application 基类、统一初始化调度器。

| 类 | 说明 |
|----|------|
| `AppContext` | 全局 `Context` 持有 |
| `BaseApplication` | Application 基类，子类重写 `registerInitializers()` |
| `FrameworkInitializer` | 按优先级调度各模块初始化 |

### framework-ui

| 类 | 说明 |
|----|------|
| `BaseActivity<VB>` | ViewBinding 基类，内置检查更新菜单 |
| `BaseFragment<VB>` | ViewBinding 基类，`onDestroyView` 自动释放 binding |
| `BaseViewModel` | 封装 `request{}` 自动处理 loading / error |
| `View.setOnSingleClickListener {}` | 防抖点击（默认 500ms） |

### framework-network

负责检查更新功能（读取 GitHub Release API）。

| 类 | 说明 |
|----|------|
| `UpdateChecker` | 检查 GitHub Release 是否有新版本 |
| `safeApiCall {}` | 协程安全调用，异常转为 `ApiResult.Error` |

### framework-storage / framework-logger / framework-crash

分别封装 MMKV、Timber 日志、ACRA 崩溃上报，开箱即用。

---

## 开发指南

### 添加新的 AI 工具

1. 在 `AiSite.kt` 的 `AiSiteList.sites` 中追加一条记录：

```kotlin
AiSite(
    id = R.id.nav_new_tool,
    label = "新工具",
    url = "https://example.com",
    iconRes = R.drawable.ic_nav_new_tool,
    clearCacheNote = "Session 已重置",
)
```

2. 在 `res/menu/bottom_nav_menu.xml` 中添加对应菜单项：

```xml
<item
    android:id="@+id/nav_new_tool"
    android:icon="@drawable/ic_nav_new_tool"
    android:title="新工具" />
```

3. 创建 `res/drawable/ic_nav_new_tool.xml`（Vector Drawable，24×24dp）

> BottomNavigationView 最多显示 5 个 Tab，超过 5 个时建议换用侧边抽屉或滚动式导航。

### 清缓存机制说明

`WebFragment.clearCacheAndReload()` 执行以下五步：

```
1. webView.clearCache(true)              → 清除磁盘 HTTP 缓存
2. CookieManager.removeAllCookies()      → 清除所有 Cookie（含 Session Token）
3. webView.clearHistory()                → 清除浏览历史
4. evaluateJavascript(...)               → JS 清除 localStorage / sessionStorage / IndexedDB
5. 重新加载首页 URL
```

### PC User-Agent

所有 WebView 统一使用以下 UA，模拟 Windows Chrome 桌面浏览器：

```
Mozilla/5.0 (Windows NT 10.0; Win64; x64)
AppleWebKit/537.36 (KHTML, like Gecko)
Chrome/124.0.0.0 Safari/537.36
```

如需针对特定网站定制 UA，可在 `AiSite` 中扩展 `userAgent` 字段，在 `WebFragment.setupWebView()` 中按需读取。

---

## 构建与发布

### 本地构建

```bash
# 编译 Debug 包（输出 AI Chat-debug.apk）
./gradlew assembleDebug

# 编译 Release 包（需配置签名）
./gradlew assembleRelease

# 运行所有检查
./gradlew check

# 清理
./gradlew clean
```

### 配置签名

在项目根目录创建 `keystore.properties`（**不要提交到 Git**）：

```properties
storeFile=../release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

在 `app/build.gradle.kts` 中读取：

```kotlin
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### GitHub Actions 自动发布

项目包含两个自动化工作流：

| 工作流 | 触发方式 | 功能 |
|--------|---------|------|
| `ci.yml` | 推送 `v*` Tag 或向 `main`/`develop` 发起 PR | 编译检查 + 单元测试 + Lint |
| `release.yml` | 推送 `v*` Tag（如 `v1.0.0`） | 构建签名 APK + 创建 GitHub Release |

在 GitHub → Settings → Secrets → Actions 中添加：

| Secret 名称 | 说明 |
|------------|------|
| `KEYSTORE_BASE64` | `base64 release.jks` 的输出内容 |
| `KEYSTORE_PASSWORD` | KeyStore 密码 |
| `KEY_ALIAS` | Key 别名 |
| `KEY_PASSWORD` | Key 密码 |

```bash
# 生成 Base64（macOS，结果自动复制到剪贴板）
base64 -i release.jks | pbcopy

