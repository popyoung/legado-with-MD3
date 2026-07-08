package io.legado.app.ui.book.read.page

import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextParagraph

internal object ReadAloudParagraphHighlighter {

    class HighlightedPage private constructor(
        private val chapterIndex: Int,
        private val pageIndex: Int,
        private val page: TextPage
    ) {

        constructor(page: TextPage) : this(page.chapterIndex, page.index, page)

        fun clear() {
            page.removePageAloudSpan()
        }

        override fun equals(other: Any?): Boolean {
            return other is HighlightedPage &&
                    chapterIndex == other.chapterIndex &&
                    pageIndex == other.pageIndex
        }

        override fun hashCode(): Int {
            var result = chapterIndex
            result = 31 * result + pageIndex
            return result
        }
    }

    fun update(
        paragraph: TextParagraph,
        highlightedPages: Set<HighlightedPage>
    ): Set<HighlightedPage> {
        val pageKeys = paragraph.textLines.map { HighlightedPage(it.textPage) }.toSet()
        clear(highlightedPages)
        clear(pageKeys)
        paragraph.textLines.forEach { it.isReadAloud = true }
        return pageKeys
    }

    fun clear(
        highlightedPages: Set<HighlightedPage>
    ) {
        highlightedPages.forEach { it.clear() }
    }
}
