package com.flowboard.keyboard.steno

import java.io.BufferedReader
import java.io.InputStream

/**
 * Tiered sketch dictionary. Unlike Plover, one outline may legitimately map to several words:
 * FSL-1 codes phoneme classes, so ambiguity is structural. The dictionary therefore returns an
 * *ordered* set of candidates rather than a single translation
 * (`docs/flowboard_language_design.md` §5.4).
 *
 * Tiers, most precise first:
 *  - [Tier.FULL]     the word's complete sketch
 *  - [Tier.BRIEF]    a curated compression (first core) of a very frequent word
 *  - [Tier.REDUCED]  the word's stressed-syllable sketch, used for fluent writing
 */
enum class Tier(val rank: Int, val weight: Double) {
    FULL(0, 1.0),
    BRIEF(1, 0.55),
    REDUCED(2, 0.82)
}

data class DictionaryEntry(
    val word: String,
    val sketch: String,
    val reduced: String?,
    val frequency: Long,
    val brief: String?,
    val tier: Tier
) {
    val strokeCount: Int get() = sketch.count { it == ' ' } + 1
}

class StenoDictionary private constructor(
    val entries: List<DictionaryEntry>,
    private val exact: Map<String, List<DictionaryEntry>>,
    private val briefs: Map<String, List<DictionaryEntry>>,
    private val reduced: Map<String, List<DictionaryEntry>>,
    private val trie: SymbolTrie,
    private val maxFrequency: Long,
    private val ranked: List<DictionaryEntry>
) {

    val size: Int get() = entries.size

    /** Most frequent entries, used to seed next-chord prediction before the first stroke. */
    fun topWords(limit: Int): List<DictionaryEntry> = ranked.take(limit)

    /** Candidates for a committed sketch, most precise tier first. */
    fun candidates(key: String): List<DictionaryEntry> {
        if (key.isBlank()) return emptyList()
        val result = LinkedHashMap<String, DictionaryEntry>()
        exact[key]?.forEach { result.putIfAbsent(it.word, it) }
        briefs[key]?.forEach { result.putIfAbsent(it.word, it) }
        reduced[key]?.forEach { result.putIfAbsent(it.word, it) }
        return result.values.toList()
    }

    /** Entries whose sketch continues [key]; used while the word is still being written. */
    fun prefix(key: String, limit: Int): List<DictionaryEntry> {
        if (key.isBlank()) return emptyList()
        return trie.entriesWithPrefix(key.split(' ').filter { it.isNotBlank() }, limit)
    }

    /** Log-scaled so mid-frequency words are not treated as infinitely rarer than "the". */
    fun frequencyPrior(entry: DictionaryEntry): Double =
        StenoPredictor.frequencyPrior(entry, maxFrequency)

    companion object {
        const val HEADER = "#version"

        /** Empty dictionary used when the asset cannot be read; keeps the IME usable. */
        /** Builds a small dictionary from explicit entries; used by tests and future user dictionaries. */
        fun of(entries: List<DictionaryEntry>): StenoDictionary {
            val exact = HashMap<String, MutableList<DictionaryEntry>>()
            val briefs = HashMap<String, MutableList<DictionaryEntry>>()
            val reduced = HashMap<String, MutableList<DictionaryEntry>>()
            var maxFrequency = 0L
            for (entry in entries) {
                exact.getOrPut(entry.sketch) { ArrayList() }.add(entry)
                entry.brief?.let { briefs.getOrPut(it) { ArrayList() }.add(entry.copy(tier = Tier.BRIEF)) }
                entry.reduced?.let { reduced.getOrPut(it) { ArrayList() }.add(entry.copy(tier = Tier.REDUCED)) }
                maxFrequency = maxOf(maxFrequency, entry.frequency)
            }
            val trie = SymbolTrie()
            entries.forEach { trie.insert(it.sketch.split(' '), it) }
            val byFrequency = compareByDescending<DictionaryEntry> { it.frequency }.thenBy { it.word }
            val ranked = entries.sortedWith(byFrequency)
            return StenoDictionary(entries, exact, briefs, reduced, trie, maxFrequency, ranked)
        }

        fun empty(): StenoDictionary =
            StenoDictionary(emptyList(), emptyMap(), emptyMap(), emptyMap(), SymbolTrie(), 0L, emptyList())

        fun load(input: InputStream): StenoDictionary {
            val collected = ArrayList<DictionaryEntry>(24_000)
            val exact = HashMap<String, MutableList<DictionaryEntry>>()
            val briefs = HashMap<String, MutableList<DictionaryEntry>>()
            val reduced = HashMap<String, MutableList<DictionaryEntry>>()
            var maxFrequency = 0L

            input.bufferedReader().use { reader: BufferedReader ->
                var line = reader.readLine()
                var header = true
                while (line != null) {
                    if (line.startsWith("#")) {
                        line = reader.readLine()
                        continue
                    }
                    if (header) {
                        // Skip the column-name row.
                        header = false
                        line = reader.readLine()
                        continue
                    }
                    val fields = line.split('\t')
                    if (fields.size < 4) {
                        line = reader.readLine()
                        continue
                    }
                    val word = fields[0]
                    val sketch = fields[1]
                    val reducedSketch = fields[2].ifBlank { null }
                    val frequency = fields[3].toLongOrNull() ?: 0L
                    val briefSketch = fields.getOrNull(4)?.ifBlank { null }
                    if (word.isNotEmpty() && sketch.isNotEmpty()) {
                        val full = DictionaryEntry(word, sketch, reducedSketch, frequency, briefSketch, Tier.FULL)
                        collected.add(full)
                        exact.getOrPut(sketch) { ArrayList() }.add(full)
                        if (reducedSketch != null) {
                            reduced.getOrPut(reducedSketch) { ArrayList() }
                                .add(full.copy(tier = Tier.REDUCED))
                        }
                        if (briefSketch != null) {
                            briefs.getOrPut(briefSketch) { ArrayList() }
                                .add(full.copy(tier = Tier.BRIEF))
                        }
                        if (frequency > maxFrequency) maxFrequency = frequency
                    }
                    line = reader.readLine()
                }
            }

            val byFrequency = compareByDescending<DictionaryEntry> { it.frequency }.thenBy { it.word }
            exact.values.forEach { it.sortWith(byFrequency) }
            briefs.values.forEach { it.sortWith(byFrequency) }
            reduced.values.forEach { it.sortWith(byFrequency) }

            val trie = SymbolTrie()
            collected.forEach { trie.insert(it.sketch.split(' '), it) }

            return StenoDictionary(
                collected, exact, briefs, reduced, trie, maxFrequency,
                collected.sortedWith(byFrequency)
            )
        }
    }
}

/**
 * Packed prefix tree over sketch symbols. Lookup is linear in the number of strokes, and the
 * subtree walk is bounded by [entriesWithPrefix]'s limit so an empty prefix cannot dump the whole
 * lexicon.
 */
class SymbolTrie {

    private class Node {
        val children = LinkedHashMap<String, Node>()
        val terminal = ArrayList<DictionaryEntry>()
    }

    private val root = Node()

    fun insert(symbols: List<String>, entry: DictionaryEntry) {
        var node = root
        for (symbol in symbols) {
            node = node.children.getOrPut(symbol) { Node() }
        }
        node.terminal.add(entry)
    }

    fun entriesWithPrefix(prefix: List<String>, limit: Int): List<DictionaryEntry> {
        var node = root
        for (symbol in prefix) {
            node = node.children[symbol] ?: return emptyList()
        }
        val found = ArrayList<DictionaryEntry>()
        val queue = ArrayDeque<Node>()
        queue.add(node)
        while (queue.isNotEmpty() && found.size < limit) {
            val current = queue.removeFirst()
            current.terminal.sortedByDescending { it.frequency }.forEach {
                if (found.size < limit) found.add(it)
            }
            current.children.values.forEach(queue::addLast)
        }
        return found.sortedByDescending { it.frequency }.take(limit)
    }
}
