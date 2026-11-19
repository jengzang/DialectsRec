# 数据格式

## 内置句库

`app/src/main/assets/corpus_v1/sentences.csv` 是 UTF-8 CSV，列为：

`sentence_id,text,category,difficulty,length_bucket,notes`

`sentence_id` 从 `S000001` 开始，顺序是用户默认看到的顺序。`text` 是书面汉语语义提示，不是方言逐字转写。`difficulty` 使用 `short`、`situational`、`narrative`，内置库比例为 3500/1000/500。

句库哈希按 CSV 数据行（不含表头）计算：每行按字段顺序用 U+001F 连接，再追加 LF，全部以 UTF-8 编码后连续计算 SHA-256。字段顺序固定为 `sentence_id,text,category,difficulty,length_bucket,notes`。该规则与 `tools/generate_corpus.py` 和 Android 导入器一致。

## 导出 ZIP

ZIP 必须包含 `manifest.json`、`corpus.json`、`speaker.json`、`sentences.csv`、`recordings.csv`、`progress.csv` 和 `wav/`。CSV 使用 UTF-8 和标准引号转义。`spoken_transcript` 默认为空；不得从 `prompt_text` 自动复制。
