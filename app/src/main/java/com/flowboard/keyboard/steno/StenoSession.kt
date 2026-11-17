package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.predict.InMemoryPersonalization
import com.flowboard.keyboard.predict.TextContext

/**
 * The linguistic state between keystrokes: the current stroke sequence, its ordered translations,
 * the selected alternative, the committed-word context and the undo record.
 *
 * Undo restores the *chords* that produced the last committed word, not just the visible text, so
 * alternatives can be picked without re-stroking the word. That is the requirement in
 * `docs/architecture_proposal.md` §5 ("undo must restore the whole linguistic state").
 */
class StenoSession(
    private val engine: StenoEngine,
    private val translator: TranslationEngine,
    private val predictor: StenoPredictor,
    private val personalization: InMemoryPersonalization? = null
) {

    data class Commit(val text: String, val word: String, val chords: List<Chord>)

    data class Snapshot(
        val key: String,
        val translation: Translation,
        val predictions: List<ChordPrediction>,
        val selected: Int,
        val chordCount: Int,
        val committedWords: Int
    )

    private val strokes = mutableListOf<Chord>()
    val context = TextContext()

    var selected: Int = 0
        private set

    var lastCommit: Commit? = null
        private set

    var committedWords: Int = 0
        private set

    val chordCount: Int get() = strokes.size
    val chords: List<Chord> get() = strokes.toList()

    fun addChord(chord: Chord): Snapshot {
        strokes.add(chord)
        selected = 0
        return snapshot()
    }

    /** @return true when a stroke was removed. */
    fun backspace(): Boolean {
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.size - 1)
        selected = 0
        return true
    }

    fun clearSketch() {
        strokes.clear()
        selected = 0
    }

    fun snapshot(limit: Int = 4): Snapshot {
        val translation = translator.translate(strokes, context.committedWords, limit)
        return Snapshot(
            key = engine.parse(strokes).key,
            translation = translation,
            predictions = if (strokes.size <= MAX_PREDICTION_STROKES) {
                predictor.nextChords(engine.parse(strokes).key, context.committedWords, PREDICTION_LIMIT)
            } else {
                emptyList()
            },
            selected = selected,
            chordCount = strokes.size,
            committedWords = committedWords
        )
    }

    fun candidates(): List<RankedCandidate> =
        (translator.translate(strokes, context.committedWords) as? Translation.Candidates)?.ranked
            ?: emptyList()

    fun cycleAlternative(): Int {
        val count = candidates().size
        if (count == 0) return 0
        selected = (selected + 1) % count
        return selected
    }

    fun select(index: Int) {
        val count = candidates().size
        if (count == 0) return
        selected = index.coerceIn(0, count - 1)
    }

    /**
     * Commits the selected candidate. Returns the text to insert, or null when there is nothing
     * that can be committed (never guesses).
     */
    fun accept(withSpace: Boolean, remember: Boolean): String? {
        val entry = candidates().getOrNull(selected)?.entry ?: return null
        val text = translator.textFor(entry, withSpace)
        lastCommit = Commit(text, entry.word, strokes.toList())
        strokes.clear()
        selected = 0
        context.push(entry.word)
        if (remember) personalization?.record(entry.word)
        committedWords += 1
        return text
    }

    /** @return the text that must be deleted from the editor, or null when there is no undo. */
    fun undo(): String? {
        val commit = lastCommit ?: return null
        lastCommit = null
        context.removeLastWord()
        strokes.clear()
        strokes.addAll(commit.chords)
        selected = 0
        committedWords = (committedWords - 1).coerceAtLeast(0)
        return commit.text
    }

    fun resetForEditor() {
        strokes.clear()
        selected = 0
        lastCommit = null
        context.clear()
    }

    /** True when the sketch matches at least one word (drives the commit affordance). */
    fun canCommit(): Boolean = candidates().isNotEmpty()

    private companion object {
        const val PREDICTION_LIMIT = 4
        const val MAX_PREDICTION_STROKES = 12
    }
}
