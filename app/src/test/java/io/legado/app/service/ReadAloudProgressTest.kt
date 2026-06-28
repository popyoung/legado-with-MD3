package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudProgressTest {

    @Test
    fun rangeStartKeepsResumePositionInsideCurrentParagraph() {
        val position = ReadAloudProgress.fromRangeStart(
            readAloudNumber = 1200,
            paragraphStartPos = 80,
            rangeStart = 35,
            nowSpeak = 4,
            pageIndex = 7
        )

        assertEquals(1235, position.readAloudNumber)
        assertEquals(115, position.paragraphStartPos)
        assertEquals(4, position.nowSpeak)
        assertEquals(7, position.pageIndex)
    }

    @Test
    fun negativeRangeStartDoesNotMoveResumePositionBackwards() {
        val position = ReadAloudProgress.fromRangeStart(
            readAloudNumber = 1200,
            paragraphStartPos = 80,
            rangeStart = -20,
            nowSpeak = 4,
            pageIndex = 7
        )

        assertEquals(1200, position.readAloudNumber)
        assertEquals(80, position.paragraphStartPos)
    }
}
