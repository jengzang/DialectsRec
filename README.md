# 方言语音采集 App

这是一个完全离线的 Android 方言语音采集工具。当前版本为 **1.6（versionCode 7）**，内置暨大·潮州（默认）、暨大·苏州、AI·通用补充、暨大·地方特色四套句库，发音人可以按类别或句号范围选择任务，用自己的方言自然表达书面提示的意思，然后导出可在电脑上校验和合并的 ZIP。

## 暨大数据版权与本地输入

暨大原稿、全部衍生句库、逐条审核记录和打包产物仅保留在本机，不进入 Git 远端。`.gitignore` 覆盖原稿目录、`corpus_jnu_*` 资产目录和 `docs/corpus-review/jnu-v*/`。本地现有的四库 APK 仍包含已获用户授权用于本地采集的数据。

从远端新克隆代码后，需自行提供有授权的两份原稿并运行审查脚本，或在本地恢复三个 `corpus_jnu_*` 目录，才能构建使用完整四库的 App。没有本地私有数据时，Python 的四项私有语料合同测试会跳过；Android 四库集成测试需要本地句库输入。APK、录音和导出 ZIP 也不进入远端。

## 构建

本项目使用 Android SDK 36、Build Tools 36、Gradle 8.14.3、Android Gradle Plugin 8.13.0。第一次构建需要网络下载 Gradle/AGP 依赖；本机验证使用 Java 21 和 `https://maven.aliyun.com/repository/google` 镜像。

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
```

生成 APK：`app/build/outputs/apk/debug/方言语音采集-1.6.apk`。

## 使用

首次启动建立发音人 ID 和方言点。首页可切换句库，再选择类别和句号范围，录音页支持录音、停止保存、试听、跳过、上一句和下一句。停止后只有 WAV 校验和数据库写入都成功才会标记完成。导出使用系统文件选择器，源录音不会自动删除。

创建采集任务时随机打乱符合条件的句子，队列内无重复、无遗漏。“继续上次任务”恢复原队列和当前位置；重新选择新任务会重新随机。首页“全部句子与状态”及分类进度始终按原句号顺序展示，录音文件和导出仍关联原始 sentence_id。

切换句库和发音人后，各自的任务顺序、当前位置和录音进度独立保留。暨大·地方特色可按“潮州”“苏州”两个类别选择；不熟悉的专名、俗语应跳过，避免猜读。原稿文本和编号不静默改写；显示时隐藏 & 标记，导出保留原文及来源。

数据库恢复回归可在模拟器上运行（使用独立测试数据库，不修改采集数据）：

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/方言语音采集-1.6.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.dialectrec.test/com.dialectrec.TaskOrderInstrumentation
```

App 只申请麦克风权限，不申请联网权限。录音目标是 PCM signed 16-bit little-endian、16 kHz、单声道 WAV；没有麦克风权限时仍可以浏览句库和导出已有数据。

## 句库和电脑工具

`tools/generate_corpus.py` 可离线重建固定句库；规范哈希和 ZIP 协议见 [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md)。

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
python3 tools/validate_dataset.py sample.zip
python3 tools/merge_datasets.py a.zip b.zip --output merged_dataset
```

暨大原文共 7,728 条，通过本地 `gpt-6-luna` Responses API 全量语义审查，地方特色项及疑难项二次复核；五并发、流式返回，每批精确校验原编号集合。提示词见 [locality-prompt.txt](docs/corpus-review/locality-prompt.txt)，本地逐条判定见 [audit.csv](docs/corpus-review/jnu-v1/audit.csv)，统计见 [summary.json](docs/corpus-review/jnu-v1/summary.json)。第三方原稿中的录音指令、括号释义和疑似文本问题以独立质量标记保留，不自动改写；可在 App 中跳过。

重新执行审查（API 密钥仅由环境变量提供，不写入仓库或 Codex 配置；已完成批次从 checkpoint 恢复）：

```bash
export OPENAI_API_KEY
python3 tools/review_jnu_corpus.py --source-dir /path/to/暨大 --workers 5
```

`spoken_transcript` 首版始终保持空值，不会使用 `prompt_text` 冒充方言逐字转写。`tools/test_dataset_tools.py` 中的 WAV 仅为测试模拟音频。

## 已知限制

当前实现优先保证离线采集、句子关联、WAV 事务和导出协议。首版没有自动转写、后台录音、云同步或实时音量分析。设备不支持 16 kHz 原生采集时会提示设备能力；没有真实麦克风的模拟器无法证明实机录音质量，需要在至少三台真实 Android 手机上复测。

本仓库提供 `.githooks/pre-push` 防误传检查：检查待推送分支的**全部可达历史**，有暨大私有目录或 APK/ZIP/录音等产物就拒绝推送。当前本机已启用。新克隆可用 `git config core.hooksPath .githooks` 启用；手动检查用 `python3 tools/check_corpus_privacy.py HEAD`。不要推送本地私有备份分支，也不要使用 `git push --all`。
