package io.legado.app.model

import org.junit.Assert.assertFalse
import org.junit.Test

class ReadAloudPageChangePolicyTest {

    @Test
    fun pausedPageChangeDoesNotRestartReadAloudFromVisualPage() {
        val shouldRestart = ReadAloudPageChangePolicy.shouldRestartPausedServiceFromVisualPage(
            readAloudRunning = true,
            readAloudPaused = true,
            visualChapterCompleted = true,
            visualChapterChanged = false
        )

        assertFalse(shouldRestart)
    }

    @Test
    fun pausedCrossChapterPageChangeDoesNotRestartReadAloudFromVisualPage() {
        val shouldRestart = ReadAloudPageChangePolicy.shouldRestartPausedServiceFromVisualPage(
            readAloudRunning = true,
            readAloudPaused = true,
            visualChapterCompleted = true,
            visualChapterChanged = true
        )

        assertFalse(shouldRestart)
    }
}
