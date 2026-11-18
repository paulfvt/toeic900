"""Plan imposé du demi-test Reading n° 1 (Phase 3) : 50 questions du pool `mock`.

Proportions d'une vraie section Reading divisées par deux : 15 questions Part 5, 2 textes Part 6
(8 questions), 27 questions Part 7 (6 documents seuls de 2 à 4 questions, 2 jeux multiples de 5).
Identifiants dans la plage 9001+ (réservée au pool mock) : p5-9001…, p6-9001…, p7-9001….

Trois rédacteurs :
  A : Part 5 (15 questions) et Part 6 (2 textes)  -> drafts/mock/plan-a.md
  B : Part 7, 6 documents seuls                   -> drafts/mock/plan-b.md
  C : Part 7, 2 jeux multiples                    -> drafts/mock/plan-c.md

Usage : python content/drafts/mock_plan.py   (déterministe, graine fixe)
"""
import json
import random
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
rng = random.Random(1811)

GRAMMAR = [
    "word-form-noun", "word-form-adjective", "word-form-adverb", "comparatives-superlatives",
    "verb-tense", "subject-verb-agreement", "passive-voice", "gerund-infinitive",
    "participle-adjectives", "pronouns", "relative-clauses", "quantifiers-determiners",
    "prepositions", "conjunctions-connectors", "vocabulary-in-context", "collocations",
]
SECTORS = ["cabinet de conseil", "chaîne de supermarchés", "festival de musique", "fabricant de vélos",
           "compagnie ferroviaire", "laboratoire pharmaceutique", "agence de voyages d'affaires",
           "entreprise de nettoyage", "centre de conférences", "société de déménagement",
           "fournisseur d'électricité", "école de langues"]
rng.shuffle(SECTORS)


def balanced(n, values, weights=None):
    weights = weights or [1] * len(values)
    total = sum(weights)
    counts = [n * w // total for w in weights]
    order = sorted(range(len(values)), key=lambda i: -(n * weights[i] / total - counts[i]))
    for i in order[: n - sum(counts)]:
        counts[i] += 1
    out = [v for v, c in zip(values, counts) for _ in range(c)]
    rng.shuffle(out)
    return out


# Part 5 : 15 compétences distinctes sur 16.
p5_tags = GRAMMAR[:]
rng.shuffle(p5_tags)
p5_tags = p5_tags[:15]
p5_letters = balanced(15, list("ABCD"))
p5_diffs = balanced(15, [1, 2, 3], [1, 2, 1])
p5 = [(f"p5-{9001 + i:04d}", t, d, l) for i, (t, d, l) in enumerate(zip(p5_tags, p5_diffs, p5_letters))]

# Part 6 : 2 textes, 3 compétences de grammaire chacun + une insertion de phrase.
pool6 = [t for t in ["verb-tense", "conjunctions-connectors", "vocabulary-in-context", "pronouns",
                     "collocations", "prepositions", "word-form-adverb", "passive-voice"]]
rng.shuffle(pool6)
p6_letters = balanced(8, list("ABCD"))
p6_diffs = balanced(8, [1, 2, 3], [1, 2, 1])
p6 = []
q = 0
for k, genre in enumerate(["email", "article"]):
    tags = pool6[k * 3:(k + 1) * 3]
    tags.insert(rng.randrange(4), "sentence-insertion")
    qs = []
    for tag in tags:
        qs.append((tag, p6_diffs[q], p6_letters[q]))
        q += 1
    p6.append({"id": f"p6-{9001 + k:04d}", "genres": [genre], "sector": SECTORS.pop(), "questions": qs})

# Part 7 : 6 documents seuls (2, 2, 3, 3, 3, 4 questions) puis 2 jeux multiples.
P7 = [
    (["chat"], ["detail", "writer-intention"]),
    (["notice"], ["main-idea", "detail"]),
    (["email"], ["main-idea", "inference", "sentence-insertion"]),
    (["advertisement"], ["detail", "not-true", "synonym-in-context"]),
    (["webpage"], ["main-idea", "detail", "inference"]),
    (["article"], ["main-idea", "synonym-in-context", "not-true", "sentence-insertion"]),
    (["job-posting", "email"], ["main-idea", "detail", "cross-reference", "synonym-in-context", "cross-reference"]),
    (["schedule", "email", "form"], ["detail", "inference", "cross-reference", "not-true", "cross-reference"]),
]
n7 = sum(len(t) for _, t in P7)
p7_letters = balanced(n7, list("ABCD"))
p7_diffs = balanced(n7, [1, 2, 3], [1, 2, 1])
p7 = []
q = 0
for k, (genres, tags) in enumerate(P7):
    qs = []
    for tag in tags:
        qs.append((tag, p7_diffs[q], p7_letters[q]))
        q += 1
    p7.append({"id": f"p7-{9001 + k:04d}", "genres": genres, "sector": SECTORS.pop(), "questions": qs})


def load(path, key):
    return json.loads(path.read_text(encoding="utf-8"))[key]


def table(sets):
    lines = ["| id | genre(s) | secteur suggéré | questions (tag, difficulté, bonne réponse) |", "|---|---|---|---|"]
    for s in sets:
        qs = " ; ".join(f"-{n} {tag} d{d} **{l}**" for n, (tag, d, l) in enumerate(s["questions"], 1))
        lines.append(f"| {s['id']} | {' + '.join(s['genres'])} | {s['sector']} | {qs} |")
    return lines


def existing_topics():
    lines = ["## Sujets déjà traités dans la banque d'entraînement (ne pas reprendre : autres entreprises, noms, situations)", ""]
    for p in sorted((ROOT / "bank/part6").glob("*.json")):
        for t in load(p, "texts"):
            lines.append(f"- {t['id']} ({t['genre']}) : {t['text'][:90].replace(chr(10), ' ')}…")
    for p in sorted((ROOT / "bank/part7").glob("*.json")):
        for s in load(p, "sets"):
            first = s["documents"][0]
            head = first.get("title") or first["text"][:70].replace("\n", " ")
            lines.append(f"- {s['id']} ({' + '.join(d['genre'] for d in s['documents'])}) : {head}")
    return lines


out = ROOT / "drafts/mock"
out.mkdir(parents=True, exist_ok=True)
HEADER = ("Pool `mock` (demi-test Reading n° 1). Ces questions ne servent jamais à l'entraînement : "
          "même niveau d'exigence que la banque, niveau visé d'un candidat entre 750 et 900.")

a = ["# Plan imposé du rédacteur A : Part 5 et Part 6 du demi-test", "", HEADER, "",
     "## Part 5 (fichier de sortie : `lot-a-part5.json`, pool `mock`)", "",
     "| id | tag | difficulté | bonne réponse |", "|---|---|---|---|"]
a += [f"| {i} | {t} | d{d} | **{l}** |" for i, t, d, l in p5]
a += ["", "## Part 6 (fichier de sortie : `lot-a-part6.json`, pool `mock`)", "",
      "Chaque texte : 4 questions dans l'ordre des trous, une seule insertion de phrase.", ""]
a += table(p6) + [""] + existing_topics()
(out / "plan-a.md").write_text("\n".join(a) + "\n", encoding="utf-8")

for name, sets, what in (("b", p7[:6], "documents seuls (2 à 4 questions)"),
                         ("c", p7[6:], "jeux de 2 ou 3 documents (5 questions)")):
    lines = [f"# Plan imposé du rédacteur {name.upper()} : Part 7 du demi-test, {what}", "", HEADER, "",
             f"Fichier de sortie : `lot-{name}-part7.json`, pool `mock`.", "",
             "Questions dans l'ordre du tableau. Pour `sentence-insertion`, la lettre fixe la bonne position : A = [1], B = [2], C = [3], D = [4].", ""]
    lines += table(sets) + [""] + existing_topics()
    (out / f"plan-{name}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

allq = [(d, l) for _, _, d, l in p5] + [(d, l) for s in p6 + p7 for _, d, l in s["questions"]]
print(len(allq), "questions", Counter(l for _, l in allq), Counter(d for d, _ in allq))
