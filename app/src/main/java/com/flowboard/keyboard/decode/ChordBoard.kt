package com.flowboard.keyboard.decode

import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.model.Candidate

/**
 * Maps ranked candidates onto the 16 chord slots.
 *
 * Stability rule: a candidate that was already on a chord and is still in the ranked list
 * stays there, so muscle memory survives one bad prediction. Only freed slots are filled,
 * in rank order.
 */
class ChordBoard(val slots: List<Chord> = Chord.ALL) {

    private var mapping: Map<Chord, Candidate> = emptyMap()

    val current: Map<Chord, Candidate> get() = mapping

    fun candidateFor(chord: Chord): Candidate? = mapping[chord]

    fun update(candidates: List<Candidate>): Map<Chord, Candidate> {
        val remaining = ArrayList<Candidate>()
        for (candidate in candidates) {
            if (remaining.none { it.text == candidate.text }) remaining.add(candidate)
        }

        val next = LinkedHashMap<Chord, Candidate>()
        for (chord in slots) {
            val previous = mapping[chord] ?: continue
            val index = remaining.indexOfFirst { it.text == previous.text }
            if (index >= 0) next[chord] = remaining.removeAt(index)
        }
        for (chord in slots) {
            if (next.containsKey(chord)) continue
            if (remaining.isEmpty()) break
            next[chord] = remaining.removeAt(0)
        }

        mapping = next
        return next
    }

    fun clear() {
        mapping = emptyMap()
    }
}
