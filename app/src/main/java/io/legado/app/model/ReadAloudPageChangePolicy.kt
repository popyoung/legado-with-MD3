package io.legado.app.model

internal object ReadAloudPageChangePolicy {

    fun resolveOrigin(
        scopedOrigin: ReadAloudPageChangeOrigin?,
        pendingOrigin: ReadAloudPageChangeOrigin?
    ): ReadAloudPageChangeOrigin {
        return pendingOrigin ?: scopedOrigin ?: ReadAloudPageChangeOrigin.User
    }

    @Suppress("UNUSED_PARAMETER")
    fun shouldRestartPausedServiceFromVisualPage(
        readAloudRunning: Boolean,
        readAloudPaused: Boolean,
        visualChapterCompleted: Boolean,
        visualChapterChanged: Boolean
    ): Boolean {
        return false
    }

    fun shouldRefreshReadViewOnPageChanged(
        fromReadAloud: Boolean
    ): Boolean {
        return !fromReadAloud
    }

    fun shouldRefreshContentDuringChapterMove(
        fromReadAloud: Boolean
    ): Boolean {
        return !fromReadAloud
    }
}
