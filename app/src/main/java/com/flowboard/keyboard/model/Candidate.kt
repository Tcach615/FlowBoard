package com.flowboard.keyboard.model

/** What a candidate inserts. The distinction matters for spacing and for undo. */
enum class CandidateKind { SYLLABLE, FRAGMENT, WORD, PUNCTUATION, SPACE }

data class Candidate(
    val text: String,
    val kind: CandidateKind,
    val score: Double = 0.0
) {
    val isCommand: Boolean
        get() = kind == CandidateKind.SPACE || kind == CandidateKind.PUNCTUATION
}
