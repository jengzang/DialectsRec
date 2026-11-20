# 随机采集顺序 Implementation Plan

> 执行方式：当前会话内执行，完成后独立代码复核，按用户既定偏好合回 main 并推送。

**Goal:** 随机采集、稳定恢复、固定进度浏览。

**Architecture:** 独立的纯 Java `TaskOrder` 负责洗牌、旧任务后缀迁移和队列编码恢复；`CorpusDatabase.saveTask` 原子保存队列和身份；MainActivity 仅在新建/恢复任务入口应用，不修改 list 查询。

**Tech Stack:** Java 17、Android 原生 UI/SQLite、JUnit4、Gradle、本机 API35 模拟器。

### 1. 随机与恢复规则

- [x] 新增 `app/src/test/java/com/dialectrec/data/TaskOrderTest.java`，测试 5,000 条完整排列、原列表不变、不同 Random 种子输出不同；空/单句/范围；encode/restore 一致；非法快照拒绝；legacy 保留前缀和当前句。
- [x] 运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew testDebugUnitTest --no-daemon --console=plain`，确认因缺少 TaskOrder 失败。
- [x] 实现 `TaskOrder.shuffled(List<Sentence>,Random)`（复制后 Collections.shuffle）、`migrateLegacy(List<Sentence>,int,Random)`（仅洗牌 current+1 后缀）、`encode(List<Sentence>)`（逗号分隔安全 ID）、`restore(List<Sentence>,String)`（Map.remove 校验一一对应，非法抛 IllegalArgumentException）。运行上述单测命令至通过。

### 2. 接入与保存

- [x] `CorpusDatabase.saveTask` 用事务保存 `task_category/start/end/current/order_ids/speaker_id/corpus_id/corpus_version`。保留原 `list` SQL 固定排序。
- [x] MainActivity 新建任务调用 shuffled；persistTask 调用 saveTask；resumeTask 校验身份和快照后恢复；legacy 只随机后缀并保存快照。身份不符或快照非法显示任务选择，避免错误断点。
- [x] 更新任务/录音说明和版本 1.5。扩展 SentenceListFilterTest 验证分类前后仍保持原句号顺序。
- [x] 执行 `python3 -m unittest tools/test_generate_corpus.py tools/test_dataset_tools.py -v` 与 `./gradlew testDebugUnitTest lint assembleDebug --no-daemon --console=plain`，检查 `git diff --check`。

### 3. 模拟器与收尾

- [x] 安装升级至模拟器 `dialects_api35`。使用 uiautomator 和 SQLite 快照核对旧任务迁移、新任务随机队列、上下句、重启恢复及原句号录音保存/跳过、固定进度列表与分类。
- [x] 更新 `docs/TEST_REPORT.md`，记录实际结果和 APK。
- [x] 代码复核，修正实际发现的问题并复验。
- [ ] 提交并合回 main，原目录构建 1.5 APK，推送 `origin main`。

附加回归：实际 ZIP 暴露旧句库元数据丢失，已修复并通过 native 回归及真实 schema 3→4 升级、ZIP 校验；详见 TEST_REPORT.md。
