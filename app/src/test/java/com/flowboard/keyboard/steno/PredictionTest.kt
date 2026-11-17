package com.flowboard.keyboard.steno

import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking and next-chord prediction. Nothing here is allowed to remove a chord or commit text.
 */
class PredictionTest {

    private val engine = StenoEngine()

    private val dictionary: StenoDictionary by lazy {
        GZIPInputStream(File("src/main/assets/steno_en.tsvz").inputStream()).use { StenoDictionary.load(it) }
    }
    private val languageModel: LanguageModel by lazy {
        GZIPInputStream(File("src/main/assets/lm_en.tsvz").inputStream()).use { LanguageModel.load(it) }
    }
    private val predictor by lazy { StenoPredictor(dictionary, languageModel) }
    private val translator by lazy { TranslationEngine(engine, predictor) }

    @Test
    fun `language model prefers plausible words`() {
        val common = languageModel.probability("the", listOf("of"))
        val rare = languageModel.probability("aardvark", listOf("of"))
        assertTrue("P(the|of)=$common should exceed P(aardvark|of)=$rare", common > rare)
        assertTrue(common > 0.0)
        assertTrue(languageModel.isKnown("the"))
    }

    @Test
    fun `context raises the score of a word that fits`() {
        val key = "COR·F COR·∅"
        val neutral = predictor.rank(key, emptyList(), limit = 12)
        val primed = predictor.rank(key, listOf("over", "there"), limit = 12)
        val neutralThere = neutral.first { it.entry.word == "there" }.score
        val primedThere = primed.first { it.entry.word == "there" }.score
        assertTrue("context should lift 'there' ($neutralThere -> $primedThere)", primedThere > neutralThere)
    }

    @Test
    fun `different contexts reorder the same candidate set`() {
        val key = "COR·F COR·∅"
        val needs = predictor.rank(key, listOf("i", "really"), limit = 7).map { it.entry.word }
        val places = predictor.rank(key, listOf("over", "there"), limit = 7).map { it.entry.word }
        assertTrue("ordering should depend on context: $needs vs $places", needs != places)
        assertTrue("'over ...' should favour there: $places", "there" in places.take(3))
    }

    @Test
    fun `empty context still ranks by dictionary prior`() {
        val ranked = predictor.rank("COR·A", emptyList())
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test
    fun `next chord prediction is a distribution over real chords`() {
        val predictions = predictor.nextChords("COR·F", listOf("i"))
        assertTrue(predictions.isNotEmpty())
        assertTrue(predictions.size <= 4)
        assertTrue(predictions.all { it.chord in Fsl.ALL_CHORDS })
        assertTrue(predictions.all { it.score > 0.0 && it.score <= 1.0 })
        // Never more chords than exist, and never an invented chord.
        assertEquals(predictions.map { it.chord }.toSet().size, predictions.size)
    }

    @Test
    fun `next chord prediction exists before the first stroke`() {
        val predictions = predictor.nextChords("", listOf("the"))
        assertTrue("the keyboard should suggest openers", predictions.isNotEmpty())
    }

    @Test
    fun `unknown sketch yields nothing to highlight`() {
        assertTrue(predictor.nextChords("LAB·U LAB·U LAB·U LAB·U LAB·U LAB·U", emptyList()).isEmpty())
    }

    @Test
    fun `ranking respects the requested limit`() {
        assertEquals(1, predictor.rank("COR·F COR·∅", emptyList(), limit = 1).size)
        assertEquals(3, predictor.rank("COR·F COR·∅", emptyList(), limit = 3).size)
    }

    @Test
    fun `accept inserts the selected alternative and undo restores the sketch`() {
        val session = StenoSession(engine, translator, predictor)
        listOf("COR·F", "COR·∅").forEach { session.addChord(Fsl.chordOf(Fsl.parse(it).first())) }
        val before = session.candidates().map { it.entry.word }
        assertEquals(0, session.selected)
        session.cycleAlternative()
        val text = session.accept(withSpace = true, remember = false)
        assertEquals(before[1] + " ", text)
        assertEquals(0, session.chordCount)
        assertEquals(listOf(before[1]), session.context.committedWords)

        val undone = session.undo()
        assertEquals(before[1] + " ", undone)
        assertEquals(2, session.chordCount)
        assertEquals(before, session.candidates().map { it.entry.word })
        assertTrue("context must roll back too", session.context.committedWords.isEmpty())
    }

    @Test
    fun `undo after two commits restores the last one only`() {
        val session = StenoSession(engine, translator, predictor)
        Fsl.parse("COR·A").forEach { session.addChord(Fsl.chordOf(it)) }
        val first = session.accept(withSpace = true, remember = false)
        Fsl.parse("SIB·U").forEach { session.addChord(Fsl.chordOf(it)) }
        val second = session.accept(withSpace = true, remember = false)
        assertNotNull("first word should commit", first)
        assertNotNull("second word should commit", second)
        assertEquals(2, session.context.committedWords.size)
        assertEquals(second!!.trim(), session.undo()!!.trim())
        assertEquals(listOf(first!!.trim()), session.context.committedWords)
        assertEquals("undo restores the one stroke of the second word", 1, session.chordCount)
    }

    @Test
    fun `backspace removes one stroke and keeps the rest`() {
        val session = StenoSession(engine, translator, predictor)
        Fsl.parse("DOR·F COR·∅").forEach { session.addChord(Fsl.chordOf(it)) }
        assertTrue(session.backspace())
        assertEquals("DOR·F", engine.parse(session.chords).key)
        assertTrue(session.backspace())
        assertEquals(0, session.chordCount)
        assertFalse(session.backspace())
    }

    @Test
    fun `personalisation boosts a frequently used word`() {
        val personal = com.flowboard.keyboard.predict.InMemoryPersonalization()
        repeat(10) { personal.record("red") }
        val withMemory = StenoPredictor(dictionary, languageModel, personalization = personal)
        val ranked = withMemory.rank("COR·F COR·∅", emptyList(), limit = 60)
        val redScore = ranked.first { it.entry.word == "red" }.score
        val baseline = predictor.rank("COR·F COR·∅", emptyList(), limit = 60)
            .first { it.entry.word == "red" }.score
        assertTrue("personal use should lift 'red' ($baseline -> $redScore)", redScore > baseline)
    }

    @Test
    fun `prediction latency stays inside the documented budget`() {
        val samples = ArrayList<Long>()
        // Warm up the JIT before timing: the first call includes class loading and allocation.
        repeat(20) { predictor.nextChords("COR·F", listOf("i", "think")) }
        repeat(300) { index ->
            val context = listOf("i", "think", "the")
            val key = if (index % 2 == 0) "COR·F COR·∅" else "SIB·F COR·∅"
            val started = System.nanoTime()
            predictor.rank(key, context)
            predictor.nextChords(key, context)
            samples.add(System.nanoTime() - started)
        }
        samples.sort()
        val p95 = samples[(samples.size * 0.95).toInt().coerceAtMost(samples.size - 1)] / 1_000_000.0
        val mean = samples.average() / 1_000_000.0
        println("prediction latency: mean %.3f ms, p95 %.3f ms, max %.3f ms".format(mean, p95, samples.last() / 1_000_000.0))
        assertTrue("p95 ${p95}ms must stay under the 10 ms budget", p95 < 10.0)
    }
}
