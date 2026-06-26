package io.legado.app.ui.book.read.page

import kotlin.math.roundToInt

internal object ReadAloudVisualPositioner {

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
        val safeTop = visibleTop + visibleHeight * 0.1f
        val safeBottom = visibleTop + visibleHeight * 0.9f
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
}
