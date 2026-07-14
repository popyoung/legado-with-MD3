# TTS Visual State Synchronization Design

## Context

The `3.26.12.6` trace shows two remaining failures in the read-aloud visual flow:

1. Selecting the visual-center paragraph in an adjacent chapter calls `ReadBook.openChapter`. That resets the rendered page and emits a normal page-change signal, which marks visual following as paused. The selected paragraph moves from the screen center to the chapter top, and subsequent TTS progress no longer follows.
2. While visual following is paused, TTS progress updates the highlight only when the paragraph is visible at that instant. If the user later scrolls the current paragraph into view, the center indicator is updated but the highlight remains stale until the next TTS progress event.

The previous materialized-anchor fix remains valid. The new work must not change the 10% lower-edge repositioning rule or introduce another chronological patch.

## Goals

- Preserve the current rendered geometry when visual-center selection crosses into an already cached adjacent chapter.
- Treat the resulting chapter alignment as a read-aloud operation, not as a user page change.
- Synchronize paused-follow highlight and center-indicator decisions from one immutable read-aloud position snapshot.
- Refresh the highlight once when scrolling changes the current read-aloud position from invisible to visible.
- Keep the change inside the existing TTS visual-follow feature boundary and fold it into personal patch `0003`.

## Non-Goals

- No change to the TTS service's paragraph stepping rules.
- No change to non-scroll page animation modes.
- No change to the 10% safe-edge calculation.
- No new global coordinator, Flow, or persisted state.
- No local Android toolchain verification.

## Design

### 1. In-place visual-center chapter alignment

When the visual-center line belongs to a different chapter, resolve that chapter from `ReadBook`'s current, previous, and next completed chapter cache. Because the line is already visible, its chapter must normally be present in this cache.

For a cached target chapter:

1. Keep `ContentTextView.textPage`, `pageOffset`, and the continuous rendered stream unchanged.
2. Call `ReadBook.alignToReadAloudChapter` with the cached target chapter and selected line position.
3. Start reading from the selected paragraph through the existing `readAloudFromLineParagraphStart` path.
4. Let the next TTS progress event activate normal paragraph following. Since the target paragraph is already visible, offset calculation keeps the text at its current position.

If the cached chapter cannot be resolved, trace the violated invariant and leave playback unchanged. Do not fall back to `openChapter`, because that would reintroduce the page reset and false user-page-change behavior this design removes.

### 2. One paused-follow presentation snapshot

Add an activity-local immutable snapshot containing:

- read-aloud chapter index;
- read-aloud chapter position;
- matching completed cached `TextChapter`, when available;
- resolved paragraph, when available;
- whether that paragraph is currently visible.

Both paused-follow highlighting and center-indicator evaluation use this same snapshot during one UI callback. They must not independently reread mutable `BaseReadAloudService` fields.

### 3. Visibility-transition highlight refresh

Track only the last evaluated visibility as activity-local transient state. When all of the following are true, refresh the paragraph span and invalidate the content view once:

- read-aloud playback is active;
- visual following is paused;
- the previous snapshot was invisible;
- the current snapshot is visible;
- the current paragraph can be resolved.

This transition check prevents highlight work on every scroll frame. Normal TTS progress continues to update visible highlights as before.

The center indicator is derived from the same current snapshot. This removes the interval where the indicator disappears because the latest service position is visible while the highlight still represents an older progress event.

## Invariants

- Audio position remains owned by `BaseReadAloudService`.
- `ReadBook` alignment may change for a read-aloud command without forcing the rendered page to reset.
- User scrolling may pause visual following but never changes the audio position by itself.
- With visual positioning enabled, a hidden center indicator during paused following means the visible current read-aloud paragraph has a current highlight span.
- Virtual follow offsets are materialized only by the existing anchor materialization code.
- A target chapter/page anchor is written to `ReadBook` only after exact chapter and page materialization.

## Alternatives Considered

### Patch the two symptoms independently

Suppressing `pageChanged` around `openChapter` and refreshing the highlight when the indicator hides would be smaller. It would still reset the visual stream at chapter boundaries and would keep two independent reads of mutable service state. Rejected because it preserves the underlying race and chapter-top jump.

### Introduce a full presentation-state coordinator

A dedicated StateFlow-backed coordinator could make audio, reading progress, rendering, and highlighting explicit state machines. This is a larger migration across the activity, service, and page renderer. It is unsuitable for the maintained `3.26.12` release branch and is not required for the confirmed failures.

The selected design uses one activity-local snapshot data class and one transition policy. It adds no cross-module layer, allocation-heavy scroll-frame stream, or persisted compatibility state.

## Acceptance Checklist

- Visual-center selection in a cached adjacent chapter does not call `openChapter`.
- The selected paragraph keeps its pre-command screen position until the normal lower-edge rule requires movement.
- Programmatic read-aloud alignment does not set visual following to paused.
- Paused-follow highlight and indicator decisions use the same chapter/position snapshot per callback.
- An invisible-to-visible transition refreshes the current highlight exactly once.
- Existing anchor materialization and lower-edge positioning tests remain unchanged and passing.
- No new fallback path, sixth personal patch, local Android build, or change to `main` is introduced.

## Expected Edit Footprint

The implementation should touch the activity's read-aloud visual helpers, the existing pure visual-position policy and tests, the personal version property, and the functional patch archive. Future changes to visibility-transition policy should require edits only to the policy, its tests, and the activity call site.

## Validation

Pure unit tests will cover:

- an invisible-to-visible transition refreshes a paused-follow highlight;
- repeated visible evaluations do not refresh again;
- active following and stopped playback do not use the paused-follow refresh path;
- a cached adjacent chapter is eligible for in-place visual-center alignment.

GitHub Actions `.github/workflows/ci.yml` is the only build and test path. Device verification should reproduce these sequences:

1. Scroll until the visual center is in the next chapter, press previous/next paragraph, and verify that text does not jump to the chapter top and following remains active.
2. Scroll away from the active paragraph, wait for progress, then scroll it back into view and verify that the indicator disappears together with the correct highlight.
3. Repeat normal lower-edge paragraph transitions and confirm that repositioning still occurs only when the paragraph bottom enters the lower 10% region.

## Patch and Version Handling

- Fold source and test changes into `patches/personal/3.26.12/0003-*`.
- Keep the functional patch count at five.
- Put the personal version increment in `0001`.
- Push only `personal/3.26.12-tts`; do not change `main`.
