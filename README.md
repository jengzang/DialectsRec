# 方言语音采集 App

这是一个完全离线的 Android 方言语音采集工具。当前版本为 **1.5（versionCode 6）**，内置 `corpus_v1` 统一句库（5,000 条），发音人可以按主题或句号范围选择任务，用自己的方言自然表达书面提示的意思，然后导出可在电脑上校验和合并的 ZIP。

## 构建

本项目使用 Android SDK 36、Build Tools 36、Gradle 8.14.3、Android Gradle Plugin 8.13.0。第一次构建需要网络下载 Gradle/AGP 依赖；本机验证使用 Java 21 和 `https://maven.aliyun.com/repository/google` 镜像。

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
```

生成 APK：`app/build/outputs/apk/debug/方言语音采集-1.5.apk`。

## 使用

首次启动建立发音人 ID 和方言点。首页可选择主题和句号范围，录音页支持录音、停止保存、试听、跳过、上一句和下一句。停止后只有 WAV 校验和数据库写入都成功才会标记完成。导出使用系统文件选择器，源录音不会自动删除。

创建采集任务时随机打乱符合条件的句子，队列内无重复、无遗漏。“继续上次任务”恢复原队列和当前位置；重新选择新任务会重新随机。首页“全部句子与状态”及分类进度始终按原句号顺序展示，录音文件和导出仍关联原始 sentence_id。

数据库恢复回归可在模拟器上运行（使用独立测试数据库，不修改采集数据）：

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/方言语音采集-1.5.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.dialectrec.test/com.dialectrec.TaskOrderInstrumentation
```

App 只申请麦克风权限，不申请联网权限。录音目标是 PCM signed 16-bit little-endian、16 kHz、单声道 WAV；没有麦克风权限时仍可以浏览句库和导出已有数据。

## 句库和电脑工具

`tools/generate_corpus.py` 可离线重建固定句库；规范哈希和 ZIP 协议见 [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md)。

```bash
python3 -m unittest tools/test_generate_corpus.py tools/test_dataset_tools.py -v
python3 tools/validate_dataset.py sample.zip
python3 tools/merge_datasets.py a.zip b.zip --output merged_dataset
```

`spoken_transcript` 首版始终保持空值，不会使用 `prompt_text` 冒充方言逐字转写。`tools/test_dataset_tools.py` 中的 WAV 仅为测试模拟音频。

## 已知限制

当前实现优先保证离线采集、句子关联、WAV 事务和导出协议。首版没有自动转写、后台录音、云同步或实时音量分析。设备不支持 16 kHz 原生采集时会提示设备能力；没有真实麦克风的模拟器无法证明实机录音质量，需要在至少三台真实 Android 手机上复测。
