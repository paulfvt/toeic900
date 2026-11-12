#!/usr/bin/env python3
"""Valide la banque de questions de TOEIC 900.

Usage (depuis la racine du dépôt) :
    python content/tools/validate.py                 # toute la banque content/bank/
    python content/tools/validate.py fichier.json    # un ou plusieurs lots

Le script n'utilise que la bibliothèque standard de Python : rien à installer.
Il sort avec le code 0 si aucune erreur n'est trouvée, 1 sinon. Les
avertissements n'empêchent pas la validation, mais méritent une relecture.

Ce qu'il vérifie :
  - le schéma de chaque question (content/schemas/part5.schema.json) ;
  - une seule bonne réponse, 4 options distinctes, un seul trou dans l'énoncé ;
  - une explication en français et en anglais, avec la raison de l'erreur pour
    chacun des 3 distracteurs, et un piège ;
  - des tags connus de la taxonomie (content/schemas/tags.json) et autorisés
    pour la part ;
  - l'absence de doublons : identifiants, énoncés identiques ou quasi identiques ;
  - les répartitions, dès qu'un ensemble (part, pool) atteint 40 questions :
    lettre de la bonne réponse, difficulté, nombre de questions par tag.
"""
from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

CONTENT_DIR = Path(__file__).resolve().parents[1]
BANK_DIR = CONTENT_DIR / "bank"
TAGS_FILE = CONTENT_DIR / "schemas" / "tags.json"

LETTERS = "ABCD"
BLANK = "-------"
POOLS = ("training", "mock", "diagnostic")
DIFFICULTIES = (1, 2, 3)
LANGS = ("fr", "en")
ID_PATTERNS = {5: re.compile(r"^p5-\d{4}$")}

FILE_FIELDS = {"part", "pool", "questions"}
QUESTION_FIELDS = {"id", "part", "tags", "difficulty", "stem", "options", "answer", "explanation"}
EXPLANATION_FIELDS = {"rule", "distractors", "trap"}

STEM_MIN, STEM_MAX = 30, 220
OPTION_MAX = 60
RULE_MIN, NOTE_MIN, TRAP_MIN = 20, 10, 10
MAX_TAGS = 3

# Répartitions, contrôlées à partir de DISTRIBUTION_MIN_QUESTIONS par (part, pool).
DISTRIBUTION_MIN_QUESTIONS = 40
ANSWER_SHARE_MIN, ANSWER_SHARE_MAX = 0.18, 0.32     # chaque lettre : 25 % ± 7 points
DIFFICULTY_SHARE_MIN = 0.15                          # chaque niveau : au moins 15 %
DIFFICULTY_MEDIUM_MAX = 0.60                         # niveau 2 : au plus 60 %
MIN_QUESTIONS_PER_TAG = 5                            # sinon la révision espacée ne peut pas varier

NEAR_DUPLICATE_RATIO = 0.88


@dataclass
class Issue:
    level: str      # "error" ou "warning"
    where: str      # fichier et identifiant de la question
    message: str

    def __str__(self) -> str:
        label = "ERREUR" if self.level == "error" else "attention"
        return f"[{label}] {self.where} : {self.message}"


@dataclass
class Report:
    issues: list[Issue] = field(default_factory=list)

    def error(self, where: str, message: str) -> None:
        self.issues.append(Issue("error", where, message))

    def warning(self, where: str, message: str) -> None:
        self.issues.append(Issue("warning", where, message))

    @property
    def errors(self) -> list[Issue]:
        return [i for i in self.issues if i.level == "error"]

    @property
    def warnings(self) -> list[Issue]:
        return [i for i in self.issues if i.level == "warning"]

    @property
    def ok(self) -> bool:
        return not self.errors


def load_tags(path: Path = TAGS_FILE) -> dict[str, dict]:
    """Charge la taxonomie : {id du tag: définition}."""
    data = json.loads(path.read_text(encoding="utf-8"))
    tags: dict[str, dict] = {}
    for tag in data["tags"]:
        if tag["id"] in tags:
            raise ValueError(f"tag en double dans la taxonomie : {tag['id']}")
        tags[tag["id"]] = tag
    return tags


def _is_text(value: object, min_len: int = 1) -> bool:
    return isinstance(value, str) and len(value.strip()) >= min_len and value == value.strip()


def normalize(text: str) -> str:
    """Forme comparable d'un texte : minuscules, sans ponctuation ni espaces multiples."""
    text = text.lower().replace(BLANK, " _ ")
    text = re.sub(r"[^a-z0-9_ ]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def validate_explanation(expl: object, answer: str, where: str, lang: str, report: Report) -> None:
    if not isinstance(expl, dict):
        report.error(where, f"explanation.{lang} doit être un objet")
        return
    if set(expl) != EXPLANATION_FIELDS:
        report.error(where, f"explanation.{lang} doit contenir exactement {sorted(EXPLANATION_FIELDS)}")
        return
    if not _is_text(expl["rule"], RULE_MIN):
        report.error(where, f"explanation.{lang}.rule est vide ou trop courte")
    if not _is_text(expl["trap"], TRAP_MIN):
        report.error(where, f"explanation.{lang}.trap est vide ou trop court")
    notes = expl["distractors"]
    expected = set(LETTERS) - {answer}
    if not isinstance(notes, dict) or set(notes) != expected:
        report.error(
            where,
            f"explanation.{lang}.distractors doit expliquer exactement les lettres "
            f"{sorted(expected)} (les 3 mauvaises réponses)",
        )
        return
    for letter, note in notes.items():
        if not _is_text(note, NOTE_MIN):
            report.error(where, f"explanation.{lang}.distractors.{letter} est vide ou trop courte")


def validate_question(q: object, part: int, known_tags: dict[str, dict], where: str, report: Report) -> bool:
    """Contrôle une question. Renvoie True si sa structure est exploitable pour les contrôles globaux."""
    if not isinstance(q, dict):
        report.error(where, "la question doit être un objet JSON")
        return False
    missing, extra = QUESTION_FIELDS - set(q), set(q) - QUESTION_FIELDS
    if missing:
        report.error(where, f"champs manquants : {sorted(missing)}")
    if extra:
        report.error(where, f"champs inconnus : {sorted(extra)}")
    if missing:
        return False

    before = len(report.errors)

    pattern = ID_PATTERNS.get(part)
    if not isinstance(q["id"], str) or (pattern and not pattern.match(q["id"])):
        report.error(where, f"id invalide : {q['id']!r} (attendu : p{part}-0001)")
    if q["part"] != part:
        report.error(where, f"part = {q['part']!r}, mais le fichier est déclaré part {part}")

    tags = q["tags"]
    if not isinstance(tags, list) or not 1 <= len(tags) <= MAX_TAGS or len(set(map(str, tags))) != len(tags):
        report.error(where, f"tags doit être une liste de 1 à {MAX_TAGS} tags distincts")
    else:
        for tag in tags:
            if tag not in known_tags:
                report.error(where, f"tag inconnu de la taxonomie : {tag!r}")
            elif part not in known_tags[tag]["parts"]:
                report.error(where, f"le tag {tag!r} n'est pas prévu pour la part {part}")

    if q["difficulty"] not in DIFFICULTIES or isinstance(q["difficulty"], bool):
        report.error(where, f"difficulty doit valoir 1, 2 ou 3 (reçu : {q['difficulty']!r})")

    stem = q["stem"]
    if not _is_text(stem):
        report.error(where, "stem doit être un texte non vide, sans espace en début ou en fin")
    else:
        if stem.count(BLANK) != 1 or re.search(r"-{8,}|_{3,}", stem) or len(re.findall(r"-{3,}", stem)) != 1:
            report.error(where, f"stem doit contenir exactement un trou, noté {BLANK} (7 tirets)")
        if not STEM_MIN <= len(stem) <= STEM_MAX:
            report.error(where, f"stem fait {len(stem)} caractères (attendu : {STEM_MIN} à {STEM_MAX})")
        if stem[-1] not in ".?!":
            report.error(where, "stem doit se terminer par un point, un ? ou un !")

    options = q["options"]
    if not isinstance(options, list) or len(options) != 4:
        report.error(where, "options doit contenir exactement 4 propositions")
    elif not all(_is_text(o) for o in options):
        report.error(where, "chaque option doit être un texte non vide, sans espace en début ou en fin")
    else:
        if len({o.lower() for o in options}) != 4:
            report.error(where, "deux options sont identiques : la bonne réponse ne serait plus unique")
        if any(BLANK in o or len(o) > OPTION_MAX for o in options):
            report.error(where, f"une option contient le trou ou dépasse {OPTION_MAX} caractères")

    answer = q["answer"]
    if answer not in tuple(LETTERS):
        report.error(where, f"answer doit être A, B, C ou D (reçu : {answer!r})")
        answer = None

    expl = q["explanation"]
    if not isinstance(expl, dict) or set(expl) != set(LANGS):
        report.error(where, "explanation doit contenir exactement les langues fr et en")
    elif answer:
        for lang in LANGS:
            validate_explanation(expl[lang], answer, where, lang, report)
        fr, en = expl["fr"], expl["en"]
        if isinstance(fr, dict) and isinstance(en, dict) and fr.get("rule") == en.get("rule"):
            report.error(where, "les explications fr et en sont identiques : l'une des deux n'est pas traduite")

    return len(report.errors) == before


def validate_file_data(data: object, known_tags: dict[str, dict], source: str, report: Report) -> list[dict]:
    """Contrôle le contenu d'un fichier de lot. Renvoie les questions structurellement valides,
    enrichies de _pool et _source pour les contrôles globaux."""
    if not isinstance(data, dict) or set(data) != FILE_FIELDS:
        report.error(source, f"le fichier doit être un objet avec exactement {sorted(FILE_FIELDS)}")
        return []
    part, pool, questions = data["part"], data["pool"], data["questions"]
    if part not in ID_PATTERNS:
        report.error(source, f"part non prise en charge : {part!r}")
        return []
    if pool not in POOLS:
        report.error(source, f"pool inconnu : {pool!r} (attendu : {', '.join(POOLS)})")
        return []
    if not isinstance(questions, list) or not questions:
        report.error(source, "questions doit être une liste non vide")
        return []
    valid = []
    for index, q in enumerate(questions):
        qid = q.get("id") if isinstance(q, dict) else None
        where = f"{source} › {qid or f'question n°{index + 1}'}"
        if validate_question(q, part, known_tags, where, report):
            valid.append({**q, "_pool": pool, "_source": source})
    return valid


def check_duplicates(questions: list[dict], report: Report) -> None:
    """Doublons sur toute la banque, tous pools confondus : une question de test blanc
    ne doit pas non plus exister à l'entraînement."""
    by_id: dict[str, dict] = {}
    for q in questions:
        if q["id"] in by_id:
            report.error(f"{q['_source']} › {q['id']}", f"id déjà utilisé dans {by_id[q['id']]['_source']}")
        else:
            by_id[q["id"]] = q

    unique = list(by_id.values())
    stems = [normalize(q["stem"]) for q in unique]
    option_sets: dict[tuple, str] = {}
    for i, q in enumerate(unique):
        where = f"{q['_source']} › {q['id']}"
        key = tuple(sorted(o.lower() for o in q["options"]))
        if key in option_sets:
            report.warning(where, f"mêmes 4 options que {option_sets[key]} : peu de variété pour la révision")
        else:
            option_sets[key] = q["id"]
        for j in range(i):
            if stems[i] == stems[j]:
                report.error(where, f"énoncé identique à {unique[j]['id']}")
                continue
            matcher = difflib.SequenceMatcher(None, stems[i], stems[j], autojunk=False)
            if matcher.quick_ratio() >= NEAR_DUPLICATE_RATIO and matcher.ratio() >= NEAR_DUPLICATE_RATIO:
                report.error(where, f"énoncé quasi identique à {unique[j]['id']} ({matcher.ratio():.0%} de ressemblance)")


def check_distributions(questions: list[dict], known_tags: dict[str, dict], report: Report) -> None:
    """Répartitions par (part, pool), dès que l'ensemble est assez grand pour qu'elles aient un sens."""
    groups: dict[tuple[int, str], list[dict]] = defaultdict(list)
    for q in questions:
        groups[(q["part"], q["_pool"])].append(q)

    for (part, pool), group in sorted(groups.items()):
        where = f"part {part}, pool {pool}"
        total = len(group)
        if total < DISTRIBUTION_MIN_QUESTIONS:
            continue

        answers = Counter(q["answer"] for q in group)
        for letter in LETTERS:
            share = answers[letter] / total
            if not ANSWER_SHARE_MIN <= share <= ANSWER_SHARE_MAX:
                report.error(
                    where,
                    f"la bonne réponse est {letter} dans {share:.0%} des questions "
                    f"(attendu : entre {ANSWER_SHARE_MIN:.0%} et {ANSWER_SHARE_MAX:.0%})",
                )

        levels = Counter(q["difficulty"] for q in group)
        for level in DIFFICULTIES:
            if levels[level] / total < DIFFICULTY_SHARE_MIN:
                report.error(where, f"difficulté {level} : {levels[level] / total:.0%} des questions (minimum {DIFFICULTY_SHARE_MIN:.0%})")
        if levels[2] / total > DIFFICULTY_MEDIUM_MAX:
            report.error(where, f"difficulté 2 : {levels[2] / total:.0%} des questions (maximum {DIFFICULTY_MEDIUM_MAX:.0%})")

        if pool == "training":
            primary = Counter(q["tags"][0] for q in group)
            for tag, count in sorted(primary.items()):
                if count < MIN_QUESTIONS_PER_TAG:
                    report.error(where, f"le tag {tag!r} n'a que {count} question(s) : il en faut au moins {MIN_QUESTIONS_PER_TAG} pour varier les révisions")
            for tag, definition in known_tags.items():
                if part in definition["parts"] and tag not in primary:
                    report.warning(where, f"aucune question n'a {tag!r} pour tag principal")


def find_bank_files(bank_dir: Path = BANK_DIR) -> list[Path]:
    return sorted(bank_dir.rglob("*.json"))


def validate_bank(paths: list[Path], known_tags: dict[str, dict] | None = None) -> tuple[Report, list[dict]]:
    """Valide un ensemble de fichiers. Renvoie le rapport et les questions valides."""
    known_tags = load_tags() if known_tags is None else known_tags
    report = Report()
    questions: list[dict] = []
    for path in paths:
        try:
            source = path.resolve().relative_to(CONTENT_DIR).as_posix()
        except ValueError:
            source = path.name
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            report.error(source, f"fichier illisible ou JSON invalide : {exc}")
            continue
        questions.extend(validate_file_data(data, known_tags, source, report))
    check_duplicates(questions, report)
    check_distributions(questions, known_tags, report)
    return report, questions


def summarize(questions: list[dict]) -> str:
    lines = [f"{len(questions)} question(s) valide(s)"]
    if questions:
        answers = Counter(q["answer"] for q in questions)
        levels = Counter(q["difficulty"] for q in questions)
        tags = Counter(q["tags"][0] for q in questions)
        lines.append("  réponses   : " + "  ".join(f"{l}={answers[l]}" for l in LETTERS))
        lines.append("  difficulté : " + "  ".join(f"{d}={levels[d]}" for d in DIFFICULTIES))
        lines.append("  tags       : " + ", ".join(f"{t} ({n})" for t, n in sorted(tags.items())))
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Valide la banque de questions TOEIC 900.")
    parser.add_argument("files", nargs="*", type=Path, help="lots à valider (par défaut : tout content/bank/)")
    args = parser.parse_args(argv)

    paths = args.files or find_bank_files()
    if not paths:
        print("Aucun fichier à valider dans content/bank/.")
        return 1

    report, questions = validate_bank(paths)
    for issue in report.issues:
        print(issue)
    print(summarize(questions))
    print(f"{len(report.errors)} erreur(s), {len(report.warnings)} avertissement(s)")
    return 0 if report.ok else 1


if __name__ == "__main__":
    # Sous Windows, la console n'est pas toujours en UTF-8 : on évite les erreurs d'affichage.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
