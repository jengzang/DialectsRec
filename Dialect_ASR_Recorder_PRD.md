# Android 方言 ASR 语料采集 APP — 产品需求与技术实现文档（PRD v1.0）

> **交付对象**：AI 编程 Agent / Android 开发者  
> **交付目标**：可离线安装使用的 Android APK、可构建的完整源代码、数据合并/校验 Python 脚本及使用文档。  
> **优先级**：数据不丢失、数据对应正确、离线可用 > 功能丰富 > 视觉效果。

## 1. 项目背景与目标

开发一款专供汉语方言语音语料采集的 Android 原生 APP。研究者预先准备书面汉语独立句子列表，通过 TXT 或 CSV 分发给多位发音人。发音人在**各自的 Android 手机**上看到一条书面语句子，**用自己的方言自然表达该句的意思**，而不是强制按普通话书面语逐字读出。APP 为每条录音生成规范的 16 kHz / 16-bit / mono PCM WAV，自动持久化录音进度及对应信息，最终离线导出 ZIP。研究者在电脑端将不同发音人的数据安全合并与校验，为后续 ASR/语音理解研究准备语料。

### 1.1 必须明确的数据语义

- `prompt_text`：显示给发音人的**书面汉语语义提示文本**。
- `spoken_transcript`：实际说出的**方言口语逐字转写**，首版可为 `null`；不可由 `prompt_text` 自动冒充。
- 一段录音与一个 `sentence_id` 是**语义对应**，未必字词一一对应。导出文档和数据字段必须清楚标注，避免错误地将提示文本直接作为严格监督 ASR 的真值标签。
- 对于后续严格监督 ASR 训练，需另行补充、核验实际口语转写；首版仅预留字段和状态，不开发自动转写模型。

### 1.2 确定的产品约束

| 维度 | 已确认需求 |
|---|---|
| 平台 | Android 原生 APK |
| 句库 | 从本地导入 TXT/CSV；APP 不生成句子、不调用 AI |
| 录音行为 | 阅读书面语，用方言口语自然表达，尽量保持原意 |
| 发音人 | 多位发音人，各自在自己的手机采集 |
| 网络 | 全程离线，无登录、无账号服务器、无云同步 |
| 音频输出 | PCM WAV，16,000 Hz、16-bit、单声道 |
| 导出 | ZIP 包，包含 WAV、句库、元数据、清单 |
| 设备 | 优先兼容常见 Android 手机，适配不同屏幕尺寸 |

## 2. 范围、原则与非目标

### 2.1 第一版必须实现（P0）

1. 首次使用向导：创建/选择本地发音人资料，导入句库。
2. TXT/CSV 句库导入、预览、校验、稳定标识和版本管理。
3. 逐句采集：录制、停止保存、试听、重录、上一句、下一句、跳过、跳过原因、继续上次进度。
4. 本地持久化：SQLite/Room 管理元数据；WAV 文件保存到应用专属存储中。
5. 音频：目标输出 16 kHz/16-bit/mono WAV；录音失败、权限、空间不足和中断处理。
6. 录音列表与状态筛选：未录制、已完成、已跳过、待复核。
7. 完全离线 ZIP 导出；包含结构化 JSON/CSV；导出时校验关联关系和文件完整性。
8. Python 脚本：校验单份 ZIP，合并多位发音人的 ZIP；冲突不静默覆盖。
9. 完整的错误提示、恢复与人工备份路径。

### 2.2 第二版可选（P1，P0 完成后）

- 自动进入下一句、倒计时、按指定句号范围分配任务、批量检查未完成句子。
- 实时音量条、过短/静音/削波等提示（不自动删除）。
- 更丰富的发音人字段、统计图、可选的录音重试历史导出。
- 句子搜索和关键词筛选、导入后修正操作（必须产生新句库版本而非静默修改）。

### 2.3 明确不做

- 不做服务器、注册登录、联网同步、广告或统计 SDK。
- 不在 APP 内接入大语言模型、ASR 模型、云端转写或强制 IPA 标注。
- 不默认生成任何“实际方言逐字转写”；不将提示文字命名为 `ground_truth_transcript`。
- 不自动剔除“不标准”的方言表达或有噪音的录音。
- 不申请位置、联系人、相册全库、管理所有文件等不必要权限。

## 3. 典型用户流程

### 3.1 研究者准备句库

1. 编写 UTF-8 TXT（每行一句）或 UTF-8 CSV（至少 `sentence_id,text` 两列）。
2. **推荐 CSV**，研究者统一分配全局稳定的句子 ID；不同发音人得到同一版本的文件。
3. 句库预览可查看条数、空句、重复 ID、重复文本、异常过长句子等。
4. APP 导入时生成/读取 `corpus_id`、`corpus_version` 和内容指纹 `corpus_hash`。

### 3.2 发音人初次使用

1. 打开 APP，阅读简明的录音和数据用途说明。
2. 建立本地发音人：输入研究者分配的 `speaker_id`，方言名称和具体方言点；其他个人信息选填。
3. 导入句库，确认句数及版本，进入采集首页。
4. 首次录音时再请求麦克风权限。

### 3.3 逐句录音

1. 录音页显示：当前句子 ID、书面语原文、当前进度、录音状态。
2. 点击“开始录音”，界面明显提示正在录音；点击“结束并保存”后写出 WAV 并确认成功。
3. 保存后允许试听；可重录、标记待复核、进入下一句。
4. 若句子难以用方言表达，可“跳过”并选择原因（无法自然表达、不了解含义、暂时跳过、其他）。
5. 可随时前后切换；不能在正在录音时切换，须先停止/取消并提示。
6. 关闭 APP、手机重启后，保持已完成状态、已采集音频以及最后访问的位置。

### 3.4 研究者收集数据

1. 发音人在“导出数据”页查看采集数、未录制数、已跳过数、预计 ZIP 大小。
2. 点击导出，系统文件选择器选择保存 ZIP 的位置或外部存储目录。
3. 导出完成后显示文件路径/分享入口及导出时间，绝不自动删除源数据。
4. 研究者在电脑运行 Python 校验、合并脚本，获得按发音人分类的 WAV 和总表。

## 4. 页面与交互规范

### 4.1 首页

- 显示：项目/句库名称、版本、哈希摘要、发音人 ID、方言点。
- 统计：总句数、已完成、未完成、已跳过、待复核；进度条。
- 操作：“继续录音”“全部句子”“导入句库”“导出数据”“设置”。
- 未导出或上次导出后有新录音时，突出显示“有尚未导出的录音”。

### 4.2 句库导入页

- 系统文件选择器打开 `.txt` / `.csv`，文件内容按 UTF-8 读取（可兼容 UTF-8 BOM）。
- TXT：忽略空行、保留原始顺序、生成 `S000001` 这样的 ID；**同一批 TXT 导入后不得在原句库内插入新句造成 ID 错位**。
- CSV：必需 `sentence_id,text`；可选 `category,notes`；解析规范 CSV 引号、逗号及换行。
- 导入前展示前若干条与错误报告，包含重复 ID、空文本、不支持格式。
- 同一 `corpus_id + corpus_version` 若内容哈希不同，应拒绝覆盖并明确提示；新内容须作为新版本/新句库导入。
- 同一内容重复导入不生成重复句库、不清空已有录音。
- 用规范规则计算 SHA-256：定义一个与设备无关的排序及 UTF-8 序列化方式；在 README 写明算法，Python 脚本复现。

### 4.3 录音页

- 主视觉突出句子，不出现过多按钮或遮挡文本；长句可完整滚动。
- 必须按钮：“开始录音 / 结束并保存”“试听”“重录”“上一句”“下一句”“跳过”。
- 显示句子 ID、句子进度、是否已有录音、录音计时、保存反馈。
- 停止后必须等待 WAV 校验及数据库提交成功，才显示“已保存”。
- 试听不可与麦克风采集并行；重录应保留旧版本，只有新版本成功保存后才将它设为当前选用版本。
- 音频时长上限采用保守的可配置参数（如 60 秒），避免误触导致长时间录制；超时提醒并安全结束。
- 返回/切换句子/应用离开前，如仍在录音，应明确处理停止保存或放弃，不能默默丢弃。
- 默认不自动跳下一句，避免发音人失去确认机会。

### 4.4 录音管理页

- 按句子顺序展示：ID、文本摘要、录音状态、时长、重录次数、问题标记。
- 过滤：全部、未录制、已完成、已跳过、待复核。
- 点击句子进入录音页；允许对已有录音试听、重新录制或恢复跳过项。

### 4.5 设置与导出页

- 发音人基本资料管理（修改 `speaker_id` 应经过确认，并避免破坏现存关联）。
- 当前句库详情与版本展示。
- 录音采样配置的**实际能力信息**（采集率、是否重采样、音源模式）；输出规格固定。
- 导出 ZIP；显示导出进度，支持取消；失败时不影响已有录音。
- 数据清理：区分“删除废弃重录文件”和“删除有效录音”，二者均需明确确认。

## 5. 数据模型与约束

使用 Room + SQLite。WAV 为磁盘上的独立文件，数据库只存路径和元数据。至少以下表（可根据工程需要正规化）：

### 5.1 `corpus`

- `corpus_id: String`：句库唯一标识，研究者提供或由 APP 首次导入生成。
- `corpus_version: String`：如 `1.0`。
- `corpus_hash: String`：规范内容 SHA-256。
- `title: String?`、`sentence_count: Int`、`created_at: ISO8601`。

### 5.2 `sentence`

- 复合键 `(corpus_id, corpus_version, sentence_id)`。
- `sentence_id: String`：不可依赖 Android 自增 ID 做跨设备关联。
- `order_index: Int`：呈现顺序。
- `prompt_text: String`：不可在既有句库版本内静默修改。
- 可选 `category: String?`、`notes: String?`。

### 5.3 `speaker`

- `speaker_id: String`：研究者事先分配；**不能假定离线条件下跨手机绝对唯一**。
- `local_speaker_uuid: UUID`：安装实例内部稳定 UUID，区分意外重名。
- `dialect: String`、`dialect_location: String?`。
- 可选 `age_group`、`gender`、`notes`；个人信息应最小化。
- `device_manufacturer`、`device_model` 可以存到录音会话的设备元数据。

### 5.4 `session`

- `session_id: UUID`、`speaker_id`、`corpus_id`、`corpus_version`。
- `started_at`、`ended_at?`、`device_model`、`app_version`。

### 5.5 `recording`

- `recording_id: UUID`：每次录音唯一。
- `session_id`、`sentence_id`、`speaker_id`、`corpus_id`、`corpus_version`。
- `take_no: Int`：同一发音人对同一句的录音序号。
- `wav_path: String`：APP 内部相对路径，不使用绝对路径作为导出数据依赖。
- `duration_ms: Long`、`sample_rate_hz: Int = 16000`、`bit_depth: Int = 16`、`channels: Int = 1`。
- `capture_sample_rate_hz: Int`：若发生重采样，记录原始采集率。
- `audio_source: String`、`audio_sha256: String?`。
- `created_at: ISO8601`、`status: enum`、`is_selected: Boolean`、`review_flag: Boolean`。
- `spoken_transcript: String? = null`、`transcription_status: not_annotated`：预留但不自动填值。

### 5.6 `sentence_progress`

- 复合键 `(speaker_id, corpus_id, corpus_version, sentence_id)`。
- `status`: `not_recorded | completed | skipped`。
- `selected_recording_id: UUID?`（completed 时必须指向有效录音）。
- `skip_reason: String?`、`review_flag: Boolean`、`updated_at`。
- `review_flag` 是附加维度，不取代录音完成状态。

必须建立外键和唯一性约束，避免孤儿数据。数据库迁移须保留旧录音。任何时候，未完成的 `.tmp` 文件不得被标记为已完成。

## 6. 音频采集与保存

### 6.1 音频格式

- **最终 WAV 规格必须固定**：PCM signed 16-bit little-endian、16000 Hz、mono、RIFF/WAVE。
- 数据率约 32 KB/s（不含文件头）。1000 句×5 秒≈160 MB/人。
- 录音 API 选用 Android `AudioRecord`，**不允许先录成 AAC/MP3 再转 WAV**。
- 优先请求设备可支持的 16 kHz/mono/PCM16；如果失败，可按设备能力改用 44.1/48 kHz PCM 再用正确的抗混叠重采样转换为 16 kHz；禁止只改 WAV 文件头伪装采样率。
- 探测 `UNPROCESSED` 等录音源是否可用，尽量避免系统自动增益、噪声抑制和回声消除；但不保证不同设备硬件无处理。记录请求的/实际的采集信息。
- 录音输出的时长必须以实际写入的 PCM 样本数为准，不凭按钮时间计算。

### 6.2 WAV 文件保存事务

1. 开始时为本次录音生成新的 `recording_id`，打开 `.pcm.tmp` 或 `.wav.tmp` 临时文件。
2. 采样时以流式方式将 PCM 写入文件，禁止整段录音只放在 RAM 中。
3. 停止后结束采集、确认所有写入完成，执行重采样（如有），生成合法 WAV 头。
4. 校验：文件长度、RIFF/WAVE 头、采样率/声道/位深、实际样本数、非空音频。必要时计算 SHA-256。
5. 使用同一文件系统上的安全重命名将临时文件转为最终 WAV，再提交数据库的 `completed` 状态和当前选用 take。
6. 启动时巡检未完成的临时文件/记录，提供安全恢复或清理；不将损坏文件虚报为已完成。
7. 重录不能先删除旧录音；新 take 成功后才修改选用指针。

### 6.3 需要明确处理的边界场景

- 用户拒绝/撤销麦克风权限。
- 录音设备初始化失败或被其他 APP 占用、来电/音频焦点变化。
- 设备锁屏、切后台、系统杀进程：首版以**前台采集**为主要场景；不承诺后台持续录音。离开采集页时尽量安全结束并明确告知。
- 录制中切换句库、修改发音人或关闭页面。
- 存储空间接近不足、写入失败、音频时间极短或空样本。
- 导出中应用被中断，禁止出现“成功”的错误提示；已有录音必须保留。

## 7. 导出协议（跨手机兼容的关键）

### 7.1 单人 ZIP 命名

建议：`<corpus_id>_<speaker_id>_<export_utc>.zip`。文件名只允许安全字符；显示文本继续使用 UTF-8。

### 7.2 ZIP 内固定目录

```text
YJ_SENTENCES_001_SP0001.zip
├── manifest.json
├── corpus.json
├── speaker.json
├── sentences.csv
├── recordings.csv
└── wav/
    ├── S000001.wav
    ├── S000002.wav
    └── ...
```

- `manifest.json`：`schema_version`、`export_id`、`exported_at_utc`、`app_version`、`corpus_id`、`corpus_version`、`corpus_hash`、`speaker_id`、文件数、已完成数、SHA-256 校验信息（可设计为独立 checksums 列表）。
- `corpus.json`：句库身份、版本及 hash。
- `speaker.json`：最小必要的发音人信息及本地安装实例 UUID；不要包含精确定位信息。
- `sentences.csv`：**全部句子**，至少 `sentence_id,prompt_text,order_index`。
- `recordings.csv`：至少 `recording_id,sentence_id,speaker_id,audio_path,duration_ms,status,review_flag,spoken_transcript,transcription_status`；**每条已选有效录音一行**。另外需要能表达跳过和未录制状态，推荐增加 `progress.csv`，覆盖全部句子。
- `wav/`：只导出已完成且已选中的有效 WAV；文件名为 `S000001.wav` 等；跨发音人通过 ZIP 和 speaker_id 区分。若发生同 ID 文件名特殊字符或冲突，使用受控安全映射，并以 `audio_path` 为准。
- 如果启用历史 take 导出，另立 `takes.csv` 和 `wav/history/`；默认只导出已选中的最终 WAV。

### 7.3 导出正确性

- ZIP 必须可被标准 Python `zipfile` 打开。
- UTF-8 CSV 采用标准 CSV 引号转义；支持文本中含逗号、引号及换行。
- 每个 `recordings.csv.audio_path` 均指向 ZIP 内存在、合法、可解码的 WAV；不存在重复目标路径。
- `sentence_id` 必须属于同一份 `sentences.csv`。
- 未录制/跳过的句子不能伪造 WAV。
- 导出阶段不覆盖用户已有同名 ZIP，建议名称带 UTC 时间或唯一导出 ID。
- 导出完成后保留全部本地源录音；设置“上次成功导出时间”和是否存在未导出更改。
- 为避免额外空间开销，优先使用系统 Storage Access Framework 将 ZIP 流式写入用户选择的目标 URI，且导出失败可告知用户删除不完整文件；绝不声称输出成功。
- APP 必须离线运行；不需要互联网权限。

## 8. PC 端 Python 工具

交付 `tools/validate_dataset.py` 和 `tools/merge_datasets.py`，使用 Python 3.10+，尽量仅依赖标准库。

### 8.1 `validate_dataset.py`

对单个 ZIP：检查 manifest、哈希、CSV、字段及关联、重复 `sentence_id`、音频路径、WAV 规格、实际帧数、时长误差、录音数量和状态一致性；打印统计与错误；以非 0 exit code 表示校验失败。

### 8.2 `merge_datasets.py`

输入多个 ZIP 或 ZIP 所在目录，输出：

```text
merged_dataset/
├── sentences.csv
├── speakers.csv
├── recordings.csv
├── progress.csv
└── wav/
    ├── SP0001/
    │   └── S000001.wav
    └── SP0002/
        └── S000001.wav
```

- 校验句库 `corpus_id + version + hash` 一致，否则拒绝合并或清楚分批，不得静默混合。
- 冲突 `speaker_id`：若重复导入相同 ZIP 可去重；如果同名但录音不同，必须报冲突并让研究者指定处理方式，不能直接覆盖。
- 所有 `audio_path` 在合并后的 CSV 中重新映射，必须指向真实文件。
- 不以 `prompt_text` 填充缺失的 `spoken_transcript`。
- 输出数据统计（发音人、录音数、总时长、缺失句子数、跳过数及冲突情况）。

## 9. 技术架构与工程约束

### 9.1 推荐技术栈

- 语言：Kotlin；界面：Jetpack Compose + Material 3。
- 架构：单 Activity、MVVM、Repository、Coroutines/Flow。
- 数据：Room (SQLite)，明确迁移策略。
- 录音：`AudioRecord`；播放：`AudioTrack` 或可靠的播放器封装。
- 后台线程：文件 IO / 解码校验 / 导出均不能阻塞 UI；长时导出采用受生命周期约束的可靠执行方案。
- 文件：APP 私有文件目录保存 WAV，系统文件选择器负责导入和导出。
- 权限：`RECORD_AUDIO`；不申请 `INTERNET`、位置或广泛存储权限。
- 版本：使用工程创建时稳定、可相互兼容的 Android Gradle Plugin、Kotlin、Compose、Room 版本，并在 README 中固定环境，确保能构建。
- Android 最低版本建议 API 26（Android 8.0）或按可用测试设备调整；目标 SDK 遵循编译时的当前 Android 规范。

### 9.2 目录参考

```text
DialectRecorder/
├── app/src/main/java/<package>/
│   ├── ui/{home,importdata,recording,recordings,settings}/
│   ├── data/{db,entity,repository}/
│   ├── audio/{AudioRecorder,AudioPlayer,WavWriter,AudioQualityChecker}.kt
│   ├── importexport/{SentenceImporter,DatasetExporter}.kt
│   └── MainActivity.kt
├── tools/{validate_dataset.py,merge_datasets.py}
├── README.md
└── docs/DATA_FORMAT.md
```

### 9.3 工程质量要求

- 不在主线程读取大文件、写长时间 WAV 或打包 ZIP。
- 核心数据模型、句库解析、文件命名、WAV 头、导出校验逻辑具备单元测试。
- 提供模拟/假录音数据用于自动化测试，但正式录音数据只能由真实麦克风生成。
- 日志中不输出不必要的敏感信息；不上传数据。
- UI 使用简体中文作为首版默认语言，按钮文案直观，允许较大字号。
- 设备横竖屏/字体缩放不能导致关键按钮不可用；录音期间防误触。

## 10. 隐私、同意与数据管理

- 初次使用说明：采集目的、内容、存储位置、离线属性、如何导出与删除。
- 发音人在知情情况下同意录制；如果需收集进一步身份/人口学信息，由研究者另行设计知情同意程序。
- 不在首版收集姓名、手机号、精确定位等非必要字段。
- 麦克风仅在明确点击开始后启动，录音中显示明显提示；停止后立即释放资源。
- 清除 APP 数据或卸载可能导致 APP 私有录音丢失，首页及帮助页面必须提醒定期导出 ZIP。
- 任何“删除”功能均明确告知范围，默认不删除已完成录音。

## 11. 状态、错误和验收标准

### 11.1 建议的录音状态机

`idle → recording → processing → completed`；失败进入 `failed`，可再次录制；`skipped` 是句子进度状态而不是一份 WAV 的状态。禁止在 `processing` 未完成前标记 `completed`。

### 11.2 必须通过的 P0 验收用例

1. **离线安装**：关闭 Wi-Fi/移动数据后能完成全部采集与导出。
2. **导入**：TXT 100 句和 CSV 100 句分别正常导入，导入顺序与 ID 一致；重复 ID、空文本被检测。
3. **跨机一致**：3 部设备导入同一 CSV，句库 hash 和 ID 完全一致。
4. **录音规格**：每个导出 WAV 经 `wave`/`ffprobe` 检测为 PCM16、16k、mono，时长误差在合理容差内。
5. **内容对应**：每个 WAV 的句子 ID 能回溯到准确 `prompt_text`；不得发生错位。
6. **保存确认**：点击停止后，只有 WAV 文件与数据库均成功写入才显示“已完成”。
7. **重录**：新录音失败不损坏上一条已成功保存的 take。
8. **断点续录**：录完 25 条后退出/重启 APP，进度及全部 25 条 WAV 保留；能继续下一句。
9. **跳过**：跳过原因保留在元数据中，跳过条目不导出虚假 WAV，可重新进入录制。
10. **导出 ZIP**：同一人录制 100 句，ZIP 内容完整，Python 校验脚本通过；不漏掉失败/跳过状态记录。
11. **多发音人合并**：3 名发音人分别导出 ZIP，可生成可对齐的总表及对应 WAV。
12. **冲突检测**：不同句库 hash、重复 speaker_id 且不同录音、丢失 WAV、损坏 ZIP 均正确报错，绝不静默覆盖。
13. **异常处理**：拒绝麦克风权限、存储空间不足、录制时中断、导出中止均明确提示且不破坏已有录音。
14. **标注语义**：所有 `spoken_transcript` 默认 `null`；`prompt_text` 不得复制到 `spoken_transcript`。
15. **数据安全**：未导出提醒可用；导出或重录不会自动删除旧录音。

### 11.3 实机测试

- 至少测试 3 个不同品牌/Android 版本的手机（研究者能获取的设备即可）。
- 对 16 kHz 原生采集不支持的设备，确认替代采样率 + 正确重采样流程真实生效。
- 验证锁屏、来电、权限被撤销、切换 APP、大号字体、长句、包含逗号和双引号的句子。

## 12. AI 编程 Agent 的执行要求

请按以下顺序开发，并保证每一步能编译、测试：

1. 创建可构建的 Kotlin/Compose Android 项目，搭建 Room schema、Repository 和导航。
2. 实现句库导入与内容校验，固定跨设备 ID、版本、hash 算法。
3. 完成录音引擎（AudioRecord → PCM → 16 kHz WAV）、播放、重录安全事务及失败恢复。
4. 完成录音页、首页、录音列表、发音人资料。
5. 实现 ZIP 导出协议、manifest 和 CSV，保证每条录音关联正确。
6. 开发并测试 Python 校验/合并工具，用自动化测试数据验证端到端过程。
7. 修复异常流程，补充 Android 实机测试说明、打包步骤及 README。

**交付清单**：Android Studio 项目源码、Gradle Wrapper、可安装 APK（如构建环境支持）、Python 工具、示例句库 CSV/TXT、示例导出包（只能用明确标示的测试/模拟音频）、README、数据格式说明、已执行测试与已知限制。

**最重要的不可妥协要求**：

- 不得为了省事将书面语提示文本伪装为方言逐字转写。
- 不得仅更改音频采样率头字段或把有损压缩音频简单改名为 WAV。
- 不得在重录、导出失败或应用异常时静默丢失已确认保存的语音。
- 不得改变既有句子的 ID 与内容映射，导致多人数据错位。
- 不得假装实机录音通过测试；没有真实 Android 设备时，应明确说明哪些部分仅做过单元/模拟测试。

## 13. 第一版成功定义

研究者将同一份 100 句的 CSV 导入 3 部不同 Android 手机，各自设定不同发音人 ID，在完全离线的情况下自然录制，进行试听、重录、跳过、退出/重启恢复，各自导出 ZIP。三份 ZIP 在电脑上通过校验脚本，并自动合并成一个文件名、发音人、句子 ID、书面语提示文本、WAV 一一对应且无数据丢失的语料集。所有“实际方言逐字转写”字段保持独立并允许为空。
