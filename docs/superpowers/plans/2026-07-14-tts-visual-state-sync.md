# TTS Visual State Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve visual geometry during cross-chapter visual-center playback selection and keep paused-follow highlighting synchronized with indicator visibility.

**Architecture:** Extend the existing pure `ReadAloudVisualPositioner` policy with two small decisions: visual-center chapter action and paused visibility-transition refresh. `ReadBookActivity` will resolve cached adjacent chapters, align read-aloud state in place, and create one immutable presentation snapshot per UI callback so highlight and indicator use the same position.

**Tech Stack:** Kotlin, Android View rendering, JUnit 4, Room-independent pure policy tests, Git format-patch, GitHub Actions.

## Global Constraints

- Work only on `personal/3.26.12-tts`; never change `main`.
- Do not run local JDK, SDK, NDK, Gradle, or `gradlew` commands.
- Verification and APK packaging use only `.github/workflows/ci.yml`.
- Do not change the 10% lower-edge repositioning rule or non-scroll animation behavior.
- Fold source and tests into personal patch `0003`; keep exactly five functional patches.
- Put the personal version increment in patch `0001`.

---

### Task 1: Add Pure Visual-State Policies

**Files:**
- Modify: `app/src/test/java/io/legado/app/ui/book/read/page/ReadAloudVisualPositionerTest.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ReadAloudVisualPositioner.kt`

**Interfaces:**
- Produces: `VisualCenterChapterAction { KeepCurrent, AlignCached, Reject }`
- Produces: `visualCenterChapterAction(currentChapterIndex: Int, targetChapterIndex: Int, targetChapterCached: Boolean): VisualCenterChapterAction`
- Produces: `shouldRefreshPausedHighlightOnVisibility(readAloudPlaying: Boolean, readAloudFollowPaused: Boolean, previousVisible: Boolean?, currentVisible: Boolean): Boolean`

- [x] **Step 1: Write failing policy tests**

Add these cases to `ReadAloudVisualPositionerTest`:

```kotlin
@Test
fun visualCenterSelectionKeepsCurrentChapterWhenTargetMatches() {
    assertEquals(
        ReadAloudVisualPositioner.VisualCenterChapterAction.KeepCurrent,
        ReadAloudVisualPositioner.visualCenterChapterAction(336, 336, true)
    )
}

@Test
fun visualCenterSelectionAlignsCachedAdjacentChapterInPlace() {
    assertEquals(
        ReadAloudVisualPositioner.VisualCenterChapterAction.AlignCached,
        ReadAloudVisualPositioner.visualCenterChapterAction(335, 336, true)
    )
}

@Test
fun visualCenterSelectionRejectsMissingAdjacentChapterCache() {
    assertEquals(
        ReadAloudVisualPositioner.VisualCenterChapterAction.Reject,
        ReadAloudVisualPositioner.visualCenterChapterAction(335, 336, false)
    )
}

@Test
fun pausedHighlightRefreshesWhenReadAloudPositionBecomesVisible() {
    assertTrue(
        ReadAloudVisualPositioner.shouldRefreshPausedHighlightOnVisibility(
            readAloudPlaying = true,
            readAloudFollowPaused = true,
            previousVisible = false,
            currentVisible = true
        )
    )
}

@Test
fun pausedHighlightDoesNotRefreshRepeatedVisibleEvaluation() {
    assertFalse(
        ReadAloudVisualPositioner.shouldRefreshPausedHighlightOnVisibility(
            readAloudPlaying = true,
            readAloudFollowPaused = true,
            previousVisible = true,
            currentVisible = true
        )
    )
}

@Test
fun activeFollowDoesNotUsePausedVisibilityRefresh() {
    assertFalse(
        ReadAloudVisualPositioner.shouldRefreshPausedHighlightOnVisibility(
            readAloudPlaying = true,
            readAloudFollowPaused = false,
            previousVisible = false,
            currentVisible = true
        )
    )
}

@Test
fun stoppedPlaybackDoesNotUsePausedVisibilityRefresh() {
    assertFalse(
        ReadAloudVisualPositioner.shouldRefreshPausedHighlightOnVisibility(
            readAloudPlaying = false,
            readAloudFollowPaused = true,
            previousVisible = false,
            currentVisible = true
        )
    )
}
```

- [x] **Step 2: Confirm the production symbols do not exist**

Run:

```powershell
rg -n "VisualCenterChapterAction|visualCenterChapterAction|shouldRefreshPausedHighlightOnVisibility" app/src/main
```

Expected: no matches. Local test execution is prohibited by project instructions, so the final GitHub Actions run is the executable RED/GREEN verification.

- [x] **Step 3: Implement the minimal pure policies**

Implement the policies without Android dependencies:

```kotlin
enum class VisualCenterChapterAction {
    KeepCurrent,
    AlignCached,
    Reject
}

fun visualCenterChapterAction(
    currentChapterIndex: Int,
    targetChapterIndex: Int,
    targetChapterCached: Boolean
): VisualCenterChapterAction {
    if (currentChapterIndex == targetChapterIndex) {
        return VisualCenterChapterAction.KeepCurrent
    }
    return if (targetChapterCached) {
        VisualCenterChapterAction.AlignCached
    } else {
        VisualCenterChapterAction.Reject
    }
}

fun shouldRefreshPausedHighlightOnVisibility(
    readAloudPlaying: Boolean,
    readAloudFollowPaused: Boolean,
    previousVisible: Boolean?,
    currentVisible: Boolean
): Boolean {
    return readAloudPlaying &&
            readAloudFollowPaused &&
            previousVisible == false &&
            currentVisible
}
```

- [x] **Step 4: Inspect tests and implementation together**

Run:

```powershell
git diff --check
rg -n "VisualCenterChapterAction|visualCenterChapterAction|shouldRefreshPausedHighlightOnVisibility" app/src/main app/src/test
```

Expected: policy definitions and all listed test cases are present; `git diff --check` reports no errors.

### Task 2: Align Visual-Center Playback In Place

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`

**Interfaces:**
- Consumes: `ReadAloudVisualPositioner.visualCenterChapterAction(...)`
- Produces: `cachedReadAloudTextChapter(chapterIndex: Int): TextChapter?`

- [x] **Step 1: Resolve the visible target chapter from existing caches**

Add this activity-local helper:

```kotlin
private fun cachedReadAloudTextChapter(chapterIndex: Int): TextChapter? {
    return sequenceOf(
        ReadBook.textChapter(-1),
        ReadBook.textChapter(0),
        ReadBook.textChapter(1)
    ).filterNotNull().firstOrNull {
        it.isCompleted && it.chapter.index == chapterIndex
    }
}
```

- [x] **Step 2: Replace cross-chapter `openChapter` with in-place alignment**

In `readAloudFromVisualCenter`, replace the `openChapter` branch with:

```kotlin
val currentChapterIndex = ReadBook.curTextChapter?.chapter?.index
    ?: ReadBook.durChapterIndex
val targetChapter = cachedReadAloudTextChapter(index)
when (ReadAloudVisualPositioner.visualCenterChapterAction(
    currentChapterIndex = currentChapterIndex,
    targetChapterIndex = index,
    targetChapterCached = targetChapter != null
)) {
    ReadAloudVisualPositioner.VisualCenterChapterAction.KeepCurrent -> {
        readAloudFromLineParagraphStart(line)
    }

    ReadAloudVisualPositioner.VisualCenterChapterAction.AlignCached -> {
        targetChapter ?: return true
        ReadBook.alignToReadAloudChapter(targetChapter, line.chapterPosition)
        ReadAloudVisualTrace.record(
            event = "manualStepVisualCenterChapter",
            detail = "action=alignCached chapter=$index lineChapterPos=${line.chapterPosition}"
        )
        readAloudFromLineParagraphStart(line)
    }

    ReadAloudVisualPositioner.VisualCenterChapterAction.Reject -> {
        ReadAloudVisualTrace.record(
            event = "manualStepVisualCenterFail",
            detail = "reason=missingCachedChapter chapter=$index lineChapterPos=${line.chapterPosition} visual=[${binding.readView.readAloudVisualDebugState()}]"
        )
    }
}
```

Return `true` after the `when` so `Reject` consumes the command without stepping from the stale audio position.

- [x] **Step 3: Inspect the chapter-switch path**

Run:

```powershell
rg -n "readAloudFromVisualCenter|manualStepVisualCenterChapter|alignToReadAloudChapter|openChapter" app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt
```

Expected: the visual-center method has no `openChapter` call; unrelated restore/open paths remain unchanged.

### Task 3: Synchronize Paused Highlight and Indicator

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`

**Interfaces:**
- Consumes: `ReadAloudVisualPositioner.shouldRefreshPausedHighlightOnVisibility(...)`
- Produces: activity-local `ReadAloudPresentationSnapshot`
- Produces: `readAloudPresentationSnapshot(chapterIndex: Int, chapterStart: Int, preferredTextChapter: TextChapter? = null): ReadAloudPresentationSnapshot`

- [x] **Step 1: Add immutable snapshot and transient visibility state**

Add the exact transient state:

```kotlin
private data class ReadAloudPresentationSnapshot(
    val chapterIndex: Int,
    val chapterStart: Int,
    val textChapter: TextChapter?,
    val paragraph: TextParagraph?,
    val paragraphVisible: Boolean
)

private var lastReadAloudPresentationVisible: Boolean? = null
```

Build it with:

```kotlin
private fun readAloudPresentationSnapshot(
    chapterIndex: Int,
    chapterStart: Int,
    preferredTextChapter: TextChapter? = null
): ReadAloudPresentationSnapshot {
    val textChapter = preferredTextChapter?.takeIf {
        it.isCompleted && it.chapter.index == chapterIndex
    } ?: cachedReadAloudTextChapter(chapterIndex)
    val paragraph = textChapter?.let { findReadAloudParagraph(it, chapterStart) }
    val paragraphVisible = textChapter != null && paragraph?.let {
        readAloudParagraphVisibleOnScreen(textChapter, it)
    } == true
    return ReadAloudPresentationSnapshot(
        chapterIndex = chapterIndex,
        chapterStart = chapterStart,
        textChapter = textChapter,
        paragraph = paragraph,
        paragraphVisible = paragraphVisible
    )
}
```

- [x] **Step 2: Centralize paused presentation synchronization**

Extend `updateReadAloudVisualCenterIndicator` with these parameters:

```kotlin
private fun updateReadAloudVisualCenterIndicator(
    forceTrace: Boolean = true,
    snapshot: ReadAloudPresentationSnapshot = readAloudPresentationSnapshot(
        chapterIndex = BaseReadAloudService.readAloudChapterIndex,
        chapterStart = BaseReadAloudService.readAloudChapterStart
    ),
    refreshVisibleHighlight: Boolean = false
)
```

Inside it, calculate `transitionRefresh` with `shouldRefreshPausedHighlightOnVisibility`. When `snapshot.paragraphVisible` and either `refreshVisibleHighlight` or `transitionRefresh` is true, call `updateReadAloudParagraphSpan(snapshot.paragraph)` and invalidate the current content view. Record `pausedHighlightVisibleTransition` only when `transitionRefresh && !refreshVisibleHighlight`. Then update `lastReadAloudPresentationVisible` and derive the indicator from `snapshot.paragraphVisible`.

- [x] **Step 3: Route paused TTS progress through the snapshot**

In the paused branch of `updateReadAloudVisual`, use:

```kotlin
val snapshot = readAloudPresentationSnapshot(
    chapterIndex = textChapter.chapter.index,
    chapterStart = chapterStart,
    preferredTextChapter = textChapter
)
ReadAloudVisualTrace.record(
    event = "pausedHighlight",
    detail = "paragraphVisible=${snapshot.paragraphVisible} updated=${snapshot.paragraphVisible} targetPage=$pageIndex visual=[${binding.readView.readAloudVisualDebugState()}]"
)
updateReadAloudVisualCenterIndicator(
    snapshot = snapshot,
    refreshVisibleHighlight = true
)
return
```

- [x] **Step 4: Inspect state reads and formatting**

Run:

```powershell
git diff --check
rg -n "ReadAloudPresentationSnapshot|lastReadAloudPresentationVisible|pausedHighlightVisibleTransition|refreshVisibleHighlight" app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt
```

Expected: paused progress and indicator synchronization share one snapshot, and visibility-transition refresh is not performed on every scroll frame.

### Task 4: Version, Patch Stack, and CI

**Files:**
- Modify: `app/version.properties`
- Modify: `patches/personal/3.26.12/0001-Set-up-personal-APK-build-workflow.patch`
- Modify: `patches/personal/3.26.12/0003-Refine-TTS-visual-follow-and-highlighting.patch`
- Regenerate: all five files under `patches/personal/3.26.12/`

**Interfaces:**
- Produces: version `3.26.12.7-noR8`
- Produces: exactly five patches applicable with `git am -3` from `upstream/3.26.12`

- [x] **Step 1: Increment the personal version**

Change `VERSION_PERSONAL=6` to `VERSION_PERSONAL=7`.

- [ ] **Step 2: Review and commit the source change**

Run non-Android checks:

```powershell
git diff --check
git status --short
git diff --stat
```

Commit the implementation as one TTS visual-state fix commit.

- [ ] **Step 3: Re-export the five functional patches**

Rebuild the patch stack from `upstream/3.26.12`, folding version/workflow content into `0001` and all visual-state source/tests into `0003`. Do not add `0006`.

- [ ] **Step 4: Validate patch application without Android tools**

Apply the final five patches with `git am -3` in a temporary detached worktree rooted at `upstream/3.26.12`. Compare affected source, test, version, and workflow files with the personal branch and run `git diff --check` on source files.

- [ ] **Step 5: Push and monitor the single workflow**

Push `personal/3.26.12-tts`. Confirm the triggered `.github/workflows/ci.yml` run and monitor it at 15-minute intervals. On success, report the versioned unsigned arm64 artifact name and ID.
