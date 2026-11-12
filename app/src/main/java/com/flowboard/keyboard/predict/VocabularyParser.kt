package com.flowboard.keyboard.predict

import org.json.JSONObject

/** Parses the bundled JSON vocabulary. Kept separate so the engine stays Android-free. */
object VocabularyParser {

    fun parse(json: String): VocabularyData {
        val root = JSONObject(json)
        return VocabularyData(
            language = root.optString("language", "en"),
            words = parseEntries(root.optJSONArray("words")),
            fragments = parseEntries(root.optJSONArray("fragments")),
            bigrams = parseBigrams(root.optJSONArray("bigrams"))
        )
    }

    private fun parseEntries(array: org.json.JSONArray?): List<WordEntry> {
        if (array == null) return emptyList()
        val entries = ArrayList<WordEntry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val text = item.optString("text", "")
            val frequency = item.optInt("frequency", 0)
            if (text.isNotBlank() && frequency > 0) entries.add(WordEntry(text, frequency))
        }
        return entries
    }

    private fun parseBigrams(array: org.json.JSONArray?): List<BigramEntry> {
        if (array == null) return emptyList()
        val entries = ArrayList<BigramEntry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val first = item.optString("first", "")
            val second = item.optString("second", "")
            val frequency = item.optInt("frequency", 0)
            if (first.isNotBlank() && second.isNotBlank() && frequency > 0) {
                entries.add(BigramEntry(first, second, frequency))
            }
        }
        return entries
    }
}
