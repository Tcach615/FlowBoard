package com.flowboard.keyboard.predict

import com.flowboard.keyboard.model.Candidate
import com.flowboard.keyboard.model.CandidateKind
import kotlin.math.min

/**
 * Weights for the transparent scoring function:
 *
 *   score = context * P(candidate | previous word)
 *         + prefix  * prefixMatch
 *         + personal * personalizationBoost
 *         - interaction * interactionCost
 */
data class ScoreWeights(
    val context: Double = 0.9,
    val prefix: Double = 1.2,
    val personal: Double = 1.5,
    val interaction: Double = 0.10
)

interface PersonalizationStore {
    /** 0..1 boost for a candidate the user has chosen before. */
    fun boost(text: String): Double
}

/** Session-scoped personalization, used by the keyboard and by tests. */
class InMemoryPersonalization : PersonalizationStore {
    private val counts = HashMap<String, Int>()
    private var total = 0

    fun record(text: String) {
        val key = text.trim().lowercase()
        if (key.isEmpty()) return
        counts[key] = (counts[key] ?: 0) + 1
        total += 1
    }

    fun count(text: String): Int = counts[text.trim().lowercase()] ?: 0

    override fun boost(text: String): Double {
        val count = counts[text.trim().lowercase()] ?: return 0.0
        return min(1.0, (count.toDouble() / maxOf(total, 1).toDouble()) * 2.0)
    }
}

/**
 * Deterministic, offline candidate ranking.
 *
 * This is a frequency + bigram + prefix model over a hand-authored vocabulary. It is not a
 * language model and is not claimed to be one; the interface keeps it replaceable.
 */
class PredictionEngine(
    private val vocabulary: Vocabulary,
    private val weights: ScoreWeights = ScoreWeights(),
    private val personalization: PersonalizationStore? = null
) {

    fun predict(context: TextContext, limit: Int = 16): List<Candidate> {
        val prefix = context.currentPrefix.trim()
        val previous = context.previousWord
        val scored = LinkedHashMap<String, Candidate>()

        fun offer(text: String, kind: CandidateKind, base: Double) {
            if (scored.containsKey(text)) return
            val personal = personalization?.boost(text) ?: 0.0
            val score = weights.context * base +
                weights.personal * personal -
                weights.interaction * interactionCost(kind)
            scored[text] = Candidate(text, kind, score)
        }

        if (prefix.isNotEmpty()) {
            for (entry in vocabulary.wordsStartingWith(prefix).take(limit * 2)) {
                val match = prefixMatch(prefix, entry.text)
                val contextScore = vocabulary.bigramProbability(previous, entry.text)
                offer(entry.text, CandidateKind.WORD, 0.65 * match + 0.35 * contextScore)
            }
            for (fragment in vocabulary.fragmentsStartingWith(prefix).take(limit)) {
                offer(fragment.text, CandidateKind.FRAGMENT, 0.5 + 0.5 * prefixMatch(prefix, fragment.text))
            }
            if (scored.isEmpty()) {
                // Unknown prefix: keep the raw prefix so the user can extend or accept it.
                offer(prefix, CandidateKind.FRAGMENT, 0.05)
            }
        } else {
            for (entry in vocabulary.topWords(limit * 2)) {
                offer(entry.text, CandidateKind.WORD, vocabulary.bigramProbability(previous, entry.text))
            }
            offer(" ", CandidateKind.SPACE, 0.30)
            offer(".", CandidateKind.PUNCTUATION, 0.22)
            offer(",", CandidateKind.PUNCTUATION, 0.16)
            offer("?", CandidateKind.PUNCTUATION, 0.12)
            offer("!", CandidateKind.PUNCTUATION, 0.08)
        }

        return scored.values
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.text })
            .take(limit)
    }

    private fun prefixMatch(prefix: String, word: String): Double {
        if (!word.startsWith(prefix)) return 0.0
        if (word.length == prefix.length) return 1.0
        return prefix.length.toDouble() / word.length.toDouble()
    }

    private fun interactionCost(kind: CandidateKind): Double = when (kind) {
        CandidateKind.SPACE, CandidateKind.PUNCTUATION -> 0.0
        CandidateKind.SYLLABLE -> 0.05
        CandidateKind.FRAGMENT -> 0.10
        CandidateKind.WORD -> 0.20
    }
}
