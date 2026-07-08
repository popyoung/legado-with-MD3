package io.legado.app.ui.book.read.page

import android.app.Application
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = ReadAloudParagraphHighlighterTest.TestApplication::class
)
class ReadAloudParagraphHighlighterTest {

    @Test
    fun marksEveryPageTouchedByParagraph() {
        val firstPage = textPage(0)
        val secondPage = textPage(1)
        val firstLine = textLine("first")
        val secondLine = textLine("second")
        firstPage.addLine(firstLine)
        secondPage.addLine(secondLine)

        val highlightedPages = ReadAloudParagraphHighlighter.update(
            paragraph = TextParagraph(1, arrayListOf(firstLine, secondLine)),
            highlightedPages = emptySet()
        )

        assertEquals(2, highlightedPages.size)
        assertTrue(firstLine.isReadAloud)
        assertTrue(secondLine.isReadAloud)
    }

    @Test
    fun clearsPreviouslyHighlightedPagesBeforeMarkingNewParagraph() {
        val oldPage = textPage(0)
        val newPage = textPage(1)
        val oldLine = textLine("old")
        val newLine = textLine("new")
        oldPage.addLine(oldLine)
        newPage.addLine(newLine)
        oldLine.isReadAloud = true

        val highlightedPages = ReadAloudParagraphHighlighter.update(
            paragraph = TextParagraph(2, arrayListOf(newLine)),
            highlightedPages = setOf(ReadAloudParagraphHighlighter.HighlightedPage(oldPage))
        )

        assertEquals(1, highlightedPages.size)
        assertFalse(oldLine.isReadAloud)
        assertTrue(newLine.isReadAloud)
    }

    @Test
    fun clearsPreviousChapterWhenNewParagraphUsesSamePageIndex() {
        val oldPage = textPage(chapterIndex = 212, index = 0)
        val newPage = textPage(chapterIndex = 213, index = 0)
        val oldLine = textLine("old")
        val newLine = textLine("new")
        oldPage.addLine(oldLine)
        newPage.addLine(newLine)
        oldLine.isReadAloud = true

        ReadAloudParagraphHighlighter.update(
            paragraph = TextParagraph(2, arrayListOf(newLine)),
            highlightedPages = setOf(ReadAloudParagraphHighlighter.HighlightedPage(oldPage))
        )

        assertFalse(oldLine.isReadAloud)
        assertTrue(newLine.isReadAloud)
    }

    private fun textPage(index: Int, chapterIndex: Int = 0): TextPage {
        return TextPage(index = index, chapterIndex = chapterIndex, text = "", title = "")
    }

    private fun textLine(text: String): TextLine {
        return TextLine(text = text, paragraphNum = 1)
    }

    class TestApplication : Application() {
        override fun onCreate() {
            injectAsAppCtx()
            super.onCreate()
        }
    }
}
