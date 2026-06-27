package io.legado.app.ui.book.read.page

import kotlin.math.roundToInt

internal object ReadAloudVisualPositioner {

    private const val SAFE_EDGE_RATIO = 0.15f

    data class FollowState(
        val pageOffset: Int,
        val readAloudOffset: Int,
        val active: Boolean
    )

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
            currentTop >= safeTop && currentBottom <= safeBottom -> currentOffset.roundToInt()
            currentTop < safeTop -> (safeTop - paragraphTop).roundToInt()
            else -> (safeBottom - paragraphBottom).roundToInt()
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

    @Suppress("UNUSED_PARAMETER")
    fun shouldRestoreStoredPositionOnResume(
        readAloudPaused: Boolean,
        visualPageChanged: Boolean
    ): Boolean {
        return readAloudPaused
    }

    fun clearFollowState(state: FollowState): FollowState {
        return state.copy(readAloudOffset = 0, active = false)
    }

    fun resetPageState(state: FollowState): FollowState {
        return clearFollowState(state).copy(pageOffset = 0)
    }
}
