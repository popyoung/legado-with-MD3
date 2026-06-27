package io.legado.app.ui.book.read.page

import android.app.Activity
import android.view.MotionEvent
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentTextViewReadAloudStateTest {

    @Test
    fun clearReadAloudVisualFollowKeepsScrollOffset() {
        val activity = Robolectric.buildActivity(FakeActivity::class.java).setup().get()
        val view = ContentTextView(activity, null)
        view.setPrivateField("pageOffset", -420)
        view.setPrivateField("readAloudPageOffset", 120)
        view.setPrivateField("readAloudFollowActive", true)

        view.clearReadAloudVisualFollow()

        assertEquals(-420, view.privateField<Int>("pageOffset"))
        assertEquals(0, view.privateField<Int>("readAloudPageOffset"))
        assertFalse(view.privateField<Boolean>("readAloudFollowActive"))
    }

    private fun ContentTextView.setPrivateField(name: String, value: Any) {
        val target = this
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ContentTextView.privateField(name: String): T {
        return javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(this) as T
        }
    }

    class FakeActivity : Activity(), ContentTextView.CallBack {
        override val headerHeight: Int = 0
        override val imgBgPaddingStart: Int = 0
        override val pageFactory: TextPageFactory = TextPageFactory(FakeDataSource)
        override val pageDelegate: PageDelegate? = null
        override val isScroll: Boolean = true
        override var isSelectingSearchResult: Boolean = false

        override fun upSelectedStart(x: Float, y: Float, top: Float) = Unit
        override fun upSelectedEnd(x: Float, y: Float) = Unit
        override fun onImageLongPress(x: Float, y: Float, src: String) = Unit
        override fun onCancelSelect() = Unit
        override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean = false
        override fun oldClickImg(src: String): Boolean = false
        override fun clickImg(click: String, src: String) = Unit
        override fun onReadAloudVisualFollowInterrupted() = Unit
    }

    object FakeDataSource : DataSource {
        override val currentChapter: TextChapter? = null
        override val nextChapter: TextChapter? = null
        override val prevChapter: TextChapter? = null
        override val isScroll: Boolean = true

        override fun hasNextChapter(): Boolean = false
        override fun hasPrevChapter(): Boolean = false
        override fun upContent(relativePosition: Int, resetPageOffset: Boolean) = Unit
    }
}
