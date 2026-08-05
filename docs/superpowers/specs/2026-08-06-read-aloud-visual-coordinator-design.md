# Read Aloud Visual State Coordinator Design

## Status

This design supersedes the activity-local state approaches in:

- `2026-07-14-tts-visual-state-sync-design.md`
- `2026-07-25-tts-visual-state-contract-repair-design.md`

Those documents remain as decision history. Their geometry rules, cached chapter
selection, and diagnostics remain useful, but their state ownership model has
not prevented callback-order races.

## Context

The `3.26.12.9-noR8` device traces confirm that several isolated fixes work:

- foreground visual following can move the highlight without moving the text;
- pausing no longer immediately replaces the current page and offset;
- recent traces contain no confirmed loading or missing-render failure;
- the lower 10-percent positioning rule works when it receives a correct,
  current paragraph and viewport.

The same traces also prove that the overall state contract remains incomplete:

1. The TTS service can continue in chapter `226` after a user scroll changes
   `ReadBook.curTextChapter` to chapter `225`. The activity then rejects valid
   progress because highlight resolution is coupled to the visual chapter.
2. A cross-chapter restore can be invalidated by its own content-load or
   page-change callback. `contentLoadFinish()` clears the restore flag before
   the restore transaction has produced a final visual result.
3. Manual previous or next may publish progress once in
   `BaseReadAloudService` and again when `TTSReadAloudService` starts the same
   paragraph.
4. Continuous scroll callbacks repeatedly interrupt and settle visual follow.
   The semantic decision is made per frame instead of once per user gesture.

These are not independent geometry bugs. Playback, rendering, visual following,
and restoration each have mutable state, but no component owns the transition
between them. Adding more checks around individual callbacks would preserve
that ambiguity.

## Goals

- Give playback, viewport, and visual-follow transitions one explicit owner
  each.
- Make cross-chapter, background-return, pause/resume, and manual-step event
  order deterministic.
- Resolve highlighting from the current playback cursor even when the user is
  viewing another chapter.
- Prevent a programmatic render callback from cancelling its own restore.
- Preserve the existing lower 10-percent geometry rule and in-place visual
  movement behavior.
- Cover the confirmed failures with pure event-sequence tests that run in the
  single GitHub Actions workflow.
- Keep the personal archive at exactly five functional patches.

## Non-Goals

- Redesigning `ReadBook` persistence or the normal reading-position database.
- Adding pre-rendering, a new page cache, or a new text layout engine.
- Changing network book loading, Room schemas, or source formats.
- Changing non-scroll page animation behavior.
- Moving Android UI ownership into the TTS service.
- Running a local JDK, SDK, NDK, Gradle, or Android build.

## Approach Comparison

### A. Consolidate flags inside `ReadBookActivity`

This approach would replace some booleans with an activity-local state object
but leave transition decisions distributed across event observers, page-load
callbacks, lifecycle callbacks, and scroll callbacks.

- Coupling: remains coupled to Activity lifecycle and mutable `ReadBook` state.
- Performance: low allocation cost, but repeated scroll-frame decisions remain.
- Memory: negligible.
- Validation: pure tests can cover helper policies, not full callback ordering.
- Naming: improves local readability but does not establish ownership.
- Extension: each new event still requires edits to several activity branches.

This is a small diff, but it repeats the structure that produced the current
races. Rejected.

### B. Add one pure visual-state coordinator

Add an internal `ReadAloudVisualCoordinator` that owns only visual follow and
restore transitions. The TTS service continues to own playback. The renderer
continues to own the materialized viewport. `ReadBookActivity` becomes the
adapter that converts callbacks into coordinator events and applies decisions.

- Coupling: removes direct transition coupling between service fields,
  Activity flags, and renderer callbacks.
- Performance: one small immutable state transition per semantic event;
  scroll frames are coalesced before reaching the coordinator.
- Memory: one coordinator state, one optional restore token, and snapshots;
  there is no unbounded stream or additional page cache.
- Validation: event sequences can be tested as ordinary Kotlin without an
  Android lifecycle or renderer.
- Naming: states and decisions make `Following`, `Detached`, and `Restoring`
  behavior explicit.
- Extension: new visual commands add an event and decision without adding a
  second source of truth.

This is the recommended approach. It adds one boundary because the boundary
removes real state ambiguity and enables deterministic tests.

### C. Let the TTS service own visual state

The service could centralize playback and visual-follow state, but it does not
own the Activity lifecycle, rendered pages, or user gestures.

- Coupling: introduces service-to-renderer and service-to-Activity lifecycle
  dependencies.
- Performance: would require more cross-component events for every visual
  update.
- Memory: similar to B.
- Validation: service tests still need UI lifecycle and render adapters.
- Naming: central state appears simple but combines unrelated ownership.
- Extension: future renderer changes leak into playback code.

Rejected because visual state must not outlive or control the renderer through
the audio service.

## State Ownership

### Playback cursor

`PlaybackCursor` is an immutable snapshot containing:

- chapter index;
- paragraph start position;
- a monotonically increasing sequence for semantic playback transitions.

Only the read-aloud service advances this cursor. User scrolling, page loading,
and visual restoration never modify it. The sequence is process-local and is
not persisted.

### Viewport anchor

`ViewportAnchor` is an immutable snapshot containing:

- rendered chapter index;
- rendered page index;
- chapter position represented by the viewport anchor;
- effective render offset when required by the current renderer.

The renderer materializes this state. The coordinator can request a target,
but it does not directly mutate `textPage`, `pageOffset`, or
`readAloudPageOffset`.

### Follow state

The coordinator owns a sealed follow state:

- `Following`: progress may update the highlight and apply the existing edge
  positioning rule.
- `Detached`: the user is viewing elsewhere. Playback continues; visible
  playback text may still be highlighted, and an invisible target shows the
  center indicator when visual positioning is enabled.
- `Restoring`: a tokenized programmatic operation is moving the viewport to a
  captured playback cursor.

Audio running or paused is not encoded in this state. Playback lifecycle and
visual-follow lifecycle are related events, but they are not the same state.

### Presentation snapshot

For each semantic update, the Activity creates one immutable presentation
snapshot from:

- the latest playback cursor;
- a matching completed cached `TextChapter` resolved by cursor chapter;
- the complete playback paragraph;
- whether any rendered line from that paragraph is visible;
- the current viewport anchor.

Highlight, indicator, and manual-step source decisions consume the same
snapshot. They must not independently reread mutable service or `ReadBook`
fields during one callback.

## Coordinator Contract

The exact Kotlin signatures may be refined during implementation, but the
boundary must remain pure. Inputs are semantic events; outputs are decisions
for the Activity and renderer adapters.

Representative events:

- `PlaybackProgress(cursor, snapshot)`
- `PlaybackPaused(cursor)`
- `PlaybackResumed(cursor, snapshot)`
- `UserScrollStarted(viewport)`
- `UserScrollSettled(viewport, snapshot)`
- `RestoreRequested(cursor, reason)`
- `RenderChanged(origin, token, viewport, snapshot)`
- `RestoreApplied(token, viewport, snapshot)`
- `ManualStepRequested(direction, snapshot, visualPositionEnabled)`
- `ActivityReturnedToForeground(cursor, snapshot)`

Representative decisions:

- update highlight in the current render;
- apply the normal lower-edge follow offset;
- open or align the target chapter with a restore token;
- keep the viewport detached and update the center indicator;
- select playback cursor or visual-center paragraph as the manual-step source;
- ignore a stale callback or duplicate progress event.

The coordinator never performs I/O, starts TTS, opens a chapter, or mutates a
view. That separation is what makes complete event sequences testable.

## Restore Transactions

Every restore receives a unique token and a captured target cursor. The token
is carried through programmatic chapter alignment, content loading, page
materialization, and final viewport application.

Rules:

1. Only one restore token is active.
2. Generic `contentLoadFinish()` cannot complete or cancel a restore.
3. A page or render callback marked with the active programmatic origin cannot
   be interpreted as a user scroll.
4. A callback with an old token has no state-changing effect.
5. Newer playback progress during restore updates the target to the latest
   playback cursor according to one coordinator transition. It does not leave
   the UI silently detached.
6. A restore completes only after the requested playback paragraph is resolved
   against materialized content and the final viewport decision is applied.
7. Explicit user scrolling after restore starts cancels the active token and
   enters `Detached`.

This replaces `restoringReadAloudVisualPosition`,
`readAloudRestoreSerial`, and callback-specific cancellation checks as sources
of truth.

## Highlight Resolution

`TTS_PROGRESS` handling must resolve text by `PlaybackCursor.chapterIndex`, not
by `ReadBook.curTextChapter`. The resolver checks the completed current,
previous, and next chapter cache and can use the chapter already captured by an
active restore. A visual chapter mismatch is expected while detached and is
not a reason to discard progress.

Behavior:

- If the playback paragraph is rendered and visible, refresh its highlight.
- If it is rendered but outside the viewport, preserve the correct paragraph
  identity and show the visual-position indicator when enabled.
- If the visual viewport is in another chapter, keep playback state current;
  do not paint an unrelated paragraph.
- When the paragraph later becomes visible through scrolling, derive and apply
  the highlight from the latest cursor without waiting for another TTS event.

## Manual Previous and Next

Manual-step source selection follows one contract:

1. If the current viewport contains the playback paragraph, step relative to
   `PlaybackCursor`.
2. Otherwise, when visual positioning is enabled, step relative to the
   paragraph at the visual center.
3. Otherwise, step relative to `PlaybackCursor`.

The selected source is captured once before any chapter alignment or TTS
restart. Cross-chapter materialization cannot change that selection.

The service publishes progress through one `publishProgressIfChanged` path.
An exact consecutive cursor already published for the same playback transition
is ignored. This allows normal TTS callbacks to report real automatic progress
while preventing manual-step plus `onStart` duplication.

## Scroll Events

Rendering still processes every scroll frame, but visual state does not.

- The first non-zero user scroll frame emits `UserScrollStarted` once.
- Further frames update only renderer-owned geometry.
- One idle/settled callback emits `UserScrollSettled` with the final viewport
  and presentation snapshot.
- Programmatic render movement is tagged with an origin and does not emit user
  scroll events.
- Repeated settled callbacks with an unchanged viewport are idempotent.

If the current renderer cannot expose a native idle transition, the Activity
may coalesce frames with one idle timer. The timer is an adapter detail; it must
not contain follow-state policy.

## Required Event Sequences

### Playback crosses a chapter while following

1. Service publishes a new playback cursor in the next chapter.
2. Activity resolves that chapter by cursor, independent of visual chapter.
3. Coordinator requests cached in-place alignment or a tokenized restore.
4. Programmatic render callbacks preserve `Following` or `Restoring`.
5. The final materialized paragraph is highlighted and positioned by the
   lower-edge rule.

### User scrolls while playback remains visible

1. Scroll start enters `Detached` without changing the playback cursor.
2. Scroll frames change only renderer geometry.
3. Scroll settle resolves the latest playback paragraph.
4. If visible, the correct highlight is refreshed; no page reposition occurs.

### User scrolls away and invokes back navigation

1. Coordinator captures the latest playback cursor and creates a restore token.
2. Activity aligns or opens the target chapter using that token.
3. Content-load and page-change callbacks tagged with the token cannot cancel
   the restore.
4. Restore completes only after the target paragraph is materialized.

### Pause, scroll, and resume

1. Pause captures but does not rewrite the playback cursor.
2. Scrolling changes only the viewport and enters or remains `Detached`.
3. Resume uses the captured playback cursor, not page start or visual center.
4. The viewport restores to that cursor before normal following resumes.

### Manual step while viewing another chapter

1. One presentation snapshot determines that playback is not visible.
2. With visual positioning enabled, visual center is captured as the source.
3. The service moves once from that source, including across chapters.
4. Exactly one semantic progress cursor is published.

### Return from background

1. Activity reads the latest service-owned playback cursor.
2. Coordinator compares it with the materialized viewport.
3. If absent, a tokenized restore runs; if visible, only highlight state is
   refreshed.
4. No stale pre-background `ReadBook.curTextChapter` value can reject progress.

## Invariants

1. User scrolling never mutates `PlaybackCursor`.
2. Programmatic rendering never enters `Detached` or cancels its own restore.
3. At most one restore token is active; stale callbacks have no side effects.
4. TTS progress resolves by playback chapter, never by visual chapter identity.
5. A visible playback paragraph is highlighted from the latest cursor.
6. Text position changes only for the existing lower-edge rule, explicit
   restore, or visual-center manual command.
7. One semantic playback transition publishes one progress cursor.
8. Scroll frames cannot repeatedly execute follow-state transitions.
9. Geometry policy remains in `ReadAloudVisualPositioner`; lifecycle and event
   order do not move into that class.
10. Non-scroll page animation modes retain their current behavior.

## Migration Scope

Create:

- `app/src/main/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinator.kt`
- `app/src/test/java/io/legado/app/ui/book/read/ReadAloudVisualCoordinatorTest.kt`

Modify as required:

- `ReadBookActivity.kt`: translate callbacks to events, apply decisions, and
  remove raw follow/restore state ownership.
- `ContentTextView.kt`, `PageView.kt`, and `ReadView.kt`: expose viewport
  snapshots and distinguish user from programmatic render origins.
- `BaseReadAloudService.kt` and `TTSReadAloudService.kt`: publish deduplicated
  semantic progress while retaining normal automatic progress.
- `ReadAloudVisualPositioner.kt`: retain geometry-only rules and tests.
- visual trace output: record coordinator state, cursor sequence, restore token,
  event, decision, and viewport snapshot.

Remove as state authorities:

- `readAloudVisualFollowPaused`;
- `restoringReadAloudVisualPosition`;
- `readAloudRestoreSerial`;
- unconditional restore cancellation in `contentLoadFinish()`;
- generic scroll callbacks that cannot identify their origin;
- TTS progress rejection based on `ReadBook.curTextChapter`;
- duplicate direct progress publication for one manual transition.

No compatibility flag or parallel legacy state machine will be retained. A
single migration avoids two implementations disagreeing at runtime.

## Validation

Pure coordinator tests must cover the full sequences above, including:

- progress continues after the visual viewport moves to a relative chapter;
- a restore survives its own content-load and page-change callbacks;
- stale restore callbacks are ignored;
- explicit user scroll cancels an active restore;
- pause/scroll/resume retains the exact playback cursor across chapters;
- detached visible progress refreshes highlight without moving text;
- detached invisible progress shows the indicator without painting unrelated
  text;
- manual selection uses playback when visible and visual center when absent;
- duplicate service progress for an identical transition is suppressed;
- repeated scroll frames and unchanged settle events are idempotent.

Existing geometry tests continue to cover lower-edge placement and oversized
paragraph behavior. Local verification is limited to static searches, patch
application checks, and `git diff --check`. Tests, compilation, and APK
packaging run only through `.github/workflows/ci.yml`.

Device logs must demonstrate event and decision continuity for the same test
sequences. An event without a matching decision or a tokenized restore without
a terminal result is a failed acceptance condition, even when the screen looks
correct once.

## Patch Ownership

The archive remains exactly five patches:

- `0001`: version and single CI workflow metadata.
- `0002`: playback cursor/progress publication and service behavior.
- `0003`: coordinator, Activity adapter, renderer origins, geometry integration,
  and coordinator tests.
- `0004`: visual-position manual previous/next behavior and tests.
- `0005`: visual diagnostics and export behavior.

Implementation updates existing functional patches. It does not add a sixth
chronological patch. The implementation version will advance from
`3.26.12.9-noR8` to `3.26.12.10-noR8`.

## Risks and Boundaries

- `ReadBook` still contains durable reading position and render cache state used
  outside TTS. This design prevents visual following from treating it as the
  playback authority, but does not redesign all reading persistence.
- The continuous renderer still has a bounded current/previous/next page and
  chapter window. A missing target may require a tokenized chapter load; the
  coordinator makes that transition deterministic but does not enlarge cache.
- Origin propagation touches a render hot path. The origin must be a compact
  value passed only on semantic render changes, not allocated per draw call.
- Removing legacy flags is intentionally broader than another symptom fix.
  Migration tests must be added before deleting each old branch.

## Acceptance Checklist

- Playback and visual chapter may differ without losing TTS progress or current
  playback identity.
- Cross-chapter restore cannot be cancelled by its own callbacks.
- Pause, visual navigation, and resume restart from the exact paused cursor.
- Returning from background restores or highlights the latest playback cursor.
- The current paragraph remains highlighted after scroll when it is visible.
- Manual previous/next obeys the playback-visible rule across chapters.
- No short paragraph is repositioned before its bottom enters the lower
  10-percent region.
- No unrelated text is highlighted when the playback paragraph is off-screen.
- One manual step produces one semantic progress transition.
- Scroll state changes once per gesture rather than once per frame.
- Existing non-scroll animation behavior is unchanged.
- Exactly five personal patches apply cleanly to `upstream/3.26.12`.
- The single GitHub Actions workflow passes and uploads the versioned
  `arm64-v8a-noR8-unsigned` APK.
