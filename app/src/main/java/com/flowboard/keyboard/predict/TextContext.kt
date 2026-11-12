package com.flowboard.keyboard.predict

/** Immutable snapshot of the linguistic context, used for undo. */
data class ContextState(val words: List<String>, val prefix: String)

/**
 * Tracks the words already committed to the editor and the token currently being composed.
 * Deliberately free of Android types so it can be unit tested.
 */
class TextContext(private val maxWords: Int = 32) {
    private val words = ArrayList<String>()
    private var prefix: String = ""

    val previousWord: String? get() = words.lastOrNull()
    val currentPrefix: String get() = prefix
    val committedWords: List<String> get() = words.toList()

    fun setPrefix(value: String) {
        prefix = value.lowercase().take(MAX_PREFIX)
    }

    fun commitPrefix() {
        val token = prefix.trim()
        if (token.isNotEmpty()) push(token.lowercase())
        prefix = ""
    }

    fun push(word: String) {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return
        words.add(normalized)
        while (words.size > maxWords) words.removeAt(0)
    }

    fun removeLastWord(): String? = if (words.isEmpty()) null else words.removeAt(words.size - 1)

    fun clear() {
        words.clear()
        prefix = ""
    }

    fun snapshot(): ContextState = ContextState(words.toList(), prefix)

    fun restore(state: ContextState) {
        words.clear()
        words.addAll(state.words)
        prefix = state.prefix
    }

    private companion object {
        const val MAX_PREFIX = 48
    }
}
