package io.legado.app.model

internal object ReadAloudPageChangePolicy {

    @Suppress("UNUSED_PARAMETER")
    fun shouldRestartPausedServiceFromVisualPage(
        readAloudRunning: Boolean,
        readAloudPaused: Boolean,
        visualChapterCompleted: Boolean,
        visualChapterChanged: Boolean
    ): Boolean {
        return false
    }
}
