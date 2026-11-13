# Evaluation plan

Status: **plan**. No FSL implementation exists, so nothing here has been measured. Everything labelled
*estimate* is arithmetic on stated assumptions, not evidence.

Related: [language design](flowboard_language_design.md) ·
[prediction](context_prediction_architecture.md) · [examples](input_language_examples.md) ·
[decision record](decision_record.md).

## 1. Research questions

| ID | Question | Falsifiable if |
| --- | --- | --- |
| RQ1 | Can a 16-chord class alphabet write ordinary English within a tolerable number of strokes? | frequency-weighted strokes/token > 5 at the 50th percentile of running text |
| RQ2 | Does context ranking make the intended word cheap to select? | mean rank of intended word > 5, or top-1 < 80% on running text |
| RQ3 | Is the language learnable in a few sessions? | a novice cannot reach 15 wpm within 8 × 30-minute sessions |
| RQ4 | Is it fast enough to be worth using? | measured throughput < 60% of the participant's own QWERTY throughput after training |
| RQ5 | Does the design cost too much in corrections? | correction strokes > 15 per 100 words steady-state |

RQ3–RQ5 need people; RQ1–RQ2 can be computed offline from a corpus before any UI exists, which is
why they come first.

## 2. Metrics

| Metric | Definition | How measured |
| --- | --- | --- |
| SPT | strokes (chords + space + commit) per token | offline from corpus sketches; on-device counters |
| CPC | chords per character | SPT ÷ mean token length |
| \|C(S)\| | candidate-set size for a sketch | offline dictionary query |
| Top-1 / Top-5 accuracy | intended word is first / in first five candidates | offline over held-out text |
| Mean rank (MR) | rank of intended word in the candidate list | offline |
| Corrected rank loss | extra selection actions caused by mis-ranking | offline: rank − 1 |
| Correction strokes / 100 words | undo/alternative actions | on-device counters |
| Gesture latency | touch-down to highlight update | on-device instrumentation |
| Word time | commit of word *n* to commit of word *n+1* | on-device timestamps |
| Error rate | wrong words committed ÷ words committed | transcript comparison |
| Throughput | bits/second via Wobbrock's text-entry throughput | transcripts + timing |
| Cognitive load | NASA-TLX after each session | questionnaire |
| Learnability | wpm per session | longitudinal sessions |

## 3. Baselines

| Baseline | Source of numbers | Caveat |
| --- | --- | --- |
| Mobile QWERTY | Senorita study: 32.7 wpm experienced users, 0.92% errors ([CHI 2020](https://www.asarif.com/pub/Rakhmetulla_CHI2020_Senorita.pdf)); OPTI 44.3 wpm after 20 sessions ([CHI 1999](https://www.yorku.ca/mack/CHI99a.html)) | different tasks/devices; use *participants' own* QWERTY speed in any study |
| Professional stenotype | 3.5–4 chords/s, 180–225 wpm certified ([Plover](https://plover.readthedocs.io/en/latest/system-dev/key-arrangement.html), [NCRA](https://www.ncra.org/certification/NCRA-Certifications/registered-professional-reporter)) | *simultaneous* multi-key chords; not comparable to serialised two-thumb chords |
| Current FlowBoard | no measurements exist (never device-verified) | treat as unmeasured baseline; establish it in the same study |
| Chording prototypes | Twiddler 26.2 wpm after 400 min; 5-key 20 wpm after ~350 min | different hardware; useful only as a sanity band |

## 4. Offline corpus evaluation (do this first, no UI needed)

### 4.1 Data

* **Pronunciation**: CMUdict (ARPAbet) mapped to the IPA symbols used by the design, with a manual
  review of the mapping. Alternative: a Wiktionary IPA dump, which is messier but closer to GA.
* **Frequency**: Norvig's `count_1w` (Google Web Trillion corpus; [data](https://www.norvig.com/ngrams/))
  for coverage, and SUBTLEX-US for speech-like frequencies
  ([Brysbaert & New 2009](https://pubmed.ncbi.nlm.nih.gov/19897807)) — the two disagree enough to
  report both.
* **Held-out text**: a few thousand sentences of ordinary prose plus a technical sample, kept out of
  any dictionary curation.

### 4.2 Procedure

1. Sketch every lexicon word by the §4 rules (build task, not hand-editing).
2. Group by sketch → collision distribution, per word length and frequency band.
3. Simulate typing the held-out text: for each token, look up the sketch, rank candidates with (a)
   unigram only, (b) bigram, (c) trigram, and record top-1, top-5, mean rank.
4. Repeat with reduced forms enabled, using a policy for when reduction is allowed.
5. Report OOV rate for lexicons of 20k / 50k / 100k words.

### 4.3 Reporting thresholds

| Result | Interpretation |
| --- | --- |
| Mean SPT ≤ 3.0, top-1 ≥ 85%, MR ≤ 2 | design is viable as specified; proceed to UI |
| Top-1 70–85%, MR 2–4 | viable but needs better ranking and/or briefs; proceed with caution |
| Top-1 < 70% or MR > 5 | the class alphabet is too coarse: FSL-2 refinement becomes mandatory before any study |
| SPT > 5 at the median | the language is too expensive; redesign the cluster/coda handling first |

## 5. Theoretical estimates (with the arithmetic shown)

These are **estimates**, not results. Assumptions are stated so they can be attacked.

**Strokes per token.** The 41-example sample in
[input_language_examples.md](input_language_examples.md) gives a mean of 2.78 full and 2.39 reduced
strokes per word, but it deliberately over-samples hard words. Function words (the top ~50 tokens)
are mostly 1–2 strokes. A frequency-weighted mean somewhere around **2.2–2.8** is plausible; call it
**2.5** for planning.

**Gesture cost.** A two-thumb chord needs both thumbs to land and drag; assume **300 ms** each
(unmeasured; a tap is typically ~100–150 ms). The space/commit tap adds ~120 ms.

```
gestures per word      = 2.5 chords + 1 space        = 3.5
time per word (300 ms) = 2.5 × 0.30 + 0.12           = 0.87 s
implied speed          = 60 / 0.87                   ≈ 69 wpm
at 400 ms per chord    = 2.5 × 0.40 + 0.12 = 1.12 s  ≈ 54 wpm
at 500 ms per chord    = 2.5 × 0.50 + 0.12 = 1.37 s  ≈ 44 wpm
```

So the *ceiling* is roughly 45–70 wpm **if** a chord costs 300–500 ms and **if** the intended
candidate is usually first. Both assumptions are untested, and candidate selection is not in that
arithmetic at all. For calibration: the measured chord prototypes sit at 20–26 wpm after 6–7 hours of
practice, and two-thumb touchscreen chording in the Senorita study reached 14 wpm.

**Honest statement:** nothing here supports claiming FlowBoard would beat QWERTY. The arithmetic
suggests it could reach the *same order of magnitude* after substantial practice, and the literature
suggests chording systems usually do not reach QWERTY speed on phones.

## 6. Human study design (only after §4 passes)

* **Design**: within-subject, counterbalanced order (FSL vs QWERTY), 12–16 participants minimum
  (pilot; not powered for small effects — report effect sizes, not just p-values).
* **Protocol**: 8 sessions × 30 min over 2–3 weeks. Sessions 1–2: alphabet and 4-chord drills.
  3–5: common words and reduced forms. 6–8: sentences (copy task) and free composition.
* **Tasks**: (a) transcription of short sentences, (b) free composition of 3 sentences on a prompt,
  (c) correction task where the top candidate is deliberately wrong.
* **Measures**: §2 metrics, NASA-TLX each session, semi-structured interview at the end.
* **Device**: target device is a Redmi Note 14 Pro on Android 16; also run one small-screen phone to
  check thumb reach. Use a debug build with local logging; no telemetry leaves the device unless the
  participant explicitly exports a log file.
* **Analysis**: per-session wpm curves, error and correction rates, MR distribution, paired
  comparisons with participant's own QWERTY baseline, bootstrap confidence intervals.

## 7. Device and latency plan (independent of the human study)

| Check | Method | Pass |
| --- | --- | --- |
| IME activation and switching | manual, per [MANUAL_QA.md](../MANUAL_QA.md) | no crash, keyboard appears in <300 ms |
| Chord recognition on real hardware | instrumentation + logs: accepted/rejected/duplicate counts | false commits ≈ 0; rejection rate reported |
| Gesture latency | timestamps at touch-down and draw | p95 ≤ 10 ms to highlight |
| Memory | `Debug.MemoryInfo` over a 10-minute session | ≤ 12 MB for dict + LM |
| Battery | 10 minutes of typing, battery stats delta | comparable to a normal keyboard |
| Sensitive fields | password fields in 3 apps | PRIVATE banner, no statistics, no context reuse |

## 8. Threats to validity

* **Corpus mismatch**: sketches computed from dictionary pronunciations may differ from what users
  actually say (especially names and compounds).
* **Sample bias**: 41 hand-picked examples over-represent hard words; do not generalise the 2.78.
* **Learning confound**: novices improve from mere device familiarity, not the language; a QWERTY
  control arm is required.
* **Author bias**: the designer of the language also defines the briefs; brief curation must be
  documented and ideally done by a second person.
* **No production dictionary**: any accuracy number is an upper bound, because a curated brief list
  would be tuned on the same corpus it is evaluated on unless held out.

## 9. Deliverables from the evaluation

1. `evaluation/report.md` with the offline numbers (SPT, collision distribution, top-1/5, MR) and the
   exact lexicon/transcription versions used.
2. A device log summary (latency, rejections, memory) from the target phone.
3. A study report with per-session curves and effect sizes.
4. An explicit go/no-go decision against §4.3 and RQ1–RQ5.
