package com.flowboard.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction
import com.flowboard.keyboard.gesture.GestureEngine
import com.flowboard.keyboard.gesture.ZoneLayout

/**
 * Practice surface for onboarding: the same gesture engine and pads as the real keyboard,
 * with direction labels instead of candidates.
 */
class ChordPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onChord: ((Chord) -> Unit)? = null
    var onRejected: ((GestureEngine.RejectionReason) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val renderer = ZoneRenderer(context)
    private val backgroundPaint = Paint().apply { color = Color.parseColor("#101620") }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A9B7CE")
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private var layout: ZoneLayout? = null
    private var snapshot: GestureEngine.Snapshot? = null
    private var message: String = ""

    private lateinit var engine: GestureEngine
    private val tickRunnable = Runnable { engine.onTick(SystemClock.uptimeMillis()); invalidate() }

    private val engineListener = object : GestureEngine.Listener {
        override fun onSnapshot(snapshot: GestureEngine.Snapshot) {
            this@ChordPadView.snapshot = snapshot
            invalidate()
        }

        override fun onChordCommitted(chord: Chord) {
            message = "${chord.left.label} + ${chord.right.label}"
            onChord?.invoke(chord)
            invalidate()
        }

        override fun onGestureRejected(reason: GestureEngine.RejectionReason) {
            message = when (reason) {
                GestureEngine.RejectionReason.INCOMPLETE_DIRECTIONS -> "Pick a direction with each thumb"
                GestureEngine.RejectionReason.FINGERS_OUT_OF_SYNC -> "Land both thumbs, then lift"
                GestureEngine.RejectionReason.OUT_OF_ZONE -> "Keep fingers on the pads"
                GestureEngine.RejectionReason.CANCELLED -> "Gesture cancelled"
            }
            onRejected?.invoke(reason)
            invalidate()
        }
    }

    init {
        engine = GestureEngine(GestureEngine.Config(), engineListener)
        isClickable = true
        setBackgroundColor(Color.parseColor("#101620"))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize(dp(250f).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = minOf(w * 0.20f, h * 0.34f)
        val layout = ZoneLayout(
            splitX = w / 2f,
            leftCenterX = w * 0.25f,
            leftCenterY = h * 0.60f,
            leftRadius = radius,
            rightCenterX = w * 0.75f,
            rightCenterY = h * 0.60f,
            rightRadius = radius
        )
        this.layout = layout
        engine.setLayout(layout)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        hintPaint.textSize = dp(12f)
        val detected = snapshot?.previewChord?.let { "${it.left.label} + ${it.right.label}" }
            ?: message.ifEmpty { "Hold both pads, then drag to a corner" }
        canvas.drawText(detected, width / 2f, dp(20f), hintPaint)

        val currentLayout = layout ?: return
        val labels = Direction.entries.associateWith { it.label }
        renderer.draw(
            canvas = canvas,
            layout = currentLayout,
            leftLabels = labels,
            rightLabels = labels,
            leftActive = snapshot?.leftDirection,
            rightActive = snapshot?.rightDirection,
            leftConnected = snapshot?.leftDirection != null,
            rightConnected = snapshot?.rightDirection != null,
            showLabels = true
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                engine.onDown(event.getPointerId(0), event.x, event.y, event.eventTime)

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
                engine.onUp(event.getPointerId(0), event.x, event.y, event.eventTime)
                scheduleTick()
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                for (index in 0 until event.pointerCount) {
                    engine.onCancel(event.getPointerId(index), event.eventTime)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun scheduleTick() {
        removeCallbacks(tickRunnable)
        postDelayed(tickRunnable, 300L)
    }
}
