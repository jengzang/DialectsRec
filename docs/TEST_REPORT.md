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
