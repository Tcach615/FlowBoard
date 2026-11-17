package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction

/**
 * FSL-1: the FlowBoard Stenographic Language, as specified in
 * `docs/flowboard_language_design.md` §4.
 *
 * The left thumb always carries onset material, the right thumb always carries nucleus
 * material, and the right thumb's SE direction marks a *bare consonant* (the "∅" nucleus).
 * Twelve chords are syllable cores, four are bare consonants. Nothing else exists: no mode
 * chords, no punctuation chords.
 *
 * Every mapping in this file is the documented one. Changing it changes the language and
 * requires regenerating the dictionary asset.
 */
enum class OnsetFamily(val label: String, val members: String) {
    LAB("LAB", "p b m f v"),
    COR("COR", "t d n s z l r θ ð"),
    DOR("DOR", "k g ŋ h w"),
    SIB("SIB", "tʃ dʒ ʃ ʒ j")
}

enum class NucleusFamily(val label: String, val members: String) {
    F("F", "i ɪ e ɛ æ"),
    A("A", "ə ʌ ɜ ɑ a"),
    U("U", "u ʊ ɔ o")
}

/** One FSL symbol: a core (onset + nucleus) or a bare consonant (onset only). */
data class FslSymbol(
    val onset: OnsetFamily,
    val nucleus: NucleusFamily?
) {
    val isBare: Boolean get() = nucleus == null

    /** Canonical notation: `LAB·F` for a core, `COR·∅` for a bare consonant. */
    val label: String get() = "${onset.label}·${nucleus?.label ?: BARE_MARK}"

    companion object {
        const val BARE_MARK = "∅"
    }
}

object Fsl {

    /** The four directions each thumb can take, in the language's own order. */
    private val THUMB_ORDER = listOf(Direction.NW, Direction.NE, Direction.SW, Direction.SE)

    fun onsetOf(direction: Direction): OnsetFamily = when (direction) {
        Direction.NW -> OnsetFamily.LAB
        Direction.NE -> OnsetFamily.COR
        Direction.SW -> OnsetFamily.DOR
        Direction.SE -> OnsetFamily.SIB
    }

    fun nucleusOf(direction: Direction): NucleusFamily? = when (direction) {
        Direction.NW -> NucleusFamily.F
        Direction.NE -> NucleusFamily.A
        Direction.SW -> NucleusFamily.U
        // SE on the right thumb is the bare-consonant marker, not a vowel.
        Direction.SE -> null
    }

    fun directionOf(onset: OnsetFamily): Direction = when (onset) {
        OnsetFamily.LAB -> Direction.NW
        OnsetFamily.COR -> Direction.NE
        OnsetFamily.DOR -> Direction.SW
        OnsetFamily.SIB -> Direction.SE
    }

    fun directionOf(nucleus: NucleusFamily?): Direction = when (nucleus) {
        NucleusFamily.F -> Direction.NW
        NucleusFamily.A -> Direction.NE
        NucleusFamily.U -> Direction.SW
        null -> Direction.SE
    }

    /** Physical chord -> FSL symbol. Total: all 16 chords are defined. */
    fun symbolOf(chord: Chord): FslSymbol =
        FslSymbol(onsetOf(chord.left), nucleusOf(chord.right))

    /** Inverse of [symbolOf]; used to highlight predicted chords. */
    fun chordOf(symbol: FslSymbol): Chord =
        Chord(directionOf(symbol.onset), directionOf(symbol.nucleus))

    fun parseLabel(label: String): FslSymbol {
        val parts = label.split('·')
        require(parts.size == 2) { "Not an FSL symbol: $label" }
        val onset = OnsetFamily.entries.first { it.label == parts[0] }
        val nucleus = NucleusFamily.entries.firstOrNull { it.label == parts[1] }
        return FslSymbol(onset, nucleus)
    }

    fun parse(sketch: String): List<FslSymbol> =
        sketch.split(' ').filter { it.isNotBlank() }.map(::parseLabel)

    fun render(symbols: List<FslSymbol>): String = symbols.joinToString(" ") { it.label }

    val ALL_CHORDS: List<Chord> = THUMB_ORDER.flatMap { left -> THUMB_ORDER.map { right -> Chord(left, right) } }

    /** Labels for the four tiles of a pad. */
    fun leftPadLabels(): Map<Direction, String> =
        OnsetFamily.entries.associate { directionOf(it) to it.label }

    fun rightPadLabels(): Map<Direction, String> =
        THUMB_ORDER.associate { direction ->
            direction to (nucleusOf(direction)?.label ?: FslSymbol.BARE_MARK)
        }
}
