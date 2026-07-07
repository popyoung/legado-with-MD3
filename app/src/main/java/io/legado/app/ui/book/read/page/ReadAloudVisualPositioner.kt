package io.legado.app.ui.book.read.page

import kotlin.math.roundToInt

internal object ReadAloudVisualPositioner {

    private const val SAFE_EDGE_RATIO = 0.15f

    data class FollowState(
        val pageOffset: Int,
        val readAloudOffset: Int,
        val active: Boolean
    )

    enum class FollowFallback {
        KeepVisualPage,
        RefreshCurrentPage,
        JumpToTargetPage
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

    fun previousPageOffset(
        currentOffset: Float,
        previousPageHeight: Float
    ): Float? {
        if (currentOffset <= 0f || previousPageHeight <= 0f) {
            return null
        }
        return currentOffset - previousPageHeight
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

    fun contentOffset(
        pageOffset: Float,
        readAloudOffset: Float,
        readAloudActive: Boolean
    ): Float {
        return pageOffset + if (readAloudActive) readAloudOffset else 0f
    }

    fun shouldStepFromVisualCenter(
        visualPositionEnabled: Boolean,
        readAloudRunning: Boolean,
        readAloudPositionVisible: Boolean
    ): Boolean {
        return visualPositionEnabled && readAloudRunning && !readAloudPositionVisible
    }

    fun shouldShowVisualCenterIndicator(
        visualPositionEnabled: Boolean,
        readAloudPlaying: Boolean,
        readAloudPositionVisible: Boolean
    ): Boolean {
        return visualPositionEnabled && readAloudPlaying && !readAloudPositionVisible
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

    fun shouldUpdateHighlightWhenFollowPaused(
        readAloudParagraphVisible: Boolean
    ): Boolean {
        return readAloudParagraphVisible
    }

    @Suppress("UNUSED_PARAMETER")
    fun shouldRestoreStoredPositionOnResume(
        readAloudPaused: Boolean,
        visualPageChanged: Boolean
    ): Boolean {
        return readAloudPaused
    }

    fun shouldRestoreWithoutPageJump(
        readAloudParagraphVisible: Boolean
    ): Boolean {
        return readAloudParagraphVisible
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

    fun resetPageState(state: FollowState): FollowState {
        return clearFollowState(state).copy(pageOffset = 0)
    }
}
