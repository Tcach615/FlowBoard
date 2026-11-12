package com.flowboard.keyboard.predict

data class WordEntry(val text: String, val frequency: Int)

data class BigramEntry(val first: String, val second: String, val frequency: Int)

data class VocabularyData(
    val language: String,
    val words: List<WordEntry> = emptyList(),
    val fragments: List<WordEntry> = emptyList(),
    val bigrams: List<BigramEntry> = emptyList()
)

/**
 * Frequency vocabulary plus a small bigram table. Everything is local and synchronous:
 * candidate generation is a few hash lookups, not a network round trip.
 */
class Vocabulary(data: VocabularyData) {

    val language: String = data.language

    private val words: List<WordEntry> = data.words
        .filter { it.text.isNotBlank() && it.frequency > 0 }
        .map { WordEntry(it.text.trim().lowercase(), it.frequency) }

    private val sortedWords: List<WordEntry> =
        words.sortedWith(compareByDescending<WordEntry> { it.frequency }.thenBy { it.text })

    private val fragments: List<WordEntry> = data.fragments
        .filter { it.text.isNotBlank() && it.frequency > 0 }
        .map { WordEntry(it.text.trim().lowercase(), it.frequency) }
        .sortedWith(compareByDescending<WordEntry> { it.frequency }.thenBy { it.text })

    private val byText: Map<String, WordEntry> = words.associateBy { it.text }

    private val totalFrequency: Long = words.sumOf { it.frequency.toLong() }.coerceAtLeast(1L)

    private val bigramTotals: Map<String, Int> = data.bigrams
        .groupBy { it.first.trim().lowercase() }
        .mapValues { entry -> entry.value.sumOf { it.frequency } }

    private val bigramsByFirst: Map<String, Map<String, Int>> = data.bigrams
        .groupBy { it.first.trim().lowercase() }
        .mapValues { entry ->
            entry.value.associate { it.second.trim().lowercase() to it.frequency }
        }

    val wordCount: Int get() = words.size

    fun isKnownWord(word: String): Boolean = byText.containsKey(word.trim().lowercase())

    fun wordsStartingWith(prefix: String): List<WordEntry> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return sortedWords.filter { it.text.startsWith(normalized) && it.text != normalized }
    }

    fun fragmentsStartingWith(prefix: String): List<WordEntry> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return fragments.filter { it.text.startsWith(normalized) && it.text != normalized }
    }

    fun topWords(limit: Int): List<WordEntry> = sortedWords.take(limit.coerceAtLeast(0))

    fun unigramProbability(word: String): Double {
        val entry = byText[word.trim().lowercase()] ?: return 0.0
        return entry.frequency.toDouble() / totalFrequency.toDouble()
    }

    fun bigramProbability(first: String?, second: String): Double {
        val normalizedSecond = second.trim().lowercase()
        val normalizedFirst = first?.trim()?.lowercase()
        if (normalizedFirst.isNullOrEmpty()) return unigramProbability(normalizedSecond)
        val counts = bigramsByFirst[normalizedFirst] ?: return 0.5 * unigramProbability(normalizedSecond)
        val total = bigramTotals[normalizedFirst] ?: 0
        if (total <= 0) return 0.5 * unigramProbability(normalizedSecond)
        val count = counts[normalizedSecond] ?: 0
        // Back off to the unigram model so unseen pairs still score sensibly.
        return 0.7 * (count.toDouble() / total.toDouble()) + 0.3 * unigramProbability(normalizedSecond)
    }
}
