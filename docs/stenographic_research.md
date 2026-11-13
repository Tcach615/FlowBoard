# Stenographic research for FlowBoard

Status: research only. Nothing in this document is implemented, and no claim here changes the
shipped app. Sources are cited inline as links; every statement is marked as either *established*
(from a cited source) or *adaptation* (my design decision) in the section where it appears.

## 1. Why research before design

FlowBoard's current linguistic layer maps sixteen chords to a changing list of ranked candidates
(`ChordBoard`, `PredictionEngine`). That produces outputs like `tion` and `and` that have no shared
structure, so the user learns sixteen rapidly-changing slots rather than a writing system. A
stenographic language inverts this: the chord inventory is *fixed*, meaning lives in the chord's
articulation, and variability is pushed into (a) multi-stroke composition and (b) dictionary plus
context disambiguation.

This document establishes what is and is not known about the systems we would be borrowing from.

## 2. English stenotype

### 2.1 Layout and hand roles — established

The canonical English steno order is written as the string `STKPWHRAO*EUFRPBLGTSDZ`
([Learn Plover, lesson 2](https://opensteno.org/learn-plover/lesson-2-steno-order.html); the
[Plover wiki](https://plover.wiki/index.php/Stenotype_layout) attributes the Ireland/WSI layout to a
1911 patent with the `*` and `-D`/`-Z` columns added later).

The keyboard divides into banks with fixed roles
([Stenograph Luminex user guide](https://www.stenograph.com/content/files/documents/Luminex%20Manuals/Luminex_CSE_User_Guide.pdf)):

| Bank | Keys | Role |
| --- | --- | --- |
| Number bar | (bar over the upper bank) | digits, via the keys below it |
| Left / upper + lower banks | `S T K P W H R` | **initial** (onset) consonants |
| Thumbs | `A O E U` (with `*` between `AO` and `EU`) | **vowels** |
| Right banks | `F R P B L G T S D Z` | **final** (coda) consonants |

Britannica states the same three-part division: left hand for consonants before the vowel, thumbs
for the vowel, right hand for consonants after it
([Britannica, machine shorthand](https://www.britannica.com/topic/shorthand/Machine-shorthand)).
The number bar is a shift over the upper bank: `#S-`=1, `#T-`=2, `#P-`=3, `#H`=4, `#A`=5, `#O`=0,
`#-F`=6, `#-P`=7, `#-L`=8, `#-T`=9
([Learn Plover, lesson 8](https://opensteno.org/learn-plover/lesson-8-numbers.html)).

### 2.2 One stroke, one syllable-shaped bundle — established

Stenotype presses an arbitrary subset of those 22 keys at once, and the *position* of a key in
steno order determines when its phoneme is read. `KAT` is "cat" in one stroke because `K` is left of
the vowels and `T` is right of them
([Wikipedia, Stenotype](https://en.wikipedia.org/wiki/Stenotype)). The Stenograph manual gives
`R E D`, `SH O P`, `ST A RT` as single-stroke words and `K O R T / H O U S` ("courthouse"),
`WORBG / HORS` ("workhorse") as two-stroke words
([Luminex guide](https://www.stenograph.com/content/files/documents/Luminex%20Manuals/Luminex_CSE_User_Guide.pdf)).

Three consequences of that structure matter for us:

1. **A stroke carries a bundle, not a string.** Several phonemes are emitted simultaneously and
   ordered by key position, which is why steno is fast.
2. **Steno order admits one vowel per stroke.** `Learn Plover` states that "steno order only allows
   for a single vowel sound in any one stroke", that unstressed vowels are therefore dropped
   (`tablet` → `TABLT`), and that one inversion of neighbouring sounds is permitted
   (`portal` → `PORLT`)
   ([lesson 2](https://opensteno.org/learn-plover/lesson-2-steno-order.html)).
3. **Words that do not fit are written over several strokes**, each of which obeys the same order.

### 2.3 Plover — established

* **Layout is logical, not physical.** Plover uses the same logical layout on any keyboard; on a
  QWERTY board it is only a default remapping (`QWER TY UIOP[` → `STPH ** FPLTD;` etc.)
  ([Plover wiki beginner's guide](https://plover.wiki/index.php/Beginner%27s_Guide/en)).
* **Dictionaries are outline → translation.** Default format is JSON keyed by canonical steno
  notation: `{"KAT": "cat", "KAT/HROG": "catalog", "-S": "{^s}"}`
  ([dict_formats](https://plover.readthedocs.io/en/latest/dict_formats.html)).
* **One translation per outline — deliberately.** "Plover does not support" conflicts between
  multiple entries with the same steno; "each outline may only have exactly one translation"
  ([design](https://plover.readthedocs.io/en/latest/design.html)). Multiple *outlines* for one word
  are normal (`TKAUR`, `TKAUT/ER`, `TKAUG/TER` for "daughter",
  [lesson 9](https://opensteno.org/learn-plover/lesson-9-designing-briefs.html)).
* **Translation is longest-match over a dictionary stack**, not probabilistic. The translator tries
  the longest series of strokes first, then one fewer, against each dictionary in priority order,
  and keeps a 100-stroke buffer
  ([design](https://plover.readthedocs.io/en/latest/design.html)).
* **Briefs are dictionary entries that violate theory** to use fewer strokes (`KPRAORD` =
  "extraordinary", `SG` = "something"), and the guidance is that they should resemble the word and
  must not collide with existing outlines "or for one of the strokes of a multi-stroke word"
  ([cheat sheet](https://opensteno.org/learn-plover/appendix-cheat-sheet.html),
  [lesson 9](https://opensteno.org/learn-plover/lesson-9-designing-briefs.html)).
* **Homophones are separated at design time**, by asterisk insertion, vowel alteration, or extra
  strokes — e.g. `TAOEPL` "team" vs `TAOEPL/R-R` "teem"
  ([Plover wiki, theory](https://plover.wiki/index.php/Theory)).

### 2.4 Speed — established, with a caveat

* NCRA's RPR certification requires 180 wpm literary, 200 wpm jury charge, 225 wpm testimony at 95%
  accuracy ([NCRA](https://www.ncra.org/certification/NCRA-Certifications/registered-professional-reporter)).
* The Guinness record is 360 wpm at 97.23% (Mark Kislingbury, 2004)
  ([Guinness](https://www.guinnessworldrecords.com/world-records/fastest-realtime-court-reporter-stenotype-writing)).
* Plover's system-design notes put professional chording at "the usual 3.5 to 4 chords per second"
  ([key arrangement](https://plover.readthedocs.io/en/latest/system-dev/key-arrangement.html)).
* **Strokes per word is theory-dependent and has no authoritative average.** Plover's wiki says
  theories sit on a continuum between "short" (few strokes, heavy briefs) and "long" (roughly one
  stroke per syllable), and that both reach professional speeds
  ([theory](https://plover.wiki/index.php/Theory)). The only concrete bands found are from one
  vendor, presented as reporter categories rather than measurements: 1.2 / 1.0 / 0.8 strokes per
  word for "medium / short / very short" theories
  ([Magnum Steno](https://www.magnumsteno.com/writingshort.html)).

**Caveat for any FlowBoard claim:** FlowBoard serialises what steno parallelises. A steno stroke can
carry onset + vowel + coda simultaneously; a FlowBoard chord cannot. Professional steno rates
therefore do *not* transfer, and no claim of steno-like speed is supported by this literature.

## 3. English phonology — the material the language must encode

| Fact | Value | Source |
| --- | --- | --- |
| Phoneme count, RP | 44 (24 consonants + 20 vowels) | [Brooks, *Dictionary of the British English Spelling System*](https://books.openedition.org/obp/2184?lang=en) |
| Consonant count, cross-dialect | 24 ("generally put at 24") | [Wikipedia, English phonology](https://en.wikipedia.org/wiki/English_phonology) |
| Vowel count, General American | 14–16 depending on analysis (14/15 teaching count; 20 in analyses with r-coloured vowels) | [Wikipedia](https://en.wikipedia.org/wiki/English_phonology); [UC Irvine OCW](https://ocw.uci.edu/upload/files/vowels.pdf) |
| Stressed vowel sets | 24 Wells lexical sets (KIT, DRESS, TRAP, LOT, STRUT, …) | [Wells table reproduction](https://pdcrodas.webs.ull.es/variedades/EnglishVowels.pdf); [Wells chart commentary](https://www.uv.es/anglotic/accents_of_english/01/jc_wells_vowel_chart.html) |
| Syllable shape | (C)₃V(C)₄ — `spry` /spraɪ/, `texts` /teksts/ | [Aslam & Kak, ch. 4](https://www.cambridge.org/core/services/aop-cambridge-core/content/view/7869189F82B207DAB0F34CCB771F1E63/9788175968653c4_p60-68_CBO.pdf/english-syllable-structure.pdf) |
| Three-consonant onsets | first must be /s/, second a voiceless stop, third a sonorant | [Essentials of Linguistics 2e §4.2](https://ecampusontario.pressbooks.pub/essentialsoflinguistics2/chapter/4-2-phonotactics-and-natural-classes/) |
| Coda restrictions | /h/ never syllable-final; after a diphthong the coda must be coronal (`kind` [kaɪnd], not *[kaɪmp]) | [Davis, *Syllable constituents*](https://cl.indiana.edu/davis/SyllableConstituentsENC.pdf) |
| Medial cluster statistics | "only 50 of the 200 most likely combinations actually occur" | [Pierrehumbert](https://www.phon.ox.ac.uk/jpierrehumbert/publications/pierrehumbert_labphon3OR.pdf) |
| Phoneme frequency | top ten phonemes = 47.3% of conversational tokens | [Mines, Hanson & Shoup 1978](https://jontalle.web.engr.illinois.edu/Public/MinesHansonShoup-FreqOfEnglishPhonesConversation.78.pdf) |
| Word length | 6.94 phonemes per type, 3.54 per token; 2.72 syllables/type, 1.43/token | [Cutler, Norris & Sebastián-Gallés 2004](https://www.isca-archive.org/interspeech_2004/cutler04_interspeech.pdf) |
| Homophony | 15.6% of English phonological wordforms share their form; 0.19 homophones per wordform; max 7 | [Trott & Bergen 2020](https://pages.ucsd.edu/~bkbergen/papers/trott_bergen_2020.pdf) |

Two of these numbers drive the whole design:

* **24 consonants and 14–20 vowels cannot be addressed one-to-one by 4 + 4 directions.** Sixteen
  chords is 4 bits; the phoneme inventory is roughly 5.5 bits before any composition. Any single-chord
  phoneme mapping must therefore *merge* phonemes.
* **Tokens average 3.54 phonemes and 1.43 syllables**, while types are much longer. A design can be
  cheap on frequent short words and expensive on rare long ones without hurting running text much —
  which is exactly what briefs exploit.

## 4. Why a phonetic system is not "grouped frequent fragments"

The current implementation groups frequent character sequences (`tion`, `and`) as candidates. That
is a *lexical* shortcut with no productive rule: there are infinitely many such fragments, no
compositional semantics, and no way to write a word that is not already in the list.

A phonetic sketch differs in three measurable ways:

1. **Closed inventory.** The phoneme set is finite and stable (24 + 14–20); once the classes are
   learned, the alphabet is complete. Fragments are an open, arbitrary set.
2. **Compositional coverage.** A sketch of phoneme classes can represent any pronounceable English
   word, including words the dictionary has never seen, because the mapping is rule-based rather
   than memorised. Fragment selection covers only listed items.
3. **Bounded ambiguity.** Phonetic coding creates *systematic* collisions (class members, homophones),
   and those collisions are enumerable and can be ranked. English homophony is modest — 0.19
   homophones per wordform — so most collisions come from our coarse classes, not from English
   itself, which means they shrink if classes are refined.

Point 3 is the design's weak point and it is quantified in
[flowboard_language_design.md](flowboard_language_design.md) §7 and measured empirically in
[evaluation_plan.md](evaluation_plan.md).

## 5. Small-keyboard and chording precedent

| System | Keys | Evidence |
| --- | --- | --- |
| GKOS | 6 (3 per hand) | Letters A–F on single keys, pairs produce G K O S W. Creator reports ~20 wpm with practice, ~40 wpm familiar, expert ~60 wpm; "practical limit … around 60 WPM". *Self-reported, not measured* ([gkos.com](https://gkos.com/gkos/gkos-faq.html)) |
| ASETNIOP | 10 (8 home + 2 thumbs) | Designer claims "over 100 wpm once proficiency is attained"; in a 2021 interview the designer reports cruising "in the low 90's" ([asetniop.com](https://asetniop.com/), [Hackaday](https://hackaday.com/2021/08/30/chordie-chording-keyboard-speaks-no-qwerty/)). No peer-reviewed evaluation found |
| 5-key chord prototype | 5 | Learnable "in less than 45 minutes" for a convenient mapping; 20 wpm after ~350 min across ten participants ([UBICOMM 2013](https://personales.upv.es/thinkmind/dl/conferences/ubicomm/ubicomm_2013/ubicomm_2013_4_20_10045.pdf)) |
| Twiddler (one-handed chord) | 12 | Session 1: 4.3 wpm chording vs 8.2 wpm multi-tap; after 400 min, 26.2 wpm; 32.5% of participants' desktop QWERTY speed ([CHI 2004](https://faculty.cc.gatech.edu/~thad/p/030_10_MTE/twiddler-chi04.pdf)) |
| Senorita (two-thumb touchscreen chords) | 10 virtual keys | 13.99 wpm vs 32.7 wpm QWERTY (~60% slower); error rate 0.89% vs 0.92% ([CHI 2020](https://www.asarif.com/pub/Rakhmetulla_CHI2020_Senorita.pdf)) |
| OPTI soft keyboard (model + study) | 15–16 soft keys | Model predicted 58.2 wpm upper bound; measured 44.3 wpm after 20 sessions of 45 min, passing QWERTY at session 10 ([CHI 1999](https://www.yorku.ca/mack/CHI99a.html)) |

What this literature supports, conservatively:

* **Chording is learnable but slow to acquire.** Expect tens of minutes to basic competence and tens
  of hours to plateau; Twiddler needed ~5 sessions before overtaking multi-tap.
* **No published chord system reaches QWERTY speed on a phone**, and the one two-thumb touchscreen
  study found ~60% slower than QWERTY with very low error rates.
* **Throughput is the honest cross-system metric** (bits/second, Wobbrock et al.,
  [CHI 2019](https://faculty.washington.edu/wobbrock/pubs/chi-19.03.pdf)) rather than wpm
  comparisons across unequal tasks.

## 6. Disambiguation precedent: phonetic input is normally resolved by candidates

Our design needs the user to resolve class-level collisions, which is standard in phonetic input:

* **Pinyin** ranks candidate characters/words for a phonetic string and pages them, five at a time;
  evaluation is framed as keystroke cost to reach the intended candidate (rank *r*, page navigation)
  ([ACL 2019](https://aclanthology.org/P19-1154.pdf); [KySS, IJCNLP 2013](https://aclanthology.org/I13-1170.pdf)).
* **Kana-kanji conversion** converts on the spacebar, and pressing space again opens the homophone
  list; one study of candidate display styles cites that "choice reaction alone consumed 36% of the
  total input time" in Chinese input ([IJHCS 2013](https://www.sciencedirect.com/science/article/pii/S1071581912001504)).
* **T9** merges letters onto keys and resolves by lexicon: `8-4-3` has 27 possible renderings, and
  its measured keystrokes-per-character is 1.0072 ([UIST 2001](https://www.yorku.ca/mack/uist01.pdf)).
  Crucially, when 15% of words were out of dictionary, dictionary-based methods became *slower* than
  multi-tap ([Barriers to adoption](https://aclanthology.org/W03-2505.pdf)) — directly relevant to a
  design whose dictionary will start small.

The transferable lesson: **candidate selection is the dominant cost when the sketch is coarse.** Our
language must therefore minimise collisions structurally (family choice, refinement strokes) rather
than rely on ranking alone.

## 7. Established theory versus adaptation

| Principle | Status | How FlowBoard uses it |
| --- | --- | --- |
| Left hand = onset, thumb = vowel, right hand = coda | established | Two thumbs only: left thumb = onset, right thumb = nucleus; coda moves to its own stroke |
| One vowel per stroke | established | Exactly one nucleus per core chord |
| Unstressed vowels may be dropped | established | "Reduced sketch" that omits unstressed syllables |
| Repeated strokes/keys disambiguate homophones | established (asterisk, vowel alteration, extra strokes) | Not adopted in the 16-chord baseline; adopted as refinement strokes in the FSL-2 proposal |
| Dictionary longest-match translation | established | Adopted, but with a divergence: our outlines are *ambiguous by construction*, so the dictionary must return ordered alternatives where Plover forbids that |
| Briefs that violate theory | established | Adopted as a priority dictionary tier |
| Number bar | established | Not part of the language; a toolbar mode (out of scope here) |
| Four consonant families / three vowel families over 16 chords | **adaptation** | This is the FlowBoard-specific invention; nothing in steno theory prescribes these classes |
| Bare-consonant chords before/after a core | **adaptation** | Replaces steno's key-position ordering with sequence position |
| Refinement (pin) half-strokes | **adaptation** | Proposed, not implemented; requires a gesture-engine change |

## 8. What this research does not establish

* No evidence that *any* 16-chord, two-thumb, serialised system can reach useful writing speed.
  All published low-key chording results are on different interaction hardware.
* No measured learnability curve for a class-based (rather than phoneme-level) alphabet. The
  Pinyin/T9 analogy suggests the cost is candidate selection, but that is an inference.
* No authoritative strokes-per-word figure for professionals; the 1.2 / 1.0 / 0.8 numbers are one
  vendor's categories.
* Nothing about FlowBoard on a real device. The app has never been device-verified; see
  [MANUAL_QA.md](../MANUAL_QA.md).

## 9. References

All URLs above, plus the following for the prediction layer, are reused by
[context_prediction_architecture.md](context_prediction_architecture.md):

* Op-Ngram (compact on-device trigram: 2.30 MB on disk, 10.47 MB RAM, 2.2–2.4 ms per query on a
  Galaxy S8) — [arXiv 2101.03967](https://arxiv.org/pdf/2101.03967)
* Gboard prediction (Katz-smoothed 5-gram, 1.25 M n-grams, 164 k unigrams; ~20 ms feedback
  expectation) — [arXiv 1811.03604](https://arxiv.org/html/1811.03604)
* Mobile keyboard decoding with finite-state transducers (`C ∘ L ∘ G`, look-ahead filters; LM budget
  "should not exceed 5 to 10 Mb") — [arXiv 1704.03987](https://arxiv.org/pdf/1704.03987)
* Katz backoff and Kneser–Ney — [Jurafsky & Martin §3.6, §3.8](https://web.stanford.edu/~jurafsky/slp3/old_feb24/3.pdf)
* Cyclic n-gram FSTs and storage estimates — [OpenGrm n-gram tour](https://www.openfst.org/twiki/bin/view/GRM/NGramQuickTour),
  [OpenFst extensions](https://www.openfst.org/twiki/bin/view/FST/FstExtensions)
* Android IME guidance (BIND_INPUT_METHOD, preload/caching, releasing memory when hidden) —
  [Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
* Android `INTERNET` permission — [Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#INTERNET)
* Android memory limits and Doze — [memory overview](https://developer.android.com/topic/performance/memory-overview),
  [Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby)
