#!/usr/bin/env python3
"""Assemble tout le contenu en un seul fichier embarqué dans l'app.

Usage (depuis la racine du dépôt) :
    python content/tools/build.py

Étapes :
  1. valide tout le contenu de content/ (mêmes contrôles que validate.py) et s'arrête à la
     première erreur : un contenu invalide n'entre jamais dans l'app ;
  2. écrit app/src/main/assets/content/content.json : les tags, les questions, les textes et
     documents des Parts 6 et 7, les fiches de cours, le chemin de leçons, les tests blancs, et une « version »
     calculée à partir du contenu lui-même.

Au démarrage, l'app compare cette version à celle de sa base de contenu : si elle a changé,
elle reconstruit la base de contenu. La progression de l'utilisateur vit dans une autre base
et n'est jamais touchée.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate  # noqa: E402

REPO_DIR = validate.CONTENT_DIR.parent
OUTPUT_FILE = REPO_DIR / "app" / "src" / "main" / "assets" / "content" / "content.json"

# À incrémenter si la FORME du fichier change (champs ajoutés ou renommés) : l'app refuse
# un fichier dont elle ne connaît pas le format.
#   1 : Part 5 seule (Phase 1).
#   2 : textes et documents (stimuli), fiches de cours (lessons), chemin de leçons (path).
#   3 : tests blancs (mockTests).
FORMAT_VERSION = 3


def build_bundle(questions: list[dict], tags: dict[str, dict], lessons: list[dict] | None = None,
                 path: dict | None = None, mock_tests: list[dict] | None = None) -> dict:
    """Construit le contenu embarqué. Déterministe : mêmes sources, même résultat, même version."""
    bundle_tags = [
        {"id": t["id"], "parts": t["parts"], "group": t["group"], "fr": t["fr"], "en": t["en"], "order": order}
        for order, t in enumerate(tags.values())
    ]
    bundle_stimuli = [
        {"id": s["id"], "part": s["part"], "pool": s["pool"], "documents": s["documents"]}
        for s in validate.stimuli_of(questions)
    ]
    bundle_questions = [
        {
            "id": q["id"],
            "part": q["part"],
            "pool": q["_pool"],
            "tags": q["tags"],
            "difficulty": q["difficulty"],
            # Part 6 : pas d'énoncé, la question est le trou du texte.
            "stem": q.get("stem", ""),
            "options": q["options"],
            "answer": q["answer"],
            "explanation": q["explanation"],
            # Parts 6 et 7 : le texte ou le jeu de documents de la question, et son rang dedans.
            "stimulusId": q["_stimulus"]["id"] if q.get("_stimulus") else None,
            "position": q.get("_position", 0),
            "evidence": q.get("evidence", []),
        }
        for q in sorted(questions, key=lambda q: q["id"])
    ]
    order = {tag: index for index, tag in enumerate(tags)}
    bundle_lessons = sorted(lessons or [], key=lambda lesson: order.get(lesson["tag"], len(order)))
    bundle_path = path or {"units": []}
    payload = {
        "format": FORMAT_VERSION,
        "tags": bundle_tags,
        "stimuli": bundle_stimuli,
        "questions": bundle_questions,
        "lessons": bundle_lessons,
        "path": bundle_path,
        "mockTests": [
            {"id": m["id"], "section": m["section"], "fr": m["fr"], "en": m["en"], "minutes": m["minutes"], "units": m["units"]}
            for m in mock_tests or []
        ],
    }
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    version = hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]
    return {"format": FORMAT_VERSION, "version": version, **{k: v for k, v in payload.items() if k != "format"}}


def main() -> int:
    tags = validate.load_tags()
    content = validate.validate_content(known_tags=tags)
    for issue in content.report.issues:
        print(issue)
    if not content.report.ok:
        print(f"{len(content.report.errors)} erreur(s) : contenu non généré. Corrige le contenu puis relance.")
        return 1

    bundle = build_bundle(content.questions, tags, content.lessons, content.path, content.mock_tests)
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_text(
        json.dumps(bundle, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(validate.summarize(content.questions, content.lessons, content.path, content.mock_tests))
    print(f"Écrit : {OUTPUT_FILE.relative_to(REPO_DIR).as_posix()} (version {bundle['version']}, {OUTPUT_FILE.stat().st_size // 1024} Ko)")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
