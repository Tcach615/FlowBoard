package com.flowboard.keyboard.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectionTest {

    @Test
    fun `diagonal vectors map to the four directions`() {
        assertEquals(Direction.NW, Direction.fromVector(-1f, -1f))
        assertEquals(Direction.NE, Direction.fromVector(1f, -1f))
        assertEquals(Direction.SW, Direction.fromVector(-1f, 1f))
        assertEquals(Direction.SE, Direction.fromVector(1f, 1f))
    }

    @Test
    fun `uneven diagonal vectors still map`() {
        assertEquals(Direction.NE, Direction.fromVector(120f, -40f))
        assertEquals(Direction.SW, Direction.fromVector(-35f, 130f))
    }

    @Test
    fun `straight lines are ambiguous and report nothing`() {
        assertNull(Direction.fromVector(10f, 0f))
        assertNull(Direction.fromVector(0f, -10f))
    }

    @Test
    fun `zero movement has no direction`() {
        assertNull(Direction.fromVector(0f, 0f))
        assertNull(Direction.fromVector(0.00001f, 0.00001f))
    }
}
