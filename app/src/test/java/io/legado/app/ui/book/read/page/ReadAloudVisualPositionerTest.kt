package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
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
    fun topParagraphMovesOnlyToMiddleAreaTop() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 100f,
            paragraphBottom = 300f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(100, offset)
    }

    @Test
    fun bottomParagraphMovesOnlyToMiddleAreaBottom() {
        val offset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = 900f,
            paragraphBottom = 1100f,
            visibleTop = 100f,
            visibleHeight = 1000f
        )

        assertEquals(-100, offset)
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
