# TTS Visual State Contract Repair Design

## Goal

Repair the visual-position failures proven by the July 17-25 read-aloud
traces without adding another chronological patch or changing non-scroll page
animation behavior.

## Confirmed Failures

1. A copied center `TextLine` loses its source `TextPage`, so visual-center
   manual navigation cannot align an adjacent rendered chapter.
2. Manual navigation checks the exact playback character while the indicator
   and highlight check the complete playback paragraph. The two answers can
   disagree for the same frame.
3. Following a paragraph on the previous chapter can leave a positive effective
   offset on the current chapter. Returning to the current page then preserves
   that inherited offset and leaves a large blank area above the text.
4. Every scroll frame records a full visual snapshot and recalculates
   presentation state, including zero-distance frames.

## Design

### Source-preserving visual selection

`ContentTextView` will return the actual rendered `TextPage` together with the
screen-adjusted copied `TextLine`. The copied line remains suitable for
paragraph-position calculations, while the original page supplies the
authoritative chapter and page identity. `ReadBookActivity` must not recover
chapter identity from `TextLine.textPage`.

No new cache or registry is needed. A `Pair<TextPage, TextLine>` is sufficient
and keeps the existing call chain through `PageView` and `ReadView`.

### One visibility contract

All activity decisions that ask whether the playback position is on screen
will use `ReadAloudPresentationSnapshot.paragraphVisible`. A paragraph is
visible when at least one of its rendered lines is visible. Manual previous or
next navigation, the center indicator, paused highlighting, foreground restore,
and back-navigation behavior will therefore use the same answer.

### Follow-offset ownership

An explicitly supplied chapter-boundary offset remains authoritative. Without
an explicit offset, a positive effective offset is considered inherited
previous-page placement when the new target paragraph belongs to the current
rendered page. That inherited value is normalized to zero before applying the
normal 10-percent positioning rule. Targets that still belong to the previous
rendered page keep the positive offset.

### Bounded scroll diagnostics

Zero-distance scroll frames will perform no TTS visual work. Non-zero frames
may interrupt follow immediately, but expensive visibility evaluation is
debounced until the viewport settles. Full trace snapshots are recorded only
when follow is interrupted, the rendered page changes, or the viewport settles.
The existing five-minute and 5,000-event export bounds remain unchanged.

## Patch Ownership

- `0003`: unified visibility and inherited follow-offset normalization.
- `0004`: source-preserving visual-center/manual navigation.
- `0005`: bounded scroll diagnostics.
- `0001`: version `3.26.12.8-noR8`.

The archive remains exactly five functional patches.

## Verification

- Add pure policy tests for inherited-offset normalization and scroll-frame
  filtering before production changes.
- Use static searches and `git diff --check` locally.
- Do not run local Android, Gradle, JDK, SDK, or NDK commands.
- Apply all five exported patches to a temporary worktree based on
  `upstream/3.26.12` and compare the affected files.
- Push once and use only `.github/workflows/ci.yml` for tests and APK packaging.
