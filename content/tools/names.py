"""Repère les noms propres (personnes, entreprises, lieux) employés dans plusieurs textes ou jeux
des Parts 6 et 7, pour éviter qu'un même nom désigne deux choses différentes.

Usage : python content/tools/names.py [autres fichiers .json à inclure, ex. un lot en cours]

Heuristique : un mot qui commence par une majuscule et n'apparaît jamais en minuscule dans le
corpus. Les mots génériques (jours, en-têtes, « Operations »…) restent dans la sortie : à trier à l'œil.
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

BANK = Path(__file__).resolve().parents[1] / "bank"
COMMON = set("""The A An In On At To For Of And But Or If As By From With Our We You Your It This That These
Those Please Thank Dear Sincerely Regards Best Subject Date Mr Ms Mrs Dr Monday Tuesday Wednesday Thursday
Friday Saturday Sunday January February March April May June July August September October November December
Street Road Avenue Lane Hall Center Room Building Office Department Manager Director Company Group Notice Memo
Program Service Services Team Staff Customer Hotel Library Museum College City Park Station Airport Bank Store
Cafe Kitchen Press Online Web Website Questions Welcome Every Each All Some Most Any No""".split())


def units(files):
    out = {}
    for f in files:
        data = json.loads(Path(f).read_text(encoding="utf-8"))
        for t in data.get("texts", []):
            out[t["id"]] = t["text"]
        for s in data.get("sets", []):
            out[s["id"]] = "\n".join(d["text"] + " " + (d.get("title") or "") for d in s["documents"])
    return out


def main(extra):
    files = sorted(BANK.glob("part6/*.json")) + sorted(BANK.glob("part7/*.json")) + [Path(e) for e in extra]
    texts = units(files)
    lower = set(re.findall(r"\b[a-z][a-z'-]+\b", " ".join(texts.values())))
    seen = defaultdict(set)
    for uid, text in texts.items():
        for word in set(re.findall(r"\b[A-Z][a-zA-Z'-]{2,}\b", text)):
            if word not in COMMON and word.lower() not in lower and "'" not in word:
                seen[word].add(uid)
    for word, ids in sorted(seen.items(), key=lambda x: (-len(x[1]), x[0])):
        if len(ids) > 1:
            print(f"{word:16} {', '.join(sorted(ids))}")


if __name__ == "__main__":
    main(sys.argv[1:])
