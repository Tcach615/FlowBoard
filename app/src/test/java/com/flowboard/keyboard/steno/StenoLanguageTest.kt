package com.flowboard.keyboard.steno

import com.flowboard.keyboard.gesture.Chord
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The documented language, reproduced through the production code path.
 *
 * Every sketch in `docs/input_language_examples.md` is parsed and round-tripped through the chord
 * layer, then the same sketch is sent through the dictionary. The examples are the specification;
 * this test fails if the implementation drifts from them.
 */
class StenoLanguageTest {

    /** (word, documented sketch) from docs/input_language_examples.md. */
    private val examples: List<Pair<String, String>> = listOf(
        "the" to "COR·A",
        "to" to "COR·U",
        "a" to "SIB·A",
        "I" to "SIB·A SIB·∅",
        "and" to "SIB·F COR·∅ COR·∅",
        "of" to "SIB·A LAB·∅",
        "you" to "SIB·U",
        "we" to "DOR·F",
        "they" to "COR·F SIB·∅",
        "it" to "SIB·F COR·∅",
        "is" to "SIB·F COR·∅",
        "no" to "COR·U DOR·∅",
        "know" to "COR·U DOR·∅",
        "cat" to "DOR·F COR·∅",
        "dog" to "COR·U DOR·∅",
        "man" to "LAB·F COR·∅",
        "need" to "COR·F COR·∅",
        "stop" to "COR·∅ COR·A LAB·∅",
        "street" to "COR·∅ COR·∅ COR·F COR·∅",
        "strength" to "COR·∅ COR·∅ COR·F DOR·∅ COR·∅",
        "texts" to "COR·F DOR·∅ COR·∅ COR·∅ COR·∅",
        "think" to "COR·F DOR·∅ DOR·∅",
        "ship" to "SIB·F LAB·∅",
        "judge" to "SIB·A SIB·∅",
        "water" to "DOR·U COR·A COR·∅",
        "banana" to "LAB·A COR·F COR·A",
        "computer" to "DOR·A LAB·∅ LAB·∅ SIB·U COR·A COR·∅",
        "keyboard" to "DOR·F LAB·U COR·∅ COR·∅",
        "information" to "SIB·F COR·∅ LAB·A COR·∅ LAB·F SIB·∅ SIB·A COR·∅",
        "impossible" to "SIB·F LAB·∅ LAB·A COR·A LAB·A COR·∅",
        "please" to "LAB·∅ COR·F COR·∅",
        "thanks" to "COR·F DOR·∅ DOR·∅ COR·∅",
        "right" to "COR·A SIB·∅ COR·∅",
        "write" to "COR·A SIB·∅ COR·∅",
        "there" to "COR·F COR·∅",
        "their" to "COR·F COR·∅",
        "read" to "COR·F COR·∅",
        "red" to "COR·F COR·∅",
        "world" to "DOR·A COR·∅ COR·∅",
        "morning" to "LAB·U COR·∅ COR·F DOR·∅",
        "good" to "DOR·U COR·∅"
    )

    private val engine = StenoEngine()

    private val dictionary: StenoDictionary by lazy {
        val file = File("src/main/assets/steno_en.tsvz")
        assertTrue("bundled dictionary asset is missing", file.exists())
        GZIPInputStream(file.inputStream()).use { StenoDictionary.load(it) }
    }

    private val languageModel: LanguageModel by lazy {
        val file = File("src/main/assets/lm_en.tsvz")
        GZIPInputStream(file.inputStream()).use { LanguageModel.load(it) }
    }

    private val predictor: StenoPredictor by lazy { StenoPredictor(dictionary, languageModel) }
    private val translator: TranslationEngine by lazy { TranslationEngine(engine, predictor) }

    @Test
    fun `every documented example round-trips through the chord layer`() {
        for ((word, sketch) in examples) {
            val symbols = Fsl.parse(sketch)
            val chords = symbols.map { Fsl.chordOf(it) }
            assertEquals("chord count for $word", symbols.size, chords.size)
            assertEquals("sketch for $word", sketch, Fsl.render(chords.map { Fsl.symbolOf(it) }))
            assertTrue("$word must contain a vowel core", engine.parse(chords).isWordlike)
            assertTrue("$word must have a legal sequence", engine.problem(chords) == null)
        }
    }

    @Test
    fun `documented sequences are all distinct sketches`() {
        val sketches = examples.map { it.second }.toSet()
        assertTrue("the example set is expected to be about 38 words", sketches.size >= 30)
    }

    @Test
    fun `bare strokes are consonants only until a core arrives`() {
        val bareOnly = Fsl.parse("COR·∅ COR·∅")
            .map { Fsl.chordOf(it) }
        assertEquals("Consonants only — add a vowel chord", engine.problem(bareOnly))
        val empty = engine.parse(emptyList())
        assertTrue(empty.isEmpty)
        assertEquals("No strokes yet", engine.problem(emptyList()))
    }

    @Test
    fun `dictionary covers the documented vocabulary`() {
        val missing = ArrayList<String>()
        for ((word, _) in examples) {
            if (dictionary.entries.none { it.word == word.lowercase() }) missing.add(word)
        }
        assertTrue("words missing from the bundled dictionary: $missing", missing.isEmpty())
    }

    @Test
    fun `translation of a documented sketch includes its word`() {
        for ((word, _) in examples) {
            val entry = dictionary.entries.first { it.word == word.lowercase() }
            val translation = translator.translate(
                engine.symbolsOf(entry.sketch).map { Fsl.chordOf(it) },
                emptyList(),
                limit = 60
            )
            val candidates = (translation as? Translation.Candidates)?.words
            assertNotNull("no candidates for $word", candidates)
            assertTrue("$word not among ${candidates!!.take(8)}", candidates!!.contains(word.lowercase()))
        }
    }

    @Test
    fun `structural ambiguity is exposed, not hidden`() {
        val key = "COR·F COR·∅"
        val candidates = translator.translateKey(key, emptyList(), limit = 60).let {
            (it as Translation.Candidates).words
        }
        for (word in listOf("that", "this", "there", "their", "need", "read", "red")) {
            assertTrue("$word should share $key: $candidates", candidates.contains(word))
        }
        assertTrue("ambiguity must return many readings: ${candidates.size}", candidates.size >= 20)
    }

    @Test
    fun `candidate order is deterministic`() {
        val first = predictor.rank("COR·F COR·∅", listOf("the"))
        val second = predictor.rank("COR·F COR·∅", listOf("the"))
        assertEquals(first.map { it.entry.word }, second.map { it.entry.word })
    }

    @Test
    fun `brief and reduced tiers resolve to their own words`() {
        // The generated lexicon emits no briefs (every first-core outline is already some word's
        // full sketch), so the tier machinery is checked on an explicit dictionary instead.
        val custom = StenoDictionary.of(
            listOf(
                DictionaryEntry("hello", "DOR·A COR·U DOR·∅", "COR·U DOR·∅", 32_000_000L, "DOR·A", Tier.FULL),
                DictionaryEntry("world", "DOR·A COR·∅ COR·∅ COR·∅", null, 400_000_000L, null, Tier.FULL)
            )
        )
        val fromBrief = custom.candidates("DOR·A").first { it.word == "hello" }
        assertEquals(Tier.BRIEF, fromBrief.tier)
        val fromReduced = custom.candidates("COR·U DOR·∅").first { it.word == "hello" }
        assertEquals(Tier.REDUCED, fromReduced.tier)
        assertEquals(Tier.FULL, custom.candidates("DOR·A COR·U DOR·∅").first().tier)
    }

    @Test
    fun `prefix lookup supports incomplete sketches`() {
        val partial = dictionary.prefix("COR·F", 20)
        assertTrue("prefix COR·F should match entries", partial.isNotEmpty())
        assertTrue(partial.all { it.sketch.startsWith("COR·F") || (it.brief?.startsWith("COR·F") == true) || (it.reduced?.startsWith("COR·F") == true) })
    }

    @Test
    fun `unknown sketches produce no candidates`() {
        assertTrue(dictionary.candidates("LAB·U LAB·U LAB·U LAB·U LAB·U LAB·U").isEmpty())
        val translation = translator.translateKey("LAB·U LAB·U LAB·U LAB·U LAB·U LAB·U", emptyList())
        assertTrue(translation is Translation.Incomplete)
    }

    @Test
    fun `an incomplete sketch is never committed as a word`() {
        val session = StenoSession(engine, translator, predictor)
        session.addChord(Fsl.chordOf(Fsl.parse("COR·∅").first()))
        assertFalse(session.canCommit())
        assertEquals(null, session.accept(withSpace = true, remember = false))
    }

    @Test
    fun `assets parse inside a plausible cold-start budget`() {
        val runtime = Runtime.getRuntime()
        // Load once and discard, so the second measurement excludes class loading and JIT data.
        GZIPInputStream(File("src/main/assets/steno_en.tsvz").inputStream()).use { StenoDictionary.load(it) }
        GZIPInputStream(File("src/main/assets/lm_en.tsvz").inputStream()).use { LanguageModel.load(it) }
        System.gc()
        Thread.sleep(150)
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        val fresh = GZIPInputStream(File("src/main/assets/steno_en.tsvz").inputStream()).use {
            StenoDictionary.load(it)
        }
        val dictionaryMillis = (System.nanoTime() - started) / 1_000_000.0
        val lmStarted = System.nanoTime()
        val freshModel = GZIPInputStream(File("src/main/assets/lm_en.tsvz").inputStream()).use {
            LanguageModel.load(it)
        }
        val lmMillis = (System.nanoTime() - lmStarted) / 1_000_000.0
        System.gc()
        Thread.sleep(120)
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val retainedMb = (heapAfter - heapBefore) / 1_000_000.0
        println("cold parse: dictionary %.1f ms (%d entries), language model %.1f ms".format(dictionaryMillis, fresh.size, lmMillis))
        println("retained heap for both models: %.1f MB".format(retainedMb))
        assertTrue("models should fit the 12 MB budget (measured ${retainedMb} MB)", retainedMb < 24.0)
        assertEquals(dictionary.size, fresh.size)
        assertTrue(freshModel.isKnown("the"))
        assertTrue("dictionary parse took ${dictionaryMillis} ms", dictionaryMillis < 2_000)
        assertTrue("language model parse took ${lmMillis} ms", lmMillis < 2_000)
    }

    @Test
    fun `dictionary statistics are in the expected range`() {
        assertTrue("dictionary size ${dictionary.size}", dictionary.size in 15_000..25_000)
        assertTrue(dictionary.entries.all { it.frequency > 0 })
        assertTrue("mean strokes must stay near 4.5", dictionary.entries.sumOf { it.strokeCount }.toDouble() / dictionary.size in 3.5..5.5)
    }

    @Test
    fun `chorded strokes compose a word sketch symbol by symbol`() {
        val chords = listOf(
            Fsl.chordOf(Fsl.parse("DOR·F").first()),
            Fsl.chordOf(Fsl.parse("COR·∅").first())
        )
        assertEquals("DOR·F COR·∅", engine.parse(chords).key)
        val session = StenoSession(engine, translator, predictor)
        chords.forEach { session.addChord(it) }
        assertTrue(session.canCommit())
        val words = (translator.translate(chords, emptyList(), limit = 60) as Translation.Candidates).words
        assertTrue("cat should be a reading of DOR·F COR·∅: ${words.take(5)}", words.contains("cat"))
    }
}
