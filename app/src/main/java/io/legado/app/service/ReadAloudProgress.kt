package io.legado.app.service

internal object ReadAloudProgress {

    data class Position(
        val readAloudNumber: Int,
        val paragraphStartPos: Int,
        val nowSpeak: Int,
        val pageIndex: Int
    )

    fun fromRangeStart(
        readAloudNumber: Int,
        paragraphStartPos: Int,
        rangeStart: Int,
        nowSpeak: Int,
        pageIndex: Int
    ): Position {
        val safeRangeStart = rangeStart.coerceAtLeast(0)
        return Position(
            readAloudNumber = readAloudNumber.coerceAtLeast(0) + safeRangeStart,
            paragraphStartPos = paragraphStartPos.coerceAtLeast(0) + safeRangeStart,
            nowSpeak = nowSpeak.coerceAtLeast(0),
            pageIndex = pageIndex.coerceAtLeast(0)
        )
    }
}
