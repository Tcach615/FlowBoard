package com.flowboard.keyboard.steno

import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Held-out evaluation. The sentences come from a Gutenberg novel that is **excluded from the
 * trigram training corpus** (see `tools/build_assets.py` and
 * `app/src/test/resources/eval_sentences.txt`), so these numbers are not measured on text the model
 * was fitted to. The set is small: treat it as a regression signal, not a benchmark.
 *
 * The task mirrors real use: given the words already committed and the sketch the user is about to
 * commit, how highly does the intended word rank?
 */
class PredictionEvaluationTest {

    private val dictionary: StenoDictionary by lazy {
        GZIPInputStream(File("src/main/assets/steno_en.tsvz").inputStream()).use { StenoDictionary.load(it) }
    }
    private val languageModel: LanguageModel by lazy {
        GZIPInputStream(File("src/main/assets/lm_en.tsvz").inputStream()).use { LanguageModel.load(it) }
    }
    private val predictor by lazy { StenoPredictor(dictionary, languageModel) }

    private fun sentences(): List<List<String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream("eval_sentences.txt")
            ?: error("eval_sentences.txt missing from test resources")
        return stream.bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(' ').filter(String::isNotBlank) }
    }

    @Test
    fun `held-out ranking metrics`() {
        val data = sentences()
        assertTrue("evaluation set should not be empty", data.size >= 20)

        var evaluated = 0
        var covered = 0
        var top1 = 0
        var top3 = 0
        var reciprocal = 0.0
        var rankSum = 0
        var candidatesSum = 0
        val latencies = ArrayList<Long>()
        val misses = ArrayList<String>()

        for (sentence in data) {
            val context = ArrayList<String>()
            for (word in sentence) {
                evaluated++
                val entry = dictionary.entries.firstOrNull { it.word == word }
                if (entry == null) {
                    misses.add("$word (no sketch)")
                    context.add(word)
                    continue
                }
                covered++
                val started = System.nanoTime()
                val ranked = predictor.rank(entry.sketch, context, limit = 4)
                latencies.add(System.nanoTime() - started)
                candidatesSum += ranked.size

                val position = ranked.indexOfFirst { it.entry.word == word } + 1
                if (position == 1) top1++
                if (position in 1..3) top3++
                reciprocal += if (position > 0) 1.0 / position else 0.0
                rankSum += if (position > 0) position else 5
                if (position == 0) misses.add("$word ${entry.sketch}")
                context.add(word)
            }
        }

        latencies.sort()
        val p50 = latencies[latencies.size / 2] / 1_000_000.0
        val p95 = latencies[(latencies.size * 0.95).toInt().coerceAtMost(latencies.size - 1)] / 1_000_000.0
        val count = covered.toDouble()

        println("---- held-out ranking (Sherlock Holmes, ${data.size} sentences) ----")
        println("tokens evaluated      $evaluated")
        println("tokens with a sketch  $covered (%.1f%%)".format(100.0 * covered / evaluated))
        println("top-1 accuracy        %.1f%%".format(100 * top1 / count))
        println("top-3 accuracy        %.1f%%".format(100 * top3 / count))
        println("MRR                   %.3f".format(reciprocal / count))
        println("mean rank             %.2f".format(rankSum / count))
        println("mean candidates       %.2f".format(candidatesSum / count))
        println("latency p50 / p95     %.3f ms / %.3f ms".format(p50, p95))
        println("wrong or missing      ${misses.size} (e.g. ${misses.take(6)})")
        println("------------------------------------------------------------------")

        // Floors, not targets: they only catch a regression, the printed numbers are the result.
        assertTrue("coverage fell: $covered/$evaluated", covered >= evaluated * 0.95)
        assertTrue("top-3 accuracy fell below a workable level", top3 / count >= 0.35)
        assertTrue("p95 latency outside budget: $p95 ms", p95 < 10.0)
        assertTrue("average candidate set should be non-empty", candidatesSum / count >= 1.0)
    }
}
