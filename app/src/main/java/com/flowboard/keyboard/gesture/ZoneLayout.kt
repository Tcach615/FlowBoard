package com.flowboard.keyboard.gesture

import kotlin.math.hypot

enum class FingerRole { LEFT, RIGHT }

/**
 * Pure geometry for the two interaction zones. No Android types, so the gesture engine
 * can be unit tested on the JVM.
 */
class ZoneLayout(
    val splitX: Float,
    val leftCenterX: Float,
    val leftCenterY: Float,
    val leftRadius: Float,
    val rightCenterX: Float,
    val rightCenterY: Float,
    val rightRadius: Float,
    val deadZoneFraction: Float = 0.26f,
    val escapeFactor: Float = 2.0f
) {
    fun roleOf(x: Float): FingerRole = if (x < splitX) FingerRole.LEFT else FingerRole.RIGHT

    fun centerX(role: FingerRole): Float = if (role == FingerRole.LEFT) leftCenterX else rightCenterX

    fun centerY(role: FingerRole): Float = if (role == FingerRole.LEFT) leftCenterY else rightCenterY

    fun radius(role: FingerRole): Float = if (role == FingerRole.LEFT) leftRadius else rightRadius

    /** Direction of a point relative to the zone centre, or null while inside the dead zone. */
    fun direction(role: FingerRole, x: Float, y: Float): Direction? {
        val dx = x - centerX(role)
        val dy = y - centerY(role)
        if (hypot(dx, dy) < radius(role) * deadZoneFraction) return null
        return Direction.fromVector(dx, dy)
    }

    /** A finger this far outside its zone has left the gesture; it is no longer tracked. */
    fun isInsideZone(role: FingerRole, x: Float, y: Float): Boolean {
        val dx = x - centerX(role)
        val dy = y - centerY(role)
        return hypot(dx, dy) <= radius(role) * escapeFactor
    }
}
