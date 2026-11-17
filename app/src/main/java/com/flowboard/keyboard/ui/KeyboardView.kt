package com.flowboard.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction
import com.flowboard.keyboard.gesture.FingerRole
import com.flowboard.keyboard.gesture.GestureEngine
import com.flowboard.keyboard.gesture.ZoneLayout
import com.flowboard.keyboard.settings.FlowBoardPreferences
import com.flowboard.keyboard.steno.ChordPrediction
import com.flowboard.keyboard.steno.Fsl
import com.flowboard.keyboard.steno.StenoSession
import com.flowboard.keyboard.steno.Translation

/**
 * The stenographic keyboard surface.
 *
 * The pads no longer show candidate words. They show the **fixed FSL-1 alphabet** — onset families
 * on the left, nucleus families (plus the bare `∅` marker) on the right — because a chord whose
 * label changed with prediction would be impossible to learn
 * (`docs/flowboard_language_design.md`, invariant I6).
 *
 * Above the pads: the live sketch, the ordered dictionary candidates (tap to commit one), and
 * prediction rings around likely next chords.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onChord(chord: Chord)
        fun onCommand(command: Command)
        fun onSelectCandidate(index: Int)
    }

    enum class Command { BACKSPACE, SPACE, COMPLETE, UNDO, ALTERNATIVES }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private var sketchText: String = ""
    private var candidates: List<String> = emptyList()
    private var selected: Int = 0
    private var predictions: List<ChordPrediction> = emptyList()
    private var hintText: String = ""
    private var statusText = ""
    private var privateMode = false
    private var haptics = true
    private var showLabels = true
    private var showHighlights = true
    private var darkTheme = true
    private var deadZone = FlowBoardPreferences.DEFAULT_DEAD_ZONE
    private var syncWindowMs = FlowBoardPreferences.DEFAULT_SYNC_WINDOW.toLong()

    private var snapshot: GestureEngine.Snapshot? = null
    private var pressedCommand: Command? = null
    private var pressedCandidate: Int? = null
    private var zoneLayout: ZoneLayout? = null
    private var buttons: List<Pair<Command, RectF>> = emptyList()
    private var chips: List<Pair<Int, RectF>> = emptyList()
    private var previewRect = RectF()
    private var chipRect = RectF()
    private var statusExpiry = 0L

    private val renderer = ZoneRenderer(context)
    private lateinit var engine: GestureEngine

    private val tickRunnable = Runnable {
        engine.onTick(SystemClock.uptimeMillis())
        invalidate()
    }

    private val engineListener = object : GestureEngine.Listener {
        override fun onSnapshot(snapshot: GestureEngine.Snapshot) {
            this@KeyboardView.snapshot = snapshot
            invalidate()
        }

        override fun onChordCommitted(chord: Chord) {
            performHaptic()
            listener?.onChord(chord)
        }

        override fun onGestureRejected(reason: GestureEngine.RejectionReason) {
            statusText = rejectionText(reason)
            statusExpiry = SystemClock.uptimeMillis() + 1600
            invalidate()
        }
    }

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#0F1115") }
    private val toolbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#161D28") }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2C4472") }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#121924") }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B2431") }
    private val chipSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2C4472") }
    private val composingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7ECF6")
        textSize = dp(15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8FA0BD")
        textSize = dp(10f)
        textAlign = Paint.Align.RIGHT
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6DEEC")
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val chipIndexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6E9BFF")
        textSize = dp(9f)
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6DEEC")
        textSize = dp(16f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    init {
        engine = GestureEngine(GestureEngine.Config(syncWindowMs = syncWindowMs), engineListener)
        isClickable = true
        setBackgroundColor(Color.parseColor("#0F1115"))
    }

    fun applyPreferences(preferences: FlowBoardPreferences) {
        haptics = preferences.hapticsEnabled
        showLabels = preferences.showCandidateLabels
        showHighlights = preferences.predictionHighlights
        darkTheme = preferences.theme != "light"
        deadZone = preferences.deadZone
        syncWindowMs = preferences.syncWindowMs.toLong()
        engine.updateConfig(GestureEngine.Config(syncWindowMs = syncWindowMs))
        renderer.setDarkTheme(darkTheme)
        applyPalette()
        if (width > 0 && height > 0) buildLayout(width.toFloat(), height.toFloat())
        invalidate()
    }

    /** Applies a full linguistic state; safe to call after every chord. */
    fun setSession(state: StenoSession.Snapshot) {
        sketchText = state.key
        candidates = when (val translation = state.translation) {
            is Translation.Candidates -> translation.words
            else -> emptyList()
        }
        hintText = when (val translation = state.translation) {
            is Translation.Candidates -> ""
            is Translation.Incomplete -> translation.reason
            Translation.Empty -> ""
        }
        selected = state.selected.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
        predictions = state.predictions
        if (chipRect.width() > 0f) {
            chips = buildChips(chipRect, maxOf(candidates.size, 1))
        }
        invalidate()
    }

    fun setPrivateMode(value: Boolean) {
        privateMode = value
        invalidate()
    }

    fun showStatus(text: String) {
        statusText = text
        statusExpiry = SystemClock.uptimeMillis() + 2400
        invalidate()
    }

    fun resetGesture() {
        removeCallbacks(tickRunnable)
        engine.reset()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val desired = dp(if (landscape) 235f else 348f).toInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        buildLayout(width.toFloat(), height.toFloat())
    }

    private fun buildLayout(width: Float, height: Float) {
        val toolbarTop = dp(4f)
        val toolbarHeight = dp(40f)
        val toolbarRect = RectF(dp(6f), toolbarTop, width - dp(6f), toolbarTop + toolbarHeight)
        val commands = Command.entries
        val buttonWidth = toolbarRect.width() / commands.size
        buttons = commands.mapIndexed { index, command ->
            val left = toolbarRect.left + index * buttonWidth
            command to RectF(left + dp(3f), toolbarRect.top, left + buttonWidth - dp(3f), toolbarRect.bottom)
        }

        val previewTop = toolbarRect.bottom + dp(4f)
        previewRect = RectF(dp(6f), previewTop, width - dp(6f), previewTop + dp(28f))

        val chipTop = previewRect.bottom + dp(4f)
        chipRect = RectF(dp(6f), chipTop, width - dp(6f), chipTop + dp(32f))
        chips = buildChips(chipRect, maxOf(candidates.size, 1))

        val zoneTop = chipRect.bottom + dp(2f)
        val zoneHeight = (height - zoneTop - dp(2f)).coerceAtLeast(dp(120f))
        val radius = minOf(width * 0.215f, zoneHeight * 0.44f)
        val centerY = zoneTop + zoneHeight / 2f
        val layout = ZoneLayout(
            splitX = width / 2f,
            leftCenterX = width * 0.25f,
            leftCenterY = centerY,
            leftRadius = radius,
            rightCenterX = width * 0.75f,
            rightCenterY = centerY,
            rightRadius = radius,
            deadZoneFraction = deadZone
        )
        zoneLayout = layout
        engine.setLayout(layout)
    }

    private fun buildChips(area: RectF, count: Int): List<Pair<Int, RectF>> {
        val slots = count.coerceIn(1, MAX_CHIPS)
        val gap = dp(4f)
        val width = (area.width() - gap * (slots - 1)) / slots
        return (0 until slots).map { index ->
            val left = area.left + index * (width + gap)
            index to RectF(left, area.top, left + width, area.bottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawToolbar(canvas)
        drawPreview(canvas)
        drawChips(canvas)

        val layout = zoneLayout ?: return
        val current = snapshot
        val (leftHighlights, rightHighlights) = highlightMaps()

        renderer.draw(
            canvas = canvas,
            layout = layout,
            leftLabels = Fsl.leftPadLabels(),
            rightLabels = Fsl.rightPadLabels(),
            leftActive = current?.leftDirection,
            rightActive = current?.rightDirection,
            leftConnected = current?.leftDirection != null,
            rightConnected = current?.rightDirection != null,
            showLabels = showLabels,
            leftHighlights = leftHighlights,
            rightHighlights = rightHighlights
        )
    }

    private fun highlightMaps(): Pair<Map<Direction, Float>, Map<Direction, Float>> {
        if (!showHighlights || predictions.isEmpty()) return emptyMap<Direction, Float>() to emptyMap()
        val left = HashMap<Direction, Float>()
        val right = HashMap<Direction, Float>()
        for (prediction in predictions) {
            val chord = prediction.chord
            left[chord.left] = maxOf(left[chord.left] ?: 0f, prediction.score.toFloat())
            right[chord.right] = maxOf(right[chord.right] ?: 0f, prediction.score.toFloat())
        }
        return left to right
    }

    private fun drawToolbar(canvas: Canvas) {
        for ((command, rect) in buttons) {
            canvas.drawRoundRect(
                rect, dp(10f), dp(10f),
                if (command == pressedCommand) pressedPaint else toolbarPaint
            )
            iconPaint.textSize = dp(16f)
            canvas.drawText(iconFor(command), rect.centerX(), rect.centerY() + iconPaint.textSize / 3f, iconPaint)
        }
    }

    private fun drawPreview(canvas: Canvas) {
        canvas.drawRoundRect(previewRect, dp(9f), dp(9f), previewPaint)
        composingPaint.color = if (darkTheme) Color.parseColor("#E7ECF6") else Color.parseColor("#1A2333")
        composingPaint.textSize = dp(14f)

        val shown = when {
            sketchText.isNotEmpty() -> sketchText
            hintText.isNotEmpty() -> hintText
            else -> "Left = onset · right = nucleus"
        }
        canvas.save()
        canvas.clipRect(previewRect.left + dp(10f), previewRect.top, previewRect.right - dp(96f), previewRect.bottom)
        canvas.drawText(shown, previewRect.left + dp(10f), previewRect.centerY() + composingPaint.textSize / 3f, composingPaint)
        canvas.restore()

        val right = when {
            privateMode -> "PRIVATE"
            statusText.isNotEmpty() && (statusExpiry == 0L || SystemClock.uptimeMillis() < statusExpiry) -> statusText
            else -> ""
        }
        if (right.isNotEmpty()) {
            statusPaint.textSize = dp(10f)
            statusPaint.color = if (privateMode) Color.parseColor("#F0B429") else Color.parseColor("#8FA0BD")
            canvas.drawText(right, previewRect.right - dp(10f), previewRect.centerY() + dp(3.5f), statusPaint)
        }
    }

    private fun drawChips(canvas: Canvas) {
        val slots = chips
        if (slots.isEmpty()) return
        if (candidates.isEmpty()) {
            chipPaint.color = if (darkTheme) Color.parseColor("#151C27") else Color.parseColor("#EDF1F8")
            canvas.drawRoundRect(chipRect, dp(9f), dp(9f), chipPaint)
            chipTextPaint.color = if (darkTheme) Color.parseColor("#5C6B85") else Color.parseColor("#7A879E")
            chipTextPaint.textSize = dp(11f)
            val message = if (sketchText.isEmpty()) "stroke a word · tap a candidate to commit" else hintText.ifEmpty { "no word for this sketch" }
            canvas.drawText(message, chipRect.centerX(), chipRect.centerY() + dp(4f), chipTextPaint)
            chipTextPaint.color = if (darkTheme) Color.parseColor("#D6DEEC") else Color.parseColor("#2A3448")
            return
        }

        val visible = minOf(candidates.size, slots.size)
        for (index in 0 until visible) {
            val rect = slots[index].second
            val isSelected = index == selected
            canvas.drawRoundRect(rect, dp(9f), dp(9f), if (isSelected) chipSelectedPaint else chipPaint)
            chipIndexPaint.textSize = dp(9f)
            canvas.drawText("${index + 1}", rect.centerX(), rect.top + dp(9f), chipIndexPaint)
            chipTextPaint.color = if (isSelected) Color.WHITE else Color.parseColor("#D6DEEC")
            chipTextPaint.textSize = dp(12f)
            val word = candidates[index]
            val fitted = if (chipTextPaint.measureText(word) > rect.width() - dp(10f)) {
                word.take(maxOf(3, (rect.width() / dp(7f)).toInt())) + "…"
            } else {
                word
            }
            canvas.drawText(fitted, rect.centerX(), rect.centerY() + dp(4f), chipTextPaint)
        }
    }

    private fun iconFor(command: Command): String = when (command) {
        Command.BACKSPACE -> "⌫"
        Command.SPACE -> "␣"
        Command.COMPLETE -> "✓"
        Command.UNDO -> "↺"
        Command.ALTERNATIVES -> "⇄"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                commandAt(event.x, event.y)?.let {
                    pressedCommand = it
                    invalidate()
                    return true
                }
                chipAt(event.x, event.y)?.let {
                    pressedCandidate = it
                    invalidate()
                    return true
                }
                engine.onDown(event.getPointerId(0), event.x, event.y, event.eventTime)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                engine.onDown(event.getPointerId(index), event.getX(index), event.getY(index), event.eventTime)
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    engine.onMove(event.getPointerId(index), event.getX(index), event.getY(index), event.eventTime)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                engine.onUp(event.getPointerId(index), event.getX(index), event.getY(index), event.eventTime)
                scheduleTick()
            }

            MotionEvent.ACTION_UP -> {
                val command = pressedCommand
                val candidate = pressedCandidate
                if (command != null) {
                    pressedCommand = null
                    if (commandAt(event.x, event.y) == command) {
                        performHaptic()
                        listener?.onCommand(command)
                    }
                    invalidate()
                } else if (candidate != null) {
                    pressedCandidate = null
                    if (chipAt(event.x, event.y) == candidate) {
                        performHaptic()
                        listener?.onSelectCandidate(candidate)
                    }
                    invalidate()
                } else {
                    engine.onUp(event.getPointerId(0), event.x, event.y, event.eventTime)
                    scheduleTick()
                    performClick()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                for (index in 0 until event.pointerCount) {
                    engine.onCancel(event.getPointerId(index), event.eventTime)
                }
                pressedCommand = null
                pressedCandidate = null
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun commandAt(x: Float, y: Float): Command? =
        buttons.firstOrNull { it.second.contains(x, y) }?.first

    private fun chipAt(x: Float, y: Float): Int? {
        val visible = minOf(candidates.size, chips.size)
        if (visible == 0) return null
        return chips.take(visible).firstOrNull { it.second.contains(x, y) }?.first
    }

    private fun scheduleTick() {
        removeCallbacks(tickRunnable)
        postDelayed(tickRunnable, syncWindowMs + 40L)
    }

    private fun performHaptic() {
        if (!haptics) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun rejectionText(reason: GestureEngine.RejectionReason): String = when (reason) {
        GestureEngine.RejectionReason.INCOMPLETE_DIRECTIONS -> "Move both thumbs"
        GestureEngine.RejectionReason.FINGERS_OUT_OF_SYNC -> "Both thumbs must land together"
        GestureEngine.RejectionReason.OUT_OF_ZONE -> "Finger left its pad"
        GestureEngine.RejectionReason.CANCELLED -> "Gesture cancelled"
    }

    private fun applyPalette() {
        backgroundPaint.color = if (darkTheme) Color.parseColor("#0F1115") else Color.parseColor("#F5F7FA")
        toolbarPaint.color = if (darkTheme) Color.parseColor("#161D28") else Color.parseColor("#FFFFFF")
        pressedPaint.color = if (darkTheme) Color.parseColor("#2C4472") else Color.parseColor("#CBD9F5")
        previewPaint.color = if (darkTheme) Color.parseColor("#121924") else Color.parseColor("#FFFFFF")
        chipPaint.color = if (darkTheme) Color.parseColor("#1B2431") else Color.parseColor("#FFFFFF")
        chipSelectedPaint.color = if (darkTheme) Color.parseColor("#2C4472") else Color.parseColor("#CBD9F5")
        iconPaint.color = if (darkTheme) Color.parseColor("#D6DEEC") else Color.parseColor("#2A3448")
        setBackgroundColor(backgroundPaint.color)
    }

    private companion object {
        const val MAX_CHIPS = 4
    }
}
