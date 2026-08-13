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
    fun activePositiveFollowOffsetScansPreviousVisiblePage() {
        val firstRelativePage = ReadAloudVisualPositioner.firstVisibleRelativePage(
            readAloudActive = true,
            currentOffset = 613f
        )

        assertEquals(-1, firstRelativePage)
    }

    @Test
    fun inactivePositiveOffsetKeepsCurrentPageAsFirstVisiblePage() {
        val firstRelativePage = ReadAloudVisualPositioner.firstVisibleRelativePage(
            readAloudActive = false,
            currentOffset = 613f
        )

        assertEquals(0, firstRelativePage)
    }

    @Test
    fun activeNonPositiveOffsetKeepsCurrentPageAsFirstVisiblePage() {
        val firstRelativePage = ReadAloudVisualPositioner.firstVisibleRelativePage(
            readAloudActive = true,
            currentOffset = 0f
        )

        assertEquals(0, firstRelativePage)
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
    fun activeFollowSuppressesResidualScrollInsideRestoreWindow() {
        val shouldSuppress = ReadAloudVisualPositioner.shouldSuppressScrollAfterVisualRestore(
            readAloudFollowActive = true,
            nowMillis = 100,
            suppressUntilMillis = 300
        )

        assertTrue(shouldSuppress)
    }

    @Test
    fun inactiveFollowDoesNotSuppressScrollInsideRestoreWindow() {
        val shouldSuppress = ReadAloudVisualPositioner.shouldSuppressScrollAfterVisualRestore(
            readAloudFollowActive = false,
            nowMillis = 100,
            suppressUntilMillis = 300
        )

        assertFalse(shouldSuppress)
    }

    @Test
    fun activeFollowDoesNotSuppressScrollAfterRestoreWindow() {
        val shouldSuppress = ReadAloudVisualPositioner.shouldSuppressScrollAfterVisualRestore(
            readAloudFollowActive = true,
            nowMillis = 301,
            suppressUntilMillis = 300
        )

        assertFalse(shouldSuppress)
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
    fun userScrollHandoffUsesMaterializedRenderBaseInsteadOfVisibleAnchor() {
        val renderBase = ReadAloudVisualPositioner.PageAnchor(321, 18, 28700)
        val target = ReadAloudVisualPositioner.resolveUserScrollHandoff(
            readAloudFollowActive = true,
            renderBase = renderBase,
            readBookChapterIndex = 321,
            readBookPageIndex = 19
        )

        assertEquals(renderBase, target)
    }

    @Test
    fun inactiveFollowDoesNotChangeReadBookAtUserScrollHandoff() {
        val target = ReadAloudVisualPositioner.resolveUserScrollHandoff(
            readAloudFollowActive = false,
            renderBase = ReadAloudVisualPositioner.PageAnchor(213, 2, 8200),
            readBookChapterIndex = 213,
            readBookPageIndex = 3
        )

        assertNull(target)
    }

    @Test
    fun matchingRenderBaseDoesNotChangeReadBookAtUserScrollHandoff() {
        val target = ReadAloudVisualPositioner.resolveUserScrollHandoff(
            readAloudFollowActive = true,
            renderBase = ReadAloudVisualPositioner.PageAnchor(213, 3, 9400),
            readBookChapterIndex = 213,
            readBookPageIndex = 3
        )

        assertNull(target)
    }

    @Test
    fun materializedRenderBaseInDifferentChapterBecomesUserScrollHandoffTarget() {
        val renderBase = ReadAloudVisualPositioner.PageAnchor(213, 2, 8200)

        val target = ReadAloudVisualPositioner.resolveUserScrollHandoff(
            readAloudFollowActive = true,
            renderBase = renderBase,
            readBookChapterIndex = 214,
            readBookPageIndex = 0
        )

        assertEquals(renderBase, target)
    }

    @Test
    fun targetAnchorDoesNotApplyToIntermediatePageFromPreviousChapter() {
        val position = ReadAloudVisualPositioner.materializedAnchorPosition(
            anchor = ReadAloudVisualPositioner.PageAnchor(
                chapterIndex = 275,
                pageIndex = 0,
                chapterPosition = 9
            ),
            materializedChapterIndex = 274,
            materializedPageIndex = 8
        )

        assertNull(position)
    }

    @Test
    fun targetAnchorDoesNotApplyToDifferentPageInSameChapter() {
        val position = ReadAloudVisualPositioner.materializedAnchorPosition(
            anchor = ReadAloudVisualPositioner.PageAnchor(
                chapterIndex = 275,
                pageIndex = 0,
                chapterPosition = 9
            ),
            materializedChapterIndex = 275,
            materializedPageIndex = 1
        )

        assertNull(position)
    }

    @Test
    fun targetAnchorAppliesWhenMaterializedPageMatchesExactly() {
        val position = ReadAloudVisualPositioner.materializedAnchorPosition(
            anchor = ReadAloudVisualPositioner.PageAnchor(
                chapterIndex = 275,
                pageIndex = 0,
                chapterPosition = 9
            ),
            materializedChapterIndex = 275,
            materializedPageIndex = 0
        )

        assertEquals(9, position)
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

    @Test
    fun explicitChapterBoundaryOffsetOverridesInheritedOffset() {
        val offset = ReadAloudVisualPositioner.resolveFollowBaseOffset(
            currentOffset = 613f,
            initialEffectiveOffset = 280,
            targetIsCurrentPage = true
        )

        assertEquals(280f, offset)
    }

    @Test
    fun currentPageTargetDropsInheritedPositiveOffset() {
        val offset = ReadAloudVisualPositioner.resolveFollowBaseOffset(
            currentOffset = 613f,
            initialEffectiveOffset = null,
            targetIsCurrentPage = true
        )

        assertEquals(0f, offset)
    }

    @Test
    fun previousPageTargetKeepsPositiveOffset() {
        val offset = ReadAloudVisualPositioner.resolveFollowBaseOffset(
            currentOffset = 613f,
            initialEffectiveOffset = null,
            targetIsCurrentPage = false
        )

        assertEquals(613f, offset)
    }

    @Test
    fun currentPageTargetKeepsNonPositiveOffset() {
        val offset = ReadAloudVisualPositioner.resolveFollowBaseOffset(
            currentOffset = -320f,
            initialEffectiveOffset = null,
            targetIsCurrentPage = true
        )

        assertEquals(-320f, offset)
    }

    @Test
    fun zeroDistanceScrollFrameIsIgnored() {
        assertFalse(ReadAloudVisualPositioner.shouldHandleScrollFrame(0))
    }

    @Test
    fun nonZeroScrollFrameIsHandled() {
        assertTrue(ReadAloudVisualPositioner.shouldHandleScrollFrame(-12))
    }

    @Test
    fun scrollTraceRecordsFollowInterruption() {
        assertTrue(
            ReadAloudVisualPositioner.shouldTraceScrollState(
                followInterrupted = true,
                pageChanged = false
            )
        )
    }

    @Test
    fun scrollTraceRecordsRenderedPageChange() {
        assertTrue(
            ReadAloudVisualPositioner.shouldTraceScrollState(
                followInterrupted = false,
                pageChanged = true
            )
        )
    }

    @Test
    fun scrollTraceSkipsOrdinaryAnimationFrame() {
        assertFalse(
            ReadAloudVisualPositioner.shouldTraceScrollState(
                followInterrupted = false,
                pageChanged = false
            )
        )
    }
}
