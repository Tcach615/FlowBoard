#!/usr/bin/env python3
"""Documentation tool (not app code): computes FSL-1 chord sequences from the
rules in docs/flowboard_language_design.md so the tables in the docs cannot
drift from the specification.

Run from the repository root or from this directory:
    python3 docs/tools/fsl_gen.py

Nothing in the Android app imports or ships this file."""

CONS = {
    "p": "LAB", "b": "LAB", "m": "LAB", "f": "LAB", "v": "LAB",
    "t": "COR", "d": "COR", "n": "COR", "s": "COR", "z": "COR",
    "l": "COR", "r": "COR", "\u03b8": "COR", "\u00f0": "COR",
    "k": "DOR", "g": "DOR", "\u014b": "DOR", "h": "DOR", "w": "DOR",
    "t\u0283": "SIB", "d\u0292": "SIB", "\u0283": "SIB", "\u0292": "SIB", "j": "SIB",
}
VOW = {
    "i": "F", "\u026a": "F", "e": "F", "\u025b": "F", "\u00e6": "F",
    "\u0259": "A", "\u028c": "A", "\u025c": "A", "\u0251": "A", "a": "A",
    "u": "U", "\u028a": "U", "\u0254": "U", "o": "U",
}
LEFT = {"LAB": "NW", "COR": "NE", "DOR": "SW", "SIB": "SE"}
RIGHT = {"F": "NW", "A": "NE", "U": "SW"}
PHONES = sorted(list(CONS) + list(VOW), key=len, reverse=True)


def tokenize(ipa):
    out, i = [], 0
    while i < len(ipa):
        if ipa[i] in "\u02c8\u02cc":
            out.append(ipa[i])
            i += 1
            continue
        for p in PHONES:
            if ipa.startswith(p, i):
                out.append(p)
                i += len(p)
                break
        else:
            raise ValueError(f"unknown phoneme at {ipa!r}: {ipa[i]!r}")
    return out


def syllables(ipa):
    phones = tokenize(ipa)
    result, buffer, stressed = [], [], False
    for p in phones:
        if p == "\u02c8":
            stressed = True
        elif p == "\u02cc":
            stressed = False
        elif p in VOW:
            result.append({"onset": buffer, "vowel": p, "stressed": stressed})
            buffer, stressed = [], False
        else:
            buffer.append(p)
    return result, buffer


def core(onset, vowel):
    family = CONS[onset[-1]] if onset else "SIB"
    return ("core", (family, VOW[vowel]))


def full_chords(ipa):
    syls, coda = syllables(ipa)
    out = []
    for s in syls:
        out.extend(("bare", c) for c in s["onset"][:-1])
        out.append(core(s["onset"], s["vowel"]))
    out.extend(("bare", c) for c in coda)
    return out


def reduced_chords(ipa):
    syls, coda = syllables(ipa)
    target = next((s for s in syls if s["stressed"]), syls[0] if syls else None)
    if target is None:
        return [("bare", c) for c in coda]
    out = [("bare", c) for c in target["onset"][:-1]]
    out.append(core(target["onset"], target["vowel"]))
    out.extend(("bare", c) for c in coda)
    return out


def render(strokes):
    parts = []
    for kind, value in strokes:
        if kind == "core":
            fam, vow = value
            parts.append(f"{fam}\u00b7{vow}")
        else:
            parts.append(f"{CONS[value]}\u00b7\u2205")
    return " \u2013 ".join(parts)


def key(strokes):
    # Collisions must be computed on the rendered symbol, not the phoneme:
    # every COR coda is the same chord however it is pronounced.
    return render(strokes)


EXAMPLES = [
    ("the", "\u00f0\u0259", "article"),
    ("and", "\u00e6nd", "conjunction"),
    ("to", "tu", "preposition"),
    ("of", "\u028cv", "preposition"),
    ("a", "\u0259", "article"),
    ("I", "aj", "pronoun"),
    ("you", "ju", "pronoun"),
    ("we", "wi", "pronoun"),
    ("they", "\u00f0ej", "pronoun"),
    ("it", "\u026at", "pronoun"),
    ("is", "\u026az", "auxiliary"),
    ("no", "now", "determiner"),
    ("know", "now", "homophone of *no*"),
    ("cat", "k\u00e6t", "CVC"),
    ("dog", "d\u0254g", "CVC"),
    ("man", "m\u00e6n", "CVC"),
    ("need", "nid", "CVC"),
    ("stop", "st\u0251p", "s-cluster onset"),
    ("street", "strit", "three-consonant onset"),
    ("strength", "str\u025b\u014b\u03b8", "onset + three-consonant coda"),
    ("texts", "t\u025bksts", "four-consonant coda"),
    ("think", "\u03b8\u026a\u014bk", "dental fricative"),
    ("ship", "\u0283\u026ap", "sibilant onset"),
    ("judge", "d\u0292\u028cd\u0292", "affricates both sides"),
    ("water", "w\u0254t\u0259r", "two syllables"),
    ("banana", "b\u0259n\u00e6n\u0259", "three syllables, repeated vowel classes"),
    ("computer", "k\u0259m\u02c8pjut\u0259r", "three syllables, cluster"),
    ("keyboard", "kib\u0254rd", "compound"),
    ("information", "\u02cc\u026anf\u0259r\u02c8mej\u0283\u0259n", "four syllables"),
    ("impossible", "\u026am\u02c8p\u0251s\u0259b\u0259l", "four syllables"),
    ("please", "pliz", "cluster onset"),
    ("thanks", "\u03b8\u00e6\u014bks", "coda cluster"),
    ("right", "rajt", "diphthong"),
    ("write", "rajt", "homophone of *right*"),
    ("there", "\u00f0\u025br", "rhotic"),
    ("their", "\u00f0\u025br", "homophone of *there*"),
    ("read", "rid", "present tense"),
    ("red", "r\u025bd", "minimal pair"),
    ("world", "w\u025cld", "rhotic + coda"),
    ("morning", "m\u0254rn\u026a\u014b", "two syllables"),
    ("good", "g\u028ad", "CVC"),
]

SENTENCES = [
    ("I think the cat is good", ["I", "think", "the", "cat", "is", "good"]),
    ("we need information", ["we", "need", "information"]),
    ("the computer keyboard", ["the", "computer", "keyboard"]),
]


def main():
    print("## Inventory\n")
    print("| left thumb | right thumb | class | meaning |")
    print("| --- | --- | --- | --- |")
    for fam, left in LEFT.items():
        for vow, right in RIGHT.items():
            print(f"| {left} | {right} | core | onset {fam} + nucleus {vow} |")
        print(f"| {left} | SE | bare | consonant, family {fam} |")

    print("\n## Examples\n")
    print("| # | text | IPA (GA) | full | reduced | full chord sequence |")
    print("| --- | --- | --- | --- | --- | --- |")
    full_total = reduced_total = 0
    for i, (text, ipa, note) in enumerate(EXAMPLES, 1):
        f, r = full_chords(ipa), reduced_chords(ipa)
        full_total += len(f)
        reduced_total += len(r)
        print(f"| {i} | **{text}** | /{ipa}/ | {len(f)} | {len(r)} | {render(f)} |")
    n = len(EXAMPLES)
    print(f"\nmean full strokes/word: {full_total/n:.2f} | mean reduced strokes/word: {reduced_total/n:.2f}")

    print("\n## Sentences\n")
    for sentence, words in SENTENCES:
        seq = []
        for w in words:
            ipa = next(i for t, i, n in EXAMPLES if t == w)
            seq.append(f"**{w}** `{render(reduced_chords(ipa))}`")
        print(f"- {sentence}: " + " \u2423 ".join(seq))

    print("\n## Collisions in the demo lexicon\n")
    groups = {}
    for text, ipa, note in EXAMPLES:
        groups.setdefault(key(full_chords(ipa)), []).append(text)
    for seq, words in sorted(groups.items(), key=lambda kv: (-len(kv[1]), kv[1][0])):
        if len(words) > 1:
            sample = render(full_chords(next(i for t, i, n in EXAMPLES if t == words[0])))
            print(f"- {len(words)} share `{sample}`: {', '.join(words)}")
    print(f"\ndistinct full sketches: {len(groups)} for {len(EXAMPLES)} words")

    print("\n## Reduced-form collisions\n")
    groups = {}
    for text, ipa, note in EXAMPLES:
        groups.setdefault(key(reduced_chords(ipa)), []).append(text)
    for seq, words in sorted(groups.items(), key=lambda kv: (-len(kv[1]), kv[1][0])):
        if len(words) > 1:
            sample = render(reduced_chords(next(i for t, i, n in EXAMPLES if t == words[0])))
            print(f"- {len(words)} share `{sample}`: {', '.join(words)}")
    print(f"\ndistinct reduced sketches: {len(groups)} for {len(EXAMPLES)} words")


if __name__ == "__main__":
    main()
