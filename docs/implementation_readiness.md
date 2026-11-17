# Implementation readiness — FSL-1

This document resolves the open questions in `docs/decision_record.md` **before** code was written
for Phase 2. Each entry states the impact, whether it blocks FSL-1, the resolution actually
implemented, whether the documented language changed, and how the resolution is tested.

Verdict: **no blocking inconsistency was found.** FSL-1 can be implemented as documented, with the
explicit limitations below.

---

## 1. No letter mode for unpronounceable names

**Impact.** FSL-1 writes phonemes, so strings with no pronunciation (product codes, initialisms that
are not spoken as words, names the user pronounces differently) cannot be spelled. The user is
limited to words present in the local dictionary.

**Blocking?** No. Letter mode is out of scope for FSL-1 and is not silently faked.

**Resolution.** A stroke sequence that the dictionary cannot resolve is reported as *incomplete*
("No word for this sketch") and cannot be committed — the keyboard never invents a spelling. The
fallback is explicit: switch to another input method (the system keyboard switcher) or pick a
different pronunciation via the alternatives list. A future letter mode would need 16 new chord
meanings, i.e. a language change, so it is deliberately not bolted onto FSL-1.

**Language change?** No.

**Test.** `StenoLanguageTest.unknown sketches produce no candidates`,
`StenoLanguageTest.an incomplete sketch is never committed as a word`,
`HeadlessTypingTest.an incomplete consonant-only sketch cannot be committed`.

---

## 2. Zero onset conflated with /ʃ/

**Impact.** FSL-1 has no "silent onset" symbol; a vowel-initial word uses the `SIB` onset family as
its onset carrier. `SIB` also contains ʃ, tʃ, dʒ, ʒ and j. Therefore `SIB·F COR·∅` can mean *is*,
*it*, *ship* (with a different coda), *chip*, *jib*, *jip*, etc.

**Blocking?** No. It is ambiguity, and FSL-1's contract is to expose ambiguity rather than hide it.

**Resolution.** The generator maps a zero onset to `SIB` exactly as the design specifies
(`tools/build_assets.py`, `sketch()`), and the dictionary returns every reading of the resulting
sketch in the candidate strip. Frequency and trigram context decide the *order*; the user decides the
*word*. No special "silent onset" chord was invented, because that would change the 16-chord
inventory.

**Language change?** No. The ambiguity is documented behaviour, not an exception.

**Test.** `StenoLanguageTest.structural ambiguity is exposed, not hidden` (the `COR·F COR·∅` cluster
returns 20+ readings), `PredictionEvaluationTest` (ordering quality under that ambiguity).

---

## 3. General American vowel assumptions

**Impact.** The first nucleus dimension (`F`) covers ɪ, ɛ, æ; `A` covers ə, ʌ, ɑ, ɔ, ɜ; `U` covers
u, ʊ, oʊ, aʊ. Speakers of other varieties (or with cot–caught, Mary–marry–merry, or rhotic
differences) will produce sketches that collide more or less than the lexicon assumes. Rhotic /ɜr/
is encoded as a bare `COR·∅` coda, so non-rhotic speakers may omit strokes that the dictionary
expects.

**Blocking?** No.

**Resolution.** The lexicon is generated from CMUdict (General American) and frequencies from
Norvig's `count_1w`. Reduced and full outlines are both present, and ambiguity is surfaced. Accent
mismatches therefore land in the alternatives list instead of producing wrong text silently. No
vowel splits were added; the documentation's three-way nucleus is preserved.

**Language change?** No.

**Test.** `StenoLanguageTest.dictionary covers the documented vocabulary`,
`PredictionEvaluationTest.held-out ranking metrics` (measured on ordinary prose, including words
with several pronunciations).

---

## 4. Undefined candidate selection surface

**Impact.** The design requires ordered alternatives and explicit selection but never says where the
user selects them. Without a surface, alternatives are either unreachable (only the top reading can
be committed) or silently auto-selected.

**Blocking?** No.

**Resolution.** Three surfaces, all explicit: a candidate strip of up to four chips above the pads
(tap a chip to commit that reading), the `⇄` toolbar command (cycles the selection, then `␣`/`✓`
commits it), and the preview bar, which shows the current sketch and its top reading. Chips are
rebuilt whenever the sketch changes and never move while a gesture is in flight.

**Language change?** No — this is presentation, not language.

**Test.** `HeadlessTypingTest.tapping a candidate chip commits that reading`,
`HeadlessTypingTest.alternatives command changes which reading is committed`,
`PredictionTest.accept inserts the selected alternative and undo restores the sketch`.

---

## 5. FSL-2 pin strokes vs commit-on-lift

**Impact.** FSL-2 adds pin strokes (one finger held while the other taps) to write two-thumb
sequences without lifting. The current engine commits a chord on the first thumb lift and has a
sync window for the second thumb; pins would need the engine to distinguish "hold" from "lift" and
would change the meaning of every gesture.

**Blocking?** No — FSL-2 is not part of this phase.

**Resolution.** Pins are **not implemented** and no gesture semantics were changed. The gesture
engine (`gesture/GestureEngine.kt`) is byte-for-byte the Phase-1 model; only the labels drawn on the
pads and the meaning of the resulting chord changed. FSL-2 remains a research direction.

**Language change?** No.

**Test.** The unchanged Phase-1 gesture tests (`GestureEngineTest`, `HeadlessScreensTest`) still pass,
plus `HeadlessTypingTest.all sixteen physical chords reach the language engine`.

---

## 6. Five-way collision involving need / there / their / read / red

**Impact.** On a 20 000-word lexicon this is not a five-way collision but a **137-way** one:
`COR·F COR·∅` is shared by *that, this, there, their, these, than, then, read, red, need, said, set,
did, let…` because `COR` covers t/d/s/z/θ/ð/n and `F` covers ɪ/ɛ/æ. A single-translation dictionary
cannot work; even a five-item list is too short.

**Blocking?** No, provided the dictionary is multi-translation and the ranking has context.

**Resolution.** The dictionary returns *all* readings, ordered by a tiered score; the strip shows the
best four; `⇄` reaches the rest. Ranking is `dictionary·tier·log-frequency + context·P(w|prev) +
personal − tierCost` (see `StenoPredictor`). With trigram context the intended word is top-1 for
73.2 % of held-out tokens and top-3 for 87.6 %; when it is not, the correct word is still reachable
and the commit is the user's.

**Language change?** No. This is the documented ambiguity model applied at scale.

**Test.** `StenoLanguageTest.structural ambiguity is exposed, not hidden`,
`PredictionTest.different contexts reorder the same candidate set`,
`PredictionEvaluationTest.held-out ranking metrics`.

---

## 7. Limitations of the 41-word demonstration lexicon

**Impact.** The design's examples use 41 hand-checked words. A keyboard needs tens of thousands, and
the examples say nothing about collisions, coverage, or ranking quality.

**Blocking?** No.

**Resolution.** `tools/build_assets.py` builds a real lexicon: CMUdict pronunciations × Norvig
`count_1w` frequencies, top 20 000 words, plus a compact trigram model from five Gutenberg novels.
Measured: 20 000 entries, 10 379 distinct sketches, **mean 4.51 strokes/word**, worst collision 137
words. The 41 documented examples are still asserted verbatim in tests, but they are no longer the
lexicon.

Two consequences are documented rather than hidden:

- **Briefs are not emitted.** A "brief" is the first core of a very frequent word, but every such
  outline is already another word's full sketch (`DOR·F` is *we* and *he*; `COR·A` is *the*), so the
  generator suppresses them. The `Tier.BRIEF` machinery remains implemented and is exercised with an
  explicit dictionary; the shipped lexicon is FULL + REDUCED only.
- **CMUdict differs from the hand-written examples for a few words.** *and* is `SIB·A COR·∅ COR·∅`
  (CMUdict's reduced /ənd/) rather than the doc's `SIB·F …`; *strength* is
  `COR·∅ COR·∅ COR·F DOR·∅ DOR·∅ COR·∅` (/strɛŋkθ/); *world* keeps its final /d/. These are
  pronunciation-source differences, not rule changes: the implementation follows CMUdict, and the
  documented examples remain valid sketches of the language (they resolve, just to their own
  readings).

**Language change?** No.

**Test.** `StenoLanguageTest.dictionary statistics are in the expected range`,
`StenoLanguageTest.every documented example round-trips through the chord layer`,
`StenoLanguageTest.dictionary covers the documented vocabulary`,
`PredictionEvaluationTest.held-out ranking metrics`.

---

## Incidental engineering findings

Two implementation-level issues surfaced during integration and are recorded here because they
affect the shipped artefact:

1. **aapt2 strips `.gz`.** An asset named `steno_en.tsv.gz` is decompressed and renamed to
   `steno_en.tsv` inside the APK, so opening it as a gzip stream fails at runtime. Assets are
   therefore named `*.tsvz` (gzip bytes, non-special suffix); the loader also falls back to a plain
   `.tsv` if a future toolchain strips compression again.
2. **Sensitive fields.** Password/URI classification keeps the Phase-1 exact-variation comparison
   (masking would misclassify `TYPE_TEXT_VARIATION_URI` as a password). In a password field the
   session keeps no context and the personalisation store records nothing.
