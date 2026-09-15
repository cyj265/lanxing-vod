# 揽星影视

手机端 TVBox 类影视播放器，开箱即用 TVBox 类接口源，支持点播与直播。

基于 [FongMi/TV](https://github.com/FongMi/TV) 改造，适配手机端（mobile flavor）。

## 功能

- 点播 / 直播 / 搜索 / 收藏 / 历史记录
- 兼容 TVBox JSON 接口协议与 DEX 爬虫源
- 弹幕、投屏（DLNA）、画中画、倍速、字幕
- 硬解 / 软解切换，HLS / DASH / RTSP 全格式支持

## 构建

环境要求：JDK 17、Android SDK（compileSdk 36）、Android Studio 或命令行 Gradle。

```bash
# 配置 local.properties（sdk.dir 及签名信息）
git clone https://github.com/cyj265/lanxing-vod.git
cd lanxing-vod
./gradlew :app:assembleMobileArm64_v8aDebug
```

## 说明

- 包名：com.cyj265.lanxingvod
- 接口与源：请在应用内「设置 → 配置地址」自行添加
- 本项目仅用于技术学习与交流，请遵守相关法律法规，勿传播侵权内容

## 感谢

- [FongMi/TV](https://github.com/FongMi/TV)：本项目的上游基础，提供了完整的 TVBox 协议实现与播放器架构
- [TVBox](https://github.com/CatVodTV/TVBox)：TVBox 接口协议的开创者与生态社区
- [AndroidX Media3](https://github.com/androidx/media)：播放内核
- [BlurView](https://github.com/Dimezis/BlurView)：毛玻璃导航效果
- 以及所有被本项目引用的开源依赖，致敬每一位开源贡献者
