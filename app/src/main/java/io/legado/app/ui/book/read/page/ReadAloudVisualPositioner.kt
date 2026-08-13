package io.legado.app.ui.book.read.page

import kotlin.math.roundToInt

internal object ReadAloudVisualPositioner {

    private const val SAFE_EDGE_RATIO = 0.10f

    data class FollowState(
        val pageOffset: Int,
        val readAloudOffset: Int,
        val active: Boolean
    )

    data class PageAnchor(
        val chapterIndex: Int,
        val pageIndex: Int,
        val chapterPosition: Int
    )

    data class InterruptedFollowState(
        val pageShift: Int,
        val state: FollowState
    )

    enum class FollowFallback {
        KeepVisualPage,
        RefreshCurrentPage,
        JumpToTargetPage
    }

    enum class VisualCenterChapterAction {
        KeepCurrent,
        AlignCached,
        Reject
    }

    fun calculateOffset(
        paragraphTop: Float,
        paragraphBottom: Float,
        visibleTop: Float,
        visibleHeight: Float,
        currentOffset: Float = 0f
    ): Int {
        if (visibleHeight <= 0f || paragraphBottom <= paragraphTop) {
            return currentOffset.roundToInt()
        }
        val paragraphHeight = paragraphBottom - paragraphTop
        val safeTop = visibleTop + visibleHeight * SAFE_EDGE_RATIO
        val safeBottom = visibleTop + visibleHeight * (1f - SAFE_EDGE_RATIO)
        if (paragraphHeight > safeBottom - safeTop) {
            return (visibleTop - paragraphTop).roundToInt()
        }
        val currentTop = paragraphTop + currentOffset
        val currentBottom = paragraphBottom + currentOffset
        return when {
            currentTop < visibleTop -> (safeTop - paragraphTop).roundToInt()
            currentBottom >= safeBottom -> (safeTop - paragraphTop).roundToInt()
            else -> currentOffset.roundToInt()
        }
    }

    fun resolveFollowBaseOffset(
        currentOffset: Float,
        initialEffectiveOffset: Int?,
        targetIsCurrentPage: Boolean
    ): Float {
        if (initialEffectiveOffset != null) {
            return initialEffectiveOffset.toFloat()
        }
        return if (targetIsCurrentPage && currentOffset > 0f) {
            0f
        } else {
            currentOffset
        }
    }

    fun relativePageTop(
        currentPageIndex: Int,
        targetPageIndex: Int,
        previousPageHeight: Float?,
        currentPageHeight: Float,
        nextPageHeight: Float?
    ): Float? {
        return when (targetPageIndex - currentPageIndex) {
            -1 -> previousPageHeight?.takeIf { it > 0f }?.let { -it }
            0 -> 0f
            1 -> currentPageHeight.takeIf { it > 0f }
            2 -> {
                val currentHeight = currentPageHeight.takeIf { it > 0f } ?: return null
                val nextHeight = nextPageHeight?.takeIf { it > 0f } ?: return null
                currentHeight + nextHeight
            }
            else -> null
        }
    }

    fun relativePagePosition(
        currentChapterIndex: Int,
        currentPageIndex: Int,
        currentPageSize: Int,
        targetChapterIndex: Int,
        targetPageIndex: Int,
        previousChapterPageSize: Int?
    ): Int? {
        val relativePosition = when (targetChapterIndex) {
            currentChapterIndex -> targetPageIndex - currentPageIndex
            currentChapterIndex + 1 -> {
                if (currentPageSize <= 0) return null
                currentPageSize - currentPageIndex + targetPageIndex
            }
            currentChapterIndex - 1 -> {
                val previousPageSize = previousChapterPageSize?.takeIf { it > 0 } ?: return null
                targetPageIndex - previousPageSize - currentPageIndex
            }
            else -> return null
        }
        return relativePosition.takeIf { it in -1..2 }
    }

    fun previousPageOffset(
        currentOffset: Float,
        previousPageHeight: Float
    ): Float? {
        if (currentOffset <= 0f || previousPageHeight <= 0f) {
            return null
        }
        return currentOffset - previousPageHeight
    }

    fun firstVisibleRelativePage(
        readAloudActive: Boolean,
        currentOffset: Float
    ): Int {
        return if (readAloudActive && currentOffset > 0f) -1 else 0
    }

    fun nextChapterOffset(
        previousEffectiveOffset: Float,
        previousPageHeight: Float
    ): Int? {
        if (previousPageHeight <= 0f) {
            return null
        }
        return (previousEffectiveOffset + previousPageHeight).roundToInt()
    }

    fun chapterBoundaryInitialOffset(
        paragraphTop: Float,
        paragraphBottom: Float,
        visibleTop: Float,
        visibleHeight: Float
    ): Int? {
        if (visibleHeight <= 0f || paragraphBottom <= paragraphTop) {
            return null
        }
        val safeTop = visibleTop + visibleHeight * SAFE_EDGE_RATIO
        return calculateOffset(
            paragraphTop = paragraphTop,
            paragraphBottom = paragraphBottom,
            visibleTop = visibleTop,
            visibleHeight = visibleHeight,
            currentOffset = safeTop - paragraphTop
        )
    }

    fun shouldUseChapterBoundaryInitialOffset(
        targetPageIndex: Int,
        previousChapterLastPageVisible: Boolean
    ): Boolean {
        return targetPageIndex == 0 && previousChapterLastPageVisible
    }

    fun shouldHandleScrollFrame(mOffset: Int): Boolean {
        return mOffset != 0
    }

    fun shouldTraceScrollState(
        followInterrupted: Boolean,
        pageChanged: Boolean
    ): Boolean {
        return followInterrupted || pageChanged
    }

    fun contentOffset(
        pageOffset: Float,
        readAloudOffset: Float,
        readAloudActive: Boolean
    ): Float {
        return pageOffset + if (readAloudActive) readAloudOffset else 0f
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

    fun followFallback(
        followSucceeded: Boolean,
        visualPageMatchesTarget: Boolean
    ): FollowFallback {
        if (followSucceeded) return FollowFallback.KeepVisualPage
        return if (visualPageMatchesTarget) {
            FollowFallback.RefreshCurrentPage
        } else {
            FollowFallback.JumpToTargetPage
        }
    }

    fun shouldSuppressScrollAfterVisualRestore(
        readAloudFollowActive: Boolean,
        nowMillis: Long,
        suppressUntilMillis: Long
    ): Boolean {
        return readAloudFollowActive && nowMillis <= suppressUntilMillis
    }

    fun shouldSyncReadBookPageBeforeManualStepProgress(
        scrollPageAnim: Boolean,
        targetPageChanged: Boolean
    ): Boolean {
        return targetPageChanged && !scrollPageAnim
    }

    fun resolveUserScrollHandoff(
        readAloudFollowActive: Boolean,
        renderBase: PageAnchor,
        readBookChapterIndex: Int,
        readBookPageIndex: Int
    ): PageAnchor? {
        if (!readAloudFollowActive) return null
        return renderBase.takeIf {
            it.chapterIndex != readBookChapterIndex || it.pageIndex != readBookPageIndex
        }
    }

    fun materializedAnchorPosition(
        anchor: PageAnchor?,
        materializedChapterIndex: Int,
        materializedPageIndex: Int
    ): Int? {
        return anchor?.takeIf {
            it.chapterIndex == materializedChapterIndex &&
                    it.pageIndex == materializedPageIndex
        }?.chapterPosition
    }

    fun clearFollowState(state: FollowState): FollowState {
        return state.copy(readAloudOffset = 0, active = false)
    }

    fun interruptFollowByUserScroll(state: FollowState): FollowState {
        return state.copy(
            pageOffset = state.pageOffset + state.readAloudOffset,
            readAloudOffset = 0,
            active = false
        )
    }

    fun interruptFollowByUserScroll(
        state: FollowState,
        previousPageHeight: Float?,
        currentPageHeight: Float,
        nextPageHeight: Float?,
        nextPlusPageHeight: Float?
    ): InterruptedFollowState {
        val materialized = materializeFollowAnchor(
            state = state,
            previousPageHeight = previousPageHeight,
            currentPageHeight = currentPageHeight,
            nextPageHeight = nextPageHeight,
            nextPlusPageHeight = nextPlusPageHeight
        )
        return materialized.copy(
            state = materialized.state.copy(active = false)
        )
    }

    fun materializeFollowAnchor(
        state: FollowState,
        previousPageHeight: Float?,
        currentPageHeight: Float,
        nextPageHeight: Float?,
        nextPlusPageHeight: Float?
    ): InterruptedFollowState {
        var pageShift = 0
        var pageOffset = contentOffset(
            pageOffset = state.pageOffset.toFloat(),
            readAloudOffset = state.readAloudOffset.toFloat(),
            readAloudActive = state.active
        )
        if (pageOffset > 0f && previousPageHeight != null && previousPageHeight > 0f) {
            pageOffset -= previousPageHeight
            pageShift = -1
        } else if (currentPageHeight > 0f) {
            if (pageOffset < -currentPageHeight && nextPageHeight != null && nextPageHeight > 0f) {
                pageOffset += currentPageHeight
                pageShift = 1
                if (
                    pageOffset < -nextPageHeight &&
                    nextPlusPageHeight != null &&
                    nextPlusPageHeight > 0f
                ) {
                    pageOffset += nextPageHeight
                    pageShift = 2
                }
            }
        }
        return InterruptedFollowState(
            pageShift = pageShift,
            state = state.copy(
                pageOffset = pageOffset.roundToInt(),
                readAloudOffset = 0,
                active = state.active
            )
        )
    }

    fun resetPageState(state: FollowState): FollowState {
        return clearFollowState(state).copy(pageOffset = 0)
    }
}
