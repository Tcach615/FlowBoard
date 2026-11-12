package com.flowboard.keyboard.decode

import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction
import com.flowboard.keyboard.model.Candidate
import com.flowboard.keyboard.model.CandidateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordBoardTest {

    private fun word(text: String) = Candidate(text, CandidateKind.WORD)

    @Test
    fun `sixteen candidates fill all sixteen slots`() {
        val board = ChordBoard()
        val mapping = board.update((1..16).map { word("w$it") })
        assertEquals(16, mapping.size)
        assertEquals(Chord.ALL.toSet(), mapping.keys)
    }

    @Test
    fun `fewer candidates leave the remaining slots empty`() {
        val board = ChordBoard()
        board.update(listOf(word("only")))
        assertEquals(1, board.current.size)
        assertNull(board.candidateFor(Chord(Direction.SE, Direction.SE)))
    }

    @Test
    fun `candidates stay on their chord while still relevant`() {
        val board = ChordBoard()
        val first = board.update(listOf(word("alpha"), word("beta"), word("gamma")))
        val chordOfBeta = first.entries.first { it.value.text == "beta" }.key

        val second = board.update(listOf(word("delta"), word("beta"), word("epsilon")))

        assertEquals("beta", second[chordOfBeta]?.text)
        assertEquals("delta", second[Chord(Direction.NW, Direction.NW)]?.text)
    }

    @Test
    fun `every chord selects exactly the assigned candidate`() {
        val board = ChordBoard()
        val candidates = (1..16).map { word("c$it") }
        val mapping = board.update(candidates)
        for (chord in Chord.ALL) {
            val candidate = board.candidateFor(chord)
            assertEquals(mapping[chord], candidate)
            assertEquals(true, candidate != null)
        }
    }

    @Test
    fun `duplicate texts are collapsed`() {
        val board = ChordBoard()
        board.update(listOf(word("same"), word("same"), word("other")))
        assertEquals(2, board.current.size)
    }

    @Test
    fun `an empty candidate list clears the mapping`() {
        val board = ChordBoard()
        board.update(listOf(word("a")))
        board.update(emptyList())
        assertTrue(board.current.isEmpty())
    }
}
