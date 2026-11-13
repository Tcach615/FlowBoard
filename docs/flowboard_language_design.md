# FlowBoard Stenographic Language (FSL) — design

Status: **proposal**. No chord in the shipped app has any of these meanings; the current build still
maps ranked candidate fragments to chords. This document defines a language, not a feature.

Companion documents: [research](stenographic_research.md) ·
[prediction](context_prediction_architecture.md) · [examples](input_language_examples.md) ·
[architecture](architecture_proposal.md) · [evaluation](evaluation_plan.md) ·
[decision record](decision_record.md).

## 1. What the language must do

Derived from the brief's decision criteria, and stated as invariants the rest of the design is
tested against:

| # | Invariant |
| --- | --- |
| I1 | Every one of the 16 chords has a fixed, stated meaning. |
| I2 | Meaning comes from articulation (place/manner, height/backness) and stroke position — never from a candidate list. |
| I3 | Words are built from *sequences*, not from single shortcuts. |
| I4 | Any pronounceable English word is writable by rule, including words absent from the dictionary. |
| I5 | Ambiguity is explicit and enumerable; it is never hidden by silently changing a chord. |
| I6 | Context may reorder or highlight, but cannot alter what a chord means. |
| I7 | The user can always reach the full inventory, alternatives, undo, and can turn prediction off. |
| I8 | Everything runs offline on an ordinary Android phone. |
| I9 | The system is learnable in layers: a small core alphabet first, refinements later. |

## 2. Capacity analysis (why the obvious mapping fails)

* 16 chords = 4 bits per stroke.
* English needs ~24 consonants and 14–20 vowels (RP: 44 phonemes total, 5.46 bits).
* A steno stroke carries many phonemes because it has ~22 keys; FlowBoard has 4 bits.

Arithmetic consequences, stated exactly:

* If each chord meant one phoneme, at most 16 of ~40+ phonemes could be written directly; the rest
  would have to be digraphs. The chord vocabulary would also violate I4 unless digraph rules were
  complete and systematic.
* A two-axis chord (one symbol per thumb) buys **4 ghost families × 4 nucleus families = 16 classes**.
  Merging 24 consonants into 4 families means an average of 6 consonants per family, so a CVC word's
  sketch has at most 4 × 3 × 4 = 48 distinct shapes. **Any lexicon with more than 48 monosyllabic
  CVC items must contain sketch collisions** (pigeonhole; no assumption needed). English certainly
  does.
* Therefore the design *must* have (a) a productive multi-stroke spelling and (b) an explicit
  disambiguation channel. Anything claiming 16 chords replace a keyboard is arithmetically wrong.

## 3. Approaches considered

| Approach | Description | Why not chosen as the design |
| --- | --- | --- |
| **A. Direct mapping** | 16 chords ↔ 16 phones/classes, one stroke per phoneme, digraphs for the rest | Violates I3, and with 24 consonants it needs digraphs for most of the inventory, ending up as a slow letter-by-letter code with unpredictable clusters. Kept, but only as the *bare-consonant* sub-part of the hybrid |
| **B. Sequential syllabic spelling** | Every stroke = a full syllable (onset+nucleus), codas and clusters as extra strokes | Closest to steno theory, but steno fits onset+vowel+coda in one stroke; serialising all three costs 2–3 strokes per syllable, and unstressed syllables make long words very expensive |
| **C. Hybrid (chosen)** | Syllable cores + bare-consonant strokes for clusters/codas + optional reduced forms + dictionary briefs + context ranking | Only approach that satisfies I1–I9 simultaneously, at the cost of class-level ambiguity that must be resolved by dictionary and context |

Approach C is adopted and specified below. The ambiguity cost is explicitly quantified in §7.

## 4. FSL-1 — the 16-chord baseline

### 4.1 The two axes

The left thumb always means **onset material**; the right thumb always means **nucleus material**.
That is the one structural rule the whole language rests on, and it is the direct adaptation of the
stenotype's left-hand/thumb/right-hand division (see
[research §7](stenographic_research.md#7-established-theory-versus-adaptation)).

**Consonant families (left thumb, shared by cores and bare strokes).** Chosen by place/manner so that
a family is learnable as "where and how the sound is made":

| Direction | Family | Members | Rationale |
| --- | --- | --- | --- |
| NW | **LAB** | /p b m f v/ | labial stop, nasal, fricatives |
| NE | **COR** | /t d n s z l r θ ð/ | coronal stop, nasal, fricatives, liquids |
| SW | **DOR** | /k g ŋ h w/ | dorsal/glottal, plus the labio-velar glide |
| SE | **SIB** | /tʃ dʒ ʃ ʒ j/ **and zero onset** | postalveolar/affricate/glide; doubles as "no onset" |

**Nucleus families (right thumb).** Chosen by height/backness, with diphthongs handled by rule
(§4.3) rather than by a fourth class, because the fourth right-thumb direction is needed for bare
consonants:

| Direction | Family | Members |
| --- | --- | --- |
| NW | **F** front | /i ɪ e ɛ æ/ |
| NE | **A** central | /ə ʌ ɜ ɑ a/ |
| SW | **U** back | /u ʊ ɔ o/ |

### 4.2 The complete 16-chord inventory

Every chord is used. The `SE` right-thumb column is not a vowel class; it marks a **bare consonant**
stroke, which is how clusters and codas are written (steno puts those on the right hand; we put them
in a stroke *position* instead).

| left thumb | right thumb | class | meaning |
| --- | --- | --- | --- |
| NW | NW | core | onset **LAB** + nucleus **F** |
| NW | NE | core | onset **LAB** + nucleus **A** |
| NW | SW | core | onset **LAB** + nucleus **U** |
| NW | SE | bare | consonant, family **LAB** |
| NE | NW | core | onset **COR** + nucleus **F** |
| NE | NE | core | onset **COR** + nucleus **A** |
| NE | SW | core | onset **COR** + nucleus **U** |
| NE | SE | bare | consonant, family **COR** |
| SW | NW | core | onset **DOR** + nucleus **F** |
| SW | NE | core | onset **DOR** + nucleus **A** |
| SW | SW | core | onset **DOR** + nucleus **U** |
| SW | SE | bare | consonant, family **DOR** |
| SE | NW | core | onset **SIB** (or zero onset) + nucleus **F** |
| SE | NE | core | onset **SIB** (or zero onset) + nucleus **A** |
| SE | SW | core | onset **SIB** (or zero onset) + nucleus **U** |
| SE | SE | bare | consonant, family **SIB** |

Twelve cores, four bare strokes. Nothing else exists: no mode chords, no punctuation chords, no
"space chord". Punctuation and space stay on the toolbar, as they are today.

### 4.3 Composition rules

Notation: `FAM·F` = core chord (left family, right nucleus); `FAM·∅` = bare consonant chord.
A sequence is written with `–` between strokes and `␣` for the word-terminating space.

1. **Core rule.** A core chord contributes one onset consonant (the last member of the pending
   consonant run) and one nucleus. Its left thumb names the onset's family and its right thumb names
   the nucleus family.
2. **Bare rule.** A bare chord contributes one consonant of the named family. Bare strokes *before* a
   core are part of its onset cluster, in order; bare strokes *after* a core are codas, in order.
3. **Nucleus rule.** Every core opens a syllable. Nucleus families are attributes of the vowel
   actually spoken, so an unstressed schwa is **A**, and /æ/ is **F**.
4. **Diphthong rule.** A diphthong is written as its nucleus followed by its glide:
   /eɪ/ → `F` + bare `/j/`; /aɪ/ → `A` + bare `/j/`; /ɔɪ/ → `U` + bare `/j/`; /oʊ/ → `U` + bare
   `/w/`; /aʊ/ → `A` + bare `/w/`. The glide's family is fixed by its articulation (/j/ is SIB,
   /w/ is DOR), so this rule needs no exceptions.
5. **Rhotics.** R-coloured vowels are nucleus + bare `/r/` (COR): /ɝ/ → `A`+COR, /ɑr/ → `A`+COR,
   /ɔr/ → `U`+COR, /ɛr/ → `F`+COR, /ɪr/ → `F`+COR.
6. **Clusters** need no special rule: they are bare strokes. `street` /strit/ = three onset members
   (s, t, r) then the core: `COR·∅ – COR·∅ – COR·F – COR·∅`.
7. **Paraphrase segmentation is structural, not probabilistic.** Because bare chords are distinguishable
   from cores, the stroke sequence segments *uniquely* into syllables. This is a genuine advantage
   over steno, where segmentation is resolved by longest-match dictionary lookup: FSL never needs
   to guess where a syllable boundary is. (It still needs to guess *which* member of a family was
   meant — §5.)

Worked illustrations (all generated from these rules, see
[examples](input_language_examples.md#2-the-41-examples)):

| Word | IPA | Strokes | Reading |
| --- | --- | --- | --- |
| the | /ðə/ | `COR·A` | one core: dental fricative onset + central vowel |
| cat | /kæt/ | `DOR·F – COR·∅` | velar onset + front vowel, coronal coda |
| street | /strit/ | `COR·∅ – COR·∅ – COR·F – COR·∅` | s + t + r onset, high front nucleus, coronal coda |
| information | /ˌɪnfərˈmeɪʃən/ | 8 strokes (3 in reduced form) | four syllables, each a core, plus bare strokes |

### 4.4 Reduced sketch (fluent mode)

Steno drops unstressed vowels (`tablet` → `TABLT`,
[Learn Plover lesson 2](https://opensteno.org/learn-plover/lesson-2-steno-order.html)). FSL-1 uses
the same principle at syllable level:

> **Reduced rule:** write the stressed syllable's onset bares and core, plus the word-final coda;
> omit unstressed syllables.

| Word | Full | Reduced |
| --- | --- | --- |
| banana | `LAB·A – COR·F – COR·A` (3) | `LAB·A` (1) |
| information | 8 | `COR·∅ – LAB·F – COR·∅` (3) |
| computer | `DOR·A – LAB·∅ – LAB·∅ – SIB·U – COR·A – COR·∅` (6) | `LAB·∅ – LAB·∅ – SIB·U – COR·∅` (4) |

Reduced forms are *the same language* — every chord means exactly what it always means — but they
rely far more on context (§7.3). The user chooses per word; the app never silently reduces.

### 4.5 Briefs

A brief is a dictionary entry whose outline is shorter than the word's full sketch. Briefs are
entries, **not new chord meanings**, exactly as in Plover
([lesson 9](https://opensteno.org/learn-plover/lesson-9-designing-briefs.html)). Rules adopted:

* Brief outlines must be *prefixes or reductions* of the word's own sketch wherever possible, so that
  a brief is a compression of the word rather than an arbitrary code (Plover's guidance: "briefs
  should resemble the word").
* A brief may not collide with another brief. Collisions with full sketches are permitted only if the
  brief's tier outranks the lexicon tier, and the collision must be listed in the dictionary manifest.
* Briefs are curated by frequency tier, not by topic: function words, pronouns, auxiliaries,
  prepositions, then common verbs and nouns.

The natural consequence of the design is that most short function words are already one stroke:
`the` `COR·A`, `to` `COR·U`, `we` `DOR·F`, `you` `SIB·U`. Only longer frequent words need curated
briefs.

## 5. Ambiguity — the central cost, stated plainly

FSL-1 is **class-level**, so any two words whose phonemes fall in the same families at the same
positions share a sketch. The alphabet deliberately trades phonemic precision for learnability; the
cost is paid at selection time.

### 5.1 Collision classes

| Class | Cause | Example (from the 41-word demo lexicon) |
| --- | --- | --- |
| Homophones (unavoidable) | English itself | `right` / `write`; `there` / `their`; `no` / `know` |
| Family-internal vowel merger | F holds /i ɪ e ɛ æ/ | `red` / `read` |
| Family-internal coda merger | COR holds /t d n s z l r θ ð/ | `need`, `red`, `there`, `their`, `read` all end `COR·∅` |
| Zero onset vs /ʃ/ | SE doubles as "no onset" | `and` and `ship` both start `SIB·F`; they differ later |

Measured on the demo lexicon (not on English): **32 distinct sketches for 41 words**, with one
5-way collision (`need`, `there`, `their`, `read`, `red`), two 3-way groups (`no`/`know`/`dog`,
`water`/`good` in reduced form) and several 2-way groups (`it`/`is`, `I`/`judge`, `right`/`write`).
See [examples §7](input_language_examples.md#7-collisions-measured-on-the-demo-lexicon).

### 5.2 Exact, assumption-free bound

For monosyllabic CVC material the sketch space is 4 onset families × 3 nuclei × 4 coda families = 48
shapes. Collisions are guaranteed once the lexicon exceeds 48 items of that shape. This is why §4.4
(reduced forms) and §6 (refinement) exist.

### 5.3 How ambiguity is resolved, in order

1. **Structural precision** — write more of the sketch (full rather than reduced form, or add pin
   strokes under FSL-2).
2. **Dictionary ordering** — tiered dictionary returns an ordered candidate list (§5.4).
3. **Context ranking** — n-gram model reorders that list ([prediction doc](context_prediction_architecture.md)).
4. **Explicit user selection** — alternatives surface, previewed but not committed.
5. **Correction** — undo/`⇄` revert exactly what was committed.

### 5.4 Dictionary model (a deliberate divergence from Plover)

Plover allows exactly one translation per outline
([design](https://plover.readthedocs.io/en/latest/design.html)). FSL cannot follow that rule: the
outline is ambiguous *by construction*, so the translator must return an **ordered set** of candidate
words per committed sketch.

Proposed tiers, highest priority first:

| Tier | Content | Match rule |
| --- | --- | --- |
| 0 | user personalisations (off by default for sensitive fields) | exact sketch |
| 1 | curated briefs (≤ 2 strokes, frequency-ranked) | exact sketch |
| 2 | core lexicon (top ~20k words with IPA, sketched at build time) | exact sketch, then reduced-form match |
| 3 | expanded lexicon (~100k, sketched at build time) | reduced-form match only |
| — | fallback | sketch shown verbatim as *unknown*, with spell/undo |

Within a tier the order is by corpus frequency; across tiers, context ranking may promote a
lower-tier candidate (that is the prediction layer's job, not the dictionary's).

## 6. FSL-2 — optional refinement (proposal, requires an engine change)

FSL-2 keeps every FSL-1 meaning and adds one optional stroke type. It exists because §5.2 shows the
coarse alphabet cannot be sufficient for a real lexicon.

**Pin strokes.** After a core, the user may produce a *single-thumb* stroke:

* single **left** thumb in direction `d` refines the **onset**, selecting among that family's members;
* single **right** thumb in direction `d` refines the **nucleus**;
* the pair (core direction, pin direction) addresses 4 × 4 = 16 onset symbols and 3 × 4 = 12 nucleus
  symbols, i.e. 256 fully specified (onset, nucleus) combinations.

Because a pin is a half-stroke, it can never be confused with a core (which needs both thumbs), so
the grammar stays deterministic: `word := (bare* core pin? pin?)+`.

**Two hard requirements, both currently unmet:**

1. `GestureEngine` rejects a gesture that never gets both directions
   (`RejectionReason.INCOMPLETE_DIRECTIONS`), by design: "a chord that never gets both directions is
   rejected, not guessed". FSL-2 needs single-direction strokes to be *accepted as a different stroke
   class*, which changes a tested invariant of the current engine and its documentation.
2. Commit currently happens when one thumb lifts after both directions are known. Pin strokes require
   a different commit model (e.g. commit only when both thumbs are up, or on pause), which affects
   duplicate-insertion protection and the existing `COMMITTED` lock semantics.

Neither is a small change, which is why FSL-2 is a proposal and the decision record keeps FSL-1 as
the near-term language.

**Why it is worth having anyway:** the pin is *optional and incremental*. A beginner writes FSL-1
sketches; a fluent user adds a pin only when the preview shows the wrong word. Precision is bought
one stroke at a time, and no chord ever changes meaning (I6 holds in both modes).

## 7. Limitations, stated as limitations

1. **Not phonemic.** Classes merge up to 9 consonants and 5 vowels. Minimal pairs inside a family are
   indistinguishable without a pin stroke.
2. **Zero onset is conflated with /ʃ/** (SE family doubles as "no onset"). A dedicated symbol would
   need a 17th chord.
3. **Clusters cost one stroke per consonant.** `strength` is five strokes; steno writes many such
   words in one. This is the price of two thumbs instead of 22 keys, and it is the main reason briefs
   matter.
4. **Syllable count is not compressed.** Long words are expensive unless a reduced form or brief is
   used; reduced forms increase ambiguity.
5. **Vowel system is GA-oriented.** RP /ɒ/ vs /ɔː/, and /əʊ/ vs /oʊ/, need either a dialect flag or a
   pragmatic merge. The design says: map by family, accept the merge, let the dictionary fix words.
6. **No numbers, punctuation, or capitalisation in the language.** They remain toolbar/mode concerns
   and are out of scope here.
7. **No letter mode is specified.** Writing an unknown name (e.g. "Książek", "Ngo") has no defined
   path yet. This is an open question in the decision record, not an oversight to paper over.
8. **Nothing here is measured.** Every stroke count in this document is generated from the rules; no
   human has typed with FSL, and no device test exists.

## 8. Summary of what each chord can and cannot express

| Question | Answer |
| --- | --- |
| Can every chord be used? | Yes — 12 cores, 4 bare consonants. |
| Can any English word be written? | By rule, yes (full sketch), with class-level ambiguity. |
| Does a chord ever change meaning? | No. Context can only reorder candidates. |
| Can the user write an unknown word? | Only via a full sketch; letter-level spelling is unspecified. |
| Is it faster than QWERTY? | Unknown, and no evidence supports claiming it (see [evaluation](evaluation_plan.md)). |
