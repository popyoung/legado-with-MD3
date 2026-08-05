package io.legado.app.service

internal data class ReadAloudPlaybackCursor(
    val chapterIndex: Int,
    val chapterStart: Int,
    val sequence: Long
)
