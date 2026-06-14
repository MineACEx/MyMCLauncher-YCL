# YCL启动器 — 完整开发与部署文档

---

## 一、项目概述

**YCL启动器** 是一款面向 Android 16 (API 36) 设备的 Minecraft Java 版启动器，基于 Kotlin + Jetpack Compose 构建，仅支持 arm64-v8a 架构，同时兼容 Root / 无Root / Termux 三种运行环境。

| 属性 | 值 |
|------|-----|
| 应用包名 | `com.mymc.launcher` |
| 应用名称 | YCL启动器 |
| 最低 SDK | 36 (Android 16) |
| 目标 SDK | 36 (Android 16) |
| 编译 SDK | 36 |
| 架构 | arm64-v8a only |
| 编程语言 | Kotlin 2.4.0 |
| UI 框架 | Jetpack Compose (BOM 2025.12.00) |
| 构建系统 | Gradle 8.11.1 + AGP 8.10.0 |

---

## 二、项目架构

```
com.mymc.launcher/
├── YCLApplication.kt              # Application 入口，全局初始化
├── di/                            # 依赖注入（预留 Hilt/Koin）
├── data/
│   ├── local/
│   │   └── PreferencesManager.kt  # DataStore 偏好设置管理
│   ├── remote/
│   │   ├── api/                   # Retrofit API 接口
│   │   │   ├── MojangApi.kt       # Mojang 版本清单 API
│   │   │   ├── ModrinthApi.kt     # Modrinth 搜索 API
│   │   │   ├── CurseForgeApi.kt   # CurseForge 搜索 API
│   │   │   ├── YggdrasilApi.kt    # LittleSkin Yggdrasil API
│   │   │   └── MicrosoftAuthApi.kt# 微软 OAuth2 API
│   │   ├── dto/                   # 数据传输对象
│   │   └── RetrofitClient.kt      # 网络客户端工厂
│   └── repository/                # 数据仓库（预留）
├── domain/
│   ├── model/                     # 领域模型
│   │   ├── JavaVersion.kt
│   │   ├── GameVersion.kt
│   │   ├── AccountInfo.kt
│   │   ├── ResourceItem.kt
│   │   └── DownloadTask.kt
│   └── usecase/                   # 用例（预留）
├── ui/
│   ├── theme/                     # 主题系统
│   │   ├── Color.kt               # 深海蓝 + 预置调色板
│   │   ├── Typography.kt          # 排版规范
│   │   └── Theme.kt               # Compose 主题入口
│   ├── components/                # 通用 UI 组件
│   │   ├── GlassCard.kt           # 高斯模糊毛玻璃卡片
│   │   ├── BackgroundImage.kt     # 全局背景图片
│   │   └── BottomNavBar.kt        # 底部导航栏
│   ├── navigation/                # 导航系统
│   │   ├── Screen.kt              # 路由定义
│   │   └── NavGraph.kt            # 导航图
│   ├── screens/                   # 页面
│   │   ├── home/                  # 主界面
│   │   ├── java/                  # Java 版本管理
│   │   ├── version/               # 游戏版本管理
│   │   ├── versionSettings/       # 版本设置 (JVM 参数)
│   │   ├── resource/              # 资源中心 (Mod/光影/材质)
│   │   ├── account/               # 账号管理
│   │   └── settings/              # 设置页
│   └── MainActivity.kt            # 主 Activity
├── service/                       # 服务层
│   ├── java/JavaManager.kt        # Java 版本管理器
│   ├── version/VersionManager.kt  # 游戏版本管理器
│   ├── download/DownloadManager.kt# 下载管理器
│   ├── download/DownloadService.kt# 下载前台服务
│   ├── account/AccountManager.kt  # 账号管理器
│   ├── process/ProcessManager.kt  # 多环境进程管理
│   ├── theme/ThemeManager.kt      # 主题管理器
│   └── game/GameLauncher.kt       # MC 启动核心
└── util/                          # 工具类
    ├── EnvDetector.kt             # 环境检测工具
    ├── HashUtil.kt                # 哈希校验工具
    ├── FileUtil.kt                # 文件处理工具
    └── LogUtil.kt                 # 日志工具
```

### 分层职责

| 层级 | 职责 |
|------|------|
| **data** | 数据持久化（DataStore）、网络请求（Retrofit）、DTO 转换 |
| **domain** | 领域模型定义，纯 Kotlin data class |
| **ui** | Compose 页面 + 组件 + 导航 + 主题 |
| **service** | 业务逻辑层，单例 Manager 模式 |
| **util** | 纯工具函数，无副作用 |

---

## 三、功能模块详解

### 3.1 运行模式适配
`EnvDetector` 自动检测三种环境：
- **无 Root**：直接使用 `ProcessBuilder`
- **已 Root**：`su -c` 包装命令，请求 Root 权限
- **Termux**：使用 Termux 环境中的 `ProcessBuilder`

### 3.2 Java 环境管理
- 独立下载页面，提供 Java 8/17/21/25 arm64 OpenJDK
- `autoMatchJava()` 根据 MC 版本自动匹配：
  - MC 1.8 ~ 1.16 → Java 8
  - MC 1.17 ~ 1.20 → Java 17
  - MC 1.21+ → Java 21
- 支持手动选择 Java 版本

### 3.3 游戏版本管理
- 对接 Mojang 版本清单 API（`launchermeta.mojang.com`）
- 支持 Forge / Fabric 安装器
- **默认开启版本隔离**，设置页提供开关

### 3.4 版本设置
- 自定义 JVM 参数输入框
- 每个版本独立保存，key 格式：`jvm_args_{versionId}`
- 支持一键重置为默认参数

### 3.5 资源中心
- 对接 Modrinth API v2 + CurseForge API
- 三 Tab 分类：Mod / 光影 / 材质
- 按游戏版本 + 加载器类型筛选
- 本地资源查看/启用/禁用/删除

### 3.6 账号系统
- **离线账号**：用户名 → MD5 生成 UUID
- **微软账号**：OAuth2 设备码 → Xbox Live → XSTS → Minecraft 认证
- **LittleSkin**：Yggdrasil 协议，`https://littleskin.cn/api/yggdrasil`

### 3.7 游戏启动
- 整合 Java 选择、JVM 参数、内存配置
- 构建完整 classpath 和启动参数
- 实时日志输出，崩溃捕获

---

## 四、本地编译打包步骤

### 4.1 环境要求

| 工具 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Android Studio | Latest Stable |
| Android SDK | Platform 36, Build Tools 36.0.0 |
| NDK | 27.0+ (可选) |
| Gradle | 8.11.1 (wrapper 自动下载) |

### 4.2 编译步骤

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd YCL-Launcher

# 2. 使 Gradle Wrapper 可执行
chmod +x ./gradlew

# 3. 编译 Debug 版本（无需签名）
./gradlew assembleDebug

# 4. 编译 Release 版本（需要签名密钥）
# 先创建签名密钥文件：
keytool -genkey -v \
  -keystore app/release.jks \
  -alias ycllauncher \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass your_password \
  -keypass your_password

# 在 gradle.properties 或 ~/.gradle/gradle.properties 中添加：
# RELEASE_STORE_FILE=app/release.jks
# RELEASE_STORE_PASSWORD=your_password
# RELEASE_KEY_ALIAS=ycllauncher
# RELEASE_KEY_PASSWORD=your_password

# 编译 Release APK
./gradlew assembleRelease

# 5. 产物位置
# Debug: app/build/outputs/apk/debug/
# Release: app/build/outputs/apk/release/
```

### 4.3 在 Android Studio 中打开
1. File → Open → 选择 `YCL-Launcher/` 目录
2. 等待 Gradle Sync 完成
3. 选择 Run Configuration → app
4. 连接 Android 16 设备或启动模拟器
5. 点击 Run

---

## 五、GitHub Actions 云端打包部署

### 5.1 工作流说明
工作流文件位置：`.github/workflows/build.yml`

触发条件：
- Push 到 `main` / `master` 分支
- 向 `main` / `master` 发起 Pull Request
- 手动触发（workflow_dispatch）

### 5.2 配置 GitHub Secrets

在 GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret，添加以下密钥：

| Secret 名称 | 说明 | 获取方式 |
|-------------|------|---------|
| `KEYSTORE_BASE64` | 签名密钥文件的 Base64 编码 | `base64 -i app/release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | 签名密钥库密码 | 创建密钥时设置的密码 |
| `KEY_ALIAS` | 签名密钥别名 | 创建密钥时设置的别名（如 `ycllauncher`） |
| `KEY_PASSWORD` | 签名密钥密码 | 创建密钥时设置的密钥密码 |

> ⚠️ **安全警告**：切勿将以上信息硬编码到 `build.gradle.kts` 或提交到仓库！

### 5.3 生成 KEYSTORE_BASE64

```bash
# macOS / Linux
base64 -i app/release.jks | tr -d '\n'

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\release.jks"))
```

将输出的 Base64 字符串完整复制到 GitHub Secrets 中。

### 5.4 构建产物获取
1. 进入 GitHub 仓库 → Actions 标签页
2. 点击最新的一次 Workflow Run
3. 滚动到页面底部 → Artifacts 区域
4. 下载 `YCL-Launcher-Release-APK`

---

## 六、环境兼容性说明

### 6.1 Android 16 分区存储
- `targetSdk = 36`，完全遵循分区存储规范
- 应用内部文件存储在 `context.filesDir`
- 需要大文件存储时请求 `MANAGE_EXTERNAL_STORAGE` 权限

### 6.2 仅 arm64-v8a 架构
- `abiFilters = "arm64-v8a"`，不打包 32 位原生库
- 显著减小 APK 体积
- 所有设备均为近 3 年 arm64 Android 设备，不存在兼容性问题

### 6.3 Termux 环境兼容
- `EnvDetector` 检测 `$PREFIX` 环境变量和 Termux 特征目录
- Termux 环境下 PATH 已配置，直接使用 `ProcessBuilder`
- Java 下载到 Termux 的 `$HOME` 目录

---

## 七、主题系统说明

### 7.1 默认主题色：深海蓝 `#0D47A1`
- 亮色模式：纯白背景 + 深海蓝 UI 元素
- 暗色模式：深色背景 + 深海蓝 UI 元素

### 7.2 预置主题色
| 名称 | 色值 |
|------|------|
| 深海蓝 | `#0D47A1` |
| 樱花粉 | `#E91E63` |
| 翡翠绿 | `#009688` |
| 琥珀橙 | `#FF6F00` |
| 紫罗兰 | `#7C4DFF` |
| 石墨灰 | `#607D8B` |

### 7.3 全局高斯模糊
所有卡片和面板使用 `Modifier.blur()` 实现毛玻璃效果，通过 `GlassCard` 组件统一应用。

---

## 八、开发约定

1. 注释全部使用**简体中文**
2. 类名使用 PascalCase，方法名 camelCase
3. 所有 Manager 类采用单例模式（`object` 或 `companion object + lazy`）
4. 状态管理使用 `StateFlow` + `ViewModel`
5. 网络请求统一通过 `RetrofitClient` 工厂方法
6. 文件 I/O 统一通过 `FileUtil` 工具类
7. 日志统一通过 `LogUtil` 工具类

---

## 九、常见问题

### Q: Release 构建失败 "Keystore file not found"
A: 确保已创建签名密钥文件或将签名配置指向有效的密钥路径。也可临时将 `signingConfig` 改为 `signingConfigs.getByName("debug")`。

### Q: Gradle Sync 失败 "Unsupported major.minor version"
A: 确保 JDK 版本为 17+，AGP 8.10.0 需要 JDK 17+。

### Q: 模拟器上无法安装
A: 确保模拟器镜像为 arm64-v8a 架构，x86 模拟器不支持。

### Q: GitHub Actions 构建失败
A: 检查 Secrets 是否正确配置，确保 `KEYSTORE_BASE64` 是完整且未截断的 Base64 字符串。