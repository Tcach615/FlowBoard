# Software architecture proposal

Status: **proposal**. Nothing here is implemented. The shipped APK and its behaviour are unchanged,
and the existing `GestureEngine` is preserved (with one documented exception in §6).

Related: [language design](flowboard_language_design.md) ·
[prediction](context_prediction_architecture.md) · [evaluation](evaluation_plan.md) ·
[decision record](decision_record.md).

## 1. Constraints taken as given

* Kotlin, plain Android SDK, no new runtime dependencies for v1.
* `GestureEngine` stays as-is unless a technical reason forces a change; its tests must keep passing.
* Offline only: no `INTERNET`, no analytics, no model downloads.
* The app's editing model (nothing inserted until the user commits) is preserved.
* Everything testable stays Android-free where possible, so the existing JVM/ Robolectric split keeps
  working.

## 2. Components

| Component | Responsibility | Purity | Replaces / reuses |
| --- | --- | --- | --- |
| `ChordInputLayer` | turn touch into normalised chord identifiers, with timing | Android (`KeyboardView` today) | reuses the existing gesture engine and view plumbing |
| `StenoLanguageEngine` | validate stroke sequences, segment them into syllables, detect bare vs core | pure Kotlin | new; replaces the implicit grammar in `ChordBoard` |
| `StenoDictionary` | sketch → ordered words; briefs; reduced-form matching | pure Kotlin over assets | new; replaces `Vocabulary` + `VocabularyParser` |
| `TranslationEngine` | resolve a sketch into a ranked candidate list, manage ambiguity and selection | pure Kotlin | new; there is no equivalent today |
| `PredictionEngine` (new) | rank candidates and predict next strokes | pure Kotlin + local asset | replaces the prefix/bigram predictor, keeps `TextContext` |
| `TextContextManager` | committed words, current sketch, candidate set, sensitive-field reset | pure Kotlin | extends today's `TextContext` |
| `KeyboardPresentationLayer` | draw fixed family labels, highlights, preview, alternatives | Android (`KeyboardView`) | major rework of what is drawn, not how touches are read |
| `EngineFacade` | wires the above for the IME service | Kotlin | new, small |

Data flow:

```
MotionEvent → GestureEngine → Chord
   → StenoLanguageEngine (validate / segment)
   → StenoDictionary.candidates(sketch)
   → PredictionEngine.rank(sketch, context)
   → KeyboardPresentationLayer (preview + highlights)
   → user commits  → TranslationEngine.accept(top or selected)
   → InputController → InputConnection → editor
```

Note the inversion compared with today: **the pads no longer display candidate words**. They display
the fixed family alphabet plus optional highlights, because candidate-to-chord assignment would
violate invariant I6.

## 3. Data structures

### 3.1 Dictionary entry (JSON, authored or generated)

```json
{
  "version": 1,
  "language": "en",
  "letters": {
    "onsetFamilies": [{"id": "LAB", "thumb": "NW", "members": ["p", "b", "m", "f", "v"]}],
    "nucleusFamilies": [{"id": "F", "thumb": "NW", "members": ["i", "ɪ", "e", "ɛ", "æ"]}]
  },
  "entries": [
    {
      "word": "street",
      "ipa": "strit",
      "sketch": "COR·∅ COR·∅ COR·F COR·∅",
      "reduced": "COR·∅ COR·∅ COR·F COR·∅",
      "frequency": 51234,
      "tier": "core"
    }
  ],
  "briefs": [
    {"word": "information", "sketch": "COR·∅ LAB·F COR·∅", "tier": "brief"}
  ]
}
```

* Sketches are stored as the canonical symbol string; the build step validates them against the
  language rules and fails the build on a mismatch (this is the mechanism that keeps docs and data
  honest).
* `frequency` comes from a corpus (Norvig `count_1w` or SUBTLEX frequency, both citable), normalised
  to a per-million scale.
* Tiers follow [language design §5.4](flowboard_language_design.md#54-dictionary-model-a-deliberate-divergence-from-plover).

### 3.2 Sketch index

A trie keyed by symbols (`LAB·F`, `COR·∅`, …). Each node carries either candidates (terminal) or
children. Built offline; loaded by memory-mapping a packed byte format:

```
[u16 nodeCount][u32 offsets[nodeCount+1]][payload]
payload per node: u8 symbolCount, u8[] symbolIds, u16 entryCount, u16[] entryIds
```

Tries are the right structure here because all lookups are prefix lookups on a small alphabet, and
lookup is linear in key length; the cost is pointer overhead, which the packed form avoids
([Tightly Packed Tries](https://aclanthology.org/W09-1505.pdf)).

### 3.3 Language model asset

Trigram, words mapped to integer ids, quantised log-probabilities with a backoff table. The target
size and latency are taken from the measured Op-Ngram prototype (2.30 MB disk / 10.47 MB RAM /
2.2–2.4 ms per query for a 100k-vocabulary trigram), adjusted in [evaluation](evaluation_plan.md).
Binary layout should be fixed *after* measuring, not before.

## 4. Interfaces

```kotlin
// StenoLanguageEngine
sealed interface Stroke { data class Core(val onset: OnsetFamily, val nucleus: NucleusFamily) : Stroke
                          data class Bare(val family: OnsetFamily) : Stroke }

data class ParsedSketch(val strokes: List<Stroke>, val syllables: List<Syllable>, val valid: Boolean)

interface StenoLanguageEngine {
    fun parse(chords: List<Chord>): ParsedSketch
    fun labelsFor(core: Chord): Pair<String, String>   // what the pads show for that chord
}

// TranslationEngine
sealed interface Translation {
    data class Candidates(val ranked: List<DictionaryEntry>, val sketch: List<Chord>) : Translation
    data class Unknown(val sketch: List<Chord>) : Translation
}

interface TranslationEngine {
    fun translate(sketch: List<Chord>, context: List<String>): Translation
    fun accept(entry: DictionaryEntry): String          // text plus spacing rule
}
```

These are deliberately small: a `Translation` either has candidates or is explicitly unknown, so the
"never guess silently" rule is expressible in the type system rather than in comments.

## 5. Conflicts with the current architecture (documented, not silently changed)

| Existing | Conflict | Proposal |
| --- | --- | --- |
| `ChordBoard` assigns ranked candidates to chords dynamically, with slot stability | Directly contradicts I6: a chord's meaning would depend on the predictor | Replace `ChordBoard` for FSL with a fixed alphabet + a separate selection surface. Keep the class until migration completes; do not use both at once |
| `PredictionEngine` scores Latin-letter prefixes (`wordsStartingWith(prefix)`) | FSL sketches are not letter prefixes; a sketch is a phoneme-class sequence | New `SketchPredictor`; reuse `ScoreWeights` idea and `TextContext` |
| `Vocabulary` holds words + fragments + bigrams | No IPA, no sketch index, fragments are exactly the "arbitrary" layer being replaced | New dictionary asset and parser; old asset stays for the legacy path |
| `InputController` composes a token by appending text and records undo per insertion | FSL commits whole words chosen from a candidate set; undo must restore the *sketch*, not just text | Extend `InputController` with an explicit pending-translation state, or wrap it in a `TranslationController`; keep its 15 tests as the contract |
| `KeyboardView` draws 4 candidate labels per pad and anchors them to the other thumb's direction | The fixed alphabet needs family labels, highlights and a candidate list elsewhere | New drawing path behind a flag; extensive golden/render tests (`HeadlessScreensTest` style) |
| `GestureEngine` rejects a gesture that never captures both directions (`INCOMPLETE_DIRECTIONS`) | FSL-2 refinement strokes need single-direction strokes to be accepted as a different class | **Not changed in FSL-1.** For FSL-2, add an opt-in config and new tests; preserve the existing rejection behaviour by default |
| Onboarding teaches "sixteen chords, sixteen candidates" | Wrong mental model for FSL | Tutorial and practice must be rewritten for FSL-1 before any release |
| `SettingsActivity` copy mentions candidates and statistics | Terminology and semantics change | Update copy and add "prediction highlights" and "reduced forms" toggles |

## 6. Migration plan

Each phase is independently shippable and reversible via a settings flag; no phase changes the
default behaviour until the evaluation passes.

| Phase | Deliverable | Tests | Rollback |
| --- | --- | --- | --- |
| 0 | These documents; no code | docs review | n/a |
| 1 | Build-time sketcher: IPA → sketch, with the generator used for these docs promoted to a validated Gradle task | golden tests over a lexicon; docs examples re-generated | remove task |
| 2 | `StenoLanguageEngine` (pure) + sketch parsing/segmentation | unit tests mirroring `GestureEngineTest` style, incl. all 16 chords and every invalid sequence | unused code |
| 3 | `StenoDictionary` + packed trie + parser; lexicon asset (top ~20k) | unit tests on lookup, tiers, collisions; size/latency test | legacy `Vocabulary` untouched |
| 4 | `TranslationEngine` + candidate list UI behind a flag | pure tests + Robolectric rendering of the new pads | flag off |
| 5 | Trigram LM + ranking + next-stroke highlights | offline accuracy harness; latency budget test | flag off |
| 6 | Onboarding/tutorial rewrite, settings, statistics | Robolectric walkthrough (like `HeadlessScreensTest`) | flag off |
| 7 | Optional FSL-2 pins: new gesture config, commit-model change, pin parsing | engine tests for single-direction strokes; regression of all existing rejection tests | config off |

Phase 7 must not start before a human study (see [evaluation](evaluation_plan.md) §6) says the
ambiguity cost is the thing worth fixing. There is no point building refinement machinery for a
language nobody can yet type.

## 7. Latency and memory budgets

| Path | Budget | How it will be measured |
| --- | --- | --- |
| chord → highlight update | ≤ 10 ms p95 | timestamps inside the view, logged in a debug build |
| sketch → candidate list | ≤ 5 ms p95 | JMH-style microbenchmark on JVM + on-device loop |
| cold start of input view | ≤ 50 ms added over today | `SystemClock` deltas in `onCreateInputView` |
| resident memory (dict + LM) | ≤ 12 MB | `Debug.MemoryInfo` on the target device |

Numbers are targets derived from the cited literature (Op-Ngram's 2.2–2.4 ms; the ~20 ms visible
response expectation), not measurements of FlowBoard.

## 8. Testing strategy

* **Pure engine tests** for language, dictionary, translation, prediction — the majority, fast.
* **Golden sketch tests**: a fixed lexicon of ~2,000 words with IPA, sketched by the build task, with
  the expected sequences committed so any rule change shows up as an explicit diff.
* **Robolectric** for the IME service wiring, view rendering and onboarding flow, extending the
  existing `HeadlessTypingTest` / `HeadlessScreensTest` approach (synthetic two-thumb gestures →
  real editor).
* **Instrumentation on device** for real multi-touch, latency and haptics — currently missing
  entirely; see [MANUAL_QA.md](../MANUAL_QA.md).
* **Offline accuracy harness** over a corpus, not unit tests, for prediction quality.

## 9. What will not change

* The gesture engine (FSL-1), the privacy model, the build system, the offline promise.
* The user-visible editing guarantees: nothing inserted without a commit, undo reverts exactly what
  was inserted, password fields are stateless.
