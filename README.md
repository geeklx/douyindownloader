# 抖音去水印下载器 (TikSaver Pro)

一款基于 **Kotlin** 和 **Jetpack Compose** 打造的高颜值、现代化的 抖音视频去水印极速下载与媒体管理工具（应用内部代号 **TikSaver Pro**）。

本项目遵循 **Material Design 3 (M3)** 设计规范，致力于提供极致流畅的交互体验、高速的并发下载能力以及便捷的本地下载历史与媒体库管理。

---

## ✨ 核心功能特性

- 🚀 **极速无水印下载**：内置高效的无水印解析引擎与多线程极速断点续传下载器，下载视频既干净又快速。
- 📋 **智能剪贴板智能检测**：开启应用或返回应用时，自动识别剪贴板中的抖音分享/复制链接并弹出解析提示，免去反复手动粘贴的痛苦。
- 🛡️ **安全的隐私监听**：针对 Android 10+ (API 29+) 系统的安全剪贴板限制进行了深度优化（采用 Window Focus 机制，拒绝后台静默读取与非法调用导致的 Crash 与告警）。
- 📺 **高级视频播放与预览**：支持在下载前、下载中“边看边下”，也支持内置的高聚合视频播放器直接流畅预览本地下载的内容。
- 🧱 **全方位本地媒体库**：
  - 基于 Room 数据库的高效下载历史管理器，状态实时保存。
  - 支持视频**批量选中、一键批量删除、本地条件模糊搜索**。
  - 支持一键导出并保存到**系统内置相册**，跨应用分享更方便。
- 🎨 **极客暗色美学美化**：深沉、内敛的专业拼色夜间美学界面（Professional Polish Dark-First App Theme），提供极佳的护眼视觉体验。

---

## 🛠️ 现代化技术栈

- **UI 界面**：[Jetpack Compose](https://developer.android.com/compose) (完全声明式 UI 构建)
- **设计规范**：[Material Design 3](https://m3.material.io/)（全系统色调适配与优雅微动效）
- **核心架构**：MVVM（ViewModel + StateFlow 响应式数据流）
- **数据持久化**：[Room Database](https://developer.android.com/training/data-storage/room)（搭载 SQLite 用于历史数据本地二级缓存）
- **网络流与异步**：Kotlin Coroutines (协程) + Flow
- **生命周期生命感知**：Android Lifecycle Components + `collectAsStateWithLifecycle()`
- **视频底层播放**：基于 Android 原生高级多媒体播放内核接口
- **依赖配置管理**：Gradle Kotlin DSL (`.gradle.kts`) + 集中化版本目录管理 (`libs.versions.toml`)

---

## 🛡️ 专业技术亮点：Android 10+ 安全剪贴板合规优化

### 问题背景
在 Android 10 (API 29) 及以上系统中，若应用在处于后台或未完全获取 Window 焦点时尝试非法读取系统剪贴板（如在传统的 Activity `onResume` 阶段直接调用 `primaryClip`），系统会处于隐私考虑抛出权限拒绝异常并打印 Log：
```text
ClipboardService: Denying clipboard access to com.aistudio.douyindownloader, application is not in focus nor is it a system service for user 0
```
该错误不仅会导致功能失效，部分定制系统更有可能引发 App Crash。

### TikSaver Pro 的解决方案
我们在 `MainActivity` 整合了 Jetpack Compose 的 `LocalWindowInfo` 架构。在应用启动与切换前后台时，**仅在当前 Window 彻底获取焦点（Focused）后**才执行安全的剪贴板检测行为。

```kotlin
// MainActivity.kt 关键设计代码片断
val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
val windowInfo = LocalWindowInfo.current

LaunchedEffect(windowInfo.isWindowFocused) {
    if (windowInfo.isWindowFocused) {
        try {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                viewModel.checkClipboardOnStart(clipText)
            }
        } catch (e: Exception) {
            // 对背景安全限制异常做静默降级处理，保障 App 优雅健壮运行
        }
    }
}
```
**这一设计彻底解决了在未经用户许可下后台窃取剪贴板、系统组件初始化过快导致焦点获取延迟等高发系统 Crash 问题，是最高标准的商业级品质架构。**

---

## 📦 项目结构一览

```text
/app/src/main/java/com/example/
├── MainActivity.kt               # 主入口页面，采用 Single Activity + 响应式 Tab 切换
├── data/                         # 数据访问层
│   ├── DownloadDao.kt            # Room 数据库操作接口
│   ├── DownloadDatabase.kt       # 数据库工厂生成
│   ├── DownloadItem.kt           # Room 本地持久化实体 (Id, 视频标题, URL, 本地文件物理路径, 大小, 状态等)
│   └── DownloadRepository.kt     # 数据访问中介仓库层
├── ui/                           # UI层与其状态层
│   ├── theme/                    # 视觉主题系统 (Color.kt, Theme.kt, Type.kt)
│   │   ├── Color.kt              # M3 品牌色调与抖音、极客暗调精细化定制
│   │   └── Theme.kt              # 主题配置类，支持 M3 暗调/动态色彩配置机制
│   └── DownloadViewModel.kt      # 界面核心控制器，管理网络请求、状态流转及下载引擎交互
└── utils/                        # 核心解析与下载底座
    ├── DouyinParser.kt           # 自研抖音视频无水印直链智能解析器
    └── DownloadEngine.kt         # 并发、多线程、断点续传底层下载调度引擎机制
```

---

## 🚀 编译与初次上手

### 前置要求
1. **Android Studio** Chameleon / Ladybug 及以上版本。
2. **JDK 17+** 环境。
3. **Android SDK 34** 编译平台支持。

### 常用 Gradle 命令

- **一键构建应用 & 输出：**
  ```bash
  gradle assembleDebug
  ```
- **进行单元测试：**
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## 📝 鸣谢与版权

- 出色的 Material Icons 提供极为直观易懂的视觉指示。
- 感谢 Jetpack 阵营中优雅而强大的 Kotlin 库在多线程极速传输中所起到的强大支撑。
