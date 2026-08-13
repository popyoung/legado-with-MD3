package io.legado.app.model

import org.junit.Assert.assertEquals
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

    @Test
    fun asyncRestoreCompletionKeepsItsRestoreToken() {
        val origin = ReadAloudPageChangePolicy.resolveOrigin(
            scopedOrigin = null,
            pendingOrigin = ReadAloudPageChangeOrigin.Restore(41L)
        )

        assertEquals(ReadAloudPageChangeOrigin.Restore(41L), origin)
    }

    @Test
    fun explicitAsyncRestoreOriginWinsOverUnrelatedProgrammaticScope() {
        val origin = ReadAloudPageChangePolicy.resolveOrigin(
            scopedOrigin = ReadAloudPageChangeOrigin.Programmatic,
            pendingOrigin = ReadAloudPageChangeOrigin.Restore(41L)
        )

        assertEquals(ReadAloudPageChangeOrigin.Restore(41L), origin)
    }

    @Test
    fun ordinaryPageChangeDefaultsToUserOrigin() {
        val origin = ReadAloudPageChangePolicy.resolveOrigin(
            scopedOrigin = null,
            pendingOrigin = null
        )

        assertEquals(ReadAloudPageChangeOrigin.User, origin)
    }
}
