#!/usr/bin/env python3
"""Build FlowBoard's FSL-1 dictionary and trigram assets.

Sources (all public):
  * CMU Pronouncing Dictionary  — pronunciations (ARPAbet)
  * Norvig count_1w.txt         — word frequencies (Google Web Trillion corpus)
  * Project Gutenberg texts     — trigram training corpus

Run from the repository root:

    python3 tools/build_assets.py --download

The script is deterministic given the cached downloads. It writes:

    app/src/main/assets/steno_en.json.gz
    app/src/main/assets/lm_en.json.gz

Nothing here ships inside the app; the outputs do.
"""

import argparse
import gzip
import json
import os
import re
import sys
import urllib.request
from collections import Counter

CACHE = os.path.join("tools", "cache")
OUT_DIR = os.path.join("app", "src", "main", "assets")

CMUDICT_URL = "https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict"
COUNTS_URL = "https://norvig.com/ngrams/count_1w.txt"
BOOKS = {
    "pride_and_prejudice.txt": "https://www.gutenberg.org/files/1342/1342-0.txt",
    "moby_dick.txt": "https://www.gutenberg.org/files/2701/2701-0.txt",
    "alice.txt": "https://www.gutenberg.org/files/11/11-0.txt",
    # sherlock.txt is intentionally excluded from training: it is the held-out
    # evaluation text (see app/src/test/resources/eval_sentences.txt).
    "frankenstein.txt": "https://www.gutenberg.org/files/84/84-0.txt",
}

# ---------------------------------------------------------------- phoneme map
# ARPAbet -> the sound inventory of docs/flowboard_language_design.md §4.1.
ARPABET = {
    "AA": ["ɑ"], "AE": ["æ"], "AH": ["ʌ"], "AO": ["ɔ"], "AW": ["a", "w"],
    "AY": ["a", "j"], "EH": ["ɛ"], "ER": ["ɜ", "r"], "EY": ["e", "j"],
    "IH": ["ɪ"], "IY": ["i"], "OW": ["o", "w"], "OY": ["ɔ", "j"],
    "UH": ["ʊ"], "UW": ["u"],
    "B": ["b"], "CH": ["tʃ"], "D": ["d"], "DH": ["ð"], "F": ["f"], "G": ["g"],
    "HH": ["h"], "JH": ["dʒ"], "K": ["k"], "L": ["l"], "M": ["m"], "N": ["n"],
    "NG": ["ŋ"], "P": ["p"], "R": ["r"], "S": ["s"], "SH": ["ʃ"], "T": ["t"],
    "TH": ["θ"], "V": ["v"], "W": ["w"], "Y": ["j"], "Z": ["z"], "ZH": ["ʒ"],
}
# AH0 (unstressed) is schwa, not the STRUT vowel.
SCHWA = ["ə"]

CONS = {
    "p": "LAB", "b": "LAB", "m": "LAB", "f": "LAB", "v": "LAB",
    "t": "COR", "d": "COR", "n": "COR", "s": "COR", "z": "COR",
    "l": "COR", "r": "COR", "θ": "COR", "ð": "COR",
    "k": "DOR", "g": "DOR", "ŋ": "DOR", "h": "DOR", "w": "DOR",
    "tʃ": "SIB", "dʒ": "SIB", "ʃ": "SIB", "ʒ": "SIB", "j": "SIB",
}
VOW = {
    "i": "F", "ɪ": "F", "e": "F", "ɛ": "F", "æ": "F",
    "ə": "A", "ʌ": "A", "ɜ": "A", "ɑ": "A", "a": "A",
    "u": "U", "ʊ": "U", "ɔ": "U", "o": "U",
}


# ---------------------------------------------------------------- sketching
def arpabet_to_phones(pron):
    """Return [(phone, stress_or_None)] for one CMUdict pronunciation."""
    out = []
    for token in pron.split():
        match = re.match(r"^([A-Z]+)([0-2])?$", token)
        if not match:
            return None
        base, stress = match.group(1), match.group(2)
        if base not in ARPABET:
            return None
        phones = SCHWA if (base == "AH" and stress == "0") else ARPABET[base]
        for index, phone in enumerate(phones):
            out.append((phone, int(stress) if stress and index == 0 else None))
    return out


def sketch(phones):
    """Implements the core/bare rule of the language design (§4.3).

    A vowel's core carries the consonant immediately before it; every other
    consonant becomes a bare stroke. Word-final consonants are codas.
    """
    strokes, buffer = [], []
    for phone, _ in phones:
        if phone in VOW:
            onset = buffer.pop() if buffer else None
            strokes.extend(("bare", c) for c in buffer)
            buffer = []
            strokes.append(("core", (CONS[onset] if onset else "SIB", VOW[phone])))
        else:
            buffer.append(phone)
    strokes.extend(("bare", c) for c in buffer)
    return strokes


def reduced_sketch(phones):
    """Stressed syllable's onset + core, plus the word-final coda."""
    syllables, buffer, stressed = [], [], False
    for phone, stress in phones:
        if phone in VOW:
            syllables.append({"onset": buffer, "vowel": phone, "stressed": stress == 1})
            buffer, stressed = [], False
        else:
            buffer.append(phone)
    if not syllables:
        return [("bare", c) for c in buffer]
    target = next((s for s in syllables if s["stressed"]), syllables[0])
    out = [("bare", c) for c in target["onset"][:-1]]
    onset = target["onset"]
    out.append(("core", (CONS[onset[-1]] if onset else "SIB", VOW[target["vowel"]])))
    out.extend(("bare", c) for c in buffer)
    return out


def render(strokes):
    parts = []
    for kind, value in strokes:
        parts.append(f"{value[0]}·{value[1]}" if kind == "core" else f"{CONS[value]}·∅")
    return " ".join(parts)


def first_core(strokes):
    for kind, value in strokes:
        if kind == "core":
            return f"{value[0]}·{value[1]}"
    return None


# ---------------------------------------------------------------- downloads
def download(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    print(f"  fetching {url}")
    with urllib.request.urlopen(url, timeout=120) as response, open(path, "wb") as handle:
        handle.write(response.read())
    return path


def load_cmudict(allow_download):
    path = os.path.join(CACHE, "cmudict.dict")
    if allow_download:
        download(CMUDICT_URL, path)
    if not os.path.exists(path):
        sys.exit("cmudict cache missing; run with --download")
    entries = {}
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.split("#", 1)[0].strip()
            if not line or " " not in line:
                continue
            word, pron = line.split(" ", 1)
            word = word.lower()
            if not re.fullmatch(r"[a-z']+", word) or word.endswith("'"):
                continue
            if "(" in word:  # alternative pronunciations: keep the first
                continue
            phones = arpabet_to_phones(pron)
            if phones:
                entries.setdefault(word, phones)
    return entries


def load_counts(allow_download):
    path = os.path.join(CACHE, "count_1w.txt")
    if allow_download:
        download(COUNTS_URL, path)
    if not os.path.exists(path):
        sys.exit("frequency cache missing; run with --download")
    counts = {}
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            parts = line.split()
            if len(parts) != 2:
                continue
            word, count = parts
            if re.fullmatch(r"[a-z']+", word):
                counts[word] = int(count)
    return counts


def load_corpus(allow_download):
    tokens = []
    for name, url in BOOKS.items():
        path = os.path.join(CACHE, name)
        if allow_download:
            download(url, path)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8", errors="replace") as handle:
            text = handle.read().lower()
        # Drop the Gutenberg header/footer boilerplate.
        start = text.find("*** start")
        if start >= 0:
            text = text[text.find("\n", start) + 1:]
        end = text.find("*** end")
        if end >= 0:
            text = text[:end]
        tokens.extend(re.findall(r"[a-z]+(?:'[a-z]+)?", text))
    return tokens


# ---------------------------------------------------------------- build
def build_dictionary(cmudict, counts, limit):
    ranked = sorted(
        ((w, c) for w, c in counts.items() if w in cmudict),
        key=lambda item: -item[1],
    )[:limit]

    entries = []
    for rank, (word, frequency) in enumerate(ranked):
        phones = cmudict[word]
        full = sketch(phones)
        if not full:
            continue
        reduced = reduced_sketch(phones)
        entry = {
            "w": word,
            "s": render(full),
            "f": frequency,
            "n": len(full),
        }
        if render(reduced) != entry["s"]:
            entry["r"] = render(reduced)
        if rank < 200 and len(full) > 1:
            brief = first_core(full)
            if brief:
                entry["_b"] = brief
        entries.append(entry)

    # A one-core brief may never shadow a word whose whole sketch it is ("with" -> DOR·F would
    # otherwise sit on top of "we"/"he"). Briefs that merely collide with each other survive:
    # FSL-1 exposes ambiguity instead of hiding it, and the ranking prefers the full tier.
    full_sketches = {entry["s"] for entry in entries}
    claimed = {}
    for entry in entries:
        if "_b" in entry:
            claimed.setdefault(entry["_b"], []).append(entry["w"])
    briefs = 0
    for entry in entries:
        brief = entry.pop("_b", None)
        if brief and brief not in full_sketches:
            entry["b"] = brief
            briefs += 1
    print(f"usable briefs     {briefs} of {len(claimed)} first-core candidates")

    return {
        "version": 1,
        "language": "en",
        "notation": "onsetFamily·nucleusFamily for cores, onsetFamily·∅ for bare strokes",
        "source": "CMUdict pronunciations, Norvig count_1w frequencies",
        "entries": entries,
    }


def build_lm(tokens, min_trigram=3, min_bigram=2, vocab_limit=50000):
    vocab = Counter(tokens)
    keep = {w for w, _ in vocab.most_common(vocab_limit)}
    uni = {w: vocab[w] for w in vocab if w in keep}
    bi = Counter()
    tri = Counter()
    padded = ["<s>", "<s>"] + tokens + ["</s>"]
    for index in range(2, len(padded)):
        second, third = padded[index - 1], padded[index]
        first = padded[index - 2]
        if third != "<s>":
            bi[(second, third)] += 1
            tri[(first, second, third)] += 1
    bi = {f"{a} {b}": c for (a, b), c in bi.items() if c >= min_bigram}
    tri = {f"{a} {b} {c}": n for (a, b, c), n in tri.items() if n >= min_trigram}
    return {"u": uni, "b": bi, "t": tri}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true", help="fetch sources into tools/cache")
    parser.add_argument("--limit", type=int, default=20000)
    args = parser.parse_args()

    print("lexicon...")
    cmudict = load_cmudict(args.download)
    counts = load_counts(args.download)
    dictionary = build_dictionary(cmudict, counts, args.limit)

    print("language model...")
    tokens = load_corpus(args.download)
    if len(tokens) < 100000:
        sys.exit(f"corpus too small ({len(tokens)} tokens); run with --download")
    lm = build_lm(tokens)

    os.makedirs(OUT_DIR, exist_ok=True)
    # Tab-separated and gzipped: an IME must parse this on a cold start, and
    # org.json over a 2 MB document is far slower than splitting lines.
    #
    # The suffix is .tsvz, not .tsv.gz: aapt2 special-cases the .gz extension, silently
    # decompresses the asset and strips the suffix, so a ".gz" name cannot be opened as a
    # gzip stream at runtime. A non-standard suffix keeps the bytes intact in the APK.
    with gzip.open(os.path.join(OUT_DIR, "steno_en.tsvz"), "wt", encoding="utf-8") as handle:
        handle.write(f"#version\t1\n#language\ten\n#source\tCMUdict + Norvig count_1w\n")
        handle.write("word\tsketch\treduced\tfrequency\tbrief\n")
        for entry in dictionary["entries"]:
            handle.write(
                "\t".join(str(entry.get(k, "")) for k in ("w", "s", "r", "f", "b")) + "\n"
            )
    with gzip.open(os.path.join(OUT_DIR, "lm_en.tsvz"), "wt", encoding="utf-8") as handle:
        handle.write(f"#tokens\t{len(tokens)}\n")
        for word, count in lm["u"].items():
            handle.write(f"U\t{word}\t{count}\n")
        for ngram, count in lm["b"].items():
            handle.write(f"B\t{ngram}\t{count}\n")
        for ngram, count in lm["t"].items():
            handle.write(f"T\t{ngram}\t{count}\n")

    sketches = {}
    for entry in dictionary["entries"]:
        sketches.setdefault(entry["s"], []).append(entry["w"])
    collisions = sorted(sketches.values(), key=len, reverse=True)[:5]
    strokes = [entry["n"] for entry in dictionary["entries"]]
    print(f"entries          {len(dictionary['entries'])}")
    print(f"distinct sketches {len(sketches)}")
    print(f"mean strokes      {sum(strokes)/len(strokes):.2f}")
    print(f"top collisions    {[(len(c), c[:6]) for c in collisions]}")
    print(f"lm tokens         {len(tokens)}  unigrams {len(lm['u'])}  bigrams {len(lm['b'])}  trigrams {len(lm['t'])}")
    for name in ("steno_en.tsvz", "lm_en.tsvz"):
        path = os.path.join(OUT_DIR, name)
        print(f"{name:18s} {os.path.getsize(path)/1024:.0f} kB")


if __name__ == "__main__":
    main()
