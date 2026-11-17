package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.predict.PersonalizationStore
import kotlin.math.ln

data class StenoWeights(
    val dictionary: Double = 1.0,
    val context: Double = 1.4,
    val personal: Double = 1.5,
    val tierCost: Double = 0.12
)

data class RankedCandidate(
    val entry: DictionaryEntry,
    val score: Double,
    val dictionaryPrior: Double,
    val contextScore: Double
)

/** A predicted next chord and its share of the candidate probability mass. */
data class ChordPrediction(val chord: Chord, val score: Double)

/**
 * Ranks dictionary candidates and predicts the next chord.
 *
 *     score(w) = dictionary · tierWeight · frequencyPrior(w)
 *              + context    · P(w | previous words) / max P
 *              + personal   · sessionBoost(w)
 *              - tierCost   · (tier + length penalty)
 *
 * The context term is normalised across the candidate set so that a dictionary prior (0..1) and a
 * Stupid Backoff probability (which changes scale with the backoff level) are comparable.
 *
 * Prediction is highlight-only: [nextChords] returns suggestions, and nothing here can change what
 * a chord means or remove a chord from the keyboard.
 */
class StenoPredictor(
    private val dictionary: StenoDictionary,
    private val languageModel: LanguageModel? = null,
    private val weights: StenoWeights = StenoWeights(),
    private val personalization: PersonalizationStore? = null
) {

    fun rank(
        key: String,
        context: List<String>,
        limit: Int = 4,
        includeContinuations: Boolean = false
    ): List<RankedCandidate> {
        val direct = dictionary.candidates(key)
        val pool = if (includeContinuations) {
            val extended = LinkedHashMap<String, DictionaryEntry>()
            direct.forEach { extended.putIfAbsent(it.word, it) }
            val extra = if (key.isBlank()) {
                dictionary.topWords(limit * 8)
            } else {
                dictionary.prefix(key, limit * 8)
            }
            extra.forEach { extended.putIfAbsent(it.word, it) }
            // Bound the work: continuation prediction only needs the most frequent readings.
            extended.values.sortedByDescending { it.frequency }.take(MAX_CONTINUATION_POOL)
        } else {
            direct
        }
        if (pool.isEmpty()) return emptyList()

        val contextScores = pool.map { it to (languageModel?.probability(it.word, context) ?: 0.0) }
        val maxContext = contextScores.maxOfOrNull { it.second } ?: 0.0

        return contextScores
            .map { (entry, probability) ->
                val prior = weights.dictionary * entry.tier.weight * dictionary.frequencyPrior(entry)
                val contextScore = if (maxContext > 0.0) {
                    weights.context * (probability / maxContext)
                } else {
                    0.0
                }
                val personal = weights.personal * (personalization?.boost(entry.word) ?: 0.0)
                val cost = weights.tierCost * (entry.tier.rank + (entry.strokeCount - 1) * 0.15)
                RankedCandidate(entry, prior + contextScore + personal - cost, prior, contextScore)
            }
            .sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.entry.word })
            .take(limit)
    }

    /**
     * Distribution over the chord that continues the current sketch, obtained by summing the
     * scores of compatible candidate words. Empty sketch => the first chord of likely words.
     */
    fun nextChords(key: String, context: List<String>, limit: Int = 4): List<ChordPrediction> {
        val ranked = rank(key, context, limit = 12, includeContinuations = true)
        if (ranked.isEmpty()) return emptyList()

        val prefix = if (key.isBlank()) emptyList() else Fsl.parse(key)
        val totals = LinkedHashMap<Chord, Double>()
        for (candidate in ranked) {
            val outline = outlineFor(candidate.entry) ?: continue
            val symbols = Fsl.parse(outline)
            if (symbols.size <= prefix.size) continue
            if (!hasPrefix(symbols, prefix)) continue
            val chord = Fsl.chordOf(symbols[prefix.size])
            totals[chord] = (totals[chord] ?: 0.0) + candidate.score
        }
        if (totals.isEmpty()) return emptyList()
        val max = totals.values.max()
        return totals.entries
            .map { ChordPrediction(it.key, if (max > 0.0) it.value / max else 0.0) }
            .sortedWith(compareByDescending<ChordPrediction> { it.score }.thenBy { it.chord.index })
            .take(limit)
    }

    /** The outline that a candidate was actually written with. */
    private fun outlineFor(entry: DictionaryEntry): String? = when (entry.tier) {
        Tier.FULL -> entry.sketch
        Tier.BRIEF -> entry.brief ?: entry.sketch
        Tier.REDUCED -> entry.reduced ?: entry.sketch
    }

    private fun hasPrefix(symbols: List<FslSymbol>, prefix: List<FslSymbol>): Boolean {
        if (prefix.size > symbols.size) return false
        for (index in prefix.indices) {
            if (symbols[index].label != prefix[index].label) return false
        }
        return true
    }

    companion object {
        private const val MAX_CONTINUATION_POOL = 48

        /** Log-scaled prior, so a mid-frequency word is not treated as infinitely rarer than "the". */
        fun frequencyPrior(entry: DictionaryEntry, maxFrequency: Long): Double {
            if (maxFrequency <= 0L) return 0.0
            return ln(1.0 + entry.frequency.toDouble()) / ln(1.0 + maxFrequency.toDouble())
        }
    }
}
