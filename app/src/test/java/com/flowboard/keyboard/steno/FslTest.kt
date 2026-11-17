package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alphabet itself: 16 physical chords, 16 symbols, no hidden variants.
 * Expected labels come from `docs/flowboard_language_design.md` §3.
 */
class FslTest {

    private val documented: Map<Chord, String> = mapOf(
        Chord(Direction.NW, Direction.NW) to "LAB·F",
        Chord(Direction.NW, Direction.NE) to "LAB·A",
        Chord(Direction.NW, Direction.SW) to "LAB·U",
        Chord(Direction.NW, Direction.SE) to "LAB·∅",
        Chord(Direction.NE, Direction.NW) to "COR·F",
        Chord(Direction.NE, Direction.NE) to "COR·A",
        Chord(Direction.NE, Direction.SW) to "COR·U",
        Chord(Direction.NE, Direction.SE) to "COR·∅",
        Chord(Direction.SW, Direction.NW) to "DOR·F",
        Chord(Direction.SW, Direction.NE) to "DOR·A",
        Chord(Direction.SW, Direction.SW) to "DOR·U",
        Chord(Direction.SW, Direction.SE) to "DOR·∅",
        Chord(Direction.SE, Direction.NW) to "SIB·F",
        Chord(Direction.SE, Direction.NE) to "SIB·A",
        Chord(Direction.SE, Direction.SW) to "SIB·U",
        Chord(Direction.SE, Direction.SE) to "SIB·∅"
    )

    @Test
    fun `all sixteen physical chords exist`() {
        assertEquals(16, Fsl.ALL_CHORDS.size)
        assertEquals(16, Fsl.ALL_CHORDS.toSet().size)
    }

    @Test
    fun `every chord maps to its documented symbol`() {
        for ((chord, expected) in documented) {
            assertEquals(expected, Fsl.symbolOf(chord).label)
        }
    }

    @Test
    fun `symbols are unique across the keyboard`() {
        val labels = Fsl.ALL_CHORDS.map { Fsl.symbolOf(it).label }
        assertEquals(16, labels.toSet().size)
    }

    @Test
    fun `onset families follow the documented thumb directions`() {
        assertEquals(OnsetFamily.LAB, Fsl.onsetOf(Direction.NW))
        assertEquals(OnsetFamily.COR, Fsl.onsetOf(Direction.NE))
        assertEquals(OnsetFamily.DOR, Fsl.onsetOf(Direction.SW))
        assertEquals(OnsetFamily.SIB, Fsl.onsetOf(Direction.SE))
        ForEachOnset { family -> assertEquals(family, Fsl.onsetOf(Fsl.directionOf(family))) }
    }

    @Test
    fun `nucleus families follow the documented thumb directions`() {
        assertEquals(NucleusFamily.F, Fsl.nucleusOf(Direction.NW))
        assertEquals(NucleusFamily.A, Fsl.nucleusOf(Direction.NE))
        assertEquals(NucleusFamily.U, Fsl.nucleusOf(Direction.SW))
        assertEquals(null, Fsl.nucleusOf(Direction.SE))
        ForEachNucleus { family ->
            assertEquals(family, Fsl.nucleusOf(Fsl.directionOf(family)))
        }
    }

    @Test
    fun `south east on the right hand is the bare marker`() {
        val bare = Fsl.symbolOf(Chord(Direction.SE, Direction.SE))
        assertTrue(bare.isBare)
        assertEquals("SIB·∅", bare.label)
        assertEquals(FslSymbol.BARE_MARK, bare.nucleus?.label ?: FslSymbol.BARE_MARK)
    }

    @Test
    fun `parse and render round trip`() {
        val sketch = "COR·F COR·∅ DOR·A"
        val symbols = Fsl.parse(sketch)
        assertEquals(3, symbols.size)
        assertEquals(sketch, Fsl.render(symbols))
        assertEquals(sketch, Fsl.render(symbols.map { Fsl.symbolOf(Fsl.chordOf(it)) }))
    }

    @Test
    fun `pad labels cover all four directions on each hand`() {
        val left = Fsl.leftPadLabels()
        val right = Fsl.rightPadLabels()
        assertEquals(4, left.size)
        assertEquals(4, right.size)
        assertEquals(setOf("LAB", "COR", "DOR", "SIB"), left.values.toSet())
        assertEquals(setOf("F", "A", "U", "∅"), right.values.toSet())
        assertFalse(left.values.contains("F"))
    }

    private fun ForEachOnset(block: (OnsetFamily) -> Unit) = OnsetFamily.entries.forEach(block)

    private fun ForEachNucleus(block: (NucleusFamily) -> Unit) = NucleusFamily.entries.forEach(block)
}
