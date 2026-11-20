# 暨大地方特色 Implementation Plan

> **For agentic workers:** Use executing-plans inline; use requesting-code-review for final independent review.

**Goal:** API 全量分流暨大 7,728 条文本，并将四套句库接入安卓 App。

**Architecture:** 可续跑的 Python Responses/SSE 审查器生成逐条审计与确定性 CSV/manifest；安卓 catalog 加载多库，任务按采集上下文快照保存。

**Tech Stack:** Python stdlib、Java/SQLite、Android SDK/Gradle。

- [x] 1. 先写审查脚本测试：遗漏/重复/越界 ID 必须失败；保留重复文本和源 ID；输入 hash 或 prompt 变化拒绝旧 checkpoint。运行失败后实现 tools/review_jnu_corpus.py。提示词保存在 docs/corpus-review/locality-prompt.txt。五并发、stream、store=false，逐批持久化，二次复核 uncertain；不以规则兜底。
- [x] 2. 执行全量 API 审查。校验 7,728 个判定与原始内容无丢失，输出审计 JSON、便读 CSV、summary、三套暨大资产，检查 hash 与各 part 计数。
- [x] 3. 扩展安卓隔离数据库测试，先验证四库与切换行为尚未实现。新增 CorpusCatalog、扩展 CorpusDatabase.seed/selectCorpus/currentTitle，已有 AI 库不替换，切库原子归档/恢复 task_*。导入保留旧任务。
- [x] 4. MainActivity 提供句库菜单、动态数量范围、来源与地方特色提示；NavigationPolicy 返回首页；build.gradle 增至 1.6/code7。
- [x] 5. 跑 Python/Java/数据库 instrumentation/lint，模拟器验证默认、四库/part、随机录音、切回恢复、全量状态与导出 hash；修复实际失败。独立代码复核，明确文件提交、合 main 推送，交付 APK 与审计数字。

版权补充：用户要求暨大具体数据不得推送远端。原稿、衍生资产、完整审计与 APK 均本地保留并加入忽略；公开 main 从此前 origin/main 重建，只提交实现、提示词和不含原文的测试/文档。已删除曾上传数据的功能分支，服务器不可达对象/缓存的物理清理需由 GitHub 处理。
