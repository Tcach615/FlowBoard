package com.flowboard.keyboard

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.FingerRole
import com.flowboard.keyboard.gesture.ZoneLayout

/**
 * Shared helpers for headless tests: builds real multi-pointer [MotionEvent]s and replays a
 * complete two-thumb chord through a real view, exactly as a finger would.
 *
 * Nothing here touches app state directly; every assertion after using it is about what the
 * production view and service did with the events.
 */
object HeadlessGesture {

    /** Pointer ids deliberately not 0/1, to prove role comes from position not arrival order. */
    const val LEFT_ID = 7
    const val RIGHT_ID = 9

    private fun event(
        action: Int,
        actionIndex: Int,
        ids: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        time: Long
    ): MotionEvent {
        val properties = ids.map { id ->
            MotionEvent.PointerProperties().apply {
                this.id = id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coords = ids.indices.map { index ->
            MotionEvent.PointerCoords().apply {
                x = xs[index]
                y = ys[index]
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val masked = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(
            0L, time, masked, ids.size, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
    }

    private fun point(layout: ZoneLayout, role: FingerRole, dx: Int, dy: Int): Pair<Float, Float> {
        val reach = layout.radius(role) * 0.60f
        return (layout.centerX(role) + dx * reach) to (layout.centerY(role) + dy * reach)
    }

    /** A one-pointer event, for deliberately incomplete gestures. */
    fun singlePointer(action: Int, id: Int, x: Float, y: Float, time: Long): MotionEvent =
        event(action, 0, intArrayOf(id), floatArrayOf(x), floatArrayOf(y), time)

    /** Two thumbs land and drag to the chord's corners, and stay down. */
    fun startChord(view: View, layout: ZoneLayout, chord: Chord, startTime: Long = 0L) {
        val leftCentre = layout.centerX(FingerRole.LEFT) to layout.centerY(FingerRole.LEFT)
        val rightCentre = layout.centerX(FingerRole.RIGHT) to layout.centerY(FingerRole.RIGHT)
        val (lx, ly) = point(layout, FingerRole.LEFT, chord.left.dx, chord.left.dy)
        val (rx, ry) = point(layout, FingerRole.RIGHT, chord.right.dx, chord.right.dy)

        view.dispatchTouchEvent(
            event(
                MotionEvent.ACTION_DOWN, 0, intArrayOf(LEFT_ID),
                floatArrayOf(leftCentre.first), floatArrayOf(leftCentre.second), startTime
            )
        )
        view.dispatchTouchEvent(
            event(
                MotionEvent.ACTION_POINTER_DOWN, 1, intArrayOf(LEFT_ID, RIGHT_ID),
                floatArrayOf(leftCentre.first, rightCentre.first),
                floatArrayOf(leftCentre.second, rightCentre.second), startTime + 10
            )
        )
        view.dispatchTouchEvent(
            event(
                MotionEvent.ACTION_MOVE, 0, intArrayOf(LEFT_ID, RIGHT_ID),
                floatArrayOf(lx, rx), floatArrayOf(ly, ry), startTime + 20
            )
        )
    }

    /** Lifts the right thumb first: that is what commits the chord. */
    fun commitChord(view: View, layout: ZoneLayout, chord: Chord, time: Long = 30L) {
        val (lx, ly) = point(layout, FingerRole.LEFT, chord.left.dx, chord.left.dy)
        val (rx, ry) = point(layout, FingerRole.RIGHT, chord.right.dx, chord.right.dy)
        view.dispatchTouchEvent(
            event(
                MotionEvent.ACTION_POINTER_UP, 1, intArrayOf(LEFT_ID, RIGHT_ID),
                floatArrayOf(lx, rx), floatArrayOf(ly, ry), time
            )
        )
        view.dispatchTouchEvent(
            event(
                MotionEvent.ACTION_UP, 0, intArrayOf(LEFT_ID),
                floatArrayOf(lx), floatArrayOf(ly), time + 10
            )
        )
    }

    /** Sends DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP in order on [view]. */
    fun playChord(
        view: View,
        layout: ZoneLayout,
        chord: Chord,
        startTime: Long = 0L
    ): Boolean {
        startChord(view, layout, chord, startTime)
        commitChord(view, layout, chord, startTime + 30)
        return true
    }

    /** Cancels the in-flight gesture, the way the system does when focus is stolen. */
    fun cancel(view: View, time: Long = 60L) {
        view.dispatchTouchEvent(
            event(MotionEvent.ACTION_CANCEL, 0, intArrayOf(LEFT_ID, RIGHT_ID), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), time)
        )
    }

    /** Sends a single-finger tap, used for the toolbar buttons. */
    fun tap(view: View, x: Float, y: Float, time: Long = 0L) {
        view.dispatchTouchEvent(
            event(MotionEvent.ACTION_DOWN, 0, intArrayOf(0), floatArrayOf(x), floatArrayOf(y), time)
        )
        view.dispatchTouchEvent(
            event(MotionEvent.ACTION_UP, 0, intArrayOf(0), floatArrayOf(x), floatArrayOf(y), time + 20)
        )
    }
}
