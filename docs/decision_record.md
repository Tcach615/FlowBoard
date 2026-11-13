# Decision record — stenographic language for FlowBoard

Status: **proposal awaiting approval**. No code, asset or APK change accompanies this record.

Date: 2026-11-13. Scope: linguistic input architecture only.
Related: [research](stenographic_research.md) · [language](flowboard_language_design.md) ·
[prediction](context_prediction_architecture.md) · [examples](input_language_examples.md) ·
[architecture](architecture_proposal.md) · [evaluation](evaluation_plan.md).

## 1. Context

The shipped keyboard maps sixteen chords to a ranked list of candidates that is recomputed as the
user types (`ChordBoard`). The result is a set of arbitrary fragments (`tion`, `and`, …) with no
compositional rule: the user cannot derive what a chord will produce, and the mapping moves as
prediction changes. The brief is to replace that linguistic layer with a learnable stenographic
system, while leaving the gesture engine, the IME plumbing and the offline/privacy model intact.

Two facts constrain any answer and are the reason this record is cautious:

1. Sixteen chords is four bits per stroke. English has ~24 consonants and 14–20 vowels. A one-chord
   phoneme mapping is arithmetically impossible, and any class-based mapping must collide.
2. There is no published evidence that a serialised, two-thumb, 16-chord system reaches QWERTY
   speed. The closest measured systems sit at 14–26 wpm after hours of practice
   ([Senorita](https://www.asarif.com/pub/Rakhmetulla_CHI2020_Senorita.pdf),
   [Twiddler](https://faculty.cc.gatech.edu/~thad/p/030_10_MTE/twiddler-chi04.pdf)).

## 2. Decision

**Adopt the documents in this directory as the design baseline for a stenographic FlowBoard, and
implement nothing until the language has passed the offline evaluation in
[evaluation_plan.md](evaluation_plan.md) §4.**

Specifically:

1. **Language (FSL-1).** Twelve syllable-core chords (onset family × nucleus family) plus four
   bare-consonant chords used for clusters and codas. Fixed meanings, structurally deterministic
   segmentation, productive multi-stroke spelling, reduced forms for fluency, dictionary briefs as
   compression rather than as new meanings. (See [language design](flowboard_language_design.md).)
2. **Refinement (FSL-2) is the target, not the start.** Single-thumb *pin* strokes would give
   16 onset and 12 nucleus symbols at the cost of one optional stroke — capacity without changing any
   chord meaning. It requires a gesture-engine and commit-model change, so it is sequenced after the
   language proves itself (§5, criterion 2 and 3).
3. **Prediction is highlight-only in v1**, over a tiered dictionary and a trigram model; it reorders
   and highlights, and never redefines a chord or hides the inventory permanently.
4. **Ambiguity is explicit.** The dictionary returns ordered alternatives where Plover forbids
   multiple translations, because our alphabet is coarser than steno's keys. This divergence is
   deliberate and documented.

## 3. Why this over the alternatives

| Alternative | Rejected because |
| --- | --- |
| Keep candidate fragments, add more words | No compositional rule, no learnable mapping; this is precisely what the brief rejects |
| 16 chords as 16 phrases/words (shortcut keyboard) | Satisfies shortcut memorisation, violates the language criteria (I3/I4): unlisted words untypeable |
| One chord per phoneme (Approach A) | 16 < 44; requires digraphs for most of the inventory, degenerating into a slow letter code |
| Pure syllable spelling (Approach B) | Every coda and cluster becomes its own stroke; long words become very expensive and there is no capacity for unstressed-syllable compression |
| Full 22-key steno layout on an on-screen grid | Abandons the product's core two-thumb interaction; a different app |
| LLM / cloud prediction | Violates offline, privacy and "lightweight" constraints; also cannot fix a coarse alphabet |
| Filtering (hiding) unlikely chords | Breaks mapping stability, which is the whole point of a chord language; no evidence it helps |
| 5-gram / WFST first | Higher build complexity with no measured benefit at this stage; trigram has a published, measured compact implementation (2.3 MB / 10.47 MB RAM / ~2.4 ms) |

## 4. Consequences

* The current chord-to-candidate UI is incompatible with the design and would eventually be replaced;
  until then it keeps working exactly as it does today.
* `ChordBoard` and `Vocabulary` become legacy. `PredictionEngine` is replaced behind its interface.
* Onboarding, settings copy and statistics change meaning and must be rewritten before any release.
* The dictionary becomes a build artefact with a validated sketch index; a bad rule change fails the
  build rather than shipping silently.
* No change to privacy, offline behaviour, or the "nothing inserted until committed" rule.

## 5. Criteria check (the brief's ten requirements)

| # | Criterion | Status |
| --- | --- | --- |
| 1 | Foundations traceable to established stenographic/linguistic principles | **Met** — onset/nucleus split, one-vowel-per-stroke, unstressed reduction, briefs and dictionary translation all trace to stenotype/Plover; citations in the research doc |
| 2 | All 16 chords have a defined role | **Met** — 12 cores + 4 bare consonants, table in the design doc |
| 3 | Sequential composition, not 16 isolated shortcuts | **Met for FSL-1**; briefs exist but are compressions of sketches |
| 4 | English words constructible systematically | **Met by rule, with class ambiguity.** `strength` needs 5 strokes; long words rely on reduced forms or briefs |
| 5 | Dictionary translation handles ambiguity | **Designed, not built** — tiered dictionary returning ordered alternatives; diverges from Plover's one-translation rule by necessity |
| 6 | Context genuinely reduces choices | **Unproven** — the mechanism is specified; the reduction must be measured (RQ2). This is the weakest link |
| 7 | Prediction cannot change chord semantics | **Met by construction** — highlight-only, fixed alphabet |
| 8 | User can always reach valid alternatives | **Met by design** — full inventory always live, `⇄` alternatives, undo, prediction off |
| 9 | Feasible on an ordinary Android phone | **Plausible, not measured** — 12 MB budget, trigram within published compact numbers; target device is a Redmi Note 14 Pro |
| 10 | Learnable with a credible efficiency path | **Plausible, not measured** — 24 symbols to learn, progressive precision via reduced forms and (later) pins; no user data |

**Honest summary:** criteria 1–4 and 7–8 are satisfied by the design; 5, 6, 9 and 10 are designed but
unvalidated. If the offline evaluation in §4 of the evaluation plan shows top-1 below ~70% or a mean
rank above ~5, criterion 6 fails and **FSL-1 alone is not acceptable** — refinement strokes become
mandatory, and the language would need another design round.

## 6. Unresolved questions

1. **Zero onset vs /ʃ/.** SE doubles as "no onset". A dedicated symbol needs a 17th chord, or a pin
   (FSL-2). Is the collision acceptable in practice?
2. **Letter mode.** How does a user write a name that is not pronounceable ("Ngo", "Książek")? No
   design exists. This is a real product gap, not a detail.
3. **Selection surface.** Where do alternatives live? The pads cannot be reused (semantics), so is it
   a list in the preview bar, a sheet, or a temporary mode? Needs user testing.
4. **Reduced-form policy.** Who decides when a reduced form is offered? If the app suggests it, a bad
   suggestion costs a wrong word; if the user must remember it, learnability suffers.
5. **Pin ergonomics.** Lifting one thumb currently commits. Any pin scheme changes the commit model,
   which is the most safety-critical part of the current engine.
6. **Dialect.** GA-oriented vowel families; RP users will merge /ɒ/ and /ɔː/ and /əʊ/–/oʊ/.
7. **Numbers, punctuation, capitalisation.** Out of scope here, but a keyboard needs them.
8. **Brief curation authority.** Briefs will encode one person's frequency intuitions unless there is
   a rule (frequency tiers) and a review process.

## 7. Assumptions requiring validation

| Assumption | How it could be wrong | Test |
| --- | --- | --- |
| Users can learn 7 family labels + 16 chord roles | 24 symbols may be as hard as a small alphabet and much slower than a keyboard | learning curve in the study (RQ3) |
| A chord costs ~300 ms | two-thumb drags may be slower, especially on small screens | on-device latency + word-time measurement |
| Context ranking puts the intended word first most of the time | class ambiguity may swamp the LM, particularly early in sentences | offline top-1/MR (RQ2) |
| Reduced forms are acceptable at 3 strokes for long words | they may be perceived as guessing | correction-rate measurement |
| 12 MB of dictionary + LM is acceptable | low-end devices may be constrained | memory profiling on device |

## 8. Approval gate

The next action is **review of these documents**. On approval, the first code change is Phase 1/2 of
the [architecture proposal](architecture_proposal.md) (build-time sketcher + pure
`StenoLanguageEngine`), shipped behind a flag with no default behaviour change. No APK, dictionary
asset or production behaviour changes before that.

If the reviewers reject the class-based alphabet, the fallback recommendation is: keep the gesture
engine, adopt the dictionary/translation/prediction architecture, and redesign the alphabet around
single-direction strokes (more symbols) — which is a bigger engine change but a more expressive
language.
