"""Plans imposés des lots Parts 6 et 7 restants (Phase 2, volumes visés : 25 textes, 50 jeux).

Pour chaque texte ou jeu : identifiant, genre, secteur suggéré, puis tag, difficulté et lettre
de la bonne réponse par question. Lettres : 25 % chacune ; difficultés : environ 25/50/25 %.

Usage : python content/drafts/reading_plan.py
Écrit content/drafts/part6/plan-lot-NN.md et content/drafts/part7/plan-lot-NN.md.
Déterministe (graine fixe) : relancer donne les mêmes plans.
"""
import json
import random
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
rng = random.Random(1511)

GRAMMAR = [
    "word-form-noun", "word-form-adjective", "word-form-adverb", "comparatives-superlatives",
    "verb-tense", "subject-verb-agreement", "passive-voice", "gerund-infinitive",
    "participle-adjectives", "pronouns", "relative-clauses", "quantifiers-determiners",
    "prepositions", "conjunctions-connectors", "vocabulary-in-context", "collocations",
]
# Tags qui profitent le plus du contexte d'un texte : ils reçoivent les places restantes en premier.
CONTEXT_FIRST = ["verb-tense", "conjunctions-connectors", "vocabulary-in-context", "pronouns",
                 "collocations", "prepositions", "word-form-noun", "passive-voice"]

SECTORS = [
    "hôtellerie", "logistique", "banque", "informatique", "santé (clinique)", "commerce de détail",
    "industrie", "immobilier", "transport aérien", "édition", "énergie", "agroalimentaire",
    "ressources humaines", "tourisme", "BTP", "assurance", "télécoms", "musée / culture",
    "université / formation", "restauration", "mairie / services publics", "cabinet comptable",
    "agence de publicité", "salon professionnel", "pharmacie", "location de voitures",
    "jardinerie", "imprimerie", "salle de sport", "cabinet d'architectes", "transport maritime",
    "bibliothèque", "studio de design", "startup de logiciels", "coopérative agricole",
]


def balanced(n, values, weights=None):
    """n valeurs dont la répartition suit les poids, mélangées."""
    weights = weights or [1] * len(values)
    total = sum(weights)
    counts = [n * w // total for w in weights]
    order = sorted(range(len(values)), key=lambda i: -(n * weights[i] / total - counts[i]))
    for i in order[: n - sum(counts)]:
        counts[i] += 1
    out = [v for v, c in zip(values, counts) for _ in range(c)]
    rng.shuffle(out)
    return out


def load(path, key):
    return json.loads(path.read_text(encoding="utf-8"))[key]


# ----------------------------------------------------------------------------- Part 6
p6_existing = [t for p in sorted((ROOT / "bank/part6").glob("*.json")) for t in load(p, "texts")]
texts_per_tag = Counter(tag for t in p6_existing for tag in {q["tags"][0] for q in t["questions"]})

P6_LOTS = [5, 4, 4, 4]  # 17 textes : p6-0009 à p6-0025
n_texts = sum(P6_LOTS)
need = {tag: max(0, 3 - texts_per_tag[tag]) for tag in GRAMMAR}
slots = n_texts * 3
extra = slots - sum(need.values())
i = 0
while extra > 0:
    tag = (CONTEXT_FIRST + GRAMMAR)[i % (len(CONTEXT_FIRST) + len(GRAMMAR))]
    need[tag] += 1
    extra -= 1
    i += 1

# Répartit les tags sur les textes : 3 tags distincts par texte.
bag = [tag for tag, c in need.items() for _ in range(c)]
for attempt in range(1000):
    rng.shuffle(bag)
    groups = [bag[k * 3:(k + 1) * 3] for k in range(n_texts)]
    if all(len(set(g)) == 3 for g in groups):
        break
else:
    raise SystemExit("répartition Part 6 impossible")

P6_GENRES = ["press-release", "newsletter", "webpage", "job-posting", "brochure", "review", "report",
             "email", "memo", "notice", "letter", "announcement", "article", "advertisement",
             "instructions", "email", "memo"]
rng.shuffle(P6_GENRES)
n_q6 = n_texts * 4
letters6 = balanced(n_q6, list("ABCD"))
diffs6 = balanced(n_q6, [1, 2, 3], [1, 2, 1])
sectors = SECTORS[:]
rng.shuffle(sectors)

p6_plan = []
q = 0
for k in range(n_texts):
    tags = groups[k][:]
    pos = rng.randrange(4)
    tags.insert(pos, "sentence-insertion")
    qs = []
    for tag in tags:
        qs.append((tag, diffs6[q], letters6[q]))
        q += 1
    p6_plan.append({"id": f"p6-{9 + k:04d}", "genres": [P6_GENRES[k]], "sector": sectors.pop(), "questions": qs})

# ----------------------------------------------------------------------------- Part 7
# Lots : 6 ou 5 documents seuls, ou 3 jeux de documents multiples, en alternance.
P7_LOTS = [("single", 6), ("multi", 3), ("single", 5), ("multi", 3), ("single", 5),
           ("multi", 3), ("single", 5), ("multi", 3), ("single", 5)]

SINGLE_TEMPLATES = (
    # 6 chats : la réplique à interpréter
    [("chat", ["main-idea", "writer-intention", "detail"])] * 2
    + [("chat", ["writer-intention", "inference"])] * 2
    + [("chat", ["detail", "writer-intention", "inference", "not-true"])] * 2
    # 8 documents avec repères [1]-[4]
    + [("article", ["main-idea", "detail", "synonym-in-context", "sentence-insertion"])] * 2
    + [("press-release", ["main-idea", "not-true", "sentence-insertion"])]
    + [("email", ["main-idea", "inference", "sentence-insertion"])]
    + [("letter", ["main-idea", "detail", "synonym-in-context", "sentence-insertion"])]
    + [("newsletter", ["detail", "inference", "sentence-insertion"])]
    + [("memo", ["main-idea", "detail", "not-true", "sentence-insertion"])]
    + [("report", ["main-idea", "synonym-in-context", "sentence-insertion"])]
    # 12 autres documents
    + [("notice", ["main-idea", "detail", "not-true"])]
    + [("advertisement", ["detail", "inference", "synonym-in-context"])]
    + [("job-posting", ["main-idea", "not-true", "detail", "inference"])]
    + [("invoice", ["detail", "inference"])]
    + [("webpage", ["main-idea", "detail", "synonym-in-context"])]
    + [("review", ["main-idea", "inference", "not-true"])]
    + [("itinerary", ["detail", "inference", "not-true"])]
    + [("form", ["detail", "inference"])]
    + [("brochure", ["main-idea", "not-true", "synonym-in-context"])]
    + [("instructions", ["main-idea", "detail", "inference"])]
    + [("survey", ["detail", "inference", "synonym-in-context"])]
    + [("email", ["main-idea", "detail", "not-true", "inference"])]
)
MULTI_TEMPLATES = [
    (["advertisement", "email"], ["main-idea", "detail", "cross-reference", "synonym-in-context", "cross-reference"]),
    (["job-posting", "letter"], ["detail", "not-true", "cross-reference", "inference", "cross-reference"]),
    (["schedule", "email"], ["main-idea", "cross-reference", "detail", "synonym-in-context", "cross-reference"]),
    (["invoice", "email"], ["main-idea", "detail", "cross-reference", "cross-reference", "inference"]),
    (["review", "email"], ["main-idea", "synonym-in-context", "inference", "cross-reference", "detail"]),
    (["notice", "chat"], ["main-idea", "detail", "cross-reference", "writer-intention", "inference"]),
    (["webpage", "form", "email"], ["detail", "not-true", "cross-reference", "cross-reference", "inference"]),
    (["article", "email", "schedule"], ["main-idea", "inference", "cross-reference", "detail", "cross-reference"]),
    (["memo", "itinerary", "email"], ["main-idea", "detail", "cross-reference", "not-true", "cross-reference"]),
    (["brochure", "form", "email"], ["detail", "synonym-in-context", "cross-reference", "inference", "cross-reference"]),
    (["press-release", "article"], ["main-idea", "not-true", "cross-reference", "synonym-in-context", "inference"]),
    (["survey", "report", "email"], ["main-idea", "detail", "cross-reference", "cross-reference", "inference"]),
]
singles = list(SINGLE_TEMPLATES)
multis = list(MULTI_TEMPLATES)
rng.shuffle(singles)
rng.shuffle(multis)
assert len(singles) == sum(n for k, n in P7_LOTS if k == "single")
assert len(multis) == sum(n for k, n in P7_LOTS if k == "multi")

n_q7 = sum(len(t[1]) for t in singles) + sum(len(t[1]) for t in multis)
letters7 = balanced(n_q7, list("ABCD"))
diffs7 = balanced(n_q7, [1, 2, 3], [1, 2, 1])

p7_lots = []
number, q = 13, 0
for kind, size in P7_LOTS:
    lot = []
    for _ in range(size):
        genres, tags = (([singles[-1][0]], singles.pop()[1]) if kind == "single" else multis.pop())
        qs = []
        for tag in tags:
            letter = letters7[q]
            if tag == "sentence-insertion":
                letter = letter  # les options sont [1] à [4] : la lettre fixe la position.
            qs.append((tag, diffs7[q], letter))
            q += 1
        lot.append({"id": f"p7-{number:04d}", "genres": genres, "sector": sectors.pop() if sectors else rng.choice(SECTORS), "questions": qs})
        number += 1
    p7_lots.append((kind, lot))
    if not sectors:
        sectors = SECTORS[:]
        rng.shuffle(sectors)


# ----------------------------------------------------------------------------- Écriture
def table(sets):
    lines = ["| id | genre(s) | secteur suggéré | questions (tag, difficulté, bonne réponse) |", "|---|---|---|---|"]
    for s in sets:
        qs = " ; ".join(f"-{n} {tag} d{d} **{l}**" for n, (tag, d, l) in enumerate(s["questions"], 1))
        lines.append(f"| {s['id']} | {' + '.join(s['genres'])} | {s['sector']} | {qs} |")
    return lines


def existing_topics():
    lines = ["## Sujets déjà traités (ne pas reprendre : autres entreprises, noms, situations)", ""]
    for t in p6_existing:
        lines.append(f"- {t['id']} ({t['genre']}) : {t['text'][:90].replace(chr(10), ' ')}…")
    for p in sorted((ROOT / "bank/part7").glob("*.json")):
        for s in load(p, "sets"):
            first = s["documents"][0]
            head = first.get("title") or first["text"][:70].replace("\n", " ")
            lines.append(f"- {s['id']} ({' + '.join(d['genre'] for d in s['documents'])}) : {head}")
    return lines


p6_dir, p7_dir = ROOT / "drafts/part6", ROOT / "drafts/part7"
p6_dir.mkdir(parents=True, exist_ok=True)
p7_dir.mkdir(parents=True, exist_ok=True)

start = 0
for n, size in enumerate(P6_LOTS, 2):
    sets = p6_plan[start:start + size]
    start += size
    lines = [f"# Plan imposé du lot Part 6 n° {n:02d}", "",
             "Chaque texte : 4 questions dans l'ordre des trous, une seule insertion de phrase.", ""]
    lines += table(sets) + [""] + existing_topics()
    (p6_dir / f"plan-lot-{n:02d}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

for n, (kind, sets) in enumerate(p7_lots, 2):
    what = "documents seuls (2 à 4 questions)" if kind == "single" else "jeux de 2 ou 3 documents (5 questions)"
    lines = [f"# Plan imposé du lot Part 7 n° {n:02d} : {what}", "",
             "Questions dans l'ordre du tableau. Pour `sentence-insertion`, la lettre fixe la bonne position : A = [1], B = [2], C = [3], D = [4].", ""]
    lines += table(sets) + [""] + existing_topics()
    (p7_dir / f"plan-lot-{n:02d}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

# Contrôles affichés
all6 = [x for s in p6_plan for x in s["questions"]]
all7 = [x for _, sets in p7_lots for s in sets for x in s["questions"]]
print("Part 6 :", n_texts, "textes,", len(all6), "questions", Counter(l for _, _, l in all6), Counter(d for _, d, _ in all6))
final = Counter(texts_per_tag)
for s in p6_plan:
    final.update({q[0] for q in s["questions"]})
print("  textes par tag (banque + plan) :", min(final[t] for t in GRAMMAR), "au minimum")
print("Part 7 :", sum(len(s) for _, s in p7_lots), "jeux,", len(all7), "questions", Counter(l for _, _, l in all7), Counter(d for _, d, _ in all7))
print("  tags :", Counter(t for t, _, _ in all7))
