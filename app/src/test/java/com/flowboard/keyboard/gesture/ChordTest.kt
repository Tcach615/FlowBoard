package com.flowboard.keyboard.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChordTest {

    @Test
    fun `there are exactly sixteen chords`() {
        assertEquals(16, Chord.ALL.size)
        assertEquals(16, Chord.ALL.distinct().size)
        assertEquals(Chord.COUNT, Chord.ALL.size)
    }

    @Test
    fun `every combination of directions is present`() {
        for (left in Direction.entries) {
            for (right in Direction.entries) {
                assertEquals(true, Chord(left, right) in Chord.ALL)
            }
        }
    }

    @Test
    fun `index round trips`() {
        for (chord in Chord.ALL) {
            assertEquals(chord, Chord.of(chord.index))
        }
        assertEquals(0, Chord(Direction.NW, Direction.NW).index)
        assertEquals(15, Chord(Direction.SE, Direction.SE).index)
    }

    @Test
    fun `mirrored chords are distinct`() {
        assertNotEquals(Chord(Direction.NW, Direction.SE), Chord(Direction.SE, Direction.NW))
    }
}
