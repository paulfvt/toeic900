"""Plan des 180 nouvelles questions Part 5 (p5-0121 à p5-0300), en 6 lots de 30.

Écrit, pour chaque lot, un fichier plan-lot-NN.md : identifiant, tag, difficulté et lettre de
la bonne réponse imposés. Usage : python p5_plan.py <dossier de sortie> <dossier content/bank/part5>
"""
import json
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

out_dir, bank_dir = Path(sys.argv[1]), Path(sys.argv[2])

NEEDS = {
    "word-form-noun": 11, "word-form-adjective": 11, "word-form-adverb": 11, "comparatives-superlatives": 12,
    "verb-tense": 11, "subject-verb-agreement": 11, "passive-voice": 12, "gerund-infinitive": 11,
    "participle-adjectives": 12, "pronouns": 11, "relative-clauses": 11, "quantifiers-determiners": 10,
    "prepositions": 11, "conjunctions-connectors": 11, "vocabulary-in-context": 12, "collocations": 12,
}
assert sum(NEEDS.values()) == 180

rng = random.Random(2026)
# Tags répartis en tourniquet : chaque lot reçoit 1 à 3 questions par tag.
pool = []
remaining = dict(NEEDS)
while any(remaining.values()):
    for tag in NEEDS:
        if remaining[tag]:
            pool.append(tag)
            remaining[tag] -= 1
lots = [pool[i::6] for i in range(6)]
assert all(len(lot) == 30 for lot in lots)

DIFFICULTIES = [1] * 8 + [2] * 14 + [3] * 8
plans = []
for index, tags in enumerate(lots):
    tags = sorted(tags, key=list(NEEDS).index)
    letters = list("ABCD" * 7) + (["A", "C"] if index % 2 == 0 else ["B", "D"])
    difficulties = DIFFICULTIES[:]
    rng.shuffle(letters)
    rng.shuffle(difficulties)
    plans.append(list(zip(tags, difficulties, letters)))

existing = defaultdict(list)
for path in sorted(bank_dir.glob("*.json")):
    for q in json.loads(path.read_text(encoding="utf-8"))["questions"]:
        existing[q["tags"][0]].append(" / ".join(q["options"]))

number = 121
for index, plan in enumerate(plans):
    lot = index + 5
    lines = [f"# Plan imposé du lot {lot:02d} (Part 5)", "",
             "| id | tag | difficulté | bonne réponse |", "|---|---|---|---|"]
    for tag, difficulty, letter in plan:
        lines.append(f"| p5-{number:04d} | {tag} | {difficulty} | {letter} |")
        number += 1
    lines += ["", "## Options déjà utilisées dans la banque (à ne pas reprendre : choisis d'autres mots)", ""]
    for tag in dict.fromkeys(t for t, _, _ in plan):
        lines.append(f"- **{tag}** : " + " ; ".join(existing[tag]))
    (out_dir / f"plan-lot-{lot:02d}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(lot, Counter(t for t, _, _ in plan).most_common(3), Counter(l for _, _, l in plan), Counter(d for _, d, _ in plan))
assert number == 301
