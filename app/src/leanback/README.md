# app/src/leanback —— 电视版源码（不参与编译）

本目录是电视版源码留档，**当前完全不参与编译**，改这里不会生效：

- `app/build.gradle` 里 `leanback` productFlavor 与两行 `leanbackImplementation` 依赖均已注释停用，AGP 不会编译本目录；
- 电视版已停止维护与分发，仓库根目录 `README.md` 有说明；
- 大屏设备（平板 / 折叠屏，smallestWidth ≥ 600dp）走的是 `app/src/mobile/res/layout-sw600dp/`，与本目录无关。

如需重启电视版：解开 `app/build.gradle` 中 flavor 与两行依赖的注释，并在 workflow 里恢复 `:app:assembleLeanbackArm64_v8aRelease` 构建任务；不必翻 git 历史。

保留目录只是方便日后重启，别在这里修手机版的 bug。
