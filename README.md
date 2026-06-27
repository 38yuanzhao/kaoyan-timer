# 考研时间管理 (Kaoyan Timer)

一款面向考研备考的时间管理 Android 原生 App。使用 Kotlin + Jetpack Compose(Material3)构建,石墨蓝深色主题,底部四标签(仪表盘 / 专注 / 数据 / 设置)导航。

## 项目简介

本应用帮助考研学生跟踪各科目、各子项的学习时长,提供初试倒计时、备考进度、番茄钟(可整屏沉浸专注)、近 7 天投入图表、各科分布、白/棕噪音(内置真实录音循环)等功能。数据本地持久化、默认无需联网;并支持导出/导入 JSON 备份,在多台设备间同步进度。

## 功能列表

- **底部四标签导航**:仪表盘 / 专注 / 数据 / 设置,概览与高频操作分屏,告别功能堆成一长列。
- **初试倒计时**:仪表盘英雄区大号显示距初试天数,可点击在「天」与「时分」间切换;初试日期在设置里改。
- **概览数据卡**:仪表盘 2×2 卡片一眼看全 —— 备考进度(环形)、今日投入、连续天数、累计时长。
- **番茄钟**:选子项进行专注/休息循环,环形倒计时;**启动后整屏沉浸专注**,到点自动提示音 + 震动并结算到子项;可最小化返回(计时不停)。
- **学习计时**:每个科目选当前子项,一键开始/暂停,**运行中卡片蓝色高亮并自动置顶**;支持手动加减分钟(可负)。
- **近 7 天图表 / 各科分布**:数据页 Canvas 柱状图(今日突出)+ 各科投入横条。
- **科目模板**:内置 11408 学硕 / 22048 专硕,科目顺序统一为 数学 → 408 → 英语 → 政治;在设置里切换(重置各科时长,保留每日统计)。
- **白/棕噪音**:内置雨声(白)与 Academic Brown(棕)真实录音,互斥切换 + 音量滑块,循环播放。
- **数据备份 / 多端同步**:设置里导出一份 JSON 备份,在另一台设备导入即可同步进度(也可存进网盘同步文件夹两端互读);无需账号、无需联网。
- **石墨蓝深色主题**:中性近黑 + 蓝色单强调,零阴影亮度分层,长时间使用更护眼。

## 在 Android Studio 中打开运行

1. 使用 Android Studio(建议较新版本,内置 AGP 8.5.2 / Gradle 8.7 兼容)打开本项目根目录。
2. 等待 Gradle 同步完成,首次同步会自动下载依赖。
3. 连接 Android 设备(开启 USB 调试)或启动一个 API 24 及以上的模拟器。
4. 点击工具栏的 **Run ▶** 按钮,选择目标设备即可安装运行。

命令行构建(可选):

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 从 GitHub Actions / Releases 下载 APK

- **Actions 构建产物**:每次推送到 `main` 分支都会触发 CI 构建,进入仓库的 **Actions** 标签页,打开对应的工作流运行记录,在底部 **Artifacts** 中下载名为 `app-debug` 的压缩包,解压即得 `app-debug.apk`。
- **Releases 发布版本**:当推送形如 `v*`(例如 `v1.0.0`)的 Git tag 时,CI 会自动创建一个 GitHub Release,并把 `app-debug.apk` 作为发布资产附上,可在仓库的 **Releases** 页面直接下载。

## 技术栈

- 语言:Kotlin 1.9.24
- UI:Jetpack Compose + Material3(Compose BOM 2024.06.00,Compose Compiler 1.5.14),石墨蓝深色主题;底部 NavigationBar 分屏(状态切换,未引入 Navigation-Compose)
- 构建:Gradle 8.7,AGP 8.5.2,Kotlin DSL
- SDK:minSdk 24,compileSdk 36,targetSdk 34
- 持久化:SharedPreferences + kotlinx.serialization(JSON,1.6.3)
- 备份 / 同步:SAF(CreateDocument / OpenDocument)导出 / 导入 AppState 的 JSON,跨设备同步进度
- 音频:AudioTrack 合成提示音;MediaPlayer 循环播放内置雨声 / 棕噪音录音(`res/raw`,OGG/Vorbis);Vibrator 震动
- 架构:AndroidViewModel + StateFlow
- 包名:`com.kaoyan.timer`
