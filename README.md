# Touch Synth V0

一个最小可玩的安卓触控合成器原型。

## 当前功能

- 8 个 C 大调音符触控垫
- 正弦波 / 锯齿波 / 方波
- 音量、Attack、Release
- 使用 `AudioTrack` 流式生成 PCM Float 音频
- 请求低延迟播放模式

## 打开方式

1. 安装最新版 Android Studio。
2. 解压项目后选择 **Open**，打开 `TouchSynthV0` 文件夹。
3. 等待 Gradle Sync 完成。
4. 使用安卓真机运行，建议连接有线耳机或 USB 音频设备。

## V0 的定位

本版只验证三个问题：

1. 手机触控演奏是否顺手；
2. 基础音频输出在你的手机上是否稳定；
3. 你更喜欢键盘、网格还是后续的 XY 触控方式。

下一步计划：低通滤波器、第二振荡器、复音、录制 WAV、MIDI 事件记录。

## 关于 Gradle Wrapper

压缩包未内置 `gradle-wrapper.jar`。若 Android Studio 提示缺少 Wrapper：

- 使用 Android Studio 的 Gradle 修复/同步提示自动生成；或
- 在项目目录执行本机已安装的 `gradle wrapper --gradle-version 8.13`。
