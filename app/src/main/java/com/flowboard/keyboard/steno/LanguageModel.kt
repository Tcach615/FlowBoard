package com.flowboard.keyboard.steno

import java.io.InputStream

/**
 * Compact offline trigram model with Stupid Backoff.
 *
 * Loaded from a gzipped TSV (`U`/`B`/`T` rows). Stupid Backoff is used rather than Kneser–Ney
 * because it is cheap, needs no normalisation pass, and is the configuration the small-model
 * literature reports for on-device keyboards (`docs/context_prediction_architecture.md` §5).
 *
 * The model scores *words*; it knows nothing about chords, so it can never redefine one.
 */
class LanguageModel private constructor(
    private val unigrams: Map<String, Long>,
    private val bigrams: Map<String, Long>,
    private val trigrams: Map<String, Long>,
    private val bigramTotals: Map<String, Long>,
    private val trigramTotals: Map<String, Long>,
    private val totalTokens: Long
) {

    val vocabularySize: Int get() = unigrams.size
    val ngramCount: Int get() = unigrams.size + bigrams.size + trigrams.size

    /**
     * P(word | previous words) with backoff. Values are on Stupid Backoff's unnormalised scale,
     * so callers must compare them only against each other (the predictor normalises per query).
     */
    fun probability(word: String, previous: List<String>): Double {
        val target = normalize(word)
        if (target.isEmpty()) return 0.0
        val last = previous.lastOrNull()?.let(::normalize)?.takeIf { it.isNotEmpty() }
        val secondLast = previous.getOrNull(previous.size - 2)?.let(::normalize)?.takeIf { it.isNotEmpty() }

        if (last != null && secondLast != null) {
            val context = "$secondLast $last"
            val count = trigrams["$context $target"] ?: 0L
            val total = trigramTotals[context] ?: 0L
            if (count > 0L && total > 0L) return count.toDouble() / total.toDouble()
        }
        if (last != null) {
            val count = bigrams["$last $target"] ?: 0L
            val total = bigramTotals[last] ?: 0L
            if (count > 0L && total > 0L) return ALPHA * (count.toDouble() / total.toDouble())
        }
        val unigram = unigrams[target] ?: 0L
        if (unigram > 0L && totalTokens > 0L) {
            return ALPHA * ALPHA * (unigram.toDouble() / totalTokens.toDouble())
        }
        return 0.0
    }

    fun isKnown(word: String): Boolean = unigrams.containsKey(normalize(word))

    private fun normalize(word: String): String = word.trim().lowercase()

    companion object {
        /** Stupid Backoff discount; the value quoted in the literature is 0.4. */
        const val ALPHA = 0.4

        fun load(input: InputStream): LanguageModel {
            val unigrams = HashMap<String, Long>(32_000)
            val bigrams = HashMap<String, Long>(128_000)
            val trigrams = HashMap<String, Long>(64_000)
            val bigramTotals = HashMap<String, Long>(32_000)
            val trigramTotals = HashMap<String, Long>(48_000)
            var totalTokens = 0L

            input.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.startsWith("#")) {
                        val fields = line.removePrefix("#").split('\t')
                        if (fields.size >= 2 && fields[0] == "tokens") {
                            totalTokens = fields[1].toLongOrNull() ?: 0L
                        }
                    } else {
                        val fields = line.split('\t')
                        if (fields.size >= 3) {
                            val count = fields[2].toLongOrNull() ?: 0L
                            when (fields[0]) {
                                "U" -> unigrams[fields[1]] = count
                                "B" -> {
                                    bigrams[fields[1]] = count
                                    val first = fields[1].substringBefore(' ')
                                    bigramTotals[first] = (bigramTotals[first] ?: 0L) + count
                                }
                                "T" -> {
                                    trigrams[fields[1]] = count
                                    val context = fields[1].substringBeforeLast(' ')
                                    trigramTotals[context] = (trigramTotals[context] ?: 0L) + count
                                }
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }

            if (totalTokens <= 0L) totalTokens = unigrams.values.sum().coerceAtLeast(1L)
            return LanguageModel(
                unigrams, bigrams, trigrams, bigramTotals, trigramTotals, totalTokens
            )
        }

        /** A model with no knowledge; used by tests and as the graceful-degradation fallback. */
        fun empty(): LanguageModel = LanguageModel(
            emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), 1L
        )
    }
}
