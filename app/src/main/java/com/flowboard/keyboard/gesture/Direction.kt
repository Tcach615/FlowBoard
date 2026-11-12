package com.flowboard.keyboard.gesture

import kotlin.math.abs

/**
 * The four diagonal choices available to each finger.
 *
 * Only diagonal vectors count: a straight horizontal or vertical swipe is ambiguous
 * between two chords, so it is reported as "no direction" instead of being guessed.
 */
enum class Direction(val label: String, val dx: Int, val dy: Int) {
    NW("NW", -1, -1),
    NE("NE", 1, -1),
    SW("SW", -1, 1),
    SE("SE", 1, 1);

    companion object {
        /** The smaller axis must reach this share of the dominant axis, otherwise it is ambiguous. */
        const val DEFAULT_AXIS_RATIO = 0.22f

        fun fromVector(dx: Float, dy: Float, axisRatio: Float = DEFAULT_AXIS_RATIO): Direction? {
            val ax = abs(dx)
            val ay = abs(dy)
            val dominant = if (ax > ay) ax else ay
            if (dominant < 1e-4f) return null
            val minor = if (ax > ay) ay else ax
            if (minor / dominant < axisRatio) return null
            return when {
                dx < 0f && dy < 0f -> NW
                dx >= 0f && dy < 0f -> NE
                dx < 0f && dy >= 0f -> SW
                else -> SE
            }
        }
    }
}
