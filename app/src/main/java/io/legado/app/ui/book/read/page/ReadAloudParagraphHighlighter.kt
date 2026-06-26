package io.legado.app.ui.book.read.page

import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextParagraph

internal object ReadAloudParagraphHighlighter {

    fun update(
        paragraph: TextParagraph,
        highlightedPageIndices: Set<Int>,
        getPage: (Int) -> TextPage?
    ): Set<Int> {
        val pageIndices = paragraph.textLines.map { it.textPage.index }.toSet()
        clear(highlightedPageIndices + pageIndices, getPage)
        paragraph.textLines.forEach { it.isReadAloud = true }
        return pageIndices
    }

    fun clear(
        highlightedPageIndices: Set<Int>,
        getPage: (Int) -> TextPage?
    ) {
        highlightedPageIndices.forEach { index ->
            getPage(index)?.removePageAloudSpan()
        }
    }
}
