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

        assertEquals(-700, offset)
    }

    @Test
    fun previousFollowOffsetDoesNotKeepNextParagraphNearBottom() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 960f,
            paragraphBottom = 1100f,
            visibleTop = 100f,
            visibleHeight = 1000f,
            currentOffset = -50f
        )

        assertEquals(-760, offset)
    }

    @Test
    fun paragraphAboveVisibleAreaRestartsAtMiddleAreaTop() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 20f,
            paragraphBottom = 120f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(180, offset)
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
    fun nextChapterFirstPageCanBeNextVisualPageAfterCurrentLastPage() {
        val relativePosition = ReadAloudVisualPositioner.relativePagePosition(
            currentChapterIndex = 212,
            currentPageIndex = 9,
            currentPageSize = 10,
            targetChapterIndex = 213,
            targetPageIndex = 0,
            previousChapterPageSize = 8
        )

        assertEquals(1, relativePosition)
    }

    @Test
    fun nextChapterFirstPageCanBeSecondVisualPageAfterCurrentSecondLastPage() {
        val relativePosition = ReadAloudVisualPositioner.relativePagePosition(
            currentChapterIndex = 212,
            currentPageIndex = 8,
            currentPageSize = 10,
            targetChapterIndex = 213,
            targetPageIndex = 0,
            previousChapterPageSize = 8
        )

        assertEquals(2, relativePosition)
    }

    @Test
    fun nextChapterDistantPageIsOutsideVisualStream() {
        val relativePosition = ReadAloudVisualPositioner.relativePagePosition(
            currentChapterIndex = 212,
            currentPageIndex = 9,
            currentPageSize = 10,
            targetChapterIndex = 213,
            targetPageIndex = 2,
            previousChapterPageSize = 8
        )

        assertNull(relativePosition)
    }

    @Test
    fun previousChapterLastPageCanBePreviousVisualPageBeforeCurrentFirstPage() {
        val relativePosition = ReadAloudVisualPositioner.relativePagePosition(
            currentChapterIndex = 213,
            currentPageIndex = 0,
            currentPageSize = 9,
            targetChapterIndex = 212,
            targetPageIndex = 9,
            previousChapterPageSize = 10
        )

        assertEquals(-1, relativePosition)
    }

    @Test
    fun previousChapterNonLastPageIsOutsideVisualStream() {
        val relativePosition = ReadAloudVisualPositioner.relativePagePosition(
            currentChapterIndex = 213,
            currentPageIndex = 0,
            currentPageSize = 9,
            targetChapterIndex = 212,
            targetPageIndex = 8,
            previousChapterPageSize = 10
        )

        assertNull(relativePosition)
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

        assertEquals(282, offset)
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
    fun visiblePreviousChapterLastPageUsesChapterBoundaryOffset() {
        val shouldUseOffset = ReadAloudVisualPositioner.shouldUseChapterBoundaryInitialOffset(
            targetPageIndex = 0,
            previousChapterLastPageVisible = true
        )

        assertTrue(shouldUseOffset)
    }

    @Test
    fun hiddenPreviousChapterLastPageDoesNotUseChapterBoundaryOffset() {
        val shouldUseOffset = ReadAloudVisualPositioner.shouldUseChapterBoundaryInitialOffset(
            targetPageIndex = 0,
            previousChapterLastPageVisible = false
        )

        assertFalse(shouldUseOffset)
    }

    @Test
    fun nonFirstTargetPageDoesNotUseChapterBoundaryOffset() {
        val shouldUseOffset = ReadAloudVisualPositioner.shouldUseChapterBoundaryInitialOffset(
            targetPageIndex = 1,
            previousChapterLastPageVisible = true
        )

        assertFalse(shouldUseOffset)
    }

    @Test
    fun readAloudPageChangeSkipsCenterIndicatorPreUpdate() {
        val shouldUpdate = ReadAloudVisualPositioner.shouldUpdateCenterIndicatorOnPageChanged(
            fromReadAloud = true
        )

        assertFalse(shouldUpdate)
    }

    @Test
    fun userPageChangeUpdatesCenterIndicator() {
        val shouldUpdate = ReadAloudVisualPositioner.shouldUpdateCenterIndicatorOnPageChanged(
            fromReadAloud = false
        )

        assertTrue(shouldUpdate)
    }

    @Test
    fun quietCenterIndicatorEvaluationTracesOnlyChanges() {
        assertFalse(
            ReadAloudVisualPositioner.shouldTraceCenterIndicatorEvaluation(
                forceTrace = false,
                indicatorChanged = false
            )
        )
        assertTrue(
            ReadAloudVisualPositioner.shouldTraceCenterIndicatorEvaluation(
                forceTrace = false,
                indicatorChanged = true
            )
        )
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
    fun pausedVisualFollowBlocksImplicitRestoreFollow() {
        val shouldFollow = ReadAloudVisualPositioner.shouldFollowDuringRestore(
            readAloudVisualFollowPaused = true,
            explicitFollowRestore = false
        )

        assertFalse(shouldFollow)
    }

    @Test
    fun explicitRestoreCanResumePausedVisualFollow() {
        val shouldFollow = ReadAloudVisualPositioner.shouldFollowDuringRestore(
            readAloudVisualFollowPaused = true,
            explicitFollowRestore = true
        )

        assertTrue(shouldFollow)
    }

    @Test
    fun unpausedVisualFollowAllowsImplicitRestoreFollow() {
        val shouldFollow = ReadAloudVisualPositioner.shouldFollowDuringRestore(
            readAloudVisualFollowPaused = false,
            explicitFollowRestore = false
        )

        assertTrue(shouldFollow)
    }

    @Test
    fun scrollModeDefersReadBookPageSyncBeforeManualStepProgress() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncReadBookPageBeforeManualStepProgress(
            scrollPageAnim = true,
            targetPageChanged = true
        )

        assertFalse(shouldSync)
    }

    @Test
    fun nonScrollModeKeepsReadBookPageSyncBeforeManualStepProgress() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncReadBookPageBeforeManualStepProgress(
            scrollPageAnim = false,
            targetPageChanged = true
        )

        assertTrue(shouldSync)
    }

    @Test
    fun unchangedTargetPageDoesNotNeedReadBookPageSyncBeforeManualStepProgress() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncReadBookPageBeforeManualStepProgress(
            scrollPageAnim = false,
            targetPageChanged = false
        )

        assertFalse(shouldSync)
    }

    @Test
    fun activeFollowSyncsVisualAnchorBeforeUserScrollWhenReadBookPageMovedAhead() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncVisualPageBeforeUserScroll(
            readAloudFollowActive = true,
            visualChapterIndex = 213,
            visualPageIndex = 2,
            readBookChapterIndex = 213,
            readBookPageIndex = 3
        )

        assertTrue(shouldSync)
    }

    @Test
    fun inactiveFollowDoesNotSyncVisualAnchorBeforeUserScroll() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncVisualPageBeforeUserScroll(
            readAloudFollowActive = false,
            visualChapterIndex = 213,
            visualPageIndex = 2,
            readBookChapterIndex = 213,
            readBookPageIndex = 3
        )

        assertFalse(shouldSync)
    }

    @Test
    fun matchingReadBookPageDoesNotSyncVisualAnchorBeforeUserScroll() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncVisualPageBeforeUserScroll(
            readAloudFollowActive = true,
            visualChapterIndex = 213,
            visualPageIndex = 3,
            readBookChapterIndex = 213,
            readBookPageIndex = 3
        )

        assertFalse(shouldSync)
    }

    @Test
    fun differentReadBookChapterDoesNotSyncVisualAnchorBeforeUserScroll() {
        val shouldSync = ReadAloudVisualPositioner.shouldSyncVisualPageBeforeUserScroll(
            readAloudFollowActive = true,
            visualChapterIndex = 213,
            visualPageIndex = 2,
            readBookChapterIndex = 214,
            readBookPageIndex = 0
        )

        assertFalse(shouldSync)
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
    fun userScrollInterruptionMaterializesNextVisualPageBeforeClearingFollow() {
        val interruption = ReadAloudVisualPositioner.interruptFollowByUserScroll(
            state = ReadAloudVisualPositioner.FollowState(
                pageOffset = -1663,
                readAloudOffset = -2340,
                active = true
            ),
            previousPageHeight = 3051f,
            currentPageHeight = 3051f,
            nextPageHeight = 3032f,
            nextPlusPageHeight = 3032f
        )

        assertEquals(1, interruption.pageShift)
        assertEquals(-952, interruption.state.pageOffset)
        assertEquals(0, interruption.state.readAloudOffset)
        assertEquals(false, interruption.state.active)
    }

    @Test
    fun userScrollInterruptionMaterializesPreviousVisualPageBeforeClearingFollow() {
        val interruption = ReadAloudVisualPositioner.interruptFollowByUserScroll(
            state = ReadAloudVisualPositioner.FollowState(
                pageOffset = 0,
                readAloudOffset = 452,
                active = true
            ),
            previousPageHeight = 3051f,
            currentPageHeight = 3032f,
            nextPageHeight = 3032f,
            nextPlusPageHeight = null
        )

        assertEquals(-1, interruption.pageShift)
        assertEquals(-2599, interruption.state.pageOffset)
        assertEquals(0, interruption.state.readAloudOffset)
        assertEquals(false, interruption.state.active)
    }

    @Test
    fun followAnchorMaterializesNextVisualPageWithoutClearingFollow() {
        val materialized = ReadAloudVisualPositioner.materializeFollowAnchor(
            state = ReadAloudVisualPositioner.FollowState(
                pageOffset = 0,
                readAloudOffset = -4575,
                active = true
            ),
            previousPageHeight = 3051f,
            currentPageHeight = 3051f,
            nextPageHeight = 3032f,
            nextPlusPageHeight = 3032f
        )

        assertEquals(1, materialized.pageShift)
        assertEquals(-1524, materialized.state.pageOffset)
        assertEquals(0, materialized.state.readAloudOffset)
        assertEquals(true, materialized.state.active)
    }

    @Test
    fun followAnchorMaterializesSecondNextVisualPageWithoutClearingFollow() {
        val materialized = ReadAloudVisualPositioner.materializeFollowAnchor(
            state = ReadAloudVisualPositioner.FollowState(
                pageOffset = 0,
                readAloudOffset = -6775,
                active = true
            ),
            previousPageHeight = 3051f,
            currentPageHeight = 3051f,
            nextPageHeight = 3032f,
            nextPlusPageHeight = 3032f
        )

        assertEquals(2, materialized.pageShift)
        assertEquals(-692, materialized.state.pageOffset)
        assertEquals(0, materialized.state.readAloudOffset)
        assertEquals(true, materialized.state.active)
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
