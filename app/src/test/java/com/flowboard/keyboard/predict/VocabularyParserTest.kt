package com.flowboard.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyParserTest {

    @Test
    fun `parser reads words fragments and bigrams`() {
        val json = """
            {
              "language": "en",
              "words": [{"text": "Hello", "frequency": 5}, {"text": "", "frequency": 9}],
              "fragments": [{"text": "ing", "frequency": 2}],
              "bigrams": [{"first": "hello", "second": "world", "frequency": 3}]
            }
        """.trimIndent()

        val data = VocabularyParser.parse(json)

        assertEquals("en", data.language)
        assertEquals(1, data.words.size)
        assertEquals("hello", Vocabulary(data).wordsStartingWith("hel").first().text)
        assertEquals(1, data.fragments.size)
        assertEquals(1, data.bigrams.size)
    }

    @Test
    fun `empty arrays produce an empty vocabulary`() {
        val data = VocabularyParser.parse("""{"language":"en","words":[],"fragments":[],"bigrams":[]}""")
        assertTrue(data.words.isEmpty())
        assertEquals(0, Vocabulary(data).wordCount)
    }
}
