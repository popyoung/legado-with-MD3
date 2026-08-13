package io.legado.app.ui.book.read

import io.legado.app.service.ReadAloudPlaybackCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudVisualCoordinatorTest {

    @Test
    fun userScrollDetachesWithoutChangingPlaybackCursor() {
        val coordinator = ReadAloudVisualCoordinator()
        val cursor = cursor(chapterIndex = 226, chapterStart = 39, sequence = 7)
        val viewport = viewport(chapterIndex = 225, pageIndex = 14, chapterPosition = 5856)

        val transition = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.UserScrollStarted(cursor, viewport)
        )

        assertEquals(ReadAloudVisualCoordinator.FollowState.Detached, coordinator.state.follow)
        assertEquals(cursor, coordinator.state.playbackCursor)
        assertEquals(viewport, coordinator.state.viewport)
        assertEquals(
            listOf(ReadAloudVisualCoordinator.Effect.DetachViewport),
            transition.effects
        )
    }

    @Test
    fun progressUsesPlaybackCursorAfterVisualViewportMovesToAnotherChapter() {
        val coordinator = ReadAloudVisualCoordinator()
        val initial = cursor(chapterIndex = 226, chapterStart = 39, sequence = 7)
        coordinator.reduce(
            ReadAloudVisualCoordinator.Event.UserScrollStarted(
                initial,
                viewport(chapterIndex = 225, pageIndex = 14, chapterPosition = 5856)
            )
        )
        val progressed = cursor(chapterIndex = 226, chapterStart = 131, sequence = 8)

        val transition = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.PlaybackProgress(
                presentation(progressed, paragraphVisible = false)
            )
        )

        assertEquals(progressed, coordinator.state.playbackCursor)
        assertEquals(ReadAloudVisualCoordinator.FollowState.Detached, coordinator.state.follow)
        assertEquals(
            listOf(ReadAloudVisualCoordinator.Effect.ShowCenterIndicator),
            transition.effects
        )
    }

    @Test
    fun programmaticRenderCannotCancelItsRestore() {
        val coordinator = ReadAloudVisualCoordinator()
        val cursor = cursor(chapterIndex = 214, chapterStart = 1663, sequence = 8)
        val start = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.RestoreRequested(
                cursor,
                ReadAloudVisualCoordinator.RestoreReason.BackNavigation
            )
        ).effects.single() as ReadAloudVisualCoordinator.Effect.StartRestore

        val transition = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.RenderChanged(
                ReadAloudVisualCoordinator.RenderOrigin.Restore(start.token),
                viewport(chapterIndex = 214, pageIndex = 4, chapterPosition = 1600)
            )
        )

        assertEquals(start.token, coordinator.state.restore?.token)
        assertEquals(ReadAloudVisualCoordinator.FollowState.Restoring, coordinator.state.follow)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun staleRestoreCallbackCannotCompleteNewerRestore() {
        val coordinator = ReadAloudVisualCoordinator()
        val oldCursor = cursor(chapterIndex = 213, chapterStart = 3528, sequence = 8)
        val oldToken = startRestore(coordinator, oldCursor)
        val currentCursor = cursor(chapterIndex = 214, chapterStart = 1663, sequence = 9)
        val currentToken = startRestore(coordinator, currentCursor)

        val transition = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.RestoreApplied(
                oldToken,
                presentation(oldCursor, paragraphVisible = true)
            )
        )

        assertTrue(transition.effects.isEmpty())
        assertEquals(currentToken, coordinator.state.restore?.token)
        assertEquals(currentCursor, coordinator.state.restore?.target)
    }

    @Test
    fun userScrollCancelsActiveRestore() {
        val coordinator = ReadAloudVisualCoordinator()
        val cursor = cursor(chapterIndex = 214, chapterStart = 1663, sequence = 8)
        startRestore(coordinator, cursor)

        val transition = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.UserScrollStarted(
                cursor,
                viewport(chapterIndex = 213, pageIndex = 9, chapterPosition = 3528)
            )
        )

        assertNull(coordinator.state.restore)
        assertEquals(ReadAloudVisualCoordinator.FollowState.Detached, coordinator.state.follow)
        assertEquals(
            listOf(ReadAloudVisualCoordinator.Effect.DetachViewport),
            transition.effects
        )
    }

    @Test
    fun newerProgressReplacesActiveRestoreToken() {
        val coordinator = ReadAloudVisualCoordinator()
        val oldCursor = cursor(chapterIndex = 214, chapterStart = 1663, sequence = 8)
        val token = startRestore(coordinator, oldCursor)
        val currentCursor = cursor(chapterIndex = 215, chapterStart = 9, sequence = 9)

        val effect = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.PlaybackProgress(
                presentation(currentCursor, paragraphVisible = false)
            )
        ).effects.single() as ReadAloudVisualCoordinator.Effect.StartRestore

        assertTrue(effect.token > token)
        assertEquals(currentCursor, effect.target)
        assertEquals(currentCursor, coordinator.state.restore?.target)
    }

    @Test
    fun detachedVisibleProgressRefreshesHighlightWithoutMovingText() {
        val coordinator = detachedCoordinator()
        val current = cursor(chapterIndex = 226, chapterStart = 131, sequence = 8)

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.PlaybackProgress(
                presentation(current, paragraphVisible = true)
            )
        ).effects

        assertEquals(
            listOf(
                ReadAloudVisualCoordinator.Effect.UpdateHighlight,
                ReadAloudVisualCoordinator.Effect.HideCenterIndicator
            ),
            effects
        )
        assertFalse(effects.contains(ReadAloudVisualCoordinator.Effect.ApplyLowerEdgeFollow))
    }

    @Test
    fun followingVisibleProgressAppliesLowerEdgePolicy() {
        val coordinator = ReadAloudVisualCoordinator()

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.PlaybackProgress(
                presentation(
                    cursor(chapterIndex = 226, chapterStart = 131, sequence = 8),
                    paragraphVisible = true
                )
            )
        ).effects

        assertTrue(effects.contains(ReadAloudVisualCoordinator.Effect.UpdateHighlight))
        assertTrue(effects.contains(ReadAloudVisualCoordinator.Effect.ApplyLowerEdgeFollow))
    }

    @Test
    fun repeatedSettledViewportIsIdempotent() {
        val coordinator = detachedCoordinator()
        val snapshot = presentation(
            cursor(chapterIndex = 226, chapterStart = 131, sequence = 8),
            paragraphVisible = true
        )

        assertFalse(
            coordinator.reduce(
                ReadAloudVisualCoordinator.Event.UserScrollSettled(snapshot)
            ).effects.isEmpty()
        )
        assertTrue(
            coordinator.reduce(
                ReadAloudVisualCoordinator.Event.UserScrollSettled(snapshot)
            ).effects.isEmpty()
        )
    }

    @Test
    fun backNavigationRestoresHiddenPlaybackWithoutPausing() {
        val coordinator = detachedCoordinator()
        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.BackNavigationRequested(
                presentation(
                    cursor(chapterIndex = 214, chapterStart = 1663, sequence = 8),
                    paragraphVisible = false
                )
            )
        ).effects

        assertTrue(effects.single() is ReadAloudVisualCoordinator.Effect.StartRestore)
        assertFalse(effects.contains(ReadAloudVisualCoordinator.Effect.PausePlayback))
    }

    @Test
    fun backNavigationPausesWhenPlaybackIsVisible() {
        val coordinator = detachedCoordinator()

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.BackNavigationRequested(
                presentation(
                    cursor(chapterIndex = 214, chapterStart = 1663, sequence = 8),
                    paragraphVisible = true
                )
            )
        ).effects

        assertEquals(
            listOf(ReadAloudVisualCoordinator.Effect.PausePlayback),
            effects
        )
    }

    @Test
    fun pauseScrollResumeRestoresExactCrossChapterCursor() {
        val coordinator = ReadAloudVisualCoordinator()
        val pausedCursor = cursor(chapterIndex = 214, chapterStart = 1663, sequence = 8)
        coordinator.reduce(ReadAloudVisualCoordinator.Event.PlaybackPaused(pausedCursor))
        coordinator.reduce(
            ReadAloudVisualCoordinator.Event.UserScrollStarted(
                pausedCursor,
                viewport(chapterIndex = 216, pageIndex = 2, chapterPosition = 904)
            )
        )

        val effect = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.PlaybackResumed(
                presentation(pausedCursor, paragraphVisible = false)
            )
        ).effects.single() as ReadAloudVisualCoordinator.Effect.StartRestore

        assertEquals(pausedCursor, effect.target)
        assertEquals(ReadAloudVisualCoordinator.RestoreReason.ResumePlayback, effect.reason)
    }

    @Test
    fun foregroundVisiblePlaybackRefreshesWithoutRestore() {
        val coordinator = detachedCoordinator()

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.ForegroundReturned(
                presentation(
                    cursor(chapterIndex = 226, chapterStart = 131, sequence = 8),
                    paragraphVisible = true
                ),
                playbackContinued = true
            )
        ).effects

        assertTrue(effects.contains(ReadAloudVisualCoordinator.Effect.UpdateHighlight))
        assertFalse(effects.any { it is ReadAloudVisualCoordinator.Effect.StartRestore })
    }

    @Test
    fun manualStepUsesPlaybackWhenVisible() {
        val coordinator = detachedCoordinator()
        val visualTarget = visualTarget(
            chapterIndex = 225,
            pageIndex = 14,
            chapterPosition = 5856
        )

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.ManualStepRequested(
                presentation(
                    cursor(chapterIndex = 226, chapterStart = 131, sequence = 8),
                    paragraphVisible = true
                ),
                visualPositionEnabled = true,
                visualTarget = visualTarget
            )
        ).effects

        assertTrue(
            effects.contains(
                ReadAloudVisualCoordinator.Effect.StepFrom(
                    source = ReadAloudVisualCoordinator.ManualStepSource.PlaybackCursor,
                    visualTarget = null
                )
            )
        )
    }

    @Test
    fun manualStepUsesVisualCenterOnlyWhenPlaybackIsHidden() {
        val coordinator = detachedCoordinator()
        val visualTarget = visualTarget(
            chapterIndex = 225,
            pageIndex = 14,
            chapterPosition = 5856
        )

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.ManualStepRequested(
                presentation(
                    cursor(chapterIndex = 226, chapterStart = 131, sequence = 8),
                    paragraphVisible = false
                ),
                visualPositionEnabled = true,
                visualTarget = visualTarget
            )
        ).effects

        assertTrue(
            effects.contains(
                ReadAloudVisualCoordinator.Effect.StepFrom(
                    source = ReadAloudVisualCoordinator.ManualStepSource.VisualCenter,
                    visualTarget = visualTarget
                )
            )
        )
    }

    @Test
    fun missingVisualTargetFallsBackToPlaybackCursor() {
        val coordinator = detachedCoordinator()

        val effects = coordinator.reduce(
            ReadAloudVisualCoordinator.Event.ManualStepRequested(
                presentation(
                    cursor(chapterIndex = 226, chapterStart = 131, sequence = 8),
                    paragraphVisible = false
                ),
                visualPositionEnabled = true,
                visualTarget = null
            )
        ).effects

        assertTrue(
            effects.contains(
                ReadAloudVisualCoordinator.Effect.StepFrom(
                    source = ReadAloudVisualCoordinator.ManualStepSource.PlaybackCursor,
                    visualTarget = null
                )
            )
        )
    }

    private fun detachedCoordinator(): ReadAloudVisualCoordinator {
        return ReadAloudVisualCoordinator().apply {
            reduce(
                ReadAloudVisualCoordinator.Event.UserScrollStarted(
                    cursor(chapterIndex = 226, chapterStart = 39, sequence = 7),
                    viewport(chapterIndex = 225, pageIndex = 14, chapterPosition = 5856)
                )
            )
        }
    }

    private fun startRestore(
        coordinator: ReadAloudVisualCoordinator,
        cursor: ReadAloudPlaybackCursor
    ): Long {
        return (coordinator.reduce(
            ReadAloudVisualCoordinator.Event.RestoreRequested(
                cursor,
                ReadAloudVisualCoordinator.RestoreReason.BackNavigation
            )
        ).effects.single() as ReadAloudVisualCoordinator.Effect.StartRestore).token
    }

    private fun presentation(
        cursor: ReadAloudPlaybackCursor,
        paragraphVisible: Boolean
    ) = ReadAloudPresentation(
        cursor = cursor,
        viewport = viewport(chapterIndex = 225, pageIndex = 14, chapterPosition = 5856),
        paragraphVisible = paragraphVisible
    )

    private fun cursor(
        chapterIndex: Int,
        chapterStart: Int,
        sequence: Long
    ) = ReadAloudPlaybackCursor(chapterIndex, chapterStart, sequence)

    private fun viewport(
        chapterIndex: Int,
        pageIndex: Int,
        chapterPosition: Int
    ) = ReadAloudViewport(chapterIndex, pageIndex, chapterPosition)

    private fun visualTarget(
        chapterIndex: Int,
        pageIndex: Int,
        chapterPosition: Int
    ) = ReadAloudVisualTarget(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        chapterPosition = chapterPosition,
        pagePosition = 456,
        paragraphNum = 12
    )
}
