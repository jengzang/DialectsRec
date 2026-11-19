# 测试报告

日期：2026-11-19（Asia/Shanghai）

## 自动化检查

- `python3 -m unittest tools/test_generate_corpus.py tools/test_dataset_tools.py -v`：7 tests passed。
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew testDebugUnitTest --no-daemon --console=plain`：BUILD SUCCESSFUL。
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew lint assembleDebug --no-daemon --console=plain`：BUILD SUCCESSFUL，lint 0 errors。
- `git diff --check`：无空白错误。

## 模拟器冒烟

使用本机 AVD `dialects_api35`、Android SDK emulator 36.6.11、API 35：

1. 安装 `app/build/outputs/apk/debug/app-debug.apk`，离线启动。
2. 创建发音人 `SP001`，显示 5,000 条内置句库。
3. 选择全部主题和默认句号范围，进入 `录音 · 1 / 5000`。
4. 首次点击开始录音触发麦克风权限对话框；授予“While using the app”后录音成功。
5. 录制约 14.7 秒，停止后生成并保存 16 kHz PCM16 mono WAV，首页显示已完成 1。
6. 第二句选择“无法自然表达”跳过，首页显示已跳过 1，未录制 4,998。
7. 使用 Storage Access Framework 导出 ZIP；标准 `zipfile` 打开成功。
8. `python3 tools/validate_dataset.py <导出的 ZIP>`：通过，覆盖 5,000 sentences；导出录音包含 selected、采样参数、audio_source 和 SHA-256。
9. 复制为第二个发音人 ZIP 后运行 merge：输出两个 speaker 目录，冲突数为 0。

## 未覆盖的实机项目

模拟器麦克风不能代表真实手机的硬件音频链路；尚未在三台不同品牌的真实 Android 手机上验证来电、锁屏、厂商音频处理和不支持 16 kHz 原生采集的设备。首版没有后台持续录音和自动方言转写。

## 追加回归场景

- 对同一句再次录音后，数据库自动递增 `take_no`、清除旧 selected 指针；导出 ZIP 只含 1 条当前有效录音，Python 校验通过，没有重复 WAV 路径。
- 导出 `progress.csv` 覆盖全部 5,000 句，其中未录制句明确为 `not_recorded`。
- 录音期间上一句/下一句/跳过按钮禁用；返回键会提示继续录音或放弃当前临时录音。
- 跳过已有录音后，progress 清除 selected_recording_id，导出不会再产生矛盾的 skipped/selected 组合。
- 任务主题、范围和当前位置写入本地 settings；重启 App 后点击“继续录音”可恢复上次任务位置。
- 首次建档后显示“资料已保存”引导页，明确说明选择任务、开始录音、结束保存和进入下一句的操作顺序。
- 模拟器验证返回键：录音页返回任务选择页，任务选择页返回首页；录音中仍先提示继续录音或放弃本次录音。
- 新增暖橙色“麦克风 + 方言印章”矢量 Logo，作为启动图标和建档/引导页标识。

## 2026-11-20 回归：句库 2.0 与录音体验

- LLM 句库最终为 5,000 条唯一句子，难度分布 short/situational/narrative = 3,500/1,000/500；全量规则检查通过，corpus hash 为 `1ba47bf4167e2d9e7ef6e805c7ee09672d4f8c815be3a8a0b34f8eb4181fc135`。
- `python3 -m unittest tools/test_generate_corpus.py tools/test_dataset_tools.py -v`：7 tests passed；Android `testDebugUnitTest`：全部通过；lint 0 errors；`assembleDebug` 成功。
- 版本：versionCode 3、versionName 1.2，APK：`方言语音采集-1.2.apk`。
- 模拟器 `dialects_api35` 验证升级后保留本地资料，并从句库 manifest 自动迁移显示 `dialect_general / 2.0`。
- 录音页显示橙色“正在录音”和实时 `mm:ss` 计时；结束后后台写入 WAV，显示“已保存”；试听期间显示“正在试听当前录音…”，播放完成显示“试听完成，可再次播放。”。
- “全部句子与状态”使用完整 ListView 展示 5,000 条，不再截断 200 条；分类筛选“家庭”显示 406 条，条目包含 sentence_id、状态、类别和文本。
- 导出 ZIP 后自动打开 Android 系统分享面板；模拟器显示 Quick Share、Drive、Bluetooth、Gmail 等目标。安装微信的设备会由系统分享面板提供微信目标。

## 2026-11-20 录音原始输入与导出分流回归

- 录音源改为 Android 原生 `MIC`，不再优先使用 `UNPROCESSED`；采集的 PCM 不做软件降噪、自动增益或回声消除，只有设备采样率不是 16 kHz 时按数据集规格做重采样。
- 模拟器重复录音验证：第一次录音计时 `00:04`，保存后再次开始录音计时 `00:05`，确认 Handler 不再丢失。
- 导出 ZIP 后先显示“分享到微信 / 仅保存本地”选择框；选择本地不会打开分享面板，选择微信时已安装微信则直达微信，未安装时回退系统分享面板。
- 新版本：versionCode 4、versionName 1.3。

## 2026-11-20 录音按钮计时回归

- 录音计时不再占用独立卡片；主按钮在录音中直接显示“● 录音中 mm:ss”，接近 60 秒上限时切换深红色提醒，并在 01:00 封顶。
- 录音和结束并保存合并为同一个主按钮：未录音时显示“开始录音”，录音中再次点击结束并保存，已有录音或保存成功后显示“重新录音”。
- `RecordingTimerTest` 覆盖录音按钮文案和 60 秒封顶；`testDebugUnitTest` 通过。
- 新版本：versionCode 5、versionName 1.4。

## 2026-11-20 录音原始输入与导出分流回归

- 录音源改为 Android 原生 `MIC`，不再优先使用 `UNPROCESSED`；采集的 PCM 不做软件降噪、自动增益或回声消除，只有设备采样率不是 16 kHz 时按数据集规格做重采样。
- 模拟器重复录音验证：第一次录音计时 `00:04`，保存后再次开始录音计时 `00:05`，确认 Handler 不再丢失。
- 导出 ZIP 后先显示“分享到微信 / 仅保存本地”选择框；选择本地不会打开分享面板，选择微信时已安装微信则直达微信，未安装时回退系统分享面板。
- 新版本：versionCode 4、versionName 1.3。
