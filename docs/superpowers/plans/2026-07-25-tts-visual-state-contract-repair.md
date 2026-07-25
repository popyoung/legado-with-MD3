# TTS Visual State Contract Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair source identity, visibility, inherited-offset, and scroll-trace failures demonstrated by the July read-aloud logs.

**Architecture:** Keep visual policy in `ReadAloudVisualPositioner`, rendered-page discovery in `ContentTextView`, and playback orchestration in `ReadBookActivity`. Preserve the five functional personal patches and avoid new caches or compatibility paths.

**Tech Stack:** Kotlin, Android View rendering, JUnit 4, Git format-patch, GitHub Actions.

## Global Constraints

- Keep the safe-edge ratio at 10 percent.
- Do not change non-scroll page animation behavior.
- Do not run local JDK, SDK, NDK, Gradle, or `gradlew`.
- Use only `.github/workflows/ci.yml` for verification and packaging.
- Produce only `arm64-v8a` noR8 unsigned APK output.
- Keep exactly five functional patches.

---

### Task 1: Policy Regression Coverage

**Files:**
- Modify: `app/src/test/java/io/legado/app/ui/book/read/page/ReadAloudVisualPositionerTest.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ReadAloudVisualPositioner.kt`

**Interfaces:**
- Produces: `resolveFollowBaseOffset(currentOffset, initialEffectiveOffset, targetIsCurrentPage): Float`
- Produces: `shouldHandleScrollFrame(mOffset): Boolean`
- Produces: `shouldTraceScrollState(followInterrupted, pageChanged): Boolean`

- [x] **Step 1: Add failing policy tests**

Cover explicit chapter-boundary offsets, inherited positive offsets on the
current page, positive offsets for previous-page targets, zero scroll frames,
and semantic scroll trace transitions.

- [x] **Step 2: Confirm the tests reference missing policy methods**

Use source inspection only. The user requires a single final CI run, so the
otherwise mandatory RED Gradle execution is intentionally deferred.

- [x] **Step 3: Implement the minimal policy methods**

The offset resolver must prefer an explicit offset, normalize only inherited
positive offsets for current-page targets, and preserve all other offsets.

- [x] **Step 4: Inspect policy call sites**

Use `rg` to confirm the new methods are called only from the intended follow
and scroll paths.

### Task 2: Source-preserving Selection and Unified Visibility

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/PageView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`

**Interfaces:**
- `getReadAloudPos(): Pair<TextPage, TextLine>?`
- `getReadAloudCenterPos(): Pair<TextPage, TextLine>?`

- [x] **Step 1: Preserve the source page in visible-line return values**

Return the actual rendered `TextPage` as the first pair element while retaining
the screen-adjusted copied line as the second element.

- [x] **Step 2: Consume the source page in playback entry points**

Use `page.chapterIndex` and `page.getTextChapter()` for normal playback start
and visual-center chapter alignment.

- [x] **Step 3: Unify playback visibility**

Implement `readAloudPositionVisibleOnScreen()` through
`readAloudPresentationSnapshot(...).paragraphVisible`; remove the exact
single-character visibility decision from activity control flow.

### Task 3: Follow Offset and Scroll Hot Path

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`

**Interfaces:**
- Consumes: policy methods from Task 1.

- [x] **Step 1: Resolve the follow base offset before positioning**

Compare the target paragraph's first page with the current rendered page and
normalize only stale inherited positive offsets.

- [x] **Step 2: Filter and summarize scroll events**

Return immediately for `mOffset == 0`. Record full state only when follow is
interrupted or chapter/page identity changes.

- [x] **Step 3: Debounce viewport presentation refresh**

Pause visual follow once, coalesce repeated callbacks, update the indicator and
paused highlight after the viewport settles, and record one final snapshot.

### Task 4: Version, Patch Stack, and CI

**Files:**
- Modify: `app/version.properties`
- Regenerate: `patches/personal/3.26.12/0001-*.patch`
- Regenerate: `patches/personal/3.26.12/0003-*.patch`
- Regenerate: `patches/personal/3.26.12/0004-*.patch`
- Regenerate: `patches/personal/3.26.12/0005-*.patch`

- [x] **Step 1: Set `VERSION_PERSONAL=8`**

- [ ] **Step 2: Run non-Android verification**

Run `git diff --check`, focused static searches, patch count checks, and inspect
the complete diff.

- [ ] **Step 3: Rebuild the five functional patches**

Fold each changed file into its owning functional patch; do not add `0006`.

- [ ] **Step 4: Validate patch application**

Apply the five patches with `git am -3` to a temporary worktree based on
`upstream/3.26.12`, compare affected files, and run `git diff --check`.

- [ ] **Step 5: Push once and monitor CI**

Push `personal/3.26.12-tts`. The push triggers the sole workflow; inspect it at
15-minute intervals and report its artifact name and ID.
