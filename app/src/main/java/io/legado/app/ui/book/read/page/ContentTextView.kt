package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.ReadAloudViewport
import io.legado.app.ui.book.read.page.entities.TextParagraph
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.ButtonColumn
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.ReviewColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 阅读内容视图
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var selectAble = AppConfig.textSelectAble
    val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = ChapterProvider.visibleRect
    val selectStart = TextPos(0, -1, -1)
    private val selectEnd = TextPos(0, -1, -1)
    var textPage: TextPage = TextPage()
        private set
    var isMainView = false
    var longScreenshot = false
    var reverseStartCursor = false
    var reverseEndCursor = false

    //滚动参数
    private val pageFactory get() = callBack.pageFactory
    private val pageDelegate get() = callBack.pageDelegate
    private var pageOffset = 0
    private var readAloudPageOffset = 0
    private var autoPager: AutoPager? = null
    private var isScroll = false
    private var readAloudFollowActive = false
    private var readAloudVisualCenterIndicator = false
    private var readAloudVisualScrollSuppressedUntil = 0L
    private var readAloudUserScrollInProgress = false
    private val renderRunnable by lazy { Runnable { preRenderPage() } }
    private var lastClickTime = 0L
    private var doubleClick = false

    //绘制图片的paint
    val imagePaint by lazy {
        Paint().apply {
            isAntiAlias = AppConfig.useAntiAlias
        }
    }
    private val readAloudVisualCenterPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getCompatColor(R.color.primaryText)
            alpha = 140
            strokeWidth = 1.5f.dpToPx()
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f.dpToPx(), 6f.dpToPx()), 0f)
        }
    }

    init {
        callBack = activity as CallBack
    }

    /**
     * 设置内容
     */
    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        // 非滑动翻页动画需要同步重绘，不然翻页可能会出现闪烁
        if (isScroll) {
            postInvalidate()
        } else {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMainView) return
        ChapterProvider.upViewSize(w, h)
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        autoPager?.onDraw(canvas)
        if (longScreenshot) {
            canvas.translate(0f, scrollY.toFloat())
        }
        check(!visibleRect.isEmpty) { "visibleRect 为空" }
        canvas.clipRect(visibleRect)
        drawPage(canvas)
        drawReadAloudVisualCenterIndicator(canvas)
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = contentOffset(0)
        if (readAloudFollowActive) {
            drawReadAloudPreviousPage(canvas, relativeOffset)
        }
        textPage.draw(this, canvas, relativeOffset)
        if (!drawContinuousPages()) return
        if (readAloudFollowActive && !callBack.isScroll) {
            drawReadAloudFollowPages(canvas, relativeOffset)
            return
        }
        //滚动翻页
        val textPage1 = relativeDrawPageOrNull(1) ?: return
        relativeOffset += textPage.height
        textPage1.draw(this, canvas, relativeOffset)
        val textPage2 = relativeDrawPageOrNull(2) ?: return
        relativeOffset += textPage1.height
        if (relativeOffset < ChapterProvider.visibleHeight) {
            textPage2.draw(this, canvas, relativeOffset)
        }
    }

    private fun relativeDrawPageOrNull(relativePos: Int): TextPage? {
        return relativeCachedPage(relativePos) ?: when (relativePos) {
            -1 -> if (pageFactory.hasPrev()) pageFactory.prevPage else null
            0 -> textPage
            1 -> if (pageFactory.hasNext()) pageFactory.nextPage else null
            2 -> if (pageFactory.hasNextPlus()) pageFactory.nextPlusPage else null
            else -> null
        }
    }

    private fun relativeDrawPage(relativePos: Int): TextPage {
        return relativeDrawPageOrNull(relativePos) ?: TextPage.emptyTextPage
    }

    private fun relativeCachedPage(relativePos: Int): TextPage? {
        if (relativePos == 0) return textPage
        val textChapter = textPage.getTextChapter()
        val targetPageIndex = textPage.index + relativePos
        if (targetPageIndex in 0 until textChapter.pageSize) {
            return textChapter.getPage(targetPageIndex)
        }
        val targetChapterIndex = if (targetPageIndex < 0) {
            textPage.chapterIndex - 1
        } else {
            textPage.chapterIndex + 1
        }
        val targetChapter = cachedTextChapter(targetChapterIndex) ?: return null
        val adjustedPageIndex = if (targetPageIndex < 0) {
            targetChapter.pageSize + targetPageIndex
        } else {
            targetPageIndex - textChapter.pageSize
        }
        return targetChapter.getPage(adjustedPageIndex)
    }

    private fun cachedTextChapter(chapterIndex: Int): TextChapter? {
        return sequenceOf(
            textPage.getTextChapter(),
            ReadBook.textChapter(-1),
            ReadBook.textChapter(0),
            ReadBook.textChapter(1)
        ).filterNotNull().firstOrNull {
            it.chapter.index == chapterIndex && it.pageSize > 0
        }
    }

    private fun relativeDrawOffset(relativePos: Int): Float {
        if (relativePos < 0) {
            return pageOffset - (relativeDrawPageOrNull(relativePos)?.height ?: 0f)
        }
        var offset = pageOffset.toFloat()
        if (relativePos >= 1) {
            offset += textPage.height
        }
        if (relativePos >= 2) {
            offset += relativeDrawPageOrNull(1)?.height ?: 0f
        }
        return offset
    }

    private fun drawContentOffset(relativePos: Int): Float {
        return ReadAloudVisualPositioner.contentOffset(
            pageOffset = relativeDrawOffset(relativePos),
            readAloudOffset = readAloudPageOffset.toFloat(),
            readAloudActive = readAloudFollowActive
        )
    }

    private fun readAloudVisibleRelativePositions(): IntRange {
        val firstRelativePage = ReadAloudVisualPositioner.firstVisibleRelativePage(
            readAloudActive = readAloudFollowActive,
            currentOffset = drawContentOffset(0)
        )
        return firstRelativePage..2
    }

    private fun drawReadAloudFollowPages(canvas: Canvas, currentOffset: Float) {
        var relativeOffset = currentOffset
        val textPage1 = relativeDrawPageOrNull(1) ?: return
        relativeOffset += textPage.height
        textPage1.draw(this, canvas, relativeOffset)
        val textPage2 = relativeDrawPageOrNull(2) ?: return
        relativeOffset += textPage1.height
        if (relativeOffset < ChapterProvider.visibleHeight) {
            textPage2.draw(this, canvas, relativeOffset)
        }
    }

    private fun drawReadAloudPreviousPage(canvas: Canvas, currentOffset: Float) {
        val previousPage = relativeDrawPageOrNull(-1) ?: return
        val previousOffset = ReadAloudVisualPositioner.previousPageOffset(
            currentOffset = currentOffset,
            previousPageHeight = previousPage.height
        ) ?: return
        previousPage.draw(this, canvas, previousOffset)
    }

    private fun drawReadAloudVisualCenterIndicator(canvas: Canvas) {
        if (!readAloudVisualCenterIndicator) return
        val centerY = ChapterProvider.paddingTop + ChapterProvider.visibleHeight / 2f
        canvas.drawLine(
            visibleRect.left.toFloat(),
            centerY,
            visibleRect.right.toFloat(),
            centerY,
            readAloudVisualCenterPaint
        )
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager?.computeOffset()
    }

    /**
     * 滚动事件
     * pageOffset 向上滚动 减小 向下滚动 增大
     * pageOffset 范围 0 ~ -textPage.height 大于0为上一页，小于-textPage.height为下一页
     * 以内容显示区域顶端为界，pageOffset的绝对值为textPage上方的高度
     * pageOffset + textPage.height 为 textPage 下方的高度
     */
    fun scroll(mOffset: Int) {
        if (!ReadAloudVisualPositioner.shouldHandleScrollFrame(mOffset)) return
        val beforeChapterIndex = textPage.chapterIndex
        val beforePageIndex = textPage.index
        if (ReadAloudVisualPositioner.shouldSuppressScrollAfterVisualRestore(
                readAloudFollowActive = readAloudFollowActive,
                nowMillis = SystemClock.uptimeMillis(),
                suppressUntilMillis = readAloudVisualScrollSuppressedUntil
            )
        ) {
            ReadAloudVisualTrace.record(
                event = "scrollSuppressedAfterRestore",
                detail = "mOffset=$mOffset ${readAloudVisualDebugState()}"
            )
            postInvalidate()
            return
        }
        val followInterrupted = resetReadAloudFollowByUserScroll()
        pageOffset += mOffset
        if (longScreenshot) {
            scrollY += -mOffset
        }
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
            pageDelegate?.abortAnim()
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
            pageDelegate?.abortAnim()
        } else if (pageOffset > 0) {
            if (pageFactory.moveToPrev(true)) {
                pageOffset -= textPage.height.toInt()
            } else {
                pageOffset = 0
                pageDelegate?.abortAnim()
            }
        } else if (pageOffset < -textPage.height) {
            val height = textPage.height
            if (pageFactory.moveToNext(upContent = true)) {
                pageOffset += height.toInt()
            } else {
                pageOffset = -height.toInt()
                pageDelegate?.abortAnim()
            }
        }
        val pageChanged = beforeChapterIndex != textPage.chapterIndex ||
                beforePageIndex != textPage.index
        if (ReadAloudVisualPositioner.shouldTraceScrollState(followInterrupted, pageChanged)) {
            ReadAloudVisualTrace.record(
                event = "scrollState",
                detail = "mOffset=$mOffset interrupted=$followInterrupted from=$beforeChapterIndex/$beforePageIndex ${readAloudVisualDebugState()}"
            )
        }
        val userScrollStarted = !readAloudUserScrollInProgress
        readAloudUserScrollInProgress = true
        callBack.onReadAloudUserScroll(
            viewport = readAloudViewport(),
            started = userScrollStarted
        )
        postInvalidate()
    }

    fun submitRenderTask() {
        renderThread.submit(renderRunnable)
    }

    private fun preRenderPage() {
        val view = this
        var invalidate = false
        pageFactory.run {
            if (hasPrev() && prevPage.render(view)) {
                invalidate = true
            }
            if (curPage.render(view)) {
                invalidate = true
            }
            if (hasNext() && nextPage.render(view) && callBack.isScroll) {
                invalidate = true
            }
            if (hasNextPlus() && nextPlusPage.render(view) && callBack.isScroll
                && contentOffset(2) < ChapterProvider.visibleHeight
            ) {
                invalidate = true
            }
            if (invalidate) {
                postInvalidate()
                pageDelegate?.postInvalidate()
            }
        }
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        applyReadAloudFollowState(
            ReadAloudVisualPositioner.resetPageState(readAloudFollowState())
        )
    }

    fun clearReadAloudVisualFollow() {
        val state = readAloudFollowState()
        val cleared = ReadAloudVisualPositioner.clearFollowState(state)
        if (state == cleared) return
        applyReadAloudFollowState(cleared)
        ReadAloudVisualTrace.record("clearFollow", readAloudVisualDebugState())
        postInvalidate()
    }

    fun settleReadAloudVisualFollow(reason: String) {
        materializeReadAloudFollow(
            event = reason,
            syncVisualAnchor = false
        )
        postInvalidate()
    }

    fun suppressReadAloudVisualScrollAfterRestore() {
        readAloudVisualScrollSuppressedUntil = max(
            readAloudVisualScrollSuppressedUntil,
            SystemClock.uptimeMillis() + READ_ALOUD_VISUAL_RESTORE_SCROLL_SUPPRESS_MS
        )
    }

    private fun readAloudFollowState(): ReadAloudVisualPositioner.FollowState {
        return ReadAloudVisualPositioner.FollowState(
            pageOffset = pageOffset,
            readAloudOffset = readAloudPageOffset,
            active = readAloudFollowActive
        )
    }

    private fun applyReadAloudFollowState(state: ReadAloudVisualPositioner.FollowState) {
        pageOffset = state.pageOffset
        readAloudPageOffset = state.readAloudOffset
        readAloudFollowActive = state.active
    }

    fun readAloudNextChapterOffset(): Int? {
        return ReadAloudVisualPositioner.nextChapterOffset(
            previousEffectiveOffset = contentOffset(0),
            previousPageHeight = textPage.height
        )
    }

    fun followReadAloudParagraph(
        paragraph: TextParagraph,
        initialEffectiveOffset: Int? = null
    ): Boolean {
        val firstLine = paragraph.textLines.firstOrNull() ?: return false
        val lastLine = paragraph.textLines.lastOrNull() ?: return false
        val paragraphTop = lineTopInVisualStream(firstLine) ?: run {
            ReadAloudVisualTrace.record(
                event = "followFail",
                detail = "reason=topOutsideVisualStream target=${firstLine.textPage.chapterIndex}/${firstLine.textPage.index} ${readAloudVisualDebugState()}"
            )
            return false
        }
        val paragraphBottom = lineBottomInVisualStream(lastLine) ?: run {
            ReadAloudVisualTrace.record(
                event = "followFail",
                detail = "reason=bottomOutsideVisualStream target=${lastLine.textPage.chapterIndex}/${lastLine.textPage.index} ${readAloudVisualDebugState()}"
            )
            return false
        }
        val currentEffectiveOffset = contentOffset(0)
        val targetIsCurrentPage = firstLine.textPage.chapterIndex == textPage.chapterIndex &&
                firstLine.textPage.index == textPage.index
        // Positive placement can belong to the previous page; do not carry it onto this page.
        val baseOffset = ReadAloudVisualPositioner.resolveFollowBaseOffset(
            currentOffset = currentEffectiveOffset,
            initialEffectiveOffset = initialEffectiveOffset,
            targetIsCurrentPage = targetIsCurrentPage
        )
        val effectiveOffset = ReadAloudVisualPositioner.calculateOffset(
            paragraphTop = paragraphTop,
            paragraphBottom = paragraphBottom,
            visibleTop = ChapterProvider.paddingTop.toFloat(),
            visibleHeight = ChapterProvider.visibleHeight.toFloat(),
            currentOffset = baseOffset
        )
        readAloudPageOffset = effectiveOffset - pageOffset
        readAloudFollowActive = true
        val pageShift = materializeReadAloudFollowAnchor(
            ReadAloudVisualPositioner.PageAnchor(
                chapterIndex = firstLine.textPage.chapterIndex,
                pageIndex = firstLine.textPage.index,
                chapterPosition = paragraph.chapterPosition
            )
        )
        ReadAloudVisualTrace.record(
            event = "follow",
            detail = "target=${firstLine.textPage.chapterIndex}/${firstLine.textPage.index} paragraph=${paragraph.chapterPosition}-${paragraph.chapterIndices.last} top=$paragraphTop bottom=$paragraphBottom initial=$initialEffectiveOffset base=$baseOffset effective=$effectiveOffset pageShift=$pageShift ${readAloudVisualDebugState()}"
        )
        postInvalidate()
        return true
    }

    private fun lineTopInVisualStream(line: TextLine): Float? {
        return pageTopInVisualStream(line.textPage)?.plus(line.lineTop)
    }

    private fun lineBottomInVisualStream(line: TextLine): Float? {
        return pageTopInVisualStream(line.textPage)?.plus(line.lineBottom)
    }

    private fun pageTopInVisualStream(targetPage: TextPage): Float? {
        val relativePos = ReadAloudVisualPositioner.relativePagePosition(
            currentChapterIndex = textPage.chapterIndex,
            currentPageIndex = textPage.index,
            currentPageSize = textPage.pageSize,
            targetChapterIndex = targetPage.chapterIndex,
            targetPageIndex = targetPage.index,
            previousChapterPageSize = cachedTextChapter(textPage.chapterIndex - 1)?.pageSize
        ) ?: return null
        val visualPage = relativeCachedPage(relativePos) ?: return null
        if (visualPage.chapterIndex != targetPage.chapterIndex ||
            visualPage.index != targetPage.index
        ) {
            return null
        }
        return when (relativePos) {
            -1 -> -visualPage.height
            0 -> 0f
            1 -> textPage.height
            2 -> textPage.height + (relativeCachedPage(1)?.height ?: return null)
            else -> null
        }
    }

    private fun drawContinuousPages(): Boolean {
        return callBack.isScroll || readAloudFollowActive
    }

    private fun resetReadAloudFollowByUserScroll(): Boolean {
        val hadFollowState = readAloudFollowActive || readAloudPageOffset != 0
        materializeReadAloudFollow(
            event = "interruptFollowByScroll",
            syncVisualAnchor = true
        )
        return hadFollowState
    }

    private fun materializeReadAloudFollow(
        event: String,
        syncVisualAnchor: Boolean
    ) {
        if (!readAloudFollowActive && readAloudPageOffset == 0) return
        val followWasActive = readAloudFollowActive
        val visualAnchor = if (syncVisualAnchor) readAloudVisibleAnchor() else null
        val previousPage = relativeDrawPageOrNull(-1)
        val nextPage = relativeDrawPageOrNull(1)
        val nextPlusPage = relativeDrawPageOrNull(2)
        val interruption = ReadAloudVisualPositioner.interruptFollowByUserScroll(
            state = readAloudFollowState(),
            previousPageHeight = previousPage?.height,
            currentPageHeight = textPage.height,
            nextPageHeight = nextPage?.height,
            nextPlusPageHeight = nextPlusPage?.height
        )
        applyReadAloudMaterializedFollow(interruption, previousPage, nextPage, nextPlusPage)
        if (syncVisualAnchor) {
            syncReadBookRenderBaseBeforeUserScroll(
                followWasActive = followWasActive,
                renderBase = readAloudRenderBaseAnchor(),
                visualAnchor = visualAnchor
            )
        }
        ReadAloudVisualTrace.record(
            event,
            "pageShift=${interruption.pageShift} ${readAloudVisualDebugState()}"
        )
    }

    private fun readAloudVisibleAnchor(): ReadAloudVisualPositioner.PageAnchor? {
        val (page, line) = getReadAloudPos() ?: return null
        return ReadAloudVisualPositioner.PageAnchor(
            chapterIndex = page.chapterIndex,
            pageIndex = page.index,
            chapterPosition = line.chapterPosition
        )
    }

    private fun readAloudRenderBaseAnchor(): ReadAloudVisualPositioner.PageAnchor? {
        val chapterPosition = textPage.lines.firstOrNull()?.chapterPosition ?: return null
        return ReadAloudVisualPositioner.PageAnchor(
            chapterIndex = textPage.chapterIndex,
            pageIndex = textPage.index,
            chapterPosition = chapterPosition
        )
    }

    fun readAloudViewport(): ReadAloudViewport? {
        val anchor = readAloudVisibleAnchor() ?: return null
        val renderBase = readAloudRenderBaseAnchor() ?: anchor
        return ReadAloudViewport(
            chapterIndex = anchor.chapterIndex,
            pageIndex = anchor.pageIndex,
            chapterPosition = anchor.chapterPosition,
            renderChapterIndex = renderBase.chapterIndex,
            renderPageIndex = renderBase.pageIndex,
            effectiveOffset = contentOffset(0).toInt()
        )
    }

    fun finishReadAloudUserScroll(): ReadAloudViewport? {
        readAloudUserScrollInProgress = false
        return readAloudViewport()
    }

    private fun syncReadBookRenderBaseBeforeUserScroll(
        followWasActive: Boolean,
        renderBase: ReadAloudVisualPositioner.PageAnchor?,
        visualAnchor: ReadAloudVisualPositioner.PageAnchor?
    ) {
        renderBase ?: return
        val target = ReadAloudVisualPositioner.resolveUserScrollHandoff(
            readAloudFollowActive = followWasActive,
            renderBase = renderBase,
            readBookChapterIndex = ReadBook.durChapterIndex,
            readBookPageIndex = ReadBook.durPageIndex
        ) ?: return
        val anchorPage = cachedTextChapter(target.chapterIndex)
            ?.getPage(target.pageIndex)
            ?: return
        updateReadBookVisualAnchor(anchorPage, target.chapterPosition)
        ReadAloudVisualTrace.record(
            event = "syncRenderBaseBeforeScroll",
            detail = "render=${target.chapterIndex}/${target.pageIndex}/${target.chapterPosition} visible=$visualAnchor ${readAloudVisualDebugState()}"
        )
    }

    private fun materializeReadAloudFollowAnchor(
        targetAnchor: ReadAloudVisualPositioner.PageAnchor
    ): Int {
        // Keep chapter-boundary top padding from being folded back into the previous chapter.
        val previousPage = textPage.getTextChapter().getPage(textPage.index - 1)
        val nextPage = relativeDrawPageOrNull(1)
        val nextPlusPage = relativeDrawPageOrNull(2)
        val materialized = ReadAloudVisualPositioner.materializeFollowAnchor(
            state = readAloudFollowState(),
            previousPageHeight = previousPage?.height,
            currentPageHeight = textPage.height,
            nextPageHeight = nextPage?.height,
            nextPlusPageHeight = nextPlusPage?.height
        )
        applyReadAloudMaterializedFollow(
            interruption = materialized,
            previousPage = previousPage,
            nextPage = nextPage,
            nextPlusPage = nextPlusPage,
            targetAnchor = targetAnchor
        )
        return materialized.pageShift
    }

    private fun applyReadAloudMaterializedFollow(
        interruption: ReadAloudVisualPositioner.InterruptedFollowState,
        previousPage: TextPage?,
        nextPage: TextPage?,
        nextPlusPage: TextPage?,
        targetAnchor: ReadAloudVisualPositioner.PageAnchor? = null
    ) {
        when (interruption.pageShift) {
            -1 -> previousPage
            1 -> nextPage
            2 -> nextPlusPage
            else -> null
        }?.let {
            val anchor = targetAnchor
            val targetChapterPosition = ReadAloudVisualPositioner.materializedAnchorPosition(
                anchor = anchor,
                materializedChapterIndex = it.chapterIndex,
                materializedPageIndex = it.index
            )
            if (targetChapterPosition != null && anchor != null) {
                updateReadBookVisualAnchor(it, targetChapterPosition)
                ReadAloudVisualTrace.record(
                    event = "materializedAnchor",
                    detail = "action=apply target=${anchor.chapterIndex}/${anchor.pageIndex}/${anchor.chapterPosition} materialized=${it.chapterIndex}/${it.index}"
                )
            } else if (anchor != null) {
                ReadAloudVisualTrace.record(
                    event = "materializedAnchor",
                    detail = "action=skip target=${anchor.chapterIndex}/${anchor.pageIndex}/${anchor.chapterPosition} materialized=${it.chapterIndex}/${it.index}"
                )
            }
            textPage = it
        }
        applyReadAloudFollowState(interruption.state)
    }

    private fun updateReadBookVisualAnchor(page: TextPage, chapterPosition: Int) {
        val targetChapter = page.getTextChapter()
        ReadBook.withReadAloudPageChange {
            if (ReadBook.durChapterIndex != page.chapterIndex ||
                ReadBook.curTextChapter !== targetChapter
            ) {
                ReadBook.alignToReadAloudChapter(
                    textChapter = targetChapter,
                    chapterPos = chapterPosition
                )
            }
            ReadBook.setPageIndex(page.index, chapterPosition)
        }
    }

    fun setReadAloudVisualCenterIndicator(show: Boolean): Boolean {
        if (readAloudVisualCenterIndicator == show) return false
        readAloudVisualCenterIndicator = show
        ReadAloudVisualTrace.record("centerIndicator", "show=$show ${readAloudVisualDebugState()}")
        postInvalidate()
        return true
    }

    fun readAloudVisualDebugState(): String {
        return "textPage=${textPage.chapterIndex}/${textPage.index} pageSize=${textPage.pageSize} dur=${ReadBook.durChapterIndex}/${ReadBook.durPageIndex}/${ReadBook.durChapterPos} pageOffset=$pageOffset readAloudOffset=$readAloudPageOffset effective=${contentOffset(0)} active=$readAloudFollowActive indicator=$readAloudVisualCenterIndicator content-1=${drawContentOffset(-1)} content0=${drawContentOffset(0)} content1=${drawContentOffset(1)} content2=${drawContentOffset(2)}"
    }

    /**
     * 长按
     */
    fun longPress(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touch(x, y) { _, textPos, _, _, column ->
            when (column) {
                is ImageColumn -> callBack.onImageLongPress(x, y, column.src)
                is TextColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
                is TextHtmlColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
            }
        }
    }

    /**
     * 单击
     * @return true:已处理, false:未处理
     */
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun click(x: Float, y: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val debounceClick = currentTime - lastClickTime < 300L //300毫秒防抖和双击
        lastClickTime = currentTime
        doubleClick = if (debounceClick) {
            !doubleClick
        } else {
            false
        }
        var handled = false
        touch(x, y) { _, textPos, textPage, textLine, column ->
            when (column) {
                is ButtonColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ReviewColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ImageColumn -> when (AppConfig.clickImgWay) {
                    "1" -> { //预览图片
                        activity?.showDialogFragment(PhotoDialog(column.src, isBook = true))
                        handled = true
                    }
                    "2" -> { //兼容处理
                        if (!debounceClick) {
                            if (ReadBook.book?.isOnLineTxt == true) {
                                val click = column.click
                                val src = column.src
                                if (!click.isNullOrBlank()) {
                                    callBack.clickImg(click, src)
                                    handled = true
                                } else {
                                    handled = callBack.oldClickImg(src)
                                }
                            }
                        }
                    }
                    "3" -> { //关闭
                        handled = false
                    }
                    "4" -> { //双击
                        if (doubleClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        } else {
                            handled = true
                        }
                    }
                    else -> { //默认点击
                        if (!debounceClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        }
                    }
                }
                is TextHtmlColumn -> {
                    column.linkUrl?.let {
                        activity?.startActivity<OpenUrlConfirmActivity> {
                            putExtra("uri", it)
                        }
                        handled = true
                    }
                }
            }
        }
        return handled
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touchRough(x, y) { _, textPos, _, _, column ->
            if (column is TextBaseColumn) {
                column.selected = true
                select(textPos)
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (selectStart.compare(textPos) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectEnd) <= 0) {
                selectStartMoveIndex(textPos)
            } else {
                touchRough(x - 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectEnd) > 0) {
                        reverseStartCursor = true
                        reverseEndCursor = false
                        selectEnd.columnIndex++
                        selectStartMoveIndex(selectEnd)
                        selectEndMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (textPos.compare(selectEnd) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectStart) >= 0) {
                selectEndMoveIndex(textPos)
            } else {
                touchRough(x + 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectStart) < 0) {
                        reverseEndCursor = true
                        reverseStartCursor = false
                        selectStart.columnIndex--
                        selectEndMoveIndex(selectStart)
                        selectStartMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * @param touched 回调
     */
    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = contentOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                if (textLine.isTouch(x, y, relativeOffset)) {
                    for ((charIndex, textColumn) in textLine.columns.withIndex()) {
                        if (textColumn.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * 文本选择专用
     * @param touched 回调
     */
    private fun touchRough(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = contentOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for (lineIndex in textPage.lines.indices) {
                val textLine = textPage.getLine(lineIndex)
                if (textLine.isTouchY(y, relativeOffset)) {
                    if (textPage.doublePage) {
                        val halfWidth = width / 2
                        if (textLine.isLeftLine && x > halfWidth) {
                            continue
                        }
                        if (!textLine.isLeftLine && x < halfWidth) {
                            continue
                        }
                    }
                    val columns = textLine.columns
                    for (charIndex in columns.indices) {
                        val textColumn = columns[charIndex]
                        if (textColumn.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    val isLast = columns.first().start < x
                    val charIndex = if (isLast) columns.lastIndex + 1 else -1
                    val textColumn = if (isLast) columns.last() else columns.first()
                    touched.invoke(
                        relativeOffset,
                        TextPos(relativePos, lineIndex, charIndex),
                        textPage, textLine, textColumn
                    )
                    return
                }
            }
        }
    }

    fun getCurVisiblePage(): TextPage {
        val visiblePage = TextPage()
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = contentOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    visiblePage.addLine(visibleLine)
                }
            }
        }
        return visiblePage
    }

    fun getReadAloudPos(): Pair<TextPage, TextLine>? {
        var relativeOffset: Float
        for (relativePos in readAloudVisibleRelativePositions()) {
            relativeOffset = contentOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    return textPage to visibleLine
                }
            }
        }
        return null
    }

    fun getReadAloudCenterPos(): Pair<TextPage, TextLine>? {
        val centerY = ChapterProvider.paddingTop + ChapterProvider.visibleHeight / 2f
        var nearestLine: Pair<TextPage, TextLine>? = null
        var nearestDistance = Float.MAX_VALUE
        var relativeOffset: Float
        for (relativePos in readAloudVisibleRelativePositions()) {
            relativeOffset = drawContentOffset(relativePos)
            if (relativePos > 0) {
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativeDrawPage(relativePos)
            for (textLine in textPage.lines) {
                if (!textLine.isVisible(relativeOffset)) continue
                val top = textLine.lineTop + relativeOffset
                val bottom = textLine.lineBottom + relativeOffset
                val visibleLine = textLine.copy().apply {
                    lineTop = top
                    lineBottom = bottom
                }
                if (centerY > top && centerY < bottom) {
                    return textPage to visibleLine
                }
                val distance = when {
                    centerY < top -> top - centerY
                    else -> centerY - bottom
                }
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestLine = textPage to visibleLine
                }
            }
        }
        return nearestLine ?: getReadAloudPos()
    }

    fun containsVisibleChapterPosition(chapterIndex: Int, chapterPosition: Int): Boolean {
        var relativeOffset: Float
        for (relativePos in readAloudVisibleRelativePositions()) {
            relativeOffset = drawContentOffset(relativePos)
            if (relativePos > 0) {
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativeDrawPage(relativePos)
            if (textPage.chapterIndex != chapterIndex) continue
            for (textLine in textPage.lines) {
                if (textLine.isVisible(relativeOffset) && chapterPosition in textLine.chapterIndices) {
                    return true
                }
            }
        }
        return false
    }

    fun containsVisibleChapterLastPage(chapterIndex: Int): Boolean {
        var relativeOffset: Float
        for (relativePos in readAloudVisibleRelativePositions()) {
            relativeOffset = drawContentOffset(relativePos)
            if (relativePos > 0) {
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativeDrawPage(relativePos)
            if (textPage.chapterIndex != chapterIndex || textPage.index != textPage.pageSize - 1) {
                continue
            }
            if (textPage.lines.any { it.isVisible(relativeOffset) }) {
                return true
            }
        }
        return false
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectStart.relativePagePos = relativePagePos
        selectStart.lineIndex = lineIndex
        selectStart.columnIndex = max(0, charIndex)
        val textLine = relativePage(relativePagePos).getLine(lineIndex)
        val textColumn = textLine.getColumn(charIndex)
        upSelectedStart(
            if (charIndex < textLine.columns.size) textColumn.start else textColumn.end,
            textLine.lineBottom + relativeOffset(relativePagePos),
            textLine.lineTop + relativeOffset(relativePagePos)
        )
        upSelectChars()
    }

    fun selectStartMoveIndex(textPos: TextPos) = textPos.run {
        selectStartMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(
        relativePage: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectEnd.relativePagePos = relativePage
        selectEnd.lineIndex = lineIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        selectEnd.columnIndex = min(charIndex, textLine.columns.lastIndex)
        val textColumn = textLine.getColumn(charIndex)
        upSelectedEnd(
            if (charIndex > -1) textColumn.end else textColumn.start,
            textLine.lineBottom + relativeOffset(relativePage)
        )
        upSelectChars()
    }

    fun selectEndMoveIndex(textPos: TextPos) = textPos.run {
        selectEndMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    private fun upSelectChars() {
        if (!selectStart.isSelected() && !selectEnd.isSelected()) {
            return
        }
        val last = if (callBack.isScroll) 2 else 0
        val textPos = TextPos(0, 0, 0)
        for (relativePos in 0..last) {
            textPos.relativePagePos = relativePos
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                textPos.lineIndex = lineIndex
                for ((charIndex, column) in textLine.columns.withIndex()) {
                    textPos.columnIndex = charIndex
                    if (column is TextBaseColumn) {
                        val compareStart = textPos.compare(selectStart)
                        val compareEnd = textPos.compare(selectEnd)
                        column.selected = compareStart >= 0 && compareEnd <= 0
                        column.isSearchResult =
                            column.selected && callBack.isSelectingSearchResult
                        if (column.isSearchResult) {
                            textPage.searchResult.add(column)
                        }
                    }
                }
            }
        }
        postInvalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) {
        callBack.run {
            upSelectedStart(x + imgBgPaddingStart, y + headerHeight, top + headerHeight)
        }
    }

    private fun upSelectedEnd(x: Float, y: Float) {
        callBack.run {
            upSelectedEnd(x + imgBgPaddingStart, y + headerHeight)
        }
    }

    fun resetReverseCursor() {
        reverseStartCursor = false
        reverseEndCursor = false
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            val textPage = relativePage(relativePos)
            textPage.lines.forEach { textLine ->
                textLine.columns.forEach {
                    if (it is TextBaseColumn) {
                        it.selected = false
                        if (clearSearchResult) {
                            it.isSearchResult = false
                            textPage.searchResult.remove(it)
                        }
                    }
                }
            }
        }
        selectStart.reset()
        selectEnd.reset()
        postInvalidate()
        callBack.onCancelSelect()
    }

    fun getSelectedText(): String {
        val textPos = TextPos(0, 0, 0)
        val builder = StringBuilder()
        for (relativePos in selectStart.relativePagePos..selectEnd.relativePagePos) {
            val textPage = relativePage(relativePos)
            textPos.relativePagePos = relativePos
            textPage.lines.forEachIndexed { lineIndex, textLine ->
                textPos.lineIndex = lineIndex
                textLine.columns.forEachIndexed { charIndex, column ->
                    textPos.columnIndex = charIndex
                    val compareStart = textPos.compare(selectStart)
                    val compareEnd = textPos.compare(selectEnd)
                    if (column is TextBaseColumn) {
                        when {
                            compareStart == -1 -> if (
                                selectStart.columnIndex == textLine.columns.size
                                && charIndex == textLine.columns.lastIndex
                            ) {
                                builder.append("\n")
                            }

                            compareEnd == 1 -> if (selectEnd.columnIndex == -1 && charIndex == 0) {
                                builder.append("\n")
                            }

                            compareStart >= 0 && compareEnd <= 0 -> {
                                builder.append(column.charData)
                                if (
                                    textLine.isParagraphEnd
                                    && charIndex == textLine.columns.lastIndex
                                    && compareEnd != 0
                                ) {
                                    builder.append("\n")
                                }
                            }
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart.relativePagePos)
        page.getTextChapter().let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getPosByLineColumn(selectStart.lineIndex, selectStart.columnIndex)
                    chapterName = chapter.title
                    bookText = getSelectedText()
                }
            }
        }
        return null
    }

    private fun relativeOffset(relativePos: Int): Float {
        return relativeDrawOffset(relativePos)
    }

    private fun contentOffset(relativePos: Int): Float {
        return ReadAloudVisualPositioner.contentOffset(
            pageOffset = relativeOffset(relativePos),
            readAloudOffset = readAloudPageOffset.toFloat(),
            readAloudActive = readAloudFollowActive
        )
    }

    fun relativePage(relativePos: Int): TextPage {
        return relativeDrawPage(relativePos)
    }

    fun setAutoPager(autoPager: AutoPager?) {
        this.autoPager = autoPager
    }

    fun setIsScroll(value: Boolean) {
        isScroll = value
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return callBack.isScroll && pageFactory.hasNext()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longScreenshot = true
                scrollY = 0
            }

            MotionEvent.ACTION_UP -> {
                longScreenshot = false
                scrollY = 0
            }
        }
        return callBack.onLongScreenshotTouchEvent(event)
    }

    companion object {
        private val renderThread by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "TextPageRender")
            }
        }
        private const val READ_ALOUD_VISUAL_RESTORE_SCROLL_SUPPRESS_MS = 300L
        private val cursorWidth = 24.dpToPx()
    }

    interface CallBack {
        val headerHeight: Int
        val imgBgPaddingStart: Int
        val pageFactory: TextPageFactory
        val pageDelegate: PageDelegate?
        val isScroll: Boolean
        var isSelectingSearchResult: Boolean
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onImageLongPress(x: Float, y: Float, src: String)
        fun onCancelSelect()
        fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean
        fun oldClickImg(src: String): Boolean
        fun clickImg(click: String, src: String)
        fun onReadAloudUserScroll(viewport: ReadAloudViewport?, started: Boolean)
    }
}
