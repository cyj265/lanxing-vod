# 揽星影视

适用于 Android 手机与 Android TV 的影音应用，基于 [FongMi/TV](https://github.com/FongMi/TV) 二次开发。整合媒体浏览与播放体验，兼容 TVBox（JSON）接口协议，并支持外部配置与 [CatVod](https://github.com/CatVodTVOfficial/CatVodTVJarLoader) Spider 接口扩展。

**App 本身不内置或提供任何内容来源。** 外部内容需自行配置，也可打开本地媒体文件或推送媒体网址。

## 项目简介

揽星影视在 FongMi/TV 上游内核基础上做了面向手机端的体验改造，主要差异点：

- **毛玻璃 UI**：播放页、设置页、弹窗等大量采用毛玻璃（Blur）风格。
- **自定义底部导航 Tab**：首页底部导航可自定义排序与显隐。
- **画中画（PiP）**：手机端支持播放画中画。
- **直播增强**：直播 EPG 节目单、Catchup 回看与时移。
- **投屏**：DLNA 发送端（手机）自研轻量 SSDP 发现 + SOAP 控制栈；接收端沿用 jupnp。
- **多仓 / 多源聚合**：仓库管理支持多源聚合与线路合集双模式，一键切换、刷新、复制、删除。
- **首页推荐位**：对接豆瓣榜单、站点推荐、电视榜等 home 数据。
- **音效模式**：影院 / 重低音 / 3D 环绕 / HiFi / 人声等多档 EQ 预设。
- **Bugly 上报**与基于 GitHub Releases 的在线更新检查（用版本号判断新版本，逻辑与揽星TV一致）。

> 播放内核**仅使用 ExoPlayer（Media3）**，原 MPV 分支已移除。

## 开始使用

1. 安装适合设备的 APK：仅发布手机版 `mobile`，64 位 `arm64-v8a`。最低要求 Android 7.0（API 24）。电视版（`leanback`）已停止维护与分发，已装电视版的用户直接覆盖安装手机版即可，数据与配置保留。
2. 在设置中加入自己的配置（兼容 TVBox JSON 接口），格式与字段见[配置范例](https://fongmi.github.io/TV/config/#examples)。
3. 也可从系统文件管理器打开本地媒体文件，或通过推送入口播放媒体网址。

## 主要功能

- **播放**：Media3 / ExoPlayer；字幕、弹幕、音轨、倍速、片头 / 片尾跳过、屏显（分辨率 / 网速 / 电量）。
- **浏览与管理**：分类筛选、搜索、播放记录、收藏与无痕模式。
- **播放列表**：M3U / TXT / JSON 格式、列表分组与 XMLTV 节目信息。
- **直播**：EPG 节目单、Catchup 回看与时移、半屏 / 全屏布局。
- **操作**：电视遥控器、手机手势、画中画、后台音频。
- **互通**：DLNA 投屏（发送 / 接收）、本地 HTTP 控制。

实际能力依配置、媒体、播放引擎与设备而异；本地 HTTP API 仅供可信局域网使用，不要直接转发到公网。

## 开发文档

| 文档 | 内容 |
| --- | --- |
| [App 功能](https://fongmi.github.io/TV/features/) | 操作与功能介绍（上游通用） |
| [配置字典](https://fongmi.github.io/TV/config/) | 配置字段、网络设置与 JSON 示例 |
| [扩展接接](https://fongmi.github.io/TV/spider/) | Java / Python / JavaScript 示例、方法与返回格式 |
| [本地 API](https://fongmi.github.io/TV/local/) | 播放控制、推送、文件与同步端点 |
| [网站维护](website/README.md) | 静态网站构建与 GitHub Pages 发布 |

`app/src/main/` 为共用逻辑，`app/src/mobile/` 为手机版 UI。模块列表见 [settings.gradle](settings.gradle)，SDK 与依赖版本见 [libs.versions.toml](gradle/libs.versions.toml)。

`app/src/leanback/`（电视版源码）当前**不参与编译**：其 flavor 与依赖在 `app/build.gradle` 中已注释停用，保留目录只是方便日后重启电视版。大屏设备（平板 / 折叠屏，smallestWidth ≥ 600dp）走的是 `app/src/mobile/res/layout-sw600dp/`。

## Windows 构建

先准备以下环境与文件：

- **JDK 21、Android SDK、Python 3.10**。SDK 平台版本依 `compileSdk` 设置；Python 用于 chaquo 模块。
- **配套 AAR**：`app/libs/` 内的 5 个 `*-release.aar`（thunder 迅雷下载器 / tvbus / forcetech / jianpian / hook）**已纳入 Git**，clone 下来即可直接构建，无需另外准备。自行新增私有 AAR 时注意别把它们提交上去。
- **自己的签名文件与 `local.properties`**：在仓库根目录创建下列配置，将所有示例值替换成自己的数据。

```properties
sdk.dir=C:/Android/Sdk
storeFile=C:/keys/lanxing.jks
keyAlias=your-key-alias
storePassword=your-keystore-password
```

密钥密码与 keystore 密码共用 `storePassword`；不要提交签名文件或真实密码。

在仓库根目录以 PowerShell 执行：

```powershell
# 手机版（当前唯一发布的包）
.\gradlew.bat :app:assembleMobileArm64_v8aRelease
```

如需本地编译电视版 `leanback`，需先自行解除 `app/build.gradle` 里 `leanback` flavor 与 `leanbackImplementation` 依赖的注释。

APK 按 ABI 分包并输出至 `Release/apk/`。签名不同的 APK 不能直接覆盖既有安装。网站位于 `website/`，可独立构建，不需编译 Android App。

## 免责声明

本应用仅供学习与个人使用。使用者需自行确保所配置内容来源合法合规，作者不对任何第三方内容负责。
