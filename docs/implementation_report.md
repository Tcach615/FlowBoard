# Implementation report — FSL-1 in FlowBoard 1.1.0

Status: **implemented, tested headlessly, APK built and committed.** Not validated on a physical
device (see §12).

---

## 1. Implemented components

| Component (repo path) | Responsibility |
| --- | --- |
| `steno/Fsl.kt` | The alphabet: 16 chords ↔ 16 symbols (`LAB/COR/DOR/SIB` × `F/A/U/∅`), pad labels, parse/render |
| `steno/StenoEngine.kt` | Chord-sequence → phonetic sketch; validates completeness (needs a nucleus) and length |
| `steno/StenoDictionary.kt` | Tiered, multi-translation dictionary; exact/brief/reduced maps; symbol trie for prefixes; log-scaled frequency prior |
| `steno/LanguageModel.kt` | Compact trigram model with Stupid-Backoff style interpolation |
| `steno/StenoPredictor.kt` | Ranks readings (`P(w\|sketch,context)`), normalises context per candidate set, predicts the next chord distribution |
| `steno/TranslationEngine.kt` | `Candidates` / `Incomplete` / `Empty`; never guesses on the user's behalf |
| `steno/StenoSession.kt` | Linguistic state: current sketch, selection, committed-word context, undo record |
| `ui/KeyboardView.kt` | Fixed alphabet labels, sketch preview, candidate chips, highlight rings, toolbar |
| `ui/ZoneRenderer.kt` | Shared pad drawing, now with optional prediction highlight rings (default: none) |
| `ime/FlowBoardImeService.kt` | Wires the above to `InputConnection`; owns privacy and statistics |
| `input/InputController.kt` | Unchanged composing/undo controller for the legacy path and fallbacks |
| `gesture/*` | **Unchanged.** No change to the physical gesture model |
| `tools/build_assets.py` | Builds `steno_en.tsvz` and `lm_en.tsvz` from CMUdict, `count_1w` and Gutenberg |

## 2. Architectural changes

Before Phase 2 the keyboard wrote *fragments*: `ChordBoard` mapped chords to candidate fragments,
`Vocabulary`/`PredictionEngine` personalised them, and `InputController` inserted a token on every
chord. That is a different input language, not an implementation of FSL-1.

Changes:

- The pads no longer display candidates. They display the fixed FSL-1 families, because a chord
  whose label changes with prediction cannot be learned.
- Insertion is no longer per-chord: a chord appends a **phonetic stroke**; text is inserted only when
  the user commits a translation (`␣`/`✓`/candidate chip).
- The service owns a `StenoSession` instead of a candidate list.
- Prediction became **highlight-only**: it ranks readings and rings likely next chords; it never
  removes a chord, never remaps a chord, and never inserts text.
- The legacy `ChordBoard`, `Vocabulary`, `PredictionEngine` and `InputController` are kept intact so
  their tests keep passing and the fallback paths still work; the IME itself runs on `steno/`.
- Assets moved from JSON to gzipped TSV (`org.json` over ~2 MB is far too slow for IME start-up).
  The suffix is `.tsvz` because aapt2 strips and decompresses `.gz` assets.

## 3. FSL-1 implementation details

Inventory exactly as `docs/flowboard_language_design.md` specifies:

| Left thumb (onset) | NW = `LAB` (p b m f v) | NE = `COR` (t d n s z θ ð) | SW = `DOR` (k g ŋ w h) | SE = `SIB` (ʃ tʃ dʒ ʒ j *and zero onset*) |
| --- | --- | --- | --- | --- |
| **Right thumb (nucleus)** | NW = `F` (ɪ ɛ æ) | NE = `A` (ə ʌ ɑ ɔ ɜ) | SW = `U` (u ʊ oʊ aʊ) | SE = `∅` (bare consonant) |

Composition rules implemented:

- A **core** chord (right thumb anywhere except SE) opens a syllable: onset family + vowel class.
- A **bare** chord (right thumb SE) writes a consonant that belongs to the preceding core's coda or
  to an initial cluster.
- Structural segmentation is unambiguous — a bare stroke is always a consonant, so there is no
  guessing about syllable boundaries.
- A sketch needs at least one nucleus; consonant-only sequences are reported incomplete.
- Maximum 24 strokes per sketch (a guard against runaway input, not a language rule).

Every one of the 41 documented example words is asserted in
`StenoLanguageTest.every documented example round-trips through the chord layer`, including their
chord identities and symbol order.

## 4. Dictionary implementation

- **Lexicon:** CMUdict pronunciations × Norvig `count_1w` frequencies, top 20 000 words.
  Measured: 20 000 entries, **10 379 distinct sketches**, **mean 4.51 strokes/word**, worst collision
  137 words (`COR·F COR·∅`).
- **Lookup:** `exact[sketch]` (full outline), `briefs[sketch]`, `reduced[sketch]`, all frequency
  sorted; `SymbolTrie` gives bounded prefix walks for in-progress words.
- **Ambiguity:** `candidates()` returns *all* readings in tier order (FULL → BRIEF → REDUCED),
  deduplicated by word. Nothing is discarded.
- **Priors:** `frequencyPrior = ln(1+freq) / ln(1+maxFreq)`, so a mid-frequency word is not treated as
  infinitely rarer than *the*.
- **Briefs:** the tier exists and is tested, but the generated lexicon emits **none** — a first-core
  brief for a top-200 word always collides with another word's full sketch (`DOR·F` is *we*/*he*).
  This is a language-level finding, documented in `docs/implementation_readiness.md` §7.
- **Storage:** local only, `assets/steno_en.tsvz` (301 776 B).

## 5. Prediction algorithm

```
score(w) = 1.0 · tierWeight(w) · logFrequencyPrior(w)
         + 1.4 · P(w | previous words) / max P over the candidate set
         + 1.5 · sessionBoost(w)
         − 0.12 · (tierRank + 0.15 · (strokes − 1))
```

- `P(w | context)` is a trigram model with backoff to bigram and unigram; normalising by the
  candidate-set maximum makes the context term comparable with the dictionary prior.
- Tier weights: FULL 1.0, BRIEF 0.55, REDUCED 0.82 — a brief reading wins only when context strongly
  supports it.
- **Next-chord prediction** aggregates candidate scores by the chord that continues the current sketch
  and normalises to the best chord; before the first stroke it seeds from the most frequent words.
  The continuation pool is capped at 48 entries (frequency-sorted) to bound work.
- **Highlight-only:** predictions are returned as `(chord, strength)` and drawn as rings. All 16
  chords remain available and labelled identically; there is no filtering and no remapping. Labels
  never change during a gesture.
- **Personalisation:** in-session word counts (`InMemoryPersonalization`), never persisted, disabled
  in sensitive fields.

## 6. UI changes

- Pads show the alphabet (`LAB COR DOR SIB` left, `F A U ∅` right). Optional via
  *Alphabet labels on the pads*.
- New preview bar: current sketch (`COR·F COR·∅`) and the top reading, or a hint
  ("Consonants only — add a vowel chord", "No word for this sketch").
- New candidate strip: up to four chips with rank numbers; tapping a chip commits that reading.
  Chips are rebuilt at interaction boundaries and stay put during a gesture.
- Prediction rings around likely next chords, toggleable (*Highlight likely next chords*).
- Toolbar unchanged: `⌫` backspace · `␣` commit + space · `✓` commit without space · `↺` undo ·
  `⇄` cycle alternatives.
- Keyboard height raised to 348 dp portrait / 235 dp landscape to fit the new rows.

## 7. Test coverage

`./gradlew testDebugUnitTest` → **103 tests, 0 failures, 0 skipped**.

Added for this phase:

- `steno/FslTest` (8): all 16 chords, symbol uniqueness, documented inventory, family round-trips,
  bare marker, parse/render, pad labels.
- `steno/StenoLanguageTest` (14): the 41 documented examples, sketch distinctness, consonant-only
  rejection, dictionary coverage of the documented vocabulary, per-word resolution, structural
  ambiguity, deterministic ordering, brief/reduced tiers, prefix lookup, unknown sketches,
  no-commit-without-a-word, cold-parse and heap measurement.
- `steno/PredictionTest` (13): LM sanity, context lifting, context reordering, empty context,
  next-chord distribution, pre-first-stroke prediction, unknown sketch, limits, accept/undo state,
  multi-commit undo, backspace, personalisation, latency budget.
- `steno/PredictionEvaluationTest` (1): held-out metrics (§8).
- `HeadlessTypingTest` (13, Robolectric): real service, real view, real `EditText` +
  `EditableInputConnection`, synthetic two-pointer `MotionEvent`s: assets load, no insertion without
  commit, explicit commit, all 16 chords end-to-end, chip selection, `⇄`, `✓`, undo restoring strokes,
  backspace ordering, password privacy, URI classification, cancelled gestures, incomplete sketch.
- Regression: the Phase-1 suites (`GestureEngineTest`, `ChordBoardTest`, `PredictionEngineTest`,
  `InputControllerTest`, `VocabularyParserTest`, `HeadlessScreensTest`, settings/onboarding) still
  pass unchanged.

Not covered automatically: actual touch hardware, IME lifecycle on a real OS build, and visual
quality on a device (see §12).

## 8. Measured prediction performance

Environment: this repository's Linux build VM, desktop-class x86-64, JDK 17, unit-test JVM. These are
**Measurements of this implementation**, not the numbers quoted in the research prototype.

**Held-out ranking** — 80 sentences / 881 tokens from *The Adventures of Sherlock Holmes*, which is
excluded from the trigram training corpus (`app/src/test/resources/eval_sentences.txt`):

| metric | value |
| --- | --- |
| tokens with a dictionary sketch | 881 / 881 (100 %) |
| top-1 accuracy | 73.2 % |
| top-3 accuracy | 87.6 % |
| MRR | 0.807 |
| mean rank of the intended word | 1.64 |
| mean candidates per sketch (limit 4) | 3.69 |
| rank latency p50 / p95 | 0.147 ms / 1.525 ms |

**Interactive prediction** (rank + next-chord distribution, 300 samples after 20 warm-up iterations):
mean **1.338 ms**, p95 **2.470 ms**, max 11.742 ms. Target was p95 < 10 ms → met.

**Memory and start-up** (second load, so class loading is excluded): dictionary parse **24.1 ms**,
language model parse **36.8 ms**, retained heap for both models **10.7 MB**. Budget was ~12 MB → met,
with little headroom.

Caveats: the eval set is small and fiction-heavy (narrative prose, 19th century); accuracy on other
domains is unmeasured. The lexicon's 137-way collisions mean the predictor is doing real work — with
an empty context the ordering is close to frequency order.

## 9. Known limitations

- No letter mode: names and non-words cannot be spelled (readiness §1).
- Zero onset is encoded as `SIB`, so vowel-initial words share a family with ʃ/tʃ/dʒ/ʒ/j words
  (readiness §2).
- General American vowels only; accents produce more ambiguity, not wrong text (readiness §3).
- 137-way collisions are structural; the top-4 strip can miss the intended word, which stays
  reachable via `⇄`.
- The lexicon is 20 000 words; anything outside it is unresolvable.
- Language model trained on 450 374 tokens of five Gutenberg novels — modern/technical vocabulary is
  under-modelled.
- No punctuation chords: punctuation and spaces come from the toolbar.
- Personalisation is session-scoped and never persisted; it is off in sensitive fields.
- Continuation prediction is capped at 48 candidates, so a rare word's next chord may be unhighlighted.
- Prediction is highlight-only by design; there is no auto-commit and no chord filtering.

## 10. Unimplemented features

- FSL-2 pin strokes (explicitly out of scope; gesture engine untouched).
- Letter/dictionary-entry mode for unpronounceable strings.
- Persisted personalisation, user dictionary editing, custom sketches.
- Punctuation chord set, capitalisation strokes, numeric mode.
- Word-level language model beyond trigram; per-app or topic adaptation.
- Any cloud or LLM component (prohibited).

## 11. APK build information

| field | value |
| --- | --- |
| path | `dist/flowboard-1.1.0-debug.apk` |
| size | 1 662 026 bytes |
| SHA-256 | `2e35ff967b033a91a40847370e4b851bebbe66153a0d108e4f6c3ffbd4b3796f` |
| package | `com.flowboard.keyboard` |
| versionName / versionCode | `1.1.0` / `2` |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35 |
| permissions | **none** (`aapt2 dump permissions` lists only the package header) |
| bundled models | `assets/steno_en.tsvz` (301 776 B), `assets/lm_en.tsvz` (415 833 B) |
| engine present | `steno/StenoSession`, `steno/StenoDictionary` verified in the packaged dex files |

Build:

```bash
./gradlew testDebugUnitTest assembleDebug --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8"
cp app/build/outputs/apk/debug/app-debug.apk dist/flowboard-1.1.0-debug.apk
```

`dist/*.apk` is force-added (`git add -f`) because `.gitignore` excludes APKs; the rule itself was
not removed.

## 12. Device testing status

**Not performed.** The target device (Redmi Note 14 Pro, Android 16) is not attached to this
environment, and `/dev/kvm` is root-only here, so no emulator could be started either. No claim of
device validation is made.

Manual QA checklist (also appended to `MANUAL_QA.md`):

1. Install `dist/flowboard-1.1.0-debug.apk`, enable FlowBoard in *Manage keyboards*, select it.
2. Type in a third-party app (e.g. Messages): stroke `COR·A` (both thumbs NE) — nothing is inserted,
   the preview shows `COR·A` and the top reading.
3. Press `␣` — a word plus space appears; the candidate strip empties.
4. Stroke `COR·F`, `COR·∅` (two chords) and pick a different chip — that reading is inserted.
5. Press `⇄` before committing — the selected chip advances; then `✓` — no trailing space.
6. Press `↺` — the last word disappears and its strokes return to the preview.
7. Press `⌫` with strokes present (removes a stroke), then with none (deletes a character).
8. Type into a password field: the bar shows `PRIVATE`; text still inserts; settings statistics do
   not increase.
9. Toggle *Highlight likely next chords* and *Alphabet labels on the pads*; both take effect after
   reopening the keyboard.
10. Rotate the device and confirm the board re-lays out without losing the sketch.
11. Verify two-thumb comfort: pads at 25 %/75 % width, 348 dp tall; note any reach problems.
12. Check a long sketch (6+ strokes) does not obstruct the preview or chips.
