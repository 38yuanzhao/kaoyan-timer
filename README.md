# 考研时间管理 (Kaoyan Timer)

一款面向考研备考的时间管理 Android 原生 App。它是某网页版 App 的原生移植,功能完全对齐,使用 Kotlin + Jetpack Compose(Material3)构建,采用纯深色主题。

## 项目简介

本应用帮助考研学生跟踪各科目、各子项的学习时长,提供初试倒计时、备考进度、番茄钟、近 7 天投入图表以及白/棕噪音等功能,所有数据本地持久化,无需联网。

## 功能列表

- **初试倒计时**:大号显示距离初试还有多少天,可点击在「天」与「H 时 M 分」之间切换;初试日期可自定义。
- **备考进度**:根据备考起点与初试日期计算完成百分比,显示剩余天数,起点日期可改。
- **番茄钟**:选择任意子项进行专注/休息循环,专注与休息时长可步进调整,到点自动提示音 + 震动,并把专注时长结算到对应子项。
- **科目模板**:内置两套模板(11408 学硕 / 22048 专硕),科目顺序统一为 数学 → 408 → 英语 → 政治;支持切换模板(重置各科时长,保留每日统计)。
- **学习计时**:每个科目可选择当前子项,一键开始/暂停计时,正在计时的卡片以绿色高亮;支持手动加减分钟(可负)。
- **今日与累计**:实时显示今日学习小时数与全部累计时长。
- **近 7 天投入图表**:Canvas 绘制 7 根柱状图,今日以绿色突出,柱上标注小时数。
- **白/棕噪音**:可在白噪音、棕噪音之间互斥切换,并通过滑块调节音量。
- **纯深色主题**:统一的深色配色,长时间使用更护眼。

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
- UI:Jetpack Compose + Material3(Compose BOM 2024.06.00,Compose Compiler 1.5.14),纯深色主题
- 构建:Gradle 8.7,AGP 8.5.2,Kotlin DSL
- SDK:minSdk 24,compileSdk 34,targetSdk 34
- 持久化:SharedPreferences + kotlinx.serialization(JSON,1.6.3)
- 音频:AudioTrack 生成正弦音/白噪音/棕噪音,Vibrator 震动
- 架构:AndroidViewModel + StateFlow
- 包名:`com.kaoyan.timer`
