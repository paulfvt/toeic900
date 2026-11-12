#!/usr/bin/env python3
"""Assemble la banque de questions en un seul fichier embarqué dans l'app.

Usage (depuis la racine du dépôt) :
    python content/tools/build.py

Étapes :
  1. valide toute la banque content/bank/ (mêmes contrôles que validate.py) et s'arrête
     à la première erreur : un contenu invalide n'entre jamais dans l'app ;
  2. écrit app/src/main/assets/content/content.json : les tags, les questions et une
     « version » calculée à partir du contenu lui-même.

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
FORMAT_VERSION = 1


def build_bundle(questions: list[dict], tags: dict[str, dict]) -> dict:
    """Construit le contenu embarqué. Déterministe : mêmes sources, même résultat, même version."""
    bundle_tags = [
        {"id": t["id"], "parts": t["parts"], "group": t["group"], "fr": t["fr"], "en": t["en"], "order": order}
        for order, t in enumerate(tags.values())
    ]
    bundle_questions = [
        {
            "id": q["id"],
            "part": q["part"],
            "pool": q["_pool"],
            "tags": q["tags"],
            "difficulty": q["difficulty"],
            "stem": q["stem"],
            "options": q["options"],
            "answer": q["answer"],
            "explanation": q["explanation"],
        }
        for q in sorted(questions, key=lambda q: q["id"])
    ]
    payload = {"format": FORMAT_VERSION, "tags": bundle_tags, "questions": bundle_questions}
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    version = hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]
    return {"format": FORMAT_VERSION, "version": version, "tags": bundle_tags, "questions": bundle_questions}


def main() -> int:
    paths = validate.find_bank_files()
    tags = validate.load_tags()
    report, questions = validate.validate_bank(paths, tags)
    for issue in report.issues:
        print(issue)
    if not report.ok:
        print(f"{len(report.errors)} erreur(s) : contenu non généré. Corrige la banque puis relance.")
        return 1

    bundle = build_bundle(questions, tags)
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_text(
        json.dumps(bundle, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(validate.summarize(questions))
    print(f"Écrit : {OUTPUT_FILE.relative_to(REPO_DIR).as_posix()} (version {bundle['version']}, {OUTPUT_FILE.stat().st_size // 1024} Ko)")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
