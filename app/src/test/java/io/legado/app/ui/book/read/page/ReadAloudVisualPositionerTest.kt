package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudVisualPositionerTest {

    @Test
    fun shortParagraphIsCenteredInVisibleArea() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 900f,
            paragraphBottom = 1100f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(-400, offset)
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
}
