package com.flowboard.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.flowboard.keyboard.gesture.Direction
import com.flowboard.keyboard.gesture.FingerRole
import com.flowboard.keyboard.gesture.ZoneLayout

/** Draws the two directional pads. Shared by the keyboard and the practice view. */
class ZoneRenderer(context: Context) {

    private val density = context.resources.displayMetrics.density

    private val padFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val padStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val tileFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileActiveFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileArmedFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private var labelColor = Color.parseColor("#E7ECF6")

    init {
        setDarkTheme(true)
    }

    fun setDarkTheme(dark: Boolean) {
        if (dark) {
            padFill.color = Color.parseColor("#141A23")
            padStroke.color = Color.parseColor("#26334A")
            tileFill.color = Color.parseColor("#1B2431")
            tileActiveFill.color = Color.parseColor("#5B8CFF")
            tileArmedFill.color = Color.parseColor("#2C4472")
            labelColor = Color.parseColor("#E7ECF6")
            labelPaint.color = labelColor
            rolePaint.color = Color.parseColor("#8FA0BD")
        } else {
            padFill.color = Color.parseColor("#E9EDF5")
            padStroke.color = Color.parseColor("#C3CCDD")
            tileFill.color = Color.parseColor("#FFFFFF")
            tileActiveFill.color = Color.parseColor("#2F62D8")
            tileArmedFill.color = Color.parseColor("#B9CBF0")
            labelColor = Color.parseColor("#1A2333")
            labelPaint.color = labelColor
            rolePaint.color = Color.parseColor("#5A6A85")
        }
    }

    fun draw(
        canvas: Canvas,
        layout: ZoneLayout,
        leftLabels: Map<Direction, String?>,
        rightLabels: Map<Direction, String?>,
        leftActive: Direction?,
        rightActive: Direction?,
        leftConnected: Boolean,
        rightConnected: Boolean,
        showLabels: Boolean
    ) {
        drawPad(canvas, FingerRole.LEFT, layout, leftLabels, leftActive, leftConnected, showLabels, "LEFT")
        drawPad(canvas, FingerRole.RIGHT, layout, rightLabels, rightActive, rightConnected, showLabels, "RIGHT")
    }

    private fun drawPad(
        canvas: Canvas,
        role: FingerRole,
        layout: ZoneLayout,
        labels: Map<Direction, String?>,
        active: Direction?,
        connected: Boolean,
        showLabels: Boolean,
        roleLabel: String
    ) {
        val cx = layout.centerX(role)
        val cy = layout.centerY(role)
        val radius = layout.radius(role)

        canvas.drawCircle(cx, cy, radius, padFill)
        padStroke.alpha = if (connected) 255 else 120
        canvas.drawCircle(cx, cy, radius, padStroke)
        canvas.drawCircle(cx, cy, radius * layout.deadZoneFraction, padStroke)
        padStroke.alpha = 255

        val tileSize = radius * 0.74f
        val offset = radius * 0.52f
        val corner = radius * 0.18f

        for (direction in Direction.entries) {
            val tileCx = cx + direction.dx * offset
            val tileCy = cy + direction.dy * offset
            val rect = RectF(
                tileCx - tileSize / 2f,
                tileCy - tileSize / 2f,
                tileCx + tileSize / 2f,
                tileCy + tileSize / 2f
            )
            val isActive = direction == active
            val fill = if (isActive) tileActiveFill else if (connected) tileArmedFill else tileFill
            canvas.drawRoundRect(rect, corner, corner, fill)

            val label = labels[direction]?.takeIf { showLabels && it.isNotBlank() } ?: "·"
            labelPaint.color = if (isActive) Color.WHITE else if (showLabels) labelColor else rolePaint.color
            drawFittedText(canvas, label, tileCx, tileCy + radius * 0.055f, radius * 0.20f, tileSize * 0.84f)
        }

        rolePaint.textSize = radius * 0.10f
        canvas.drawText(roleLabel, cx, cy + rolePaint.textSize / 3f, rolePaint)
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        baseSize: Float,
        maxWidth: Float
    ) {
        labelPaint.textSize = baseSize
        val width = labelPaint.measureText(text)
        if (width > maxWidth && width > 0f) {
            labelPaint.textSize = baseSize * (maxWidth / width)
        }
        canvas.drawText(text, centerX, baselineY, labelPaint)
    }
}
