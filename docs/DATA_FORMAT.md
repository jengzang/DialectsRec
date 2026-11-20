# 数据格式

## 内置句库

所有句库使用 UTF-8 CSV，列为：

`sentence_id,text,category,difficulty,length_bucket,notes`

AI 通用补充（`corpus_v1`）的 `sentence_id` 从 `S000001` 开始，顺序是浏览进度时的固定顺序。`text` 是书面汉语语义提示，不是方言逐字转写。`difficulty` 使用 `short`、`situational`、`narrative`，AI 库比例为 3500/1000/500。

暨大句库使用 `corpus_jnu_chaozhou`、`corpus_jnu_suzhou`、`corpus_jnu_local` 三个资产目录，corpus_id 分别为 `jnu_chaozhou`、`jnu_suzhou`、`jnu_local`，初始版本均为 `1.0`。新安装默认潮州；升级会保留当前选择、已有录音和进度。地方特色库的 category 为“潮州”或“苏州”，其余暨大库使用原编号前四位分组，未将推测类别冒充原始正式分类。

暨大内部 sentence_id 为 `CZ_原八位编号` 或 `SZ_原八位编号`，避免两份原稿重号。每条只会出现在对应通用库或地方特色库之一；同文不同编号仍保留。notes 为 JSON，含 source、source_id、source_line、locality（G/L）、reason、quality_flags。text 保留原稿，App 录音页仅在显示时隐藏 & 标记；括号释义、指令和疑似异常用 quality_flags 提醒，不静默纠正或删除。全量审查与源文件 SHA256 位于 `docs/corpus-review/jnu-v1/audit.json`。

句库哈希按 CSV 数据行（不含表头）计算：每行按字段顺序用 U+001F 连接，再追加 LF，全部以 UTF-8 编码后连续计算 SHA-256。字段顺序固定为 `sentence_id,text,category,difficulty,length_bucket,notes`。该规则与 `tools/generate_corpus.py` 和 Android 导入器一致。

## 导出 ZIP

ZIP 必须包含 `manifest.json`、`corpus.json`、`speaker.json`、`sentences.csv`、`recordings.csv`、`progress.csv` 和 `wav/`。CSV 使用 UTF-8 和标准引号转义。`spoken_transcript` 默认为空；不得从 `prompt_text` 自动复制。

导出开始时固定发音人、corpus_id、corpus_version，所有数据查询使用同一上下文，切库不会把不同句库混入一个 ZIP。sentences.csv 保留原始 notes（包括来源和原编号），与内置 manifest 使用同样的哈希算法。
