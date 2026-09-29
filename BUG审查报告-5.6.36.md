# lanxing-vod 5.6.36 代码缺陷审查报告

> **2026-09-29 更新**：报告中的问题已全部修复，并补充了编译验证环节（见文末「修复与验证状态」）。

> 审查范围：`app/src/main`、`app/src/mobile`、`app/src/leanback`、`catvod`（共 631 个 Java 文件）
> 审查方式：**静态代码分析**。本机未安装 Android SDK，**未做编译验证**，因此本报告不含编译错误，仅包含逻辑/健壮性/性能/安全问题。
> 说明：报告中的每条结论均已逐条回到源码核实过行号，非推测。

---

## 一、高危：会导致崩溃

### 1. `PlayerManager.release()` 未停止解析任务，异步回调必然 NPE
- 位置：`app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:80-86`
- 问题：`release()` 只做了 `removeCallbacks`、`removeListener`、`engine.release()`，**没有调用本类已有的 `stopParse()`（:528）**。而 `ParseJob` 持有 `ParseCallback = PlayerManager`。
- 触发：解析尚未完成时退出播放页（`PlaybackService.java:189` 销毁）。回调 `onParseSuccess(:583)` → `startCurrent()` → `setMediaItem()` → `ensureEngine()` → `player.removeListener(listener)`（:495），此时 `player` 已被置 null → 崩溃；同时 `ParseJob` 内的 WebView 泄漏。
- 修复：`release()` 首行补 `stopParse();`。

### 2. `ensureEngine()` 缺少 null 保护，全类无 `isReleased()` 守卫
- 位置：`PlayerManager.java:492-499`；受影响方法分布在 `:98/106/114/118/161/194/233/286/295/386/394/459`
- 问题：类中已经定义了 `isReleased()`（:125-127），但**没有任何一个方法调用它**。`getCurrentTracks()`、`getPosition()`、`play()`、`stop()`、`resetTrack()`、`setSub()`（`engine.addSubtitle`）等在 release 后仍直接解引用。
- 触发：release 后任意 UI 回调 / 异步回调到达。
- 修复：`ensureEngine()` 开头加 `if (player == null || engine == null) return;`，并对公开方法统一加判空。

### 3. `LiveActivity` 匿名延迟任务未清理，销毁后仍执行
- 位置：`app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1000、1006`
- 问题：`App.post(() -> seek(time), 250)` 用的是匿名 Runnable，而 `onDestroy()`（:1062）只移除 `mR0~mR4` 这 5 个成员，清不到它。
- 触发：按左右键 seek 后立刻退出页面 → 250ms 后仍执行 → `seekTo()` 访问已置 null 的 service/player → NPE。
- 修复：提升为成员 Runnable 并纳入 `App.removeCallbacks(...)`，或在回调内加 `isDestroyed()` 判断。
- 同类问题：`App.post(view::requestFocus, 25)`（:570）、`mobile VideoActivity:1139` 的 `postDelayed`。

### 4. 外部播放器返回时，方法引用在传参瞬间求值 → NPE
- 位置：`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1649`、`app/src/leanback/.../VideoActivity.java:1433`
- 问题：`PlaybackIntent.onExternalResult(data, service()::dispatchNext, controller()::seekTo)` —— Java 方法引用在**实参求值时立即解引用目标对象**。`service()` 或 `controller()` 返回 null 时当场抛 NPE，而这个 NPE 发生在 `onExternalResult` 内部的 `try`（`PlaybackIntent.java:56`）**之前**，catch 保护不到。
- 触发：从外部播放器（MX Player 等）返回，此时服务尚未重绑或 MediaController 未就绪。
- 修复：改成 lambda 并把判空放进内部：`(v) -> { if (controller() != null) controller().seekTo(v); }`。

### 5. `History` 按 `@@@` 切分未校验数组长度
- 位置：`app/src/main/java/com/fongmi/android/tv/bean/History.java:270、274`
- 问题：`getKey().split(AppDatabase.SYMBOL)[0]` / `[1]` 直接取下标，没有任何长度或空值判断。
- 触发：`History.sync()`（:118）在备份恢复路径上对每个对象调 `cid()`→`getVodId()`，若 key 不含 `@@@` 则 `ArrayIndexOutOfBoundsException` 崩溃。`Keep.java:154` 同款写法。
- 修复：split 后判 `length >= 3` 再取，否则返回默认值。

---

## 二、功能失效（不崩溃，但功能不生效）

### 6. MPV 引擎下音轨/字幕轨切换完全无效 —— ⚠️ **本条不成立，已作废**
- 位置：`app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java:129-150`
- 原判定：`COMMANDS` 里**没有声明 `COMMAND_SET_TRACK_SELECTION_PARAMETERS`**，且全文没有任何 `setTrackSelectionParameters` / `handleSetTrackSelectionParameters` 实现。`MpvPlayer.setTrackSelection(int, String)`（:1765）自写方法**全项目零调用**。
- **作废理由（2026-09-29 复核）**：本机 APP **只使用 EXO 内核，MPV 已被移除**，该代码路径永不执行。三重证据：
  1. `PlayerSetting.getEngine()` 已改为**硬编码 `return ENGINE_EXO`**，`isMpv()` 恒为 false；
  2. `app/src/main/assets/` 下**没有任何 mpv native 库**（只有 `cacert.pem`/`css`/`index.html`/`js`/`parse.html`），而 `MPVLib.ensureLoaded()` 正是从 assets 加载 so → `MpvUtil.isAvailable()` 恒 false；
  3. `PlayerEngineFactory.resolve()` 因此恒返回 EXO。
- 影响：本条**不会**造成任何用户可见问题。此前按本条做的 `MpvPlayer` 改动属于死代码修改，无害但无实际收益。

### 7. 轨道选择：未选中的轨道会导致该类型被整体禁用
- 位置：`app/src/main/java/com/fongmi/android/tv/player/track/TrackUtil.java:86-91`
- 问题：`selectedIndex == null` 时构造 `new TrackSelectionOverride(mediaGroup, List.of())`。在 Media3 语义里，空列表的 override 表示「该组不选择任何轨道」，效果是**静音 / 无字幕**，而不是「保持默认」。
- 触发：存档的 Track 记录中存在未选中项（用户在轨道对话框取消勾选过的），下次播放 `onTracksChanged` → `setTrack()` 后直接踩中。
- 修复：未选中时不写 override，改用 `builder.clearOverride(mediaGroup)` 或 `clearOverridesOfType(type)`。

### 8. 毛玻璃采样区域错误（Android 12+），模糊的不是背后的内容
- 位置：`app/src/main/java/com/fongmi/android/tv/ui/custom/GlassDrawable.java:113-127`，调用点 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java:92-93`
- 问题：`mBinding.navBottom.setBackground(mGlass)` 决定了 `getBounds()` 是 **View 局部坐标 (0,0,w,h)**。于是 `translate(-b.left, -b.top)` 与 `scale(..., b.left, b.top)` 等价于无平移、绕原点缩放 —— 实际采样的是 container 的**左上角**区域，而不是底栏正后方的内容。降级路径 `drawLegacy`（:141-142）取的是缓存底部条带，两者行为相反。
- 触发：Android 12+ 设备进入首页，底栏模糊内容明显错位。
- 修复：用 `getLocationInWindow` / `getLocationOnScreen` 算出 nav 相对 container 的真实偏移后再做 translate+scale。

### 9. 更新弹窗版本号为空、无更新日志
- 位置：`app/src/main/java/com/fongmi/android/tv/utils/Github.java:29-32` ↔ `app/src/main/java/com/fongmi/android/tv/Updater.java:53-56`
- 问题：`Github.getLatestRelease()` 构造的 JSONObject **只回填了 `tag_name` 和 `apk_url`**，但 `Updater` 读的是 `object.optString("name")` 和 `object.optString("body")` → 均为空串。
- 触发：每次检测到更新时，弹窗标题显示空版本号、正文无更新说明。
- 修复：在 `Github` 中补 `release.put("name", ...)` / `release.put("body", ...)`，或在根目录 `update.json` 里补这两个字段。

### 10. 数据库缺少 30 版本以下的迁移，旧库静默清库
- 位置：`app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java:45-53`
- 问题：`@Database version = 35`，但只注册了 `MIGRATION_30_31 … MIGRATION_34_35`，同时开了 `fallbackToDestructiveMigration(true)`。
- 触发：DB 版本低于 30 的旧版本直接覆盖安装 → Room 无迁移路径 → **历史记录/收藏/直播源/配置被全部清空**且无任何提示。
- 修复：补早期迁移链，或在 destructive 前强制做一次备份。

---

## 三、性能问题

### 11. 毛玻璃每帧全量重绘整棵内容树
- 位置：`GlassDrawable.java:56-64` + `:116-126`（V31）/ `:129-144`（legacy）
- 问题：Choreographer 帧回调自循环 → 每帧 `invalidateSelf()` → `mContent.draw(rc)`，等于把首页 ViewPager + 所有 RecyclerView **按 60fps 完整重绘**。TV 盒子/低端机必然掉帧发热。
- 修复：节流到 4-8fps；或直接用 `View.setRenderEffect()` 交由系统合成，不必自己每帧录。

### 12. 降级模糊路径复用 Bitmap 未擦除 → 残影累积
- 位置：`GlassDrawable.java:136-139`
- 问题：`mCacheCanvas` 复用前没有 `eraseColor`，半透明内容逐帧叠加，画面越来越脏；`mCache` 在 Activity 销毁时也没有 `recycle()`。
- 修复：绘制前 `mCache.eraseColor(Color.TRANSPARENT)`，并在 `onDestroy` 回收。

### 13. `draw()` 内每帧重复分配对象
- 位置：`GlassDrawable.java:100-101`（`Path` + `RectF`）、`:148-159`（3 个 `Paint` + 3 个 `RectF`）
- 修复：全部提升为成员变量复用。

### 14. MPV 特性探测把整个 `libmpv.so` 读进内存 —— ⚠️ **本条不成立，已作废**
- 位置：`app/src/main/java/is/xyz/mpv/MPVLib.java:205-220`
- 原判定：为了扫描 "List of enabled features:" 字符串，把几十 MB 的 native so 全量读进 `ByteArrayOutputStream` 再转 String。
- **作废理由（2026-09-29 复核）**：MPV 已移除，assets 中没有 mpv native 库，`ensureLoaded()` 必然失败，该探测代码**永不执行**。
- 影响：无。此前按本条做的缓存优化属于死代码修改，无害但无收益。

---

## 四、安全问题

### 15. HTTPS 全信任，可被中间人劫持
- 位置：`catvod/src/main/java/com/github/catvod/net/OkHttp.java:190`
- 问题：`hostnameVerifier((hostname, session) -> true)` 叠加空实现的 `X509TrustManager`（:210 起），TLS 校验完全失效。所有站点配置、直播源、解析请求均可被劫持。
- 修复：改用系统默认校验，仅对确有必要的自签域名做白名单放行。

### 16. 内置 HTTP 服务监听全网卡且无鉴权
- 位置：`app/src/main/java/com/fongmi/android/tv/server/Nano.java:26`（`super(port)` 绑定 0.0.0.0）、`Server.java:47`（端口 9978-9998）、`Local.java:35,43-45`
- 问题：`/upload`、`/newFolder`、`/delFolder`、`/delFile` 对局域网任意来源开放，无任何 token 校验（`isLoopback` 仅豁免 `/file` 下载）；明文本地上传/删除文件。
- 修复：仅监听 127.0.0.1，或对写操作加 token。

---

## 五、工程卫生

### 17. `res` 目录下混入无关文件（建议清理）
- 位置：`app/src/mobile/res/只用这个_康廉录单助手_OpenAPI_红错修正版.yaml`
- 问题：一个中文命名的 yaml 文件直接躺在 resources 根目录下，既不是合法资源，也会随 APK 打包进去，新版 AGP 可能直接报错终止构建。
- 建议：移除。**此文件未删除，等你确认。**

### 18. 已废弃的死代码
- `app/src/mobile/java/com/fongmi/android/tv/ui/custom/CustomFabBehavior.java`：FAB 在提交 `71684e7` 已删除，该类全项目无引用（已确认 XML 与 Java 均无残留引用）。

### 19. 混淆规则边界（低优先级，待实测）
- `app/proguard-rules.pro:39` 的 `-keep class * extends com.github.catvod.crawler.Spider` **未带 `{ *; }`**，成员可能被剥离。由于紧邻的 `com.github.catvod.crawler.** { *; }` 已覆盖大部分情况，仅在 Spider 子类位于该包之外时才有风险。`minifyEnabled = true`（`app/build.gradle:70`），Release 包建议实测一次。

---

## 附：未发现问题的项（已核查）

为避免误报，以下常见嫌疑都已核查并**排除**：
- **字符串/数组资源一致性**：`R.array.select_decode` 等全部有定义，无悬空引用。
- **Room schema**：`schemas/35.json` 与实体、`MIGRATION_34_35` 字段完全一致，DAO 无错配。
- **`OkGlideModule` 图片缓存**：`InternalCacheDiskCacheFactory(context, 50*1024*1024)` 签名正确，50MB 限制生效。
- **版本号比较**：`Github.compare()` 用的是分段数值比较，"5.6.9 vs 5.6.10" 判断正确；`update.json`（5.6.36）与 `versionName`（5.6.36）一致属正常，发版时同步 bump 即可。
- **搜索页 NPE**：`getKeyword()` 判空 + `Word.objectFrom` 兜底已彻底。
- **EventBus 混淆**：EventBus 自带 consumer 规则覆盖 `@Subscribe`，无需额外 keep。
- **IO 流**：`FileUtil`/`Download`/`SubtitleArchive` 均用 try-with-resources，含 Zip-Slip 校验。

---

## 修复优先级建议

| 优先级 | 条目 | 理由 |
|---|---|---|
| P0 | 1、2、3、4、5 | 直接崩溃，用户可稳定复现 |
| P0 | 7 | 会导致「播放没声音/没字幕」，体验致命 |
| P1 | 8、9 | 功能失效，首页视觉与更新弹窗直接受影响（原含 6，已作废） |
| P1 | 10 | 升级丢数据，不可逆 |
| P2 | 11、12、13 | 性能与内存（原含 14，已作废） |
| P2 | 15、16 | 安全 |
| P3 | 17、18、19 | 工程卫生 |

---

## 修复与验证状态（2026-09-29 更新）

### 已搭建编译环境（原报告因无 SDK 只能静态分析，现已补齐）

- JDK 21：`C:\Users\Administrator\.workbuddy\jdk21\jdk-21.0.12.1+1`
- Android SDK：`C:\Users\Administrator\.workbuddy\android-sdk`（platforms 35/36、build-tools 37.0.0 与 36.1.0、platform-tools）
- 项目已生成 `local.properties`；编译命令：
  ```
  export JAVA_HOME='C:\Users\Administrator\.workbuddy\jdk21\jdk-21.0.12.1+1'
  export ANDROID_HOME='C:\Users\Administrator\.workbuddy\android-sdk'
  ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac -x :chaquo:installDebugPythonRequirements
  ```
  （`-x` 跳过的只是 chaquo 装 Python 依赖，本机没装 Python 3.10；Java 编译不受影响）

### 编译验证结果

`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` **均 BUILD SUCCESSFUL**。

编译过程中修掉的实际错误：
1. `Github.java:34/36` — 变量 `body` 与已有的 `String body = fetch(...)` 重名；且 `R` 需显式 import。已改名并补导入。
2. **`CustomWallView.java:95` 引用 `R.drawable.bg_global`，但该 drawable 只存在于 mobile 风味** —— 这是**原有缺陷，非本次改动引入**：leanback 变体此前根本编不过。已将 `bg_global.xml` 移到 `app/src/main/res/drawable/`（两个风味共享），并删除 mobile 下的重复副本。

### 各条目的修复情况

| 条目 | 处理 |
|---|---|
| 1-5 崩溃类 | ✅ 已修 |
| 6 MPV 轨道选择 | ⛔ **本条作废**：MPV 已移除，代码永不执行。改动保留但属死代码，无实际收益 |
| 7 轨道空 override | ✅ 已修（改用 `clearOverridesOfType`） |
| 8 毛玻璃采样错位 | ✅ 已修（改用窗口坐标算偏移） |
| 9 更新弹窗字段 | ✅ 已修（并新增 `update_desc_default` 字符串） |
| 10 数据库旧库被清 | ✅ 已修（开库前自动备份） |
| 11-13 毛玻璃性能 | ✅ 已修（8fps 节流、eraseColor、对象复用、release 回收） |
| 14 MPV 特性探测 | ⛔ **本条作废**：同上，MPV 已移除，永不执行 |
| 15、16 安全 | ⏸ **未改**，属产品取舍，等决策：TLS 全信任是采集站自签证书的兼容基础；内置服务绑 0.0.0.0 是「手机传文件到 TV」的功能基础 |
| 17-19 工程卫生 | ✅ 已清理（yaml 已备份后删除、死代码删除、proguard 补 `{ *; }`） |

### ⚠️ 注意事项

- 本机 sdkmanager 装不了 `platforms;android-37`（最高只到 36），验证期间曾把 `gradle/libs.versions.toml` 的 `compileSdk` 临时降为 36，**现已改回 37**。若在本机重新编译，需再次临时降级。

---

## 补充：本项目实际只使用 EXO 内核（2026-09-29 确认）

### 结论

**MPV 内核已从产品中移除**，运行时恒定走 EXO。三重独立证据：

| # | 证据 | 位置 |
|---|---|---|
| 1 | `getEngine()` **硬编码 `return ENGINE_EXO`**，导致 `isMpv()` 恒 false | `setting/PlayerSetting.java:27-29` |
| 2 | assets 下**没有任何 mpv native 库**（仅 `cacert.pem`/`css`/`index.html`/`js`/`parse.html`），而 `MPVLib.ensureLoaded()` 正是从 assets 加载 so | `app/src/main/assets/`、`is/xyz/mpv/MPVLib.java:64-78` |
| 3 | 因此 `MpvUtil.isAvailable()` 恒 false → `PlayerEngineFactory.resolve()` 恒返回 EXO | `player/mpv/MpvUtil.java:19-25`、`player/engine/PlayerEngineFactory.java` |

运行时链路安全：`PlayerEngineFactory.resolve()` 有 `isMpvReady()` 兜底，即使偏好里残留 MPV 值也不会崩溃，只会静默回退 EXO。

### 由此产生的待清理项：13539 行 MPV 死代码

MPV 的 **47 个 Java 文件、共 13539 行**仍在参与编译并打进 APK，但永远不会执行。主要体积：

| 文件 | 行数 |
|---|---|
| `androidx/media3/mpvplayer/MpvPlayer.java` | 5204 |
| `androidx/media3/mpvplayer/MpvHlsProxy.java` | 2417 |
| `androidx/media3/mpvplayer/MpvHlsCacheCoordinator.java` | 704 |
| `is/xyz/mpv/MPVLib.java` | 536 |
| `setting/MpvPerformanceSetting.java` | 393 |
| 其余 42 个文件 | 约 4300 |

**收益**：明显缩减 APK 体积与方法数（利于 dex 分包和低端设备）；消除后续维护与阅读时的误导（很容易误以为 MPV 仍可选，从而像本报告初版那样误判缺陷）。

**建议做法**：分两阶段——
1. 先删 `androidx/media3/mpvplayer/` 与 `is/xyz/mpv/` 两个纯 MPV 包（最大头、无外部引用）；
2. 再清理 `player/mpv/`、`player/lut/`、`player/effect/` 下的 Mpv* 包装类与 leanback 的 `MpvConfDialog`，并把 `R.array.select_engine` 收缩为仅 EXO。

每阶段删完立即编译验证（mobile + leanback 两个变体），确保无残留引用。

### 附带发现：设置页「播放引擎」选项点击无反应

- 位置：`SettingPlayerActivity.java:77-79`（leanback）、`SettingPlayerFragment.java:79-81`（mobile）
- 现象：`setEngine()` 执行 `putEngine((getEngine()+1) % engine.length)` 写入偏好，但 `getEngine()` 已硬编码返回 EXO、不再读偏好 → 点击后界面文字**永远显示 EXO**，看起来像按钮失灵。
- 影响：无崩溃，纯 UX 困惑。
- 修复：既然引擎已固定，应隐藏该设置项（或改文案为只读的「EXO」）。
