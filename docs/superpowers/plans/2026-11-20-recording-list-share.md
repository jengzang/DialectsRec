# 录音体验、句子浏览与导出分享 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复录音/试听体验，展示完整可筛选句库，并支持导出后系统分享。

**Architecture:** 用纯 Java 状态与计时辅助类承载可测试逻辑，Android Activity 负责 UI 状态绑定；播放器异步回调，录音保存在线程中完成；列表采用 ScrollView + 分类 Spinner，分享使用系统 ACTION_SEND 和 FileProvider URI。

**Tech Stack:** Android Java、MediaPlayer、AudioFocusRequest、Handler、JUnit 4、Gradle、ADB 模拟器。

---

### Task 1: 建立可测试的状态和 manifest 解析

**Files:**
- Create: `app/src/main/java/com/dialectrec/RecordingUiState.java`
- Create: `app/src/main/java/com/dialectrec/RecordingTimer.java`
- Create: `app/src/test/java/com/dialectrec/RecordingTimerTest.java`
- Modify: `app/src/main/java/com/dialectrec/data/CorpusDatabase.java`
- Modify: `app/src/test/java/com/dialectrec/data/CorpusManifestTest.java`

- [ ] **Step 1: Write failing tests** for `RecordingTimer.format(0) == "00:00"`, `format(12_345) == "00:12"`, and manifest JSON version extraction returning `2.0`.
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*RecordingTimerTest' --tests '*CorpusManifestTest'` and confirm the new APIs fail before implementation.
- [ ] **Step 3: Implement the two small helpers and a manifest parser in `CorpusDatabase`.
- [ ] **Step 4: Run the focused tests and confirm they pass.

### Task 2: Make playback observable and reliable

**Files:**
- Modify: `app/src/main/java/com/dialectrec/audio/AudioPlayer.java`
- Modify: `app/src/main/java/com/dialectrec/MainActivity.java`
- Create: `app/src/test/java/com/dialectrec/RecordingUiStateTest.java`

- [ ] **Step 1: Add failing state tests** for playback transitions `idle -> preparing -> playing -> completed` and error transition.
- [ ] **Step 2: Run the focused test and verify failure.
- [ ] **Step 3: Implement asynchronous MediaPlayer callbacks, media audio attributes, audio focus, explicit volume, and page status text/icon updates.
- [ ] **Step 4: Stop playback when changing sentence, leaving recording screen, or destroying the Activity.
- [ ] **Step 5: Run focused and existing unit tests.

### Task 3: Make recording state obvious and saving non-blocking

**Files:**
- Modify: `app/src/main/java/com/dialectrec/audio/RecorderEngine.java`
- Modify: `app/src/main/java/com/dialectrec/MainActivity.java`
- Modify: `app/src/main/res/drawable/ic_logo.xml` only if shared tint is needed

- [ ] **Step 1: Add failing timer/state assertions for recording start, elapsed updates, stop-save, and save failure.
- [ ] **Step 2: Run tests and verify failure.
- [ ] **Step 3: Add elapsed time access to `RecorderEngine`, a 250ms Handler refresh, an orange/red status card, and generic microphone/stop/play/timer icons.
- [ ] **Step 4: Move stop, WAV inspection, hashing, and database write to a worker thread; update UI only on the main thread and restore controls on failure.
- [ ] **Step 5: Run unit tests and Gradle build.

### Task 4: Full list, category filter, and share action

**Files:**
- Modify: `app/src/main/java/com/dialectrec/MainActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/res/values/strings.xml` if labels need resources

- [ ] **Step 1: Add a failing list-filter test for all rows and category filtering.
- [ ] **Step 2: Run the focused test and verify failure.
- [ ] **Step 3: Replace the 200-row cap with a scrollable full list and category Spinner; keep row rendering bounded to the selected category but never truncate.
- [ ] **Step 4: Add FileProvider and a share intent after successful ZIP export; use a content URI and `Intent.createChooser`.
- [ ] **Step 5: Run tests and lint.

### Task 5: Package and verify

**Files:**
- Modify: `docs/TEST_REPORT.md`

- [ ] **Step 1: Run Python corpus tests and all Android unit tests.
- [ ] **Step 2: Run `./gradlew testDebugUnitTest lint assembleDebug --no-daemon --console=plain`.
- [ ] **Step 3: Install the APK on `dialects_api35`, clear app data, and verify onboarding, 5,000 count/version 2.0, category filter, record timer, asynchronous save, playback state, full list, back navigation, and share chooser.
- [ ] **Step 4: Update the test report with APK version 1.2, corpus hash, and device evidence.
- [ ] **Step 5: Run `git diff --check`, commit, and push `main`.
