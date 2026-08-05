# Read Aloud Visual Coordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the competing read-aloud visual flags with one deterministic coordinator while preserving the exact playback cursor across scrolling, chapters, background return, pause/resume, and manual navigation.

**Architecture:** `BaseReadAloudService` remains the playback owner and exposes immutable cursor snapshots. A pure `ReadAloudVisualCoordinator` owns `Following`, `Detached`, and tokenized `Restoring` transitions. `ReadBookActivity` adapts lifecycle and event-bus callbacks to coordinator events, while `ContentTextView` remains the viewport owner and reports user-origin scroll gestures separately from programmatic render changes.

**Tech Stack:** Kotlin, Android View rendering, LiveEventBus, JUnit 4, GitHub Actions.

## Global Constraints

- Work only on `personal/3.26.12-tts`; `main` remains pure upstream.
- Do not run local JDK, SDK, NDK, Gradle, or `gradlew` commands.
- Use only `.github/workflows/ci.yml` for tests, compilation, and APK packaging.
- Build only `arm64-v8a-noR8-unsigned` and version it as `3.26.12.10-noR8`.
- Keep exactly five functional patches under `patches/personal/3.26.12`.
- Reposition only when the paragraph bottom enters the lower 10-percent region; otherwise keep text geometry unchanged.
- User scrolling never pauses audio and never mutates the playback cursor.
- System back restores an absent playback position while audio continues; it pauses only when playback is already visible.
- Visual-position manual previous/next uses playback when visible and visual center only when playback is absent.
- Do not extend or rewrite non-scroll page animation behavior.

---

## File Map

- Create `app/src/main/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinator.kt`: pure visual state machine and decisions.
- Create `app/src/main/java/io/legado/app/service/ReadAloudPlaybackCursor.kt`: immutable service-owned playback snapshot shared with the coordinator.
- Create `app/src/test/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinatorTest.kt`: event-sequence contract tests.
- Modify `app/src/main/java/io/legado/app/service/ReadAloudProgress.kt`: immutable playback cursor and consecutive publication policy.
- Modify `app/src/test/java/io/legado/app/service/ReadAloudProgressTest.kt`: cursor and deduplication tests.
- Modify `app/src/main/java/io/legado/app/service/BaseReadAloudService.kt`: single playback cursor publication path.
- Modify `app/src/main/java/io/legado/app/service/TTSReadAloudService.kt`: route automatic callbacks through the publication path without duplicate manual progress.
- Modify `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`: coordinator adapter, tokenized restore, cursor-based chapter resolution, back/pause/background/manual events.
- Modify `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`: viewport snapshot and one user-scroll start/settle gesture contract.
- Modify `app/src/main/java/io/legado/app/ui/book/read/page/PageView.kt` and `app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt`: forward viewport and scroll-origin adapters only if required by existing wrapper boundaries.
- Modify `app/src/main/java/io/legado/app/ui/book/read/page/ReadAloudVisualTrace.kt`: coordinator event/decision/cursor/token diagnostics.
- Modify `app/version.properties`: increment personal version to 10.
- Modify `.github/workflows/ci.yml`: include the new coordinator test in the existing single test command.
- Refresh the existing five files in `patches/personal/3.26.12` by functional ownership.

---

### Task 1: Pure Coordinator Contract

**Files:**
- Create: `app/src/main/java/io/legado/app/service/ReadAloudPlaybackCursor.kt`
- Create: `app/src/main/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinator.kt`
- Create: `app/src/test/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinatorTest.kt`

**Interfaces:**
- Produces: `ReadAloudPlaybackCursor`, `ReadAloudViewport`, `ReadAloudPresentation`, `ReadAloudVisualCoordinator.State`, `Event`, `Effect`, and `Transition`.
- Consumes: no Android types, service globals, renderer classes, or `ReadBook` state.

- [ ] **Step 1: Write event-sequence tests before production code**

Create tests that express complete transitions, including these concrete assertions:

```kotlin
@Test
fun userScrollDetachesWithoutChangingPlaybackCursor() {
    val coordinator = ReadAloudVisualCoordinator()
    val cursor = ReadAloudPlaybackCursor(226, 39, 7)

    val transition = coordinator.reduce(
        ReadAloudVisualCoordinator.Event.UserScrollStarted(cursor)
    )

    assertEquals(ReadAloudVisualCoordinator.FollowState.Detached, coordinator.state.follow)
    assertEquals(cursor, coordinator.state.playbackCursor)
    assertEquals(listOf(ReadAloudVisualCoordinator.Effect.DetachViewport), transition.effects)
}

@Test
fun programmaticRenderCannotCancelItsRestore() {
    val coordinator = ReadAloudVisualCoordinator()
    val cursor = ReadAloudPlaybackCursor(214, 1663, 8)
    val start = coordinator.reduce(
        ReadAloudVisualCoordinator.Event.RestoreRequested(
            cursor,
            ReadAloudVisualCoordinator.RestoreReason.BackNavigation
        )
    ).effects.single() as ReadAloudVisualCoordinator.Effect.StartRestore

    coordinator.reduce(
        ReadAloudVisualCoordinator.Event.RenderChanged(
            origin = ReadAloudVisualCoordinator.RenderOrigin.Restore(start.token),
            viewport = ReadAloudViewport(214, 4, 1600)
        )
    )

    assertEquals(start.token, coordinator.state.restore?.token)
}

@Test
fun backNavigationRestoresWhenPlaybackIsHiddenAndPausesWhenVisible() {
    val cursor = ReadAloudPlaybackCursor(214, 1663, 8)
    val viewport = ReadAloudViewport(213, 9, 3528)
    val hidden = ReadAloudVisualCoordinator()
    val hiddenEffects = hidden.reduce(
        ReadAloudVisualCoordinator.Event.BackNavigationRequested(
            ReadAloudPresentation(cursor, viewport, paragraphVisible = false)
        )
    ).effects
    assertTrue(hiddenEffects.single() is ReadAloudVisualCoordinator.Effect.StartRestore)

    val visible = ReadAloudVisualCoordinator()
    val visibleEffects = visible.reduce(
        ReadAloudVisualCoordinator.Event.BackNavigationRequested(
            ReadAloudPresentation(cursor, viewport, paragraphVisible = true)
        )
    ).effects
    assertEquals(listOf(ReadAloudVisualCoordinator.Effect.PausePlayback), visibleEffects)
}
```

Also cover stale tokens, user cancellation, newer progress during restore, detached visible highlight refresh, detached hidden indicator, foreground return, pause/scroll/resume, manual source selection, and repeated settled viewport idempotency.

- [ ] **Step 2: Verify the tests are structurally failing**

Run only static checks locally:

```powershell
rg -n "class ReadAloudVisualCoordinator|sealed interface Event|sealed interface Effect" app/src/main app/src/test
git diff --check
```

Expected before implementation: tests reference types that do not yet exist. Do not run Gradle locally.

- [ ] **Step 3: Implement the minimal pure coordinator**

Use compact immutable types and one reducer:

```kotlin
internal data class ReadAloudPlaybackCursor(
    val chapterIndex: Int,
    val chapterStart: Int,
    val sequence: Long
)

internal data class ReadAloudViewport(
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterPosition: Int
)

internal data class ReadAloudPresentation(
    val cursor: ReadAloudPlaybackCursor,
    val viewport: ReadAloudViewport,
    val paragraphVisible: Boolean
)

internal class ReadAloudVisualCoordinator {
    enum class FollowState { Following, Detached, Restoring }
    enum class RestoreReason { Progress, BackNavigation, ResumePlayback, ForegroundReturn }
    enum class ManualStepSource { PlaybackCursor, VisualCenter }

    data class Restore(val token: Long, val target: ReadAloudPlaybackCursor, val reason: RestoreReason)
    sealed interface RenderOrigin {
        data object User : RenderOrigin
        data object Programmatic : RenderOrigin
        data class Restore(val token: Long) : RenderOrigin
    }

    sealed interface Effect {
        data object DetachViewport : Effect
        data object UpdateHighlight : Effect
        data object ApplyLowerEdgeFollow : Effect
        data object ShowCenterIndicator : Effect
        data object HideCenterIndicator : Effect
        data object PausePlayback : Effect
        data class StartRestore(
            val token: Long,
            val target: ReadAloudPlaybackCursor,
            val reason: RestoreReason
        ) : Effect
        data class StepFrom(val source: ManualStepSource) : Effect
    }

    sealed interface Event {
        data class PlaybackProgress(val presentation: ReadAloudPresentation) : Event
        data class UserScrollStarted(
            val cursor: ReadAloudPlaybackCursor,
            val viewport: ReadAloudViewport? = null
        ) : Event
        data class UserScrollSettled(val presentation: ReadAloudPresentation) : Event
        data class RestoreRequested(
            val cursor: ReadAloudPlaybackCursor,
            val reason: RestoreReason
        ) : Event
        data class RenderChanged(
            val origin: RenderOrigin,
            val viewport: ReadAloudViewport
        ) : Event
        data class RestoreApplied(
            val token: Long,
            val presentation: ReadAloudPresentation
        ) : Event
        data class RestoreTimedOut(val token: Long) : Event
        data class BackNavigationRequested(val presentation: ReadAloudPresentation) : Event
        data class ManualStepRequested(
            val presentation: ReadAloudPresentation,
            val visualPositionEnabled: Boolean
        ) : Event
        data class ForegroundReturned(
            val presentation: ReadAloudPresentation,
            val playbackContinued: Boolean
        ) : Event
        data class PlaybackPaused(val cursor: ReadAloudPlaybackCursor) : Event
        data class PlaybackResumed(val presentation: ReadAloudPresentation) : Event
    }

    data class Transition(val state: State, val effects: List<Effect> = emptyList())
    data class State(
        val follow: FollowState = FollowState.Following,
        val playbackCursor: ReadAloudPlaybackCursor? = null,
        val restore: Restore? = null,
        val viewport: ReadAloudViewport? = null
    )

    var state = State()
        private set
}
```

Define the sealed `Event` variants listed in the design and implement
`reduce(event): Transition` as one exhaustive `when`. The transition table is:

| Event | Next follow state | Required effects |
| --- | --- | --- |
| user scroll starts | `Detached` | `DetachViewport` once |
| detached settle, playback visible | `Detached` | `UpdateHighlight`, `HideCenterIndicator` |
| detached settle, playback hidden | `Detached` | `ShowCenterIndicator` |
| following progress, playback visible | `Following` | `UpdateHighlight`, `ApplyLowerEdgeFollow`, `HideCenterIndicator` |
| following progress, playback hidden | `Restoring(token)` | `StartRestore(token, latestCursor, Progress)` |
| restore-origin render with matching token | unchanged `Restoring` | none until target paragraph materializes |
| restore completion with matching token | `Following` | `HideCenterIndicator`; the `StartRestore` adapter has already materialized and highlighted the target atomically |
| stale restore callback | unchanged | none |
| explicit user scroll during restore | `Detached` | `DetachViewport` |
| back with playback visible | unchanged | `PausePlayback` |
| back with playback hidden | `Restoring(token)` | `StartRestore(token, cursor, BackNavigation)` |
| manual command with playback visible | unchanged | `StepFrom(PlaybackCursor)` |
| manual command with hidden playback and visual mode enabled | unchanged | `StepFrom(VisualCenter)` |

`RenderOrigin.User` may cancel restore; `RenderOrigin.Restore(token)` may only
complete the matching restore. Repeated settle events for the same viewport and
visibility produce an empty effects list.

- [ ] **Step 4: Complete a local static review**

```powershell
rg -n "android\.|ReadBook|BaseReadAloudService|ContentTextView" app/src/main/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinator.kt
git diff --check
```

Expected: no Android or mutable global dependency in the coordinator; no whitespace errors.

---

### Task 2: Service-Owned Playback Cursor and Progress Deduplication

**Files:**
- Modify: `app/src/main/java/io/legado/app/service/ReadAloudProgress.kt`
- Modify: `app/src/test/java/io/legado/app/service/ReadAloudProgressTest.kt`
- Modify: `app/src/main/java/io/legado/app/service/BaseReadAloudService.kt`
- Modify: `app/src/main/java/io/legado/app/service/TTSReadAloudService.kt`

**Interfaces:**
- Produces: `BaseReadAloudService.playbackCursor(): ReadAloudPlaybackCursor?` and one progress publication helper.
- Consumes: coordinator cursor type from Task 1.

- [ ] **Step 1: Add failing pure publication-policy tests**

```kotlin
@Test
fun exactConsecutiveCursorIsNotPublishedTwice() {
    val policy = ReadAloudProgress.PublicationPolicy()
    val cursor = ReadAloudPlaybackCursor(226, 131, 12)

    assertTrue(policy.shouldPublish(cursor))
    assertFalse(policy.shouldPublish(cursor))
}

@Test
fun samePositionWithNewSemanticSequenceIsPublished() {
    val policy = ReadAloudProgress.PublicationPolicy()

    assertTrue(policy.shouldPublish(ReadAloudPlaybackCursor(226, 131, 12)))
    assertTrue(policy.shouldPublish(ReadAloudPlaybackCursor(226, 131, 13)))
}
```

- [ ] **Step 2: Implement cursor sequence and publication policy**

Add a service-owned monotonically increasing semantic sequence. `upTtsProgress` updates chapter/index first, forms one cursor, and posts only through `publishProgressIfChanged(cursor)`. Manual previous/next and automatic `onStart` may call the same helper; an identical already-published transition does not produce a second event.

Do not change `ReadAloudProgress.fromRangeStart`; it preserves exact pause range positions.

- [ ] **Step 3: Replace direct mutable reads with a snapshot accessor**

Expose an atomic main-process snapshot:

```kotlin
@JvmStatic
fun playbackCursor(): ReadAloudPlaybackCursor? = playbackCursor
```

Keep `readAloudChapterIndex` and `readAloudChapterStart` only where upstream or unrelated code still consumes them during migration. `ReadBookActivity` must use the snapshot after Task 4.

- [ ] **Step 4: Check publication call sites**

```powershell
rg -n "postEvent\(EventBus.TTS_PROGRESS|upTtsProgress\(" app/src/main/java/io/legado/app/service
git diff --check
```

Expected: `TTS_PROGRESS` is posted in one helper; manual and automatic paths route through it.

---

### Task 3: Renderer Viewport and Scroll-Origin Adapter

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`
- Modify if forwarding is needed: `app/src/main/java/io/legado/app/ui/book/read/page/PageView.kt`
- Modify if forwarding is needed: `app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt`
- Test: `app/src/test/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinatorTest.kt`

**Interfaces:**
- Produces: `readAloudViewport(): ReadAloudViewport?`, `onReadAloudUserScrollStarted(viewport)`, and `onReadAloudUserScrollSettled(viewport)`.
- Consumes: coordinator viewport type from Task 1.

- [ ] **Step 1: Add idempotent gesture tests to the coordinator suite**

Assert that multiple user scroll frames do not create multiple detach transitions and repeated settle events with the same viewport return an empty effects list.

- [ ] **Step 2: Add a renderer viewport snapshot**

Derive chapter, page, and visible anchor from the materialized renderer state:

```kotlin
fun readAloudViewport(): ReadAloudViewport? {
    val anchor = readAloudVisibleAnchor() ?: return null
    return ReadAloudViewport(anchor.chapterIndex, anchor.pageIndex, anchor.chapterPosition)
}
```

Do not expose or transfer ownership of `textPage`, `pageOffset`, or `readAloudPageOffset`.

- [ ] **Step 3: Coalesce semantic scroll callbacks**

Keep geometry work inside `scroll(mOffset)`, but call `onReadAloudUserScrollStarted` only for the first non-zero frame of a gesture. The existing Activity idle callback reports one final settled viewport and clears the gesture marker. Programmatic follow/restore offsets must not call the user-scroll callbacks.

Remove the per-frame semantic call to `onReadAloudVisualFollowInterrupted()` after the new callback is connected.

- [ ] **Step 4: Preserve the lower-edge geometry policy**

Do not alter `ReadAloudVisualPositioner.calculateOffset`. Retain the tests where an in-bounds paragraph keeps its current offset and a paragraph whose bottom enters the lower 10-percent region moves to the upper 10-percent position.

- [ ] **Step 5: Static hot-path check**

```powershell
rg -n "onReadAloudUserScrollStarted|onReadAloudUserScrollSettled|onReadAloudVisualFollowInterrupted" app/src/main/java/io/legado/app/ui/book/read/page app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt
git diff --check
```

Expected: semantic callbacks occur at gesture boundaries, not unconditionally per scroll frame.

---

### Task 4: Activity Coordinator Integration and Tokenized Restore

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
- Test: `app/src/test/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinatorTest.kt`

**Interfaces:**
- Consumes: service cursor snapshot, renderer viewport, coordinator events/decisions.
- Produces: Activity adapter methods `dispatchReadAloudVisual(event)` and `applyReadAloudVisualTransition(transition)`.

- [ ] **Step 1: Add the confirmed race sequences to tests**

Encode the observed sequence: playback `226/39`, visual scroll to `225`, playback `226/131`; expected decision is based on cursor chapter `226`, not visual chapter `225`. Encode restore start, generic content finish, programmatic page change, and restore completion; expected active token survives until matching completion.

- [ ] **Step 2: Add one Activity coordinator and decision adapter**

```kotlin
private val readAloudVisualCoordinator = ReadAloudVisualCoordinator()

private fun dispatchReadAloudVisual(event: ReadAloudVisualCoordinator.Event) {
    applyReadAloudVisualTransition(readAloudVisualCoordinator.reduce(event))
}
```

The transition adapter iterates the returned effects and performs existing operations such as updating spans, setting the center indicator, applying follow geometry, opening/alignment, and pausing. It does not derive a second follow state.

- [ ] **Step 3: Resolve presentation by playback cursor chapter**

Replace the `ReadBook.curTextChapter?.takeIf { chapter == readAloudChapterIndex }` observer branch with one snapshot built from `BaseReadAloudService.playbackCursor()` and `cachedReadAloudTextChapter(cursor.chapterIndex)`. A visual chapter mismatch is normal while detached.

- [ ] **Step 4: Replace restore flags with coordinator token flow**

Delete `restoringReadAloudVisualPosition`, `readAloudRestoreSerial`, and `readAloudRestoreLoadingSerial`. Pass the coordinator token through `ReadBook.openChapter` completion and the programmatic render decision. Remove the unconditional restore clear from `contentLoadFinish()`.

The 30-second timeout becomes an explicit `RestoreTimedOut(token)` event. It must not mutate newer restore state.

- [ ] **Step 5: Replace follow-paused flag ownership**

Delete `readAloudVisualFollowPaused`. Indicator, visible highlight refresh, foreground evaluation, and page-change decisions read `coordinator.state.follow`. Remove helper policies from `ReadAloudVisualPositioner` that represented lifecycle state rather than geometry once all call sites are migrated.

- [ ] **Step 6: Static source-of-truth check**

```powershell
rg -n "readAloudVisualFollowPaused|restoringReadAloudVisualPosition|readAloudRestoreSerial|readAloudRestoreLoadingSerial|curTextChapter\?\.takeIf" app/src/main/java/io/legado/app/ui/book/read
git diff --check
```

Expected: no old Activity state authority or visual-chapter TTS filter remains.

---

### Task 5: Back, Pause/Resume, Background, and Manual-Step Semantics

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
- Modify as required: `app/src/main/java/io/legado/app/service/BaseReadAloudService.kt`
- Test: `app/src/test/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinatorTest.kt`

**Interfaces:**
- Consumes: one immutable presentation snapshot per command.
- Produces: explicit coordinator decisions for back, resume, foreground, and manual source selection.

- [ ] **Step 1: Test exact product behavior**

Add tests asserting:

- back + hidden playback => `StartRestore`, no pause;
- back + visible playback => `PausePlayback`, no viewport movement;
- pause, cross-chapter visual scroll, resume => restore captured cursor exactly;
- foreground with advanced playback => latest service cursor wins;
- manual step + visible playback => `PlaybackCursor` source;
- manual step + hidden playback + visual option => `VisualCenter` source.

- [ ] **Step 2: Route system back through one snapshot**

Replace the current compound condition in `onBackPressedDispatcher` with `BackNavigationRequested(snapshot)`. Apply either restore or pause, never both.

- [ ] **Step 3: Preserve the exact pause cursor**

Pause records the latest service cursor but does not derive a new start from `ReadBook.durPageIndex`, page start, or visual center. Resume requests restore to that cursor and then calls service resume. Cross-chapter visual movement cannot rewrite the stored service cursor.

- [ ] **Step 4: Route foreground return through the coordinator**

Use the latest service cursor and current renderer viewport. If the paragraph is already visible, refresh highlight only; otherwise start a tokenized restore. Do not use the stale visual chapter as a playback filter.

- [ ] **Step 5: Capture manual source once**

Build one presentation snapshot before evaluating previous/next. If playback is visible, call existing `ReadAloud.prevParagraph` or `nextParagraph`. If hidden and visual positioning is enabled, capture `(TextPage, TextLine)` at visual center once and keep that chapter/paragraph identity through alignment and service restart.

- [ ] **Step 6: Check non-scroll mode scope**

```powershell
git diff -- app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt
rg -n "pageAnim\(\) == 3|isScroll" app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt
```

Expected: new viewport movement behavior is gated to the existing scroll-mode paths; other modes keep current behavior.

---

### Task 6: Diagnostics, Version, Functional Patches, and CI

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ReadAloudVisualTrace.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
- Modify: `app/version.properties`
- Modify: `.github/workflows/ci.yml`
- Refresh: `patches/personal/3.26.12/0001-Set-up-personal-APK-build-workflow.patch`
- Refresh: `patches/personal/3.26.12/0002-Add-TTS-playback-state-and-progress-helpers.patch`
- Refresh: `patches/personal/3.26.12/0003-Refine-TTS-visual-follow-and-highlighting.patch`
- Refresh: `patches/personal/3.26.12/0004-Add-TTS-visual-position-controls.patch`
- Refresh: `patches/personal/3.26.12/0005-Add-TTS-visual-diagnostics-export.patch`

**Interfaces:**
- Consumes: coordinator state, event, decision, cursor sequence, restore token, and viewport snapshot.
- Produces: bounded readable logs and one versioned GitHub Actions APK artifact.

- [ ] **Step 1: Trace every semantic transition**

Record one line containing event, decision, follow state, cursor, viewport, and restore token. Do not log every scroll frame. Keep the existing five-minute and 5,000-line export bounds and Download-directory export behavior.

- [ ] **Step 2: Increment and wire the test suite**

Set:

```properties
VERSION_PERSONAL=10
```

Add `io.legado.app.ui.book.read.ReadAloudVisualCoordinatorTest` to the existing `Test read aloud behavior` Gradle command. Do not add another job or workflow.

- [ ] **Step 3: Run local static verification only**

```powershell
git diff --check
rg -n "VERSION_PERSONAL=10" app/version.properties
rg -n "ReadAloudVisualCoordinatorTest" .github/workflows/ci.yml
rg -n "readAloudVisualFollowPaused|restoringReadAloudVisualPosition|readAloudRestoreSerial" app/src/main
```

Expected: clean diff, version 10, coordinator test included, and old state authorities absent.

- [ ] **Step 4: Refresh exactly five functional patches**

Follow `docs/personal-patch-workflow.md`. Rebuild the patch archive from `upstream/3.26.12` by functional ownership:

- `0001`: version and CI metadata;
- `0002`: playback cursor and service progress;
- `0003`: coordinator, renderer integration, Activity follow/restore, tests;
- `0004`: manual visual-position controls;
- `0005`: diagnostics/export.

Apply the five patches in order to a temporary clean worktree based on `upstream/3.26.12`, compare affected source files, and remove the temporary worktree. Do not run local Android tools.

- [ ] **Step 5: Commit and push one build candidate**

Commit the implementation and refreshed functional archive, push `personal/3.26.12-tts`, and let the existing single `.github/workflows/ci.yml` run compile, focused unit tests, and APK assembly sequentially.

- [ ] **Step 6: Verify GitHub Actions and artifact**

Use `gh run view` at 15-minute intervals. Success requires all steps to pass and an artifact named:

```text
legado-3.26.12.10-noR8-arm64-v8a-unsigned-apk
```

Do not start another workflow while this run is active.
