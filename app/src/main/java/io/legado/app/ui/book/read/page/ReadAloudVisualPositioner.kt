package io.legado.app.ui.book.read.page

import kotlin.math.roundToInt

internal object ReadAloudVisualPositioner {

    fun calculateOffset(
        paragraphTop: Float,
        paragraphBottom: Float,
        visibleTop: Float,
        visibleHeight: Float
    ): Int {
        if (visibleHeight <= 0f || paragraphBottom <= paragraphTop) {
            return 0
        }
        val paragraphHeight = paragraphBottom - paragraphTop
        val targetTop = if (paragraphHeight <= visibleHeight * 0.8f) {
            visibleTop + (visibleHeight - paragraphHeight) / 2f
        } else {
            visibleTop
        }
        return (targetTop - paragraphTop).roundToInt()
    }
}
