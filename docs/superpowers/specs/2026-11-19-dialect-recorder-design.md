# 方言 ASR 语料采集 Android App 设计

## 目标

交付一款可离线运行的 Android 原生 App，内置约 5,000 条统一书面汉语提示句，支持研究者按主题或句号范围给发音人分配采集子集，并可靠采集、保存、试听、重录、跳过和导出方言录音。首版只预留 `spoken_transcript`，不自动生成方言逐字转写。

## 已确认范围

- 内容单位为完整句子，不是词语或短语。
- 内置一个统一通用句库 `corpus_v1`，所有发音人使用同一 `sentence_id` 映射。
- 约 3,500 条日常短句、1,000 条情境句、500 条描述/叙事句；每句带主题、难度、句长元数据。
- 句库在开发阶段通过公开可用内容和受控 AI 补充生成，经过去重、敏感内容筛选、长度/主题校验后固定打包；App 运行时不联网、不生成。
- 采集时在 App 内按主题或句号范围选择任务；仍支持 TXT/CSV 导入新句库。
- 优先级为数据不丢失、关联正确、离线可用，其次是功能和视觉效果。

## 架构

单 Activity + Jetpack Compose/Material 3，MVVM、Repository、Coroutines/Flow；Room 管理句库、发音人、会话、录音、句子进度和任务筛选；AudioRecord 流式写 PCM 临时文件，必要时重采样后写合法 16 kHz/16-bit/mono WAV；Storage Access Framework 流式导出 ZIP；Python 标准库工具验证和合并导出包。

内置句库作为 APK assets 中的版本化 CSV/JSON，并在首次启动时以事务方式导入 Room；导入结果保存 `corpus_hash`。录音文件只存应用专属目录，数据库保存相对路径和校验信息。所有长耗时 IO、解码、校验、导出在 Dispatchers.IO 执行。

## 内容与任务数据流

1. 首次启动显示采集用途、离线属性、存储和导出提醒；创建本地发音人资料。
2. 预置句库导入 Room，计算并核验固定 SHA-256；展示总句数、主题和版本。
3. 首页展示总量和状态统计；任务选择页按主题、难度、句号范围生成当前任务视图。选择只影响待采集列表，不修改句库和 `sentence_id`。
4. 录音页以 `sentence_id`、`prompt_text` 和进度为核心，录制时显示明显状态；结束后校验 WAV，再提交 completed 和 selected recording。
5. 列表页提供全部、未录制、已完成、已跳过、待复核筛选；跳过始终保存原因。
6. 导出页用 SAF 选择目标 URI，导出固定 ZIP 协议并记录最后成功导出时间；不删除本地源录音。

## 主要界面

- 首次使用：说明、发音人资料、内置句库确认。
- 首页：句库版本/哈希摘要、发音人、任务范围、完成/跳过/待复核统计、继续采集、任务选择、录音列表、导出、设置。
- 任务选择：主题多选、难度筛选、句号起止范围、预计条数；保存为当前本地任务筛选。
- 录音页：句子文本、ID、进度、状态、计时、开始/结束并保存、试听、重录、上一句/下一句、跳过和跳过原因。
- 录音列表：状态筛选、时长、take 数、问题标记、进入录音页。
- 设置/导出：发音人资料、句库详情、录音能力、导出进度、数据清理和备份提醒。

## 关键可靠性规则

- 录音状态为 `idle -> recording -> processing -> completed`；失败进入 `failed`，跳过只存在于句子进度。
- 录音先写 `.tmp`，停止后完成 WAV 头、规格/长度/非空校验和 SHA-256，再原子重命名，最后在同一事务更新数据库；任何中间失败都不覆盖旧 take。
- 重录成功前保留旧录音和 selected 指针；新 take 成功后才切换 selected。
- 启动时扫描残留临时文件和 processing 记录，提供恢复或清理，不虚报完成。
- 麦克风权限、设备占用、音频焦点、切后台、存储不足、导出取消均给出可恢复提示，已有录音保持可用。
- 不申请 INTERNET、位置、联系人或广泛存储权限。

## 导出与 PC 工具

ZIP 固定包含 `manifest.json`、`corpus.json`、`speaker.json`、`sentences.csv`、`recordings.csv`、`progress.csv` 和 `wav/`。只导出已选中的有效 WAV；`spoken_transcript` 默认为空，`prompt_text` 不复制过去。manifest 和 CSV 记录 hash、状态、文件路径和统计，导出阶段验证每条关联。

`tools/validate_dataset.py` 使用标准库检查 ZIP、JSON、CSV、关联关系、SHA-256、WAV 头、帧数和时长；失败返回非零状态。`tools/merge_datasets.py` 先校验 corpus 身份，再按 speaker 目录重映射音频；重复 ZIP 可去重，speaker 同名但内容不同或录音不同必须报冲突，不覆盖文件。

## 测试与验收

- JVM 单元测试：句库 TXT/CSV 解析、规范哈希、ID/版本规则、WAV 头和校验、导出关联、Python 工具冲突检测。
- Android 测试：Room 事务、状态机、权限拒绝、重录失败保留旧 take、断点恢复；没有真实设备时明确标注模拟录音限制。
- 端到端模拟数据：导入 100 句，生成模拟 PCM，采集完成/跳过/重录，导出 ZIP，运行 Python 校验和多发音人合并。
- 本机 Android SDK/Gradle 可用时执行 assemble、单元测试、lint，并安装 debug APK 做离线启动和核心页面冒烟测试。
