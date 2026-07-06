package io.legado.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun readAloudPageChangeDoesNotRefreshReadViewBeforeProgress() {
        val shouldRefresh = ReadAloudPageChangePolicy.shouldRefreshReadViewOnPageChanged(
            fromReadAloud = true
        )

        assertFalse(shouldRefresh)
    }

    @Test
    fun visualPageChangeRefreshesReadView() {
        val shouldRefresh = ReadAloudPageChangePolicy.shouldRefreshReadViewOnPageChanged(
            fromReadAloud = false
        )

        assertTrue(shouldRefresh)
    }

    @Test
    fun readAloudChapterMoveDoesNotRefreshContentBeforeProgress() {
        val shouldRefresh = ReadAloudPageChangePolicy.shouldRefreshContentDuringChapterMove(
            fromReadAloud = true
        )

        assertFalse(shouldRefresh)
    }

    @Test
    fun visualChapterMoveRefreshesContent() {
        val shouldRefresh = ReadAloudPageChangePolicy.shouldRefreshContentDuringChapterMove(
            fromReadAloud = false
        )

        assertTrue(shouldRefresh)
    }
}
