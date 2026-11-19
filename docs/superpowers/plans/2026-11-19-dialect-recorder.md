# Dialect Recorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a buildable offline Android app with a fixed 5,000-sentence corpus, reliable WAV capture/export, and Python validation/merge tools.

**Architecture:** Kotlin Android app using Compose, Room, AudioRecord and SAF; assets contain the versioned corpus and are imported transactionally. Python standard-library tools define and validate the cross-device ZIP protocol. Core parsing, hashing, WAV and export rules are shared by explicit data-format documentation and covered by JVM/Python tests.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose Material 3, Room, Coroutines, JUnit, Python 3.10 standard library, Android SDK 36.

---

### Task 1: Bootstrap Android project and test harness

**Files:** settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/wrapper/*, app/build.gradle.kts, app/src/main/AndroidManifest.xml, app/src/main/java/com/dialectrec/MainActivity.kt, app/src/test/java/com/dialectrec/SmokeTest.kt, .gitignore, README.md

- [ ] Pin a compatible Gradle/AGP pair, compileSdk 36 and minSdk 26.
- [ ] Add Compose, Room, lifecycle and coroutines dependencies.
- [ ] Add a failing JUnit smoke test, run it, then add the minimal configuration/source until it passes.
- [ ] Commit the buildable skeleton.

### Task 2: Generate and verify the bundled 5,000-sentence corpus

**Files:** tools/generate_corpus.py, tools/test_generate_corpus.py, app/src/main/assets/corpus_v1/sentences.csv, app/src/main/assets/corpus_v1/manifest.json, docs/DATA_FORMAT.md

- [ ] Write tests first for stable IDs, exactly 5,000 rows, 70/20/10 difficulty distribution, UTF-8 CSV quoting, unique text and deterministic SHA-256.
- [ ] Implement deterministic template-based generation with 14 themes, neutral written prompts, no personal identifiers, and explicit difficulty/length metadata.
- [ ] Generate the checked-in corpus and manifest; verify hash and counts.
- [ ] Commit the corpus and generator.

### Task 3: Implement Python dataset validation and merge tools

**Files:** tools/validate_dataset.py, tools/merge_datasets.py, tools/test_dataset_tools.py, tools/testdata/*

- [ ] Write failing tests for valid ZIPs, malformed WAVs, missing paths, corpus hash mismatches, duplicate speaker conflicts and idempotent duplicate ZIP imports.
- [ ] Implement standard-library-only ZIP/JSON/CSV/WAV/hash checks with non-zero failures and statistics.
- [ ] Implement merge output with speaker-specific WAV directories and explicit conflict reporting.
- [ ] Generate simulated PCM fixtures and run both tools.
- [ ] Commit Python tools and fixtures.

### Task 4: Implement Android data layer and import/task selection

**Files:** app/src/main/java/com/dialectrec/data/*, app/src/test/java/com/dialectrec/data/*, app/build.gradle.kts

- [ ] Write tests for corpus hash canonicalization, Room progress transitions, duplicate import rejection and category/range task filters.
- [ ] Add Room entities/DAOs for corpus, sentence, speaker, session, recording, sentence progress and app settings.
- [ ] Add asset seeding and SAF TXT/CSV import with validation, stable IDs, version/hash checks and transactional inserts.
- [ ] Add repositories exposing Flow-based home/list/task state.
- [ ] Commit data layer.

### Task 5: Implement WAV recording, playback and transactional take handling

**Files:** app/src/main/java/com/dialectrec/audio/*, app/src/main/java/com/dialectrec/data/RecordingRepository.kt, app/src/test/java/com/dialectrec/audio/*, app/src/main/AndroidManifest.xml

- [ ] Write tests for RIFF/WAVE headers, PCM16 frame counts, atomic temp-file promotion, failed re-record preservation and null spoken transcript.
- [ ] Implement WavWriter, WavInspector, streaming AudioRecord capture with preferred 16 kHz and correct fallback resampling, and AudioTrack playback.
- [ ] Implement recording state machine, permission/error handling, max duration and startup temp-file recovery.
- [ ] Commit audio layer.

### Task 6: Implement Compose UI and navigation

**Files:** app/src/main/java/com/dialectrec/ui/*, app/src/main/java/com/dialectrec/MainActivity.kt, app/src/androidTest/java/com/dialectrec/*

- [ ] Write UI tests for onboarding, home counts, task filters, recording controls, skip reason and export entry.
- [ ] Implement onboarding, home, task selection, recording, recordings list, settings and export screens in simplified Chinese.
- [ ] Wire ViewModels to repository Flows; disable navigation while recording; show permission, processing and export errors.
- [ ] Commit UI.

### Task 7: Implement ZIP export and Android integration tests

**Files:** app/src/main/java/com/dialectrec/importexport/*, app/src/test/java/com/dialectrec/importexport/*, app/src/main/java/com/dialectrec/ui/settings/*

- [ ] Write tests for manifest fields, CSV escaping, selected WAV-only export, skipped progress rows and no source deletion.
- [ ] Implement streaming SAF ZIP exporter with manifest/corpus/speaker/sentences/recordings/progress and checksums.
- [ ] Run JVM integration tests against simulated recordings and validate the ZIP using Python tools.
- [ ] Commit export integration.

### Task 8: Build, emulator smoke test, full verification and review

**Files:** README.md, docs/TEST_REPORT.md, artifacts/*.apk if copied for delivery

- [ ] Run ./gradlew test, ./gradlew lint and ./gradlew assembleDebug with fresh output.
- [ ] Start an installed Android emulator using the local SDK, install APK, run offline onboarding/import/task-selection smoke checks, and record microphone limitations.
- [ ] Run Python end-to-end tests and validate a generated multi-speaker merge.
- [ ] Record exact commands, counts, build path and limitations in docs/TEST_REPORT.md and README.
- [ ] Commit final verification docs and request code review.
