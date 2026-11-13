# FSL-1 input language — worked examples

Every stroke sequence in this document was generated from the rules in
[flowboard_language_design.md](flowboard_language_design.md) §4 by a script, not typed by hand, so
the examples cannot drift from the specification. The dictionary and prediction columns describe
what the *proposed* engine should do; they are design intent, not measurements — no dictionary or
predictor for FSL exists yet.

Notation:

| Symbol | Meaning |
| --- | --- |
| `LAB COR DOR SIB` | consonant families on the left thumb (labial, coronal, dorsal, sibilant/zero-onset) |
| `F A U` | nucleus families on the right thumb (front, central, back) |
| `FAM·FAM` | a **core** chord: left family + right nucleus family |
| `FAM·∅` | a **bare consonant** chord (right thumb SE): used for cluster members and codas |
| `–` | next stroke |
| `␣` | the space/commit action (toolbar, not a chord) |

Reading a row: the **strokes** column is exactly what the thumbs do; **reading** is the phonetic
interpretation the grammar assigns; **dictionary** is the candidate set the tiered dictionary would
return for that sketch; **prediction** is what context ranking does to it; **user action** is what the
person must physically do to commit the intended text.

## 1. How the demos were produced

The generator tokenises a General-American IPA transcription, attaches the consonant immediately
before each vowel to that vowel's core chord, treats every other consonant as a bare chord, and
reports the sequence. The full form is the default; the reduced form applies the "drop unstressed
syllables" rule of §4.4.

## 2. The 41 examples

### 2.1 Function words and pronouns

| # | text | IPA | strokes | reading | dictionary | prediction | user action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **the** | /ðə/ | 1: `COR·A` | coronal onset + central vowel | `the` (unique sketch) | not needed | one stroke, `␣` |
| 2 | **and** | /ænd/ | 3: `SIB·F – COR·∅ – COR·∅` | zero onset + front vowel, coronal coda | `and` | competes with `it`/`is` only if the coda is omitted | three strokes, `␣` |
| 3 | **to** | /tu/ | 1: `COR·U` | coronal onset + back vowel | `to` | not needed | one stroke, `␣` |
| 4 | **of** | /ʌv/ | 2: `SIB·A – LAB·∅` | zero onset + central vowel, labial coda | `of` | not needed | two strokes, `␣` |
| 5 | **a** | /ə/ | 1: `SIB·A` | zero onset + central vowel | `a` | competes with `I` if a glide is dropped | one stroke, `␣` |
| 6 | **I** | /aj/ | 2: `SIB·A – SIB·∅` | zero onset + central nucleus + palatal glide | `I` (also `judge` family-wise) | after a verb, `I` is top-ranked | two strokes, `␣` |
| 7 | **you** | /ju/ | 1: `SIB·U` | palatal onset + back vowel | `you` | not needed | one stroke, `␣` |
| 8 | **we** | /wi/ | 1: `DOR·F` | dorsal/glide onset + front vowel | `we` | not needed | one stroke, `␣` |
| 9 | **they** | /ðej/ | 2: `COR·F – SIB·∅` | coronal onset + front nucleus + glide | `they` | not needed | two strokes, `␣` |
| 10 | **it** | /ɪt/ | 2: `SIB·F – COR·∅` | zero onset + front vowel, coronal coda | `it`, `is` (same sketch) | sentence context separates `it` from `is` reliably | select `it` if not ranked first |
| 11 | **is** | /ɪz/ | 2: `SIB·F – COR·∅` | same sketch as `it` | `it`, `is` | after a subject NP, `is` ranks first | select `is` |
| 12 | **no** | /now/ | 2: `COR·U – DOR·∅` | coronal onset + back nucleus + labio-velar glide | `no`, `know`, `dog` | after `I`/`say`, `no`/`know` dominate | select intended word |
| 13 | **know** | /now/ | 2: `COR·U – DOR·∅` | identical to `no` | `no`, `know`, `dog` | homophone ranking is purely contextual | select `know` |

### 2.2 Monosyllables and minimal pairs

| # | text | IPA | strokes | reading | dictionary | prediction | user action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 14 | **cat** | /kæt/ | 2: `DOR·F – COR·∅` | dorsal onset + front vowel, coronal coda | `cat`, `cad`, `can`, `cap` is labial so excluded | article + adjective context favours `cat` | two strokes, `␣` |
| 15 | **dog** | /dɔg/ | 2: `COR·U – DOR·∅` | coronal onset + back vowel, dorsal coda | `dog`, `dock`, `dot` is coronal so excluded | `the` before it favours `dog` | select if necessary |
| 16 | **man** | /mæn/ | 2: `LAB·F – COR·∅` | labial onset + front vowel, coronal coda | `man`, `mat`, `mad`, `mass`, `map` excluded (labial coda) | determiner context favours `man` | two strokes |
| 17 | **need** | /nid/ | 2: `COR·F – COR·∅` | coronal onset + front vowel, coronal coda | `need`, `read`, `red`, `there`, `their` (5-way) | after `we`/`I`, `need` ranks first | select if not first |

### 2.3 Clusters

| # | text | IPA | strokes | reading | dictionary | prediction | user action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 18 | **stop** | /stɑp/ | 3: `COR·∅ – COR·A – LAB·∅` | s + t onset, central vowel, labial coda | `stop` (other `stVp` words share the sketch) | imperative context favours `stop` | three strokes |
| 19 | **street** | /strit/ | 4: `COR·∅ – COR·∅ – COR·F – COR·∅` | s + t + r onset, front nucleus, coronal coda | `street` | after `the`/`on`, `street` first | four strokes |
| 20 | **strength** | /strɛŋθ/ | 5: `COR·∅ – COR·∅ – COR·F – DOR·∅ – COR·∅` | three-consonant onset, front vowel, dorsal + coronal coda | `strength` (rare; likely needs a brief) | low frequency, so no strong prior | five strokes — a brief is recommended |
| 21 | **texts** | /tɛksts/ | 5: `COR·F – DOR·∅ – COR·∅ – COR·∅ – COR·∅` | coronal onset, front vowel, four-consonant coda | `texts` | plural context helps | five strokes — brief recommended |
| 22 | **think** | /θɪŋk/ | 3: `COR·F – DOR·∅ – DOR·∅` | coronal (dental) onset, front vowel, dorsal coda ×2 | `think` | after `I`, `think` ranks first | three strokes |
| 23 | **ship** | /ʃɪp/ | 2: `SIB·F – LAB·∅` | sibilant onset, front vowel, labial coda | `ship`, `shop` differs in nucleus | after `the`, `ship` first | two strokes |
| 24 | **judge** | /dʒʌdʒ/ | 2: `SIB·A – SIB·∅` | affricate onset, central vowel, affricate coda | `judge` | legal/verb context | two strokes |

### 2.4 Multi-syllable words

| # | text | IPA | strokes | reading | dictionary | prediction | user action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 25 | **water** | /wɔtər/ | 3: `DOR·U – COR·A – COR·∅` (reduced 2: `DOR·U – COR·∅`) | two cores + coda | `water` | after `a glass of`, `water` first | three strokes (two if reduced form is accepted) |
| 26 | **banana** | /bənænə/ | 3: `LAB·A – COR·F – COR·A` (reduced 1: `LAB·A`) | three cores, repeated `A` nuclei | `banana`; reduced form collides with many `bVnV` words | reduced form needs strong context | full form: three strokes |
| 27 | **computer** | /kəmˈpjutər/ | 6: `DOR·A – LAB·∅ – LAB·∅ – SIB·U – COR·A – COR·∅` (reduced 4) | three cores + onset cluster + coda | `computer` | technical context favours `computer` over `commuter` | six strokes, or 4 reduced |
| 28 | **keyboard** | /kibɔrd/ | 4: `DOR·F – LAB·U – COR·∅ – COR·∅` (reduced 3) | two cores + coda | `keyboard` | after `the`/`my`, first | four strokes |
| 29 | **information** | /ˌɪnfərˈmejʃən/ | 8: `SIB·F – COR·∅ – LAB·A – COR·∅ – LAB·F – SIB·∅ – SIB·A – COR·∅` (reduced 3: `COR·∅ – LAB·F – COR·∅`) | four cores + coda | `information` | reduced form is the practical default; context (`more`, `the`) disambiguates | 3 strokes in reduced form |
| 30 | **impossible** | /ɪmˈpɑsəbəl/ | 6: `SIB·F – LAB·∅ – LAB·A – COR·A – LAB·A – COR·∅` (reduced 3: `LAB·∅ – LAB·A – COR·∅`) | four cores | `impossible` | `it is` context | 3 strokes reduced |
| 31 | **please** | /pliz/ | 3: `LAB·∅ – COR·F – COR·∅` | p + l onset, front nucleus, coronal coda | `please` | polite phrase context | three strokes |
| 32 | **thanks** | /θæŋks/ | 4: `COR·F – DOR·∅ – DOR·∅ – COR·∅` | coronal onset, front vowel, dorsal + coronal coda | `thanks` / `thank` | greeting context | four strokes |

### 2.5 Homophones and rhotics

| # | text | IPA | strokes | reading | dictionary | prediction | user action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 33 | **right** | /rajt/ | 3: `COR·A – SIB·∅ – COR·∅` | coronal onset, central nucleus + palatal glide, coronal coda | `right`, `write` | `the` + noun favours `right`; `to` favours `write` | select if needed |
| 34 | **write** | /rajt/ | 3: identical to `right` | identical | `right`, `write` | verb position favours `write` | select `write` |
| 35 | **there** | /ðɛr/ | 2: `COR·F – COR·∅` | coronal onset, front nucleus, rhotic coda | 5-way group with `need`, `their`, `read`, `red` | `over there`/existential context | select |
| 36 | **their** | /ðɛr/ | 2: identical | identical | same group | possessive + noun context | select |
| 37 | **read** | /rid/ | 2: `COR·F – COR·∅` | coronal onset, front nucleus, coronal coda | same group | after `I`/`to`, `read` competes with `need` | select |
| 38 | **red** | /rɛd/ | 2: `COR·F – COR·∅` | same sketch as `read` because /i/ and /ɛ/ share the F family | same group | colour context strongly favours `red` | select |
| 39 | **world** | /wɜld/ | 3: `DOR·A – COR·∅ – COR·∅` | glide onset, central rhotic, coronal coda | `world` | `the`/`around` context | three strokes |
| 40 | **morning** | /mɔrnɪŋ/ | 4: `LAB·U – COR·∅ – COR·F – DOR·∅` (reduced 2: `LAB·U – DOR·∅`) | two cores + codas | `morning`, `mourning` (homophone) | greeting context favours `morning` | two strokes reduced |
| 41 | **good** | /gʊd/ | 2: `DOR·U – COR·∅` | dorsal onset, back vowel, coronal coda | `good`; reduced form collides with `water` | adjective position | two strokes |

## 3. Sentences

Spaces are explicit toolbar actions (`␣`); no chord means "space", so a sentence is a sequence of
committed words.

| Sentence | Flow |
| --- | --- |
| I think the cat is good | `SIB·A – SIB·∅` ␣ `COR·F – DOR·∅ – DOR·∅` ␣ `COR·A` ␣ `DOR·F – COR·∅` ␣ `SIB·F – COR·∅` ␣ `DOR·U – COR·∅` |
| we need information | `DOR·F` ␣ `COR·F – COR·∅` ␣ `COR·∅ – LAB·F – COR·∅` |
| the computer keyboard | `COR·A` ␣ `LAB·∅ – LAB·∅ – SIB·U – COR·∅` ␣ `DOR·F – COR·∅ – COR·∅` |

The second sentence is the interesting one: `need` and `information` are written in reduced form, so
the sketcher is relying on context. This is the intended fluent mode and the reason prediction is not
optional decoration.

## 4. Morphology

Inflections are written with the same rules and cost one bare stroke each, because English suffixes
are single consonants or syllables:

| Word | IPA | strokes | sequence |
| --- | --- | --- | --- |
| word | /wɜrd/ | 3 | `DOR·A – COR·∅ – COR·∅` |
| words | /wɜrdz/ | 4 | `DOR·A – COR·∅ – COR·∅ – COR·∅` |
| read | /rid/ | 2 | `COR·F – COR·∅` |
| reading | /ˈridɪŋ/ | 3 (reduced 2) | `COR·F – COR·F – DOR·∅` |
| write | /rajt/ | 3 | `COR·A – SIB·∅ – COR·∅` |
| writing | /ˈrajtɪŋ/ | 4 (reduced 2) | `COR·A – SIB·∅ – COR·F – DOR·∅` |
| type | /tajp/ | 3 | `COR·A – SIB·∅ – LAB·∅` |
| typed | /tajpt/ | 4 | `COR·A – SIB·∅ – LAB·∅ – COR·∅` |
| talk | /tɔk/ | 2 | `COR·U – DOR·∅` |
| talking | /ˈtɔkɪŋ/ | 3 (reduced 2) | `COR·U – DOR·F – DOR·∅` |

Note that `-ing` is written as a second core (`COR·F` if the suffix is /ɪŋ/ with a zero onset, plus a
bare dorsal for /ŋ/), so a suffix is not free — this is exactly where briefs earn their keep.

## 5. Vowel-heavy words and difficult clusters

| Word | IPA | strokes | sequence | Note |
| --- | --- | --- | --- | --- |
| idea | /ajˈdiə/ | 4 | `SIB·A – SIB·∅ – COR·F – SIB·A` | two zero-onset nuclei plus glide |
| area | /ˈɛriə/ | 3 | `SIB·F – COR·F – SIB·A` | zero-onset final schwa |
| oil | /ɔjl/ | 3 | `SIB·U – SIB·∅ – COR·∅` | diphthong + coda |
| owl | /awl/ | 3 | `SIB·A – DOR·∅ – COR·∅` | diphthong with /w/ glide |
| sprint | /sprɪnt/ | 5 | `COR·∅ – LAB·∅ – COR·F – COR·∅ – COR·∅` | four-consonant coda |
| splashed | /splæʃt/ | 5 | `COR·∅ – LAB·∅ – COR·F – SIB·∅ – COR·∅` | onset cluster + coda cluster |

These are the design's worst cases, and they are honestly bad: five strokes for five phonemes means
no compression at all. A production dictionary would carry briefs for `sprint`, `splashed` or drop
them to reduced forms.

## 6. Where prediction helps, and where it fails

Prediction here means: reordering the dictionary's candidate list for the current sketch, and
highlighting the most likely *next* chord. It never changes what a chord means.

**Helps:**

* `it` vs `is` (same sketch `SIB·F – COR·∅`). After a noun phrase, `is` is overwhelmingly likely;
  after `I think`, `it` is. A bigram model separates them almost perfectly, so the user's usual
  action is just `␣`.
* `right` vs `write` after `to` versus after `the`. Prepositional context is a strong signal.
* Reduced forms of long words: `information` as 3 strokes is only viable because `more`/`for`/`the`
  make it the top candidate.
* Next-chord highlighting: after `SIB·A – SIB·∅` ("I"), the next word is often a verb, whose first
  chord can be highlighted.

**Fails or struggles:**

* **No context at the start of an utterance.** The first word has only a unigram prior; a 5-way
  sketch like `COR·F – COR·∅` will often need explicit selection.
* **Rare words and names.** Frequency priors actively hurt; the user must page through alternatives.
  This is the documented OOV failure mode of dictionary-based input (15% OOV made T9-style methods
  slower than multi-tap in one study, [W03-2505](https://aclanthology.org/W03-2505.pdf)).
* **Deliberate ambiguity in the language** (homophones). No amount of context ranking fixes
  `there`/`their` in isolation; the user must look.
* **Reduced forms of short words.** `banana` reduced to `LAB·A` collides with a large neighbourhood;
  recommending a reduced form there would be bad UX. The app should not suggest reduction for words
  whose reduced sketch has many competitors.
* **Code-switching and technical text**, where the n-gram prior is wrong.

## 7. Collisions measured on the demo lexicon

Computed over the 41 words above (not over English): **32 distinct sketches**.

| Words sharing a sketch | Sketch |
| --- | --- |
| need, there, their, read, red | `COR·F – COR·∅` |
| no, know, dog | `COR·U – DOR·∅` |
| it, is | `SIB·F – COR·∅` |
| I, judge | `SIB·A – SIB·∅` |
| right, write | `COR·A – SIB·∅ – COR·∅` |
| water, good (reduced forms only) | `DOR·U – COR·∅` |

This is the honest headline: a 41-word lexicon already produces a 5-way collision, and the demo
lexicon is not representative — real English would be far denser. The language as specified is a
*learning prototype*, not a production writing system, until refinement strokes (FSL-2, §6 of the
design) or a much finer alphabet exist.

## 8. Consistency check

The tables above were produced by the same generator that produced the inventory in
[flowboard_language_design.md](flowboard_language_design.md) §4.2. If a rule changes (for example
moving /w/ out of DOR), regenerate both documents rather than editing stroke sequences by hand.
