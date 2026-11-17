package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord

/**
 * Resolves a stroke sequence into an explicit result. There is deliberately no "best guess" that
 * can be committed without the user: either there are ordered candidates, or the sequence is
 * reported as incomplete/unknown.
 */
sealed interface Translation {
    /** At least one dictionary candidate exists; [ranked] is ordered and stable. */
    data class Candidates(val key: String, val ranked: List<RankedCandidate>) : Translation {
        val words: List<String> get() = ranked.map { it.entry.word }
        val top: RankedCandidate get() = ranked.first()
    }

    /** Strokes exist but cannot form a word yet (or have no dictionary entry). */
    data class Incomplete(val key: String, val reason: String) : Translation

    /** Nothing has been stroked. */
    data object Empty : Translation
}

class TranslationEngine(
    private val engine: StenoEngine,
    private val predictor: StenoPredictor
) {

    fun translate(chords: List<Chord>, context: List<String>, limit: Int = 4): Translation {
        if (chords.isEmpty()) return Translation.Empty
        val parsed = engine.parse(chords)
        val problem = engine.problem(chords)
        if (problem != null) return Translation.Incomplete(parsed.key, problem)
        return translateKey(parsed.key, context, limit)
    }

    fun translateKey(key: String, context: List<String>, limit: Int = 4): Translation {
        if (key.isBlank()) return Translation.Empty
        val ranked = predictor.rank(key, context, limit)
        if (ranked.isEmpty()) {
            return Translation.Incomplete(key, "No word for this sketch")
        }
        return Translation.Candidates(key, ranked)
    }

    /** Text a candidate inserts. Space is explicit: the language never inserts it silently. */
    fun textFor(entry: DictionaryEntry, withSpace: Boolean): String =
        if (withSpace) entry.word + " " else entry.word
}
