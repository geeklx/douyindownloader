# 抖音去水印下载器 (TikSaver Pro)

一款基于 **Kotlin** 和 **Jetpack Compose** 打造的高颜值、现代化的 抖音视频去水印极速下载与媒体管理工具（应用内部代号 **TikSaver Pro**）。

本项目遵循 **Material Design 3 (M3)** 设计规范，致力于提供极致流畅的交互体验、高速的并发下载能力以及便捷的本地下载历史与媒体库管理。

---

## ✨ 核心功能与最新视觉优化

- 🚀 **极速无水印下载**：内置高效的无水印解析引擎与多线程极速断点续传下载器，下载视频既干净又快速。
- 📋 **智能剪贴板检测**：开启应用或返回应用时，自动识别剪贴板中的抖音分享/复制链接并弹出解析提示，免去反复手动粘贴的痛苦。
- 🎨 **高保真抖音霓虹美学 🆕**：
  - **渐变横幅背景**：以抖音红（`#FE2C55`）和抖音青（`#25F4EE`）线性渐变刷饰品牌头部，拥有绝佳的暗色极客质感。
  - **流光边界视界**：在主输入界面、已解析卡片及视频悬浮预览器中大量应用高对比度渐变细描边，极大提升界面的边缘辨识度与高级感。
  - **定制专属徽章**：定制了无水印就绪霓虹小徽章，辅以磨砂质感的点击态过渡粒子，全方位消除粗制滥造的 “AI Slop” 视觉感官。
- 💡 **一站式引导型空白页 🆕**：针对新用户全新设计了结构清晰、活泼亲和的**三步入门引导流**模块，通过醒目的彩色数字徽标，让用户无需阅读文档即可在主页秒懂使用全流程。
- 🛡️ **安全的隐私监听**：针对 Android 10+ (API 29+) 系统的安全剪贴板限制进行了深度优化（采用 Window Focus 机制，保证仅在 Window 处于焦点态才安全提取），拒绝后台静默读取与非法调用导致的 Crash 与告警。
- 📺 **高级视频播放与预览 🆕**：支持在下载前、下载中“边看边下”，也支持内置的高聚合视频播放器直接流畅预览本地下载的内容，并辅以 1.5dp 霓虹流光悬浮相框。
- 🧱 **全方位本地媒体库**：
  - 基于 Room 数据库的高效下载历史管理器，状态实时保存。
  - 支持视频**批量选中、一键批量删除、本地条件模糊搜索**。
  - 支持一键导出并保存到**系统内置相册**（采用 MediaStore），跨应用分享更方便。

---

## 📋 历史更新日志 (Changelog)

本项目的优化历程和每次功能更新都记录在此，确保开发迭代路径清晰可见：

### 🚀 v1.4.0 (最新优化) — 零刷新双常驻 Tab 架构与终极兼容
- **零刷新无感 Tab 容器 (双 Box 常驻树)** 🆕
  - **改进痛点**：解决此前点击底部 Tab 导致页面被重新销毁与创建，导致输入框文本、解析结果、局部滑动状态、批量选中进度在切换 Tab 时被“意外重置”的不良体验。
  - **解决方案**：引入多态 Composition 常驻结构（两个独立 Layout，通过尺寸动态切换以及局部透明度占位，实现类似于 Android 传统 Fragment 栈的 `show/hide` 机制）。**切换 Tab 时秒切无需重建，完全保留输入词、搜索词、列表滚动偏移等完美会话上下文**。
- **自适应极速断点续传下载器 🛡️**
  - **单/多线程自动降级保障**：在利用多线程并行分块（Range）下载大文件时，如果遇到部分极其严苛的运营商或抖音服务器反爬封锁，下载引擎能秒级自动捕获、触发**静默降级降速流**，自动回落到单线程安全传输流，确保任何网络和服务器限制下“不报红、不中断、全覆盖”。
  - **增强对抖音源 Referer 验证**：全面优化了请求 Referer 请求头（`https://www.douyin.com/` 与 `https://v.douyin.com/`），穿透服务器端的基础限流防爬，大幅优化部分特定长链接解析失败、获取不到重定向的阻力。

### 🎨 v1.3.0 — 本地媒体自适应帧封面引擎与高保真抖音美学
- **动态本地视频帧提取与高保真 Fallback**
  - 引入了 `MediaMetadataRetriever` 智能视频解帧器，在本地任务下载完成后，**首选直接在本地离线 MP4 文件的第 1.0 秒提取高清视频画面作为视频卡片封面**，免去由于在线链接过期或者网络波折而导致的封面图偶现空白问题。
  - 如果文件不全或尚在下载，平滑退回外链封面，如果外链亦为空白，运用视频 Title 的 **Hash 哈希散列映射法自动计算出华丽的暗色渐变霓虹卡片底色**。
- **本地存储资源物理计算分析**
  - 能够计算 Room 下载库中已完结视频真实的外部磁盘占用大小并进行友好化单位格式换算（MB/GB），并展现“全部任务” vs. “已完成任务”的汇总指示标。
- **高颜值抖音酷黑霓虹美学**
  - 将主输入框、视频分析详情卡片以及播放悬浮面板均用 Douyin Red (`#FE2C55`) 和 Douyin Cyan (`#25F4EE`) 霓虹边进行环绕，整体融入暗色极客审美。
  - 设计了高可见度的 “1 - 2 - 3 极速去水印三大步” **高聚合新人引导流**。

### 🔄 v1.2.0 — 本地相册导出三重防护与安全检测
- **MediaStore 三重相对路径级联写入（ROM 终极防闪退）**
  - 之前许多国产手机（MIUI、ColorOS、HarmonyOS、OriginOS 等）对 `Movies/去水印视频` 等相对目录有强制沙盒权限校验。
  - 建立了**级联备选路径救助链**：首层尝试写入 `Movies/去水印视频`，如果由于权限、只读或存储介质不适配导致插入数据库异常，二级降级自动写入标准主流相册 `DCIM/Camera`，三级退守默认视频区，最大程度杜绝了在极个别特殊定制机型上的相册导出失败与闪退错误。
- **版本合规回落、运行时权限安全检查**
  - 在 Android 9.0（API 28）及以下手机，自动校验并阻断申请 `WRITE_EXTERNAL_STORAGE` 磁盘写入权限后才调取相册广播（`ACTION_MEDIA_SCANNER_SCAN_FILE`）注入。
  - 在 Android 10+（API 29+）系统上，运用新版沙盒专属 `IS_PENDING` 锁控制属性，彻底规避因不完全写入相册数据库就提供引用所引起的空值和破损封面。
- **Android 11+ 剪贴板获取合规防护**
  - 采用 compose 的 `LocalWindowInfo.current` 钩子，仅当 App 彻底占有当前 Window 焦点（Focused）时才触发剪贴板安全扫描，不仅避免了系统后台读取隐私警告，也排除了生命周期不一致对多机型的冷加载闪退问题。

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
