package com.flowboard.keyboard.predict

import com.flowboard.keyboard.model.CandidateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {

    private val vocabulary = Vocabulary(
        VocabularyData(
            language = "en",
            words = listOf(
                WordEntry("hello", 100),
                WordEntry("help", 10),
                WordEntry("held", 5),
                WordEntry("world", 80),
                WordEntry("word", 40),
                WordEntry("the", 60)
            ),
            fragments = listOf(WordEntry("elp", 5)),
            bigrams = listOf(
                BigramEntry("hello", "world", 30),
                BigramEntry("the", "world", 1)
            )
        )
    )

    private fun engine(personalization: PersonalizationStore? = null) =
        PredictionEngine(vocabulary, personalization = personalization)

    @Test
    fun `empty context produces words plus spacing commands`() {
        val candidates = engine().predict(TextContext())
        assertTrue(candidates.isNotEmpty())
        assertEquals("hello", candidates.first().text)
        assertTrue(candidates.any { it.kind == CandidateKind.SPACE })
        assertTrue(candidates.any { it.kind == CandidateKind.PUNCTUATION })
    }

    @Test
    fun `prefix completions all start with the prefix`() {
        val context = TextContext()
        context.setPrefix("hel")
        val candidates = engine().predict(context)
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.text.startsWith("hel") })
        assertEquals("hello", candidates.first().text)
    }

    @Test
    fun `context changes the ranking`() {
        val context = TextContext()
        context.push("hello")
        val candidates = engine().predict(context)
        assertEquals("world", candidates.first().text)
    }

    @Test
    fun `unknown prefix is preserved so the user can extend it`() {
        val context = TextContext()
        context.setPrefix("zzz")
        val candidates = engine().predict(context)
        assertEquals(1, candidates.size)
        assertEquals("zzz", candidates.first().text)
        assertEquals(CandidateKind.FRAGMENT, candidates.first().kind)
    }

    @Test
    fun `no vocabulary still yields usable commands`() {
        val empty = Vocabulary(VocabularyData(language = "en"))
        val candidates = PredictionEngine(empty).predict(TextContext())
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.none { it.kind == CandidateKind.WORD })
    }

    @Test
    fun `ranking is deterministic`() {
        val context = TextContext()
        context.setPrefix("hel")
        val first = engine().predict(context).map { it.text }
        val second = engine().predict(context).map { it.text }
        assertEquals(first, second)
    }

    @Test
    fun `personalization promotes a previously chosen candidate`() {
        val personalization = InMemoryPersonalization()
        repeat(6) { personalization.record("help") }
        val context = TextContext()
        context.setPrefix("hel")
        val candidates = engine(personalization).predict(context)
        assertEquals("help", candidates.first().text)
    }

    @Test
    fun `limit is respected`() {
        val candidates = engine().predict(TextContext(), limit = 2)
        assertEquals(2, candidates.size)
    }
}
