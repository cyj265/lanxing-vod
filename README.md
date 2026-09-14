# 揽星影视

手机端 TVBox 类影视播放器，开箱即用饭太硬等 DEX 爬虫源，支持点播与直播。

基于 [FongMi/TV](https://github.com/FongMi/TV) 改造，适配手机端（mobile flavor）。

## 功能

- 点播 / 直播 / 搜索 / 收藏 / 历史记录
- 兼容 TVBox JSON 接口协议与饭太硬类 DEX 爬虫源
- 弹幕、投屏（DLNA）、画中画、倍速、字幕
- 硬解 / 软解切换，HLS / DASH / RTSP 全格式支持

## 构建

环境要求：JDK 17、Android SDK（compileSdk 36）、Android Studio 或命令行 Gradle。

`ash
# 配置 local.properties（sdk.dir 及签名信息）
git clone https://github.com/cyj265/lanxing-vod.git
cd lanxing-vod
./gradlew :app:assembleMobileArm64_v8aDebug
`

## 说明

- 包名：com.cyj265.lanxingvod
- 接口与源：请在应用内「设置 → 配置地址」自行添加
- 本项目仅用于技术学习与交流，请遵守相关法律法规，勿传播侵权内容
