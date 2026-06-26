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
@Config(application = ReadAloudParagraphHighlighterTest.TestApplication::class)
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
            highlightedPageIndices = emptySet()
        ) { index -> listOf(firstPage, secondPage).getOrNull(index) }

        assertEquals(setOf(0, 1), highlightedPages)
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
            highlightedPageIndices = setOf(0)
        ) { index -> listOf(oldPage, newPage).getOrNull(index) }

        assertEquals(setOf(1), highlightedPages)
        assertFalse(oldLine.isReadAloud)
        assertTrue(newLine.isReadAloud)
    }

    private fun textPage(index: Int): TextPage {
        return TextPage(index = index, text = "", title = "")
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
