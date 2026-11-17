package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord

/**
 * Parses a raw chord sequence into FSL strokes and enforces the language's structure.
 *
 * Segmentation is structural: a bare stroke is always a consonant, a core always opens a
 * syllable, so there is nothing to guess (unlike stenotype, where a dictionary resolves where
 * syllables begin). The only invalid states are "no strokes" and "no core", because a sequence of
 * bare consonants is a cluster, not a word.
 */
class StenoEngine {

    data class ParsedSketch(val symbols: List<FslSymbol>) {
        val key: String = Fsl.render(symbols)
        val coreCount: Int = symbols.count { !it.isBare }
        val bareCount: Int = symbols.size - coreCount

        /** A word sketch needs at least one nucleus. */
        val isWordlike: Boolean get() = coreCount > 0

        val isEmpty: Boolean get() = symbols.isEmpty()
    }

    fun parse(chords: List<Chord>): ParsedSketch = ParsedSketch(chords.map(Fsl::symbolOf))

    /** Human-readable reason the sequence cannot be translated, or null when it can. */
    fun problem(chords: List<Chord>): String? {
        val parsed = parse(chords)
        return when {
            parsed.isEmpty -> "No strokes yet"
            !parsed.isWordlike -> "Consonants only — add a vowel chord"
            parsed.symbols.size > MAX_STROKES -> "Too long — commit or undo first"
            else -> null
        }
    }

    fun symbolsOf(key: String): List<FslSymbol> = Fsl.parse(key)

    private companion object {
        const val MAX_STROKES = 24
    }
}
