# hook —— 源码留档目录（改动不生效）

本目录的 `src/` 是配套库的源码留档，**当前不参与构建**：

- `settings.gradle` 只 include 了 `:app`、`:catvod`、`:chaquo`、`:quickjs`，没有 include 本模块；
- `app/build.gradle` 通过 `implementation fileTree(dir: "libs", include: ["*.aar"])` 引入依赖，实际编进 APK 的是 `app/libs/hook-release.aar`。

所以直接修改本目录下的 Java / 资源，Gradle 不会读取，APK 不会有任何变化；全仓搜索、IDE 索引和代码审查里看到的也是这份不生效的代码。

要让改动生效，需重新构建并替换 `app/libs/hook-release.aar`，或把本模块加回 `settings.gradle` 并改用 project 依赖。后者会改变编译图和产物内容，须单独评估验证后再做，不要在测试或发版期间顺手改。

保留目录仅为追溯实现来源。构建与分发说明见仓库根目录 `README.md`。
