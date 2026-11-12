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
import com.flowboard.keyboard.gesture.GestureEngine
import com.flowboard.keyboard.gesture.ZoneLayout
import com.flowboard.keyboard.model.Candidate
import com.flowboard.keyboard.settings.FlowBoardPreferences

/**
 * The keyboard surface: a compact toolbar, a composing preview and two directional pads.
 *
 * Candidate labels on the left pad are the four candidates for the right-hand direction
 * currently in play (and vice versa), which is how all sixteen chords stay visible without
 * a 16-cell grid.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onChord(chord: Chord)
        fun onCommand(command: Command)
    }

    enum class Command { BACKSPACE, SPACE, COMPLETE, UNDO, ALTERNATIVES }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private var board: Map<Chord, Candidate> = emptyMap()
    private var composingText: String = ""
    private var statusText: String = ""
    private var privateMode = false
    private var haptics = true
    private var showLabels = true
    private var darkTheme = true
    private var deadZone = FlowBoardPreferences.DEFAULT_DEAD_ZONE
    private var syncWindowMs = FlowBoardPreferences.DEFAULT_SYNC_WINDOW.toLong()

    private var snapshot: GestureEngine.Snapshot? = null
    private var pressedCommand: Command? = null
    private var zoneLayout: ZoneLayout? = null
    private var buttons: List<Pair<Command, RectF>> = emptyList()
    private var previewRect = RectF()
    private var toolbarTop = 0f
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
    private val composingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7ECF6")
        textSize = dp(17f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private var statusColor = Color.parseColor("#8FA0BD")
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = statusColor
        textSize = dp(11f)
        textAlign = Paint.Align.RIGHT
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6DEEC")
        textSize = dp(17f)
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
        darkTheme = preferences.theme != "light"
        deadZone = preferences.deadZone
        syncWindowMs = preferences.syncWindowMs.toLong()
        engine.updateConfig(GestureEngine.Config(syncWindowMs = syncWindowMs))
        renderer.setDarkTheme(darkTheme)
        applyPalette()
        // The dead zone and sync window change the geometry; rebuild in place (no resize event follows).
        if (width > 0 && height > 0) buildLayout(width.toFloat(), height.toFloat())
        invalidate()
    }

    fun setBoard(mapping: Map<Chord, Candidate>) {
        board = mapping
        invalidate()
    }

    fun setComposingPreview(text: String) {
        composingText = text
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
        val desired = dp(if (landscape) 210f else 330f).toInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        buildLayout(width.toFloat(), height.toFloat())
    }

    private fun buildLayout(width: Float, height: Float) {
        toolbarTop = dp(4f)
        val toolbarHeight = dp(42f)
        val toolbarRect = RectF(dp(6f), toolbarTop, width - dp(6f), toolbarTop + toolbarHeight)
        val commands = Command.entries
        val buttonWidth = toolbarRect.width() / commands.size
        buttons = commands.mapIndexed { index, command ->
            val left = toolbarRect.left + index * buttonWidth
            command to RectF(left + dp(3f), toolbarRect.top, left + buttonWidth - dp(3f), toolbarRect.bottom)
        }

        val previewTop = toolbarRect.bottom + dp(4f)
        previewRect = RectF(dp(6f), previewTop, width - dp(6f), previewTop + dp(34f))

        val zoneTop = previewRect.bottom + dp(2f)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawToolbar(canvas)
        drawPreview(canvas)

        val layout = zoneLayout ?: return
        val currentSnapshot = snapshot
        val anchorLeft = currentSnapshot?.leftDirection
            ?: currentSnapshot?.lastChord?.left
            ?: Direction.NW
        val anchorRight = currentSnapshot?.rightDirection
            ?: currentSnapshot?.lastChord?.right
            ?: Direction.NE

        val leftLabels = Direction.entries.associateWith { direction ->
            board[Chord(direction, anchorRight)]?.let { displayText(it) }
        }
        val rightLabels = Direction.entries.associateWith { direction ->
            board[Chord(anchorLeft, direction)]?.let { displayText(it) }
        }

        renderer.draw(
            canvas = canvas,
            layout = layout,
            leftLabels = leftLabels,
            rightLabels = rightLabels,
            leftActive = currentSnapshot?.leftDirection,
            rightActive = currentSnapshot?.rightDirection,
            leftConnected = currentSnapshot?.leftDirection != null,
            rightConnected = currentSnapshot?.rightDirection != null,
            showLabels = showLabels
        )
    }

    private fun displayText(candidate: Candidate): String = when (candidate.kind) {
        com.flowboard.keyboard.model.CandidateKind.SPACE -> "space"
        com.flowboard.keyboard.model.CandidateKind.PUNCTUATION -> candidate.text
        else -> candidate.text
    }

    private fun drawToolbar(canvas: Canvas) {
        for ((command, rect) in buttons) {
            canvas.drawRoundRect(rect, dp(10f), dp(10f), if (command == pressedCommand) pressedPaint else toolbarPaint)
            iconPaint.textSize = dp(16f)
            canvas.drawText(
                iconFor(command),
                rect.centerX(),
                rect.centerY() + iconPaint.textSize / 3f,
                iconPaint
            )
        }
    }

    private fun drawPreview(canvas: Canvas) {
        canvas.drawRoundRect(previewRect, dp(10f), dp(10f), previewPaint)
        composingPaint.color = if (darkTheme) Color.parseColor("#E7ECF6") else Color.parseColor("#1A2333")
        statusColor = if (darkTheme) Color.parseColor("#8FA0BD") else Color.parseColor("#5A6A85")
        statusPaint.color = statusColor

        val text = if (composingText.isEmpty()) "Two thumbs, four directions" else composingText
        composingPaint.textSize = dp(17f)
        canvas.save()
        canvas.clipRect(previewRect.left + dp(10f), previewRect.top, previewRect.right - dp(120f), previewRect.bottom)
        canvas.drawText(
            text,
            previewRect.left + dp(10f),
            previewRect.centerY() + composingPaint.textSize / 3f,
            composingPaint
        )
        canvas.restore()

        val right = if (privateMode) "PRIVATE" else if (statusText.isNotEmpty() &&
            (statusExpiry == 0L || SystemClock.uptimeMillis() < statusExpiry)
        ) statusText else ""
        if (right.isNotEmpty()) {
            statusPaint.textSize = dp(11f)
            statusPaint.color = if (privateMode) Color.parseColor("#F0B429") else statusColor
            canvas.drawText(right, previewRect.right - dp(10f), previewRect.centerY() + dp(4f), statusPaint)
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
                val command = commandAt(event.x, event.y)
                if (command != null) {
                    pressedCommand = command
                    invalidate()
                    return true
                }
                engine.onDown(event.getPointerId(0), event.x, event.y, event.eventTime)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                engine.onDown(
                    event.getPointerId(index), event.getX(index), event.getY(index), event.eventTime
                )
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    engine.onMove(
                        event.getPointerId(index), event.getX(index), event.getY(index), event.eventTime
                    )
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                engine.onUp(
                    event.getPointerId(index), event.getX(index), event.getY(index), event.eventTime
                )
                scheduleTick()
            }

            MotionEvent.ACTION_UP -> {
                val command = pressedCommand
                if (command != null) {
                    pressedCommand = null
                    if (commandAt(event.x, event.y) == command) {
                        performHaptic()
                        listener?.onCommand(command)
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

    private fun scheduleTick() {
        removeCallbacks(tickRunnable)
        postDelayed(tickRunnable, syncWindowMs + 40L)
    }

    private fun performHaptic() {
        if (!haptics) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun rejectionText(reason: GestureEngine.RejectionReason): String = when (reason) {
        GestureEngine.RejectionReason.INCOMPLETE_DIRECTIONS -> "Move both thumbs to a corner"
        GestureEngine.RejectionReason.FINGERS_OUT_OF_SYNC -> "Both thumbs must land together"
        GestureEngine.RejectionReason.OUT_OF_ZONE -> "Finger left its pad"
        GestureEngine.RejectionReason.CANCELLED -> "Gesture cancelled"
    }

    private fun applyPalette() {
        backgroundPaint.color = if (darkTheme) Color.parseColor("#0F1115") else Color.parseColor("#F5F7FA")
        toolbarPaint.color = if (darkTheme) Color.parseColor("#161D28") else Color.parseColor("#FFFFFF")
        pressedPaint.color = if (darkTheme) Color.parseColor("#2C4472") else Color.parseColor("#CBD9F5")
        previewPaint.color = if (darkTheme) Color.parseColor("#121924") else Color.parseColor("#FFFFFF")
        iconPaint.color = if (darkTheme) Color.parseColor("#D6DEEC") else Color.parseColor("#2A3448")
        setBackgroundColor(backgroundPaint.color)
    }
}
