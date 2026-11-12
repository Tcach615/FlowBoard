package com.flowboard.keyboard.gesture

/**
 * One simultaneous left + right directional selection: the unit of input in ADCI.
 *
 * There are exactly [COUNT] valid chords. [index] is stable and used by mapping tables,
 * tests and the candidate board.
 */
data class Chord(val left: Direction, val right: Direction) {
    val index: Int get() = left.ordinal * Direction.entries.size + right.ordinal

    override fun toString(): String = "${left.label} + ${right.label}"

    companion object {
        const val COUNT: Int = 16

        val ALL: List<Chord> = Direction.entries.flatMap { left ->
            Direction.entries.map { right -> Chord(left, right) }
        }

        fun of(index: Int): Chord {
            require(index in 0 until COUNT) { "Chord index out of range: $index" }
            return ALL[index]
        }
    }
}
