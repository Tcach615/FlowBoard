# Context-aware prediction architecture

Status: **proposal**. The shipped app has a different, simpler predictor
(`PredictionEngine`: frequency + bigram + prefix over a 110-word vocabulary). This document describes
what a prediction layer for the stenographic language would have to be, not what exists.

Related: [language design](flowboard_language_design.md) ·
[research](stenographic_research.md) · [examples](input_language_examples.md) ·
[architecture](architecture_proposal.md) · [evaluation](evaluation_plan.md).

## 1. Responsibilities and invariants

The predictor has exactly two jobs:

1. **Rank the current sketch's candidate words** so the intended word is usually first.
2. **Predict the next stroke** so the next chord can be highlighted, and so a reduced-form or brief
   can be offered with confidence.

It must never:

* change what a chord means (design invariant I6);
* insert or commit text on its own;
* hide the full chord inventory permanently;
* keep typing history in sensitive fields;
* require a network.

The separation of concerns is the point: **the language decides what can be written, the predictor
decides what is likely next, the user decides what is written.**

## 2. State model

```
CommittedText        the words already accepted into the editor (bounded, e.g. last 32)
CurrentSketch        the chord sequence stroked since the last commit, plus its parse
CandidateSet         ordered dictionary entries for CurrentSketch
StrokePosition       which stroke index within the top candidate(s) the user is at
SessionProfile       local frequency adjustments (off in sensitive fields)
```

`CommittedText` is already modelled by `TextContext` in the app; `CurrentSketch` and `CandidateSet`
are new.

State transitions:

| Event | Effect |
| --- | --- |
| chord committed | append to `CurrentSketch`, re-query dictionary, re-rank |
| stroke added after a core (bare) | narrow the candidate set |
| space / `✓` | accept top candidate (or the user's selection) into `CommittedText`; clear sketch |
| `⇄` alternatives | keep sketch, cycle/expand the candidate list |
| undo | revert exactly the last acceptance, restore sketch |
| editor change / password field | clear `CommittedText` and `CandidateSet` |

## 3. The four context levels

| Level | Signal | What it can do |
| --- | --- | --- |
| **L1 phonetic** | the sketch's own structure | prune candidates that cannot continue the current sketch; predict the *family* of the next chord |
| **L2 word** | dictionary entries sharing the sketch prefix | show only words compatible with what has been stroked |
| **L3 linguistic** | previous one or two words | reorder candidates (`P(word | previous words)`) |
| **L4 sentence** | longer context, dialogue register | future work; needs a stronger model than an n-gram and is explicitly out of scope for the first version |

Only L1–L3 are proposed for the first implementation. L4 is named so that the interface does not
foreclose it.

## 4. Ranking model

For a committed sketch `S` and context `C` (previous words):

```
P(w | S, C) ∝ P(S | w) · P(w | C)
```

* `P(S | w)` is a **dictionary property**, not a learned one: 1.0 if `S` is the word's full sketch,
  slightly below 1 for a reduced form, lower for a brief that diverges from the word's own sketch.
  It is a constant per (sketch, word) pair precomputed at build time.
* `P(w | C)` is the language model: a trigram with backoff, plus a session-local boost.

So the score is a transparent sum of a dictionary weight and a context weight, in the same spirit as
the current `ScoreWeights`, but with the language model replacing prefix matching:

```
score(w) = w_dict · dictPrior(S, w)
         + w_ctx  · P_lm(w | previous words)
         + w_pers · personalBoost(w)
         - w_cost · interactionCost(w)        // alternatives shown, length, rarity
```

The weights are configuration, not magic, and must be evaluated rather than asserted.

### 4.1 Next-stroke prediction

The useful twist for a chord keyboard: predict the **chord**, not just the word.

```
P(next chord c | S, C) = Σ_w P(w | S, C) · [c is the next symbol of w's sketch]
```

Summing over the candidate set gives a distribution over the 16 chords. The top-k chords get
highlighted on the pads (same positions, extra emphasis), which is a pure visual aid: pressing any
other chord still works and still means what it always means.

This is where prediction pays for itself: for reduced forms and ambiguous sketches, the highlight
tells the user which stroke disambiguates without them learning a second alphabet.

## 5. Algorithms — evaluated before choosing

| Approach | Memory | Latency | Fit | Verdict |
| --- | --- | --- | --- | --- |
| Frequency list only | trivial | <1 ms | poor: no context | baseline only |
| Trie over sketches and words | small for 20k–100k entries; lookup linear in key length, but pointer overhead is real ([Tightly Packed Tries](https://aclanthology.org/W09-1505.pdf)) | sub-ms | required for L2 prefix/narrowing | **adopt** |
| Unigram + bigram | small | <1 ms | weak at sentence level; current app already does this | fallback tier |
| **Trigram with backoff** | measured prototype: 2.30 MB on disk / 10.47 MB RAM for top-100k vocab, 200k bigrams, 250k trigrams; 2.2–2.4 ms per suggestion on a Galaxy S8 ([Op-Ngram](https://arxiv.org/pdf/2101.03967)) | 2–5 ms | best accuracy/effort ratio for offline | **adopt (v1)** |
| 5-gram (Gboard-style) | production description: 1.25 M n-grams, 164 k unigrams ([Gboard paper](https://arxiv.org/html/1811.03604)); keyboard LMs "should not exceed 5 to 10 Mb" ([FST decoder](https://arxiv.org/pdf/1704.03987)) | unknown; ~20 ms feedback expectation | better, but the gain over trigram is unmeasured here | later if measured to help |
| WFST / OpenGrm | ~8 bytes per arc + 8 bytes per state ([OpenFst](https://www.openfst.org/twiki/bin/view/FST/FstExtensions)); cyclic n-gram FSTs ([OpenGrm](https://www.openfst.org/twiki/bin/view/GRM/NGramQuickTour)) | excellent, composes lexicon + LM (`C ∘ L ∘ G`) | powerful but heavy tooling and build complexity | revisit only if trie+n-gram hits a wall |
| Compact neural LM | tens of MB typical | device-dependent | violates the "lightweight, no LLM" constraint | rejected for now |

Smoothing: **Stupid Backoff** or **Katz** for v1 (simple, cheap, well described in
[Jurafsky & Martin §3.6](https://web.stanford.edu/~jurafsky/slp3/old_feb24/3.pdf)); Kneser–Ney is the
better model (§3.8) and is a later swap behind the same interface. The Op-Ngram prototype used
Stupid Backoff with pruning, which is the closest published configuration at this size.

### 5.1 Chosen stack for v1

1. **Trie** keyed by sketch symbol sequence → candidate word IDs (built offline).
2. **Trigram LM** over words, top ~50k vocabulary, pruned, quantised, memory-mapped from assets.
3. **Session personalisation** — small in-memory counts.
4. **Deterministic tie-break** by `(score, frequency, word)` so tests can assert exact ordering.

Memory budget: **≤ 12 MB resident** for dictionary + trigram (the measured prototype sits at
10.47 MB, so this is realistic rather than aspirational). First-chord latency target: **≤ 10 ms** on
a mid-range phone, to stay inside the ~20 ms visible-response expectation; this is a *target to
measure*, not a measured result.

## 6. Filtering versus prioritising

The brief asks which is better. Three options:

| Option | Behaviour | Advantages | Costs |
| --- | --- | --- | --- |
| **A. Highlight only** | all 16 chords remain available and unchanged; likely ones are emphasised | never blocks the user; invariant I6 trivially holds; zero learning interference | less reduction of choices; visual noise if everything is highlighted |
| **B. Dynamic filtering** | unlikely chords are hidden or dimmed to near-invisible | fewer visible choices; faster on known text | the keyboard changes shape under the user; violates the spirit of "stable mapping"; impossible to type an unexpected word without a mode switch |
| **C. Adaptive combination** | highlight by default; filtering only after the user opts in per session | both behaviours measured | extra mode to learn and to test |

**Recommendation: A for v1, with C as an experiment.** Rationale: the brief's invariant I6 and the
HCI evidence both point the same way — a chord keyboard's learnability depends on the mapping being
stable, and the Senorita study found chording rarely exceeded ~10% of keystrokes even after practice,
so hiding chords would rarely help and could prevent recovery. Option B is rejected unless an
evaluation shows a large, consistent gain.

Visual rules for A:

* highlight colour/outline on likely chords, never a change of label;
* at most 4 highlights, sorted by probability, so the display does not become noise;
* highlights disappear the moment the user's thumb lands (the state machine already reports this);
* a "no prediction" toggle in settings that removes highlights entirely.

## 7. Fallback behaviour

The system must remain fully usable when prediction is uncertain or disabled:

| Situation | Behaviour |
| --- | --- |
| empty candidate set for a sketch | show the sketch as *unknown*; offer alternatives, undo, and (future) spell mode |
| prediction disabled | all chords unhighlighted, dictionary still ranks candidates by frequency |
| model fails to load | log locally, fall back to unigram + dictionary; never block the IME |
| OOV word | user can undo and re-stroke a fuller sketch; no silent guessing |
| sensitive field | stateless ranking: dictionary order only, no personal boost, no context |
| user disagrees with top candidate | `⇄` opens the full ordered list; `↺` reverts |

## 8. Interfaces

Kotlin-facing sketch of the seams (names for discussion, not code to merge yet):

```kotlin
interface StenoDictionary {
    /** Ordered candidates for a committed sketch; deterministic for a given state. */
    fun candidates(sketch: List<Chord>, limit: Int): List<DictionaryEntry>
    fun wordsStartingWith(sketch: List<Chord>, limit: Int): List<DictionaryEntry>
}

data class DictionaryEntry(
    val word: String,
    val sketch: List<Chord>,
    val reduced: Boolean,
    val frequency: Int
)

interface LanguageModel {
    /** P(word | previous words), already backed off and normalised to a comparable scale. */
    fun score(word: String, previous: List<String>): Double
    fun nextStrokeDistribution(candidates: List<DictionaryEntry>, strokeIndex: Int): Map<Chord, Double>
}

interface SketchPredictor {
    fun rank(sketch: List<Chord>, context: List<String>, limit: Int): List<RankedCandidate>
}
```

Everything above `SketchPredictor` is pure Kotlin and JVM-testable; only the asset loading and the
IME wiring touch Android, which keeps the existing testing strategy (pure engines in `app/src/test`,
Robolectric for framework seams) intact.

## 9. Personalisation

* Session-scoped counts of accepted words, decaying, exactly like the current
  `InMemoryPersonalization` but keyed by dictionary word rather than fragment.
* Disabled entirely for password and no-suggestion fields, and never written to disk in v1.
* Optional later: persisted counts with a "clear my data" action; must be local-only.

## 10. Privacy

* No `INTERNET` permission (already true of the shipped app; keep it that way).
* Model and dictionary ship as assets; no downloads, no telemetry.
* Sensitive fields: stateless ranking, cleared context, no personalisation (already implemented for
  the current predictor via `strictPredictor`; the same rule must carry over).
* No keystroke logs. If evaluation needs telemetry, it must be an explicit, opt-in debug build.

## 11. Risks

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| Class ambiguity swamps ranking | candidate selection could dominate typing time (Pinyin's choice reaction was 36% of input time in one study) | measure mean rank of the intended word; keep refinement strokes on the roadmap |
| Model size vs device memory | Android heap limits are real | 12 MB budget, memory-mapped assets, measure on the Redmi Note 14 Pro |
| Cold start latency | IMEs must appear instantly | load dictionary lazily/off the first chord path, warm the trigram on first view |
| Highlights mistaken for remapping | destroys the language's learnability | highlight-only, no label changes, visible toggle |
| Evaluation runs on synthetic data only | would overstate accuracy | require a held-out human-typed corpus before any claim |
