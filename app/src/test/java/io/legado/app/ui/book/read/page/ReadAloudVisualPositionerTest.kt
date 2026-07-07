package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudVisualPositionerTest {

    @Test
    fun paragraphInsideMiddleAreaKeepsCurrentOffset() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 250f,
            paragraphBottom = 450f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(0, offset)
    }

    @Test
    fun paragraphAlreadyInsideMiddleAreaWithCurrentOffsetKeepsCurrentOffset() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 600f,
            paragraphBottom = 800f,
            visibleTop = 100f,
            visibleHeight = 1000f,
            currentOffset = -200f
        )

        assertEquals(-200, offset)
    }

    @Test
    fun visibleTopParagraphKeepsCurrentOffset() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 100f,
            paragraphBottom = 300f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(0, offset)
    }

    @Test
    fun naturalBottomOverflowKeepsCurrentOffsetWhenParagraphIsCurrentlySafe() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 900f,
            paragraphBottom = 1000f,
            visibleTop = 100f,
            visibleHeight = 1000f,
            currentOffset = -200f
        )

        assertEquals(-200, offset)
    }

    @Test
    fun bottomOverflowParagraphRestartsAtMiddleAreaTop() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 900f,
            paragraphBottom = 1100f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(-650, offset)
    }

    @Test
    fun previousFollowOffsetDoesNotKeepNextParagraphNearBottom() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 960f,
            paragraphBottom = 1000f,
            visibleTop = 100f,
            visibleHeight = 1000f,
            currentOffset = -50f
        )

        assertEquals(-710, offset)
    }

    @Test
    fun paragraphAboveVisibleAreaRestartsAtMiddleAreaTop() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 20f,
            paragraphBottom = 120f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(230, offset)
    }

    @Test
    fun positiveOffsetDrawsPreviousPageBottomAboveCurrentPage() {
        val offset = ReadAloudVisualPositioner.previousPageOffset(
            currentOffset = 150f,
            previousPageHeight = 900f
        )

        assertEquals(-750f, offset)
    }

    @Test
    fun nonPositiveOffsetDoesNotDrawPreviousPage() {
        val offset = ReadAloudVisualPositioner.previousPageOffset(
            currentOffset = 0f,
            previousPageHeight = 900f
        )

        assertNull(offset)
    }

    @Test
    fun relativePageTopUsesContinuousVisualCoordinates() {
        assertEquals(
            -900f,
            ReadAloudVisualPositioner.relativePageTop(
                currentPageIndex = 3,
                targetPageIndex = 2,
                previousPageHeight = 900f,
                currentPageHeight = 1000f,
                nextPageHeight = 1100f
            )
        )
        assertEquals(
            0f,
            ReadAloudVisualPositioner.relativePageTop(
                currentPageIndex = 3,
                targetPageIndex = 3,
                previousPageHeight = 900f,
                currentPageHeight = 1000f,
                nextPageHeight = 1100f
            )
        )
        assertEquals(
            1000f,
            ReadAloudVisualPositioner.relativePageTop(
                currentPageIndex = 3,
                targetPageIndex = 4,
                previousPageHeight = 900f,
                currentPageHeight = 1000f,
                nextPageHeight = 1100f
            )
        )
        assertEquals(
            2100f,
            ReadAloudVisualPositioner.relativePageTop(
                currentPageIndex = 3,
                targetPageIndex = 5,
                previousPageHeight = 900f,
                currentPageHeight = 1000f,
                nextPageHeight = 1100f
            )
        )
    }

    @Test
    fun nonAdjacentPageHasNoContinuousVisualCoordinate() {
        val pageTop = ReadAloudVisualPositioner.relativePageTop(
            currentPageIndex = 3,
            targetPageIndex = 6,
            previousPageHeight = 900f,
            currentPageHeight = 1000f,
            nextPageHeight = 1100f
        )

        assertNull(pageTop)
    }

    @Test
    fun nextChapterOffsetContinuesAfterPreviousPage() {
        val offset = ReadAloudVisualPositioner.nextChapterOffset(
            previousEffectiveOffset = -420f,
            previousPageHeight = 1000f
        )

        assertEquals(580, offset)
    }

    @Test
    fun invalidPreviousPageHeightDoesNotContinueToNextChapter() {
        val offset = ReadAloudVisualPositioner.nextChapterOffset(
            previousEffectiveOffset = -420f,
            previousPageHeight = 0f
        )

        assertNull(offset)
    }

    @Test
    fun chapterBoundaryInitialOffsetStartsParagraphAtMiddleAreaTop() {
        val offset = ReadAloudVisualPositioner.chapterBoundaryInitialOffset(
            paragraphTop = 21f,
            paragraphBottom = 114f,
            visibleTop = 0f,
            visibleHeight = 3032f
        )

        assertEquals(434, offset)
    }

    @Test
    fun chapterBoundaryInitialOffsetStartsLongParagraphAtVisibleTop() {
        val offset = ReadAloudVisualPositioner.chapterBoundaryInitialOffset(
            paragraphTop = 21f,
            paragraphBottom = 2900f,
            visibleTop = 0f,
            visibleHeight = 3032f
        )

        assertEquals(-21, offset)
    }

    @Test
    fun activeFollowOffsetIsAddedToScrollOffsetForDrawingOnly() {
        val offset = ReadAloudVisualPositioner.contentOffset(
            pageOffset = -300f,
            readAloudOffset = 150f,
            readAloudActive = true
        )

        assertEquals(-150f, offset)
    }

    @Test
    fun inactiveFollowOffsetKeepsScrollOffset() {
        val offset = ReadAloudVisualPositioner.contentOffset(
            pageOffset = -300f,
            readAloudOffset = 150f,
            readAloudActive = false
        )

        assertEquals(-300f, offset)
    }

    @Test
    fun visibleReadAloudPositionKeepsManualStepOnReadAloudPosition() {
        val shouldUseVisualCenter = ReadAloudVisualPositioner.shouldStepFromVisualCenter(
            visualPositionEnabled = true,
            readAloudRunning = true,
            readAloudPositionVisible = true
        )

        assertFalse(shouldUseVisualCenter)
    }

    @Test
    fun hiddenReadAloudPositionAllowsManualStepFromVisualCenter() {
        val shouldUseVisualCenter = ReadAloudVisualPositioner.shouldStepFromVisualCenter(
            visualPositionEnabled = true,
            readAloudRunning = true,
            readAloudPositionVisible = false
        )

        assertTrue(shouldUseVisualCenter)
    }

    @Test
    fun disabledVisualPositionKeepsManualStepOnReadAloudPosition() {
        val shouldUseVisualCenter = ReadAloudVisualPositioner.shouldStepFromVisualCenter(
            visualPositionEnabled = false,
            readAloudRunning = true,
            readAloudPositionVisible = false
        )

        assertFalse(shouldUseVisualCenter)
    }

    @Test
    fun hiddenReadAloudPositionShowsVisualCenterIndicator() {
        val shouldShow = ReadAloudVisualPositioner.shouldShowVisualCenterIndicator(
            visualPositionEnabled = true,
            readAloudPlaying = true,
            readAloudPositionVisible = false
        )

        assertTrue(shouldShow)
    }

    @Test
    fun visibleReadAloudPositionHidesVisualCenterIndicator() {
        val shouldShow = ReadAloudVisualPositioner.shouldShowVisualCenterIndicator(
            visualPositionEnabled = true,
            readAloudPlaying = true,
            readAloudPositionVisible = true
        )

        assertFalse(shouldShow)
    }

    @Test
    fun successfulVisualFollowKeepsExistingVisualPage() {
        val fallback = ReadAloudVisualPositioner.followFallback(
            followSucceeded = true,
            visualPageMatchesTarget = false
        )

        assertEquals(ReadAloudVisualPositioner.FollowFallback.KeepVisualPage, fallback)
    }

    @Test
    fun failedVisualFollowRefreshesWhenVisualPageAlreadyMatchesTarget() {
        val fallback = ReadAloudVisualPositioner.followFallback(
            followSucceeded = false,
            visualPageMatchesTarget = true
        )

        assertEquals(ReadAloudVisualPositioner.FollowFallback.RefreshCurrentPage, fallback)
    }

    @Test
    fun failedVisualFollowJumpsWhenVisualPageDoesNotMatchTarget() {
        val fallback = ReadAloudVisualPositioner.followFallback(
            followSucceeded = false,
            visualPageMatchesTarget = false
        )

        assertEquals(ReadAloudVisualPositioner.FollowFallback.JumpToTargetPage, fallback)
    }

    @Test
    fun pausedVisualFollowStillUpdatesHighlightWhenParagraphVisible() {
        val shouldUpdate = ReadAloudVisualPositioner.shouldUpdateHighlightWhenFollowPaused(
            readAloudParagraphVisible = true
        )

        assertTrue(shouldUpdate)
    }

    @Test
    fun pausedVisualFollowDoesNotUpdateHighlightWhenParagraphHidden() {
        val shouldUpdate = ReadAloudVisualPositioner.shouldUpdateHighlightWhenFollowPaused(
            readAloudParagraphVisible = false
        )

        assertFalse(shouldUpdate)
    }

    @Test
    fun pausedResumeUsesStoredReadAloudPositionEvenAfterVisualPageChanged() {
        val shouldRestore = ReadAloudVisualPositioner.shouldRestoreStoredPositionOnResume(
            readAloudPaused = true,
            visualPageChanged = true
        )

        assertTrue(shouldRestore)
    }

    @Test
    fun nonPausedStateDoesNotRunStoredPositionResumePath() {
        val shouldRestore = ReadAloudVisualPositioner.shouldRestoreStoredPositionOnResume(
            readAloudPaused = false,
            visualPageChanged = true
        )

        assertFalse(shouldRestore)
    }

    @Test
    fun visibleReadAloudParagraphRestoresWithoutPageJump() {
        val shouldReuseVisiblePage = ReadAloudVisualPositioner.shouldRestoreWithoutPageJump(
            readAloudParagraphVisible = true
        )

        assertTrue(shouldReuseVisiblePage)
    }

    @Test
    fun hiddenReadAloudParagraphRequiresPageJumpOnRestore() {
        val shouldReuseVisiblePage = ReadAloudVisualPositioner.shouldRestoreWithoutPageJump(
            readAloudParagraphVisible = false
        )

        assertFalse(shouldReuseVisiblePage)
    }

    @Test
    fun clearFollowStateKeepsScrollOffset() {
        val state = ReadAloudVisualPositioner.clearFollowState(
            ReadAloudVisualPositioner.FollowState(
                pageOffset = -420,
                readAloudOffset = 120,
                active = true
            )
        )

        assertEquals(-420, state.pageOffset)
        assertEquals(0, state.readAloudOffset)
        assertEquals(false, state.active)
    }

    @Test
    fun userScrollInterruptionKeepsVisualPosition() {
        val state = ReadAloudVisualPositioner.interruptFollowByUserScroll(
            ReadAloudVisualPositioner.FollowState(
                pageOffset = -420,
                readAloudOffset = 120,
                active = true
            )
        )

        assertEquals(-300, state.pageOffset)
        assertEquals(0, state.readAloudOffset)
        assertEquals(false, state.active)
    }

    @Test
    fun resetPageStateClearsScrollOffset() {
        val state = ReadAloudVisualPositioner.resetPageState(
            ReadAloudVisualPositioner.FollowState(
                pageOffset = -420,
                readAloudOffset = 120,
                active = true
            )
        )

        assertEquals(0, state.pageOffset)
        assertEquals(0, state.readAloudOffset)
        assertEquals(false, state.active)
    }

    @Test
    fun longParagraphStartsAtVisibleTop() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 850f,
            paragraphBottom = 1800f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(-750, offset)
    }

    @Test
    fun invalidVisibleAreaKeepsCurrentOffset() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 100f,
            paragraphBottom = 200f,
            visibleTop = 100f,
            visibleHeight = 0f,
            currentOffset = -50f
        )

        assertEquals(-50, offset)
    }
}
