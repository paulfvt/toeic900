#!/usr/bin/env python3
"""Valide le contenu de TOEIC 900 : banque de questions, fiches de cours, chemin de leçons.

Usage (depuis la racine du dépôt) :
    python content/tools/validate.py                 # tout le contenu de content/
    python content/tools/validate.py fichier.json    # un ou plusieurs lots ou fiches

Le script n'utilise que la bibliothèque standard de Python : rien à installer.
Il sort avec le code 0 si aucune erreur n'est trouvée, 1 sinon. Les
avertissements n'empêchent pas la validation, mais méritent une relecture.

Ce qu'il vérifie :
  - le schéma de chaque lot (content/schemas/part5|part6|part7.schema.json) ;
  - une seule bonne réponse, 4 options distinctes, une explication en français et en
    anglais avec la raison de l'erreur pour chacun des 3 distracteurs, et un piège ;
  - des tags connus de la taxonomie (content/schemas/tags.json) et autorisés pour la part ;
  - Part 5 : un seul trou dans l'énoncé ;
  - Part 6 : 4 trous numérotés [[1]] à [[4]], 4 questions, dont une seule insertion de phrase ;
  - Part 7 : 1 à 3 documents, 2 à 5 questions, et pour chaque question des « preuves »
    (evidence) recopiées mot pour mot depuis les documents ;
  - l'absence de doublons : identifiants, énoncés ou documents identiques ou quasi identiques ;
  - les répartitions, dès qu'un ensemble (part, pool) atteint 40 questions : lettre de la
    bonne réponse, difficulté, nombre de questions par tag (Part 5) ;
  - les fiches de cours (content/lessons/fiches/) : structure, versions fr et en parallèles ;
  - le chemin de leçons (content/lessons/path.json) : chaque leçon a sa fiche et assez
    d'exercices dans la banque.
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
LESSONS_DIR = CONTENT_DIR / "lessons"
FICHES_DIR = LESSONS_DIR / "fiches"
PATH_FILE = LESSONS_DIR / "path.json"
MOCK_DIR = CONTENT_DIR / "mock"
TAGS_FILE = CONTENT_DIR / "schemas" / "tags.json"

LETTERS = "ABCD"
BLANK = "-------"
POOLS = ("training", "mock", "diagnostic")
DIFFICULTIES = (1, 2, 3)
LANGS = ("fr", "en")

# Part 5 : identifiant de la question. Parts 6 et 7 : identifiant du texte ou du jeu de
# documents ; ses questions s'appellent ensuite <id>-1, <id>-2…
ID_PATTERNS = {
    5: re.compile(r"^p5-\d{4}$"),
    6: re.compile(r"^p6-\d{4}$"),
    7: re.compile(r"^p7-\d{4}$"),
}
# Plages d'identifiants par pool (partie numérique) : un identifiant dit à lui seul d'où vient
# une question, et une question de test blanc ne peut pas se glisser dans l'entraînement.
POOL_ID_RANGES = {"training": (1, 7999), "diagnostic": (8000, 8999), "mock": (9000, 9999)}

# Tests blancs (content/mock/*.json)
MOCK_TEST_FIELDS = {"id", "section", "fr", "en", "minutes", "units"}
MOCK_SECTIONS = ("reading",)
MOCK_MINUTES = (10, 120)

# Nom de la liste dans un fichier de lot, selon la part.
FILE_KEYS = {5: "questions", 6: "texts", 7: "sets"}

FILE_FIELDS = {"part", "pool", "questions"}
QUESTION_FIELDS = {"id", "part", "tags", "difficulty", "stem", "options", "answer", "explanation"}
EXPLANATION_FIELDS = {"rule", "distractors", "trap"}

# Parts 6 et 7 : champs obligatoires (+ champs facultatifs).
PART6_QUESTION_FIELDS = {"id", "tags", "difficulty", "options", "answer", "explanation"}
PART6_QUESTION_OPTIONAL = {"evidence"}
PART7_QUESTION_FIELDS = {"id", "tags", "difficulty", "stem", "options", "answer", "explanation", "evidence"}
TEXT_FIELDS = {"id", "part", "genre", "text", "questions"}
SET_FIELDS = {"id", "part", "documents", "questions"}
DOCUMENT_FIELDS = {"genre", "text"}
TITLE_OPTIONAL = {"title"}

GENRES = (
    "email", "letter", "memo", "notice", "announcement", "article", "advertisement", "press-release",
    "newsletter", "webpage", "review", "form", "survey", "schedule", "itinerary", "invoice", "receipt",
    "chat", "instructions", "job-posting", "brochure", "report",
)

STEM_MIN, STEM_MAX = 30, 220
OPTION_MAX = 60
RULE_MIN, NOTE_MIN, TRAP_MIN = 20, 10, 10
MAX_TAGS = 3

# Part 6
P6_BLANKS = tuple(f"[[{n}]]" for n in range(1, 5))
P6_QUESTIONS = 4
P6_WORDS_MIN, P6_WORDS_MAX = 60, 180
SENTENCE_OPTION_MIN, SENTENCE_OPTION_MAX = 20, 200
TAG_SENTENCE_INSERTION = "sentence-insertion"

# Part 7
P7_MARKERS = tuple(f"[{n}]" for n in range(1, 5))
P7_DOCS_MAX = 3
P7_QUESTIONS_SINGLE = (2, 4)      # un seul document : 2 à 4 questions
P7_QUESTIONS_MULTI = 5            # 2 ou 3 documents : 5 questions, comme à l'examen
P7_DOC_WORDS_MIN, P7_DOC_WORDS_MAX = 30, 350
P7_SET_WORDS_MIN, P7_SET_WORDS_MAX = 50, 650
P7_STEM_MIN, P7_STEM_MAX = 10, 300
P7_SYNONYM_ENDING = "closest in meaning to"
P7_OPTION_MAX = 160
EVIDENCE_MAX = 3
TITLE_MAX = 120
TAG_CROSS_REFERENCE = "cross-reference"
TAGS_QUOTING_THE_TEXT = ("synonym-in-context", "writer-intention")

# Répartitions, contrôlées à partir de DISTRIBUTION_MIN_QUESTIONS par (part, pool).
DISTRIBUTION_MIN_QUESTIONS = 40
ANSWER_SHARE_MIN, ANSWER_SHARE_MAX = 0.18, 0.32     # chaque lettre : 25 % ± 7 points
DIFFICULTY_SHARE_MIN = 0.15                          # chaque niveau : au moins 15 %
DIFFICULTY_MEDIUM_MAX = 0.60                         # niveau 2 : au plus 60 %
MIN_QUESTIONS_PER_TAG = 5                            # sinon la révision espacée ne peut pas varier

NEAR_DUPLICATE_RATIO = 0.88

# Fiches de cours
LESSON_FIELDS = {"tag", "fr", "en"}
LESSON_LANG_FIELDS = {"title", "summary", "keyPoints", "rules", "traps"}
RULE_FIELDS = {"title", "text", "examples"}
EXAMPLE_FIELDS = {"en", "note"}
TRAP_FIELDS = {"title", "text"}
LESSON_TITLE = (3, 60)
LESSON_SUMMARY = (40, 400)
LESSON_KEY_POINTS = (2, 4)
LESSON_KEY_POINT = (15, 220)
LESSON_RULES = (2, 5)
LESSON_RULE_TITLE = (3, 80)
LESSON_RULE_TEXT = (30, 700)
LESSON_EXAMPLES = (1, 4)
LESSON_EXAMPLE_EN = (5, 220)
LESSON_EXAMPLE_NOTE = (5, 260)
LESSON_TRAPS = (2, 4)
LESSON_TRAP_TEXT = (30, 500)

# Chemin de leçons
PATH_UNIT_FIELDS = {"id", "fr", "en", "steps"}
STEP_KINDS = ("lesson", "review")
STEP_FIELDS = {"lesson": {"id", "kind", "tag", "part", "units"}, "review": {"id", "kind", "part", "units"}}
STEP_ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
STEP_UNITS = (1, 20)

CURLY_QUOTES = re.compile("[‘’“”]")


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


@dataclass
class Content:
    """Tout le contenu validé : ce que build.py embarque dans l'app."""
    report: Report
    questions: list[dict]
    lessons: list[dict]
    path: dict | None
    mock_tests: list[dict] = field(default_factory=list)


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


def _in_range(value: int, bounds: tuple[int, int]) -> bool:
    return bounds[0] <= value <= bounds[1]


def normalize(text: str) -> str:
    """Forme comparable d'un texte : minuscules, sans ponctuation ni espaces multiples."""
    text = text.lower().replace(BLANK, " _ ")
    text = re.sub(r"[^a-z0-9_ ]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def word_count(text: str) -> int:
    """Nombre de mots d'un document, sans compter les repères [[1]] ou [1]."""
    return len(re.sub(r"\[\[?[1-4]\]?\]", " ", text).split())


def _check_fields(obj: dict, required: set, optional: set, where: str, report: Report) -> bool:
    """Champs manquants ou inconnus. Renvoie False s'il manque un champ obligatoire."""
    missing, extra = required - set(obj), set(obj) - required - optional
    if missing:
        report.error(where, f"champs manquants : {sorted(missing)}")
    if extra:
        report.error(where, f"champs inconnus : {sorted(extra)}")
    return not missing


def _check_english(text: object, label: str, where: str, report: Report) -> None:
    """Textes d'examen des Parts 6 et 7 : guillemets et apostrophes droits, pour que les
    citations (evidence) se retrouvent mot pour mot dans les documents."""
    if isinstance(text, str) and CURLY_QUOTES.search(text):
        report.error(where, f"{label} : utilise des guillemets et apostrophes droits (\" et '), pas des guillemets courbes")


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


def _check_answer_fields(q: dict, part: int, known_tags: dict[str, dict], where: str, report: Report,
                         option_max: int = OPTION_MAX) -> None:
    """Ce que toutes les questions ont en commun : tags, difficulté, options, réponse, explication."""
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

    options = q["options"]
    if not isinstance(options, list) or len(options) != 4:
        report.error(where, "options doit contenir exactement 4 propositions")
    elif not all(_is_text(o) for o in options):
        report.error(where, "chaque option doit être un texte non vide, sans espace en début ou en fin")
    else:
        if len({o.lower() for o in options}) != 4:
            report.error(where, "deux options sont identiques : la bonne réponse ne serait plus unique")
        if any(BLANK in o or len(o) > option_max for o in options):
            report.error(where, f"une option contient le trou ou dépasse {option_max} caractères")

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


def validate_question(q: object, part: int, known_tags: dict[str, dict], where: str, report: Report) -> bool:
    """Contrôle une question Part 5. Renvoie True si sa structure est exploitable pour les contrôles globaux."""
    if not isinstance(q, dict):
        report.error(where, "la question doit être un objet JSON")
        return False
    if not _check_fields(q, QUESTION_FIELDS, set(), where, report):
        return False

    before = len(report.errors)

    pattern = ID_PATTERNS.get(part)
    if not isinstance(q["id"], str) or (pattern and not pattern.match(q["id"])):
        report.error(where, f"id invalide : {q['id']!r} (attendu : p{part}-0001)")
    if q["part"] != part:
        report.error(where, f"part = {q['part']!r}, mais le fichier est déclaré part {part}")

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

    _check_answer_fields(q, part, known_tags, where, report)
    return len(report.errors) == before


def _check_title(obj: dict, where: str, report: Report) -> None:
    if "title" in obj:
        if not _is_text(obj["title"]) or len(obj["title"]) > TITLE_MAX:
            report.error(where, f"title doit être un texte de {TITLE_MAX} caractères au plus (ou être absent)")
        _check_english(obj["title"], "title", where, report)


def _check_evidence(evidence: object, haystacks: list[str], minimum: int, where: str, report: Report) -> list[set[int]]:
    """Les « preuves » : des passages recopiés mot pour mot depuis les documents.
    Renvoie, pour chaque citation trouvée, les numéros des documents qui la contiennent."""
    if not isinstance(evidence, list) or not minimum <= len(evidence) <= EVIDENCE_MAX or \
            not all(_is_text(e, 2) for e in evidence) or len(set(evidence)) != len(evidence):
        report.error(where, f"evidence doit être une liste de {minimum} à {EVIDENCE_MAX} citations distinctes")
        return []
    found = []
    for quote in evidence:
        docs = {index for index, text in enumerate(haystacks) if quote in text}
        if "[[" in quote or not docs:
            report.error(where, f"evidence : « {quote} » ne se retrouve pas mot pour mot dans le texte (hors trous)")
        else:
            found.append(docs)
    return found


def _check_question_ids(questions: list, stimulus_id: object, where: str, report: Report) -> None:
    for index, q in enumerate(questions):
        expected = f"{stimulus_id}-{index + 1}"
        if isinstance(q, dict) and q.get("id") != expected:
            report.error(where, f"la question n°{index + 1} doit avoir l'id {expected!r} (reçu : {q.get('id')!r})")


def validate_text(item: object, known_tags: dict[str, dict], where: str, report: Report) -> bool:
    """Contrôle un texte à trous de la Part 6 et ses 4 questions."""
    if not isinstance(item, dict):
        report.error(where, "le texte doit être un objet JSON")
        return False
    if not _check_fields(item, TEXT_FIELDS, TITLE_OPTIONAL, where, report):
        return False
    before = len(report.errors)

    if not isinstance(item["id"], str) or not ID_PATTERNS[6].match(item["id"]):
        report.error(where, f"id invalide : {item['id']!r} (attendu : p6-0001)")
    if item["part"] != 6:
        report.error(where, f"part = {item['part']!r}, mais le fichier est déclaré part 6")
    if item["genre"] not in GENRES:
        report.error(where, f"genre inconnu : {item['genre']!r} (attendu : {', '.join(GENRES)})")
    _check_title(item, where, report)

    text = item["text"]
    text_ok = _is_text(text)
    if not text_ok:
        report.error(where, "text doit être un texte non vide, sans espace en début ou en fin")
    else:
        _check_english(text, "text", where, report)
        positions = [text.find(marker) for marker in P6_BLANKS]
        if any(text.count(marker) != 1 for marker in P6_BLANKS) or positions != sorted(positions) \
                or text.count("[[") != P6_QUESTIONS or BLANK in text or re.search(r"_{3,}|-{4,}", text):
            report.error(where, "text doit contenir exactement 4 trous, notés [[1]], [[2]], [[3]] et [[4]], dans cet ordre")
            text_ok = False
        words = word_count(text)
        if not P6_WORDS_MIN <= words <= P6_WORDS_MAX:
            report.error(where, f"text fait {words} mots (attendu : {P6_WORDS_MIN} à {P6_WORDS_MAX})")

    questions = item["questions"]
    if not isinstance(questions, list) or len(questions) != P6_QUESTIONS:
        report.error(where, f"un texte Part 6 a exactement {P6_QUESTIONS} questions, une par trou")
        return False
    _check_question_ids(questions, item["id"], where, report)

    insertions = []
    for index, q in enumerate(questions):
        q_where = f"{where} › trou {index + 1}"
        if not isinstance(q, dict):
            report.error(q_where, "la question doit être un objet JSON")
            continue
        if not _check_fields(q, PART6_QUESTION_FIELDS, PART6_QUESTION_OPTIONAL, q_where, report):
            continue
        is_insertion = isinstance(q["tags"], list) and q["tags"][:1] == [TAG_SENTENCE_INSERTION]
        _check_answer_fields(q, 6, known_tags, q_where, report,
                             option_max=SENTENCE_OPTION_MAX if is_insertion else OPTION_MAX)
        options = q["options"] if isinstance(q["options"], list) else []
        for option in options:
            _check_english(option, "options", q_where, report)
        if is_insertion:
            insertions.append(index)
            if not all(isinstance(o, str) and len(o) >= SENTENCE_OPTION_MIN and o[-1:] in ".?!" for o in options):
                report.error(q_where, "insertion de phrase : chaque option est une phrase complète, terminée par un point")
            if text_ok and re.match(r"\s*[.,;:!?]", text[text.find(P6_BLANKS[index]) + len(P6_BLANKS[index]):]):
                report.error(q_where, "insertion de phrase : le trou remplace une phrase entière, il ne doit pas être suivi d'une ponctuation")
        if "evidence" in q and text_ok:
            _check_evidence(q["evidence"], [text], 0, q_where, report)
    if len(insertions) != 1:
        report.error(where, f"un texte Part 6 a exactement une question dont le premier tag est {TAG_SENTENCE_INSERTION!r} (trouvé : {len(insertions)})")

    return len(report.errors) == before


def validate_set(item: object, known_tags: dict[str, dict], where: str, report: Report) -> bool:
    """Contrôle un jeu de documents de la Part 7 (1 à 3 documents) et ses questions."""
    if not isinstance(item, dict):
        report.error(where, "le jeu de documents doit être un objet JSON")
        return False
    if not _check_fields(item, SET_FIELDS, set(), where, report):
        return False
    before = len(report.errors)

    if not isinstance(item["id"], str) or not ID_PATTERNS[7].match(item["id"]):
        report.error(where, f"id invalide : {item['id']!r} (attendu : p7-0001)")
    if item["part"] != 7:
        report.error(where, f"part = {item['part']!r}, mais le fichier est déclaré part 7")

    documents = item["documents"]
    if not isinstance(documents, list) or not 1 <= len(documents) <= P7_DOCS_MAX:
        report.error(where, f"documents doit contenir 1 à {P7_DOCS_MAX} documents")
        return False
    haystacks: list[str] = []
    total_words = 0
    for index, doc in enumerate(documents):
        d_where = f"{where} › document {index + 1}"
        if not isinstance(doc, dict) or not _check_fields(doc, DOCUMENT_FIELDS, TITLE_OPTIONAL, d_where, report):
            if not isinstance(doc, dict):
                report.error(d_where, "le document doit être un objet JSON")
            return False
        if doc["genre"] not in GENRES:
            report.error(d_where, f"genre inconnu : {doc['genre']!r} (attendu : {', '.join(GENRES)})")
        _check_title(doc, d_where, report)
        if not _is_text(doc["text"]):
            report.error(d_where, "text doit être un texte non vide, sans espace en début ou en fin")
            return False
        _check_english(doc["text"], "text", d_where, report)
        if "[[" in doc["text"] or BLANK in doc["text"]:
            report.error(d_where, "un document Part 7 n'a pas de trou")
        words = word_count(doc["text"])
        total_words += words
        if not P7_DOC_WORDS_MIN <= words <= P7_DOC_WORDS_MAX:
            report.error(d_where, f"text fait {words} mots (attendu : {P7_DOC_WORDS_MIN} à {P7_DOC_WORDS_MAX})")
        haystacks.append(doc["text"] + "\n" + doc.get("title", "") if isinstance(doc.get("title", ""), str) else doc["text"])
    if not P7_SET_WORDS_MIN <= total_words <= P7_SET_WORDS_MAX:
        report.error(where, f"les documents font {total_words} mots au total (attendu : {P7_SET_WORDS_MIN} à {P7_SET_WORDS_MAX})")

    multi = len(documents) > 1
    questions = item["questions"]
    if not isinstance(questions, list):
        report.error(where, "questions doit être une liste")
        return False
    if multi and len(questions) != P7_QUESTIONS_MULTI:
        report.error(where, f"un jeu de {len(documents)} documents a exactement {P7_QUESTIONS_MULTI} questions (reçu : {len(questions)})")
    if not multi and not _in_range(len(questions), P7_QUESTIONS_SINGLE):
        report.error(where, f"un document seul a {P7_QUESTIONS_SINGLE[0]} à {P7_QUESTIONS_SINGLE[1]} questions (reçu : {len(questions)})")
    _check_question_ids(questions, item["id"], where, report)

    marker_docs = [i for i, doc in enumerate(documents) if any(m in doc["text"] for m in P7_MARKERS)]
    insertions = 0
    cross_references = 0
    for index, q in enumerate(questions):
        q_where = f"{where} › question {index + 1}"
        if not isinstance(q, dict):
            report.error(q_where, "la question doit être un objet JSON")
            continue
        if not _check_fields(q, PART7_QUESTION_FIELDS, set(), q_where, report):
            continue
        _check_answer_fields(q, 7, known_tags, q_where, report, option_max=P7_OPTION_MAX)
        tags = q["tags"] if isinstance(q["tags"], list) else []
        options = q["options"] if isinstance(q["options"], list) else []
        for option in options:
            _check_english(option, "options", q_where, report)

        stem = q["stem"]
        # Les questions de synonyme se terminent sans ponctuation, comme à l'examen :
        # « The word "secure" in paragraph 3 is closest in meaning to ».
        if not _is_text(stem) or not P7_STEM_MIN <= len(stem) <= P7_STEM_MAX or \
                not (stem[-1] in '?."' or stem.endswith(P7_SYNONYM_ENDING)):
            report.error(q_where, f"stem doit être une question de {P7_STEM_MIN} à {P7_STEM_MAX} caractères, terminée par ? . \" ou « {P7_SYNONYM_ENDING} »")
            stem = ""
        _check_english(stem, "stem", q_where, report)

        found = _check_evidence(q["evidence"], haystacks, 1, q_where, report)

        if TAG_CROSS_REFERENCE in tags:
            cross_references += 1
            if not multi:
                report.error(q_where, f"le tag {TAG_CROSS_REFERENCE!r} est réservé aux jeux de plusieurs documents")
            elif len(found) < 2 or len(set().union(*found)) < 2 or all(f == found[0] and len(f) == 1 for f in found):
                report.error(q_where, "croisement de documents : evidence doit citer au moins deux documents différents")

        if tags[:1] == [TAG_SENTENCE_INSERTION]:
            insertions += 1
            if options != list(P7_MARKERS):
                report.error(q_where, f"insertion de phrase : les options sont exactement {list(P7_MARKERS)}")
            if '"' not in stem:
                report.error(q_where, "insertion de phrase : stem doit citer la phrase à insérer entre guillemets droits")

        if any(tag in tags[:1] for tag in TAGS_QUOTING_THE_TEXT) and stem:
            quoted = re.findall(r'"([^"]+)"', stem)
            if not any(any(phrase.rstrip(".,!?") in text for text in haystacks) for phrase in quoted):
                report.error(q_where, f"{tags[0]} : stem doit citer entre guillemets droits un mot ou une phrase du document")

    if insertions > 1:
        report.error(where, "au plus une question d'insertion de phrase par jeu de documents")
    if insertions == 1:
        text = documents[marker_docs[0]]["text"] if len(marker_docs) == 1 else ""
        positions = [text.find(m) for m in P7_MARKERS]
        if not text or any(text.count(m) != 1 for m in P7_MARKERS) or positions != sorted(positions):
            report.error(where, "insertion de phrase : un seul document doit contenir les repères [1], [2], [3] et [4], une fois chacun, dans cet ordre")
    if insertions == 0 and marker_docs:
        report.error(where, "les repères [1] à [4] sont réservés aux jeux qui ont une question d'insertion de phrase")
    if multi and cross_references == 0:
        report.error(where, f"un jeu de plusieurs documents a au moins une question avec le tag {TAG_CROSS_REFERENCE!r}")

    return len(report.errors) == before


def _stimulus_questions(item: dict, part: int, pool: str, source: str) -> list[dict]:
    """Les questions d'un texte (Part 6) ou d'un jeu de documents (Part 7), enrichies de ce dont
    les contrôles globaux et build.py ont besoin."""
    if part == 6:
        documents = [{"genre": item["genre"], "title": item.get("title"), "text": item["text"]}]
    else:
        documents = [{"genre": d["genre"], "title": d.get("title"), "text": d["text"]} for d in item["documents"]]
    stimulus = {"id": item["id"], "part": part, "pool": pool, "documents": documents, "_source": source}
    return [
        {**q, "part": part, "_pool": pool, "_source": source, "_stimulus": stimulus, "_position": position}
        for position, q in enumerate(item["questions"])
    ]


def validate_file_data(data: object, known_tags: dict[str, dict], source: str, report: Report) -> list[dict]:
    """Contrôle le contenu d'un fichier de lot. Renvoie les questions structurellement valides,
    enrichies de _pool et _source (et de _stimulus pour les Parts 6 et 7) pour les contrôles globaux."""
    if not isinstance(data, dict):
        report.error(source, "le fichier doit être un objet JSON")
        return []
    part = data.get("part")
    if part not in FILE_KEYS:
        report.error(source, f"part non prise en charge : {part!r}")
        return []
    key = FILE_KEYS[part]
    expected = {"part", "pool", key}
    if set(data) != expected:
        report.error(source, f"le fichier doit être un objet avec exactement {sorted(expected)}")
        return []
    pool, items = data["pool"], data[key]
    if pool not in POOLS:
        report.error(source, f"pool inconnu : {pool!r} (attendu : {', '.join(POOLS)})")
        return []
    if not isinstance(items, list) or not items:
        report.error(source, f"{key} doit être une liste non vide")
        return []
    valid = []
    for index, item in enumerate(items):
        item_id = item.get("id") if isinstance(item, dict) else None
        where = f"{source} › {item_id or f'élément n°{index + 1}'}"
        if part == 5:
            if validate_question(item, part, known_tags, where, report):
                valid.append({**item, "_pool": pool, "_source": source})
        elif part == 6:
            if validate_text(item, known_tags, where, report):
                valid.extend(_stimulus_questions(item, part, pool, source))
        elif validate_set(item, known_tags, where, report):
            valid.extend(_stimulus_questions(item, part, pool, source))
    return valid


def stimuli_of(questions: list[dict]) -> list[dict]:
    """Les textes et jeux de documents distincts d'une liste de questions, triés par identifiant."""
    by_id = {q["_stimulus"]["id"]: q["_stimulus"] for q in questions if q.get("_stimulus")}
    return [by_id[key] for key in sorted(by_id)]


def _near_duplicate(a: str, b: str) -> float | None:
    """Taux de ressemblance s'il dépasse le seuil, None sinon."""
    matcher = difflib.SequenceMatcher(None, a, b, autojunk=False)
    if matcher.real_quick_ratio() >= NEAR_DUPLICATE_RATIO and matcher.quick_ratio() >= NEAR_DUPLICATE_RATIO:
        ratio = matcher.ratio()
        if ratio >= NEAR_DUPLICATE_RATIO:
            return ratio
    return None


def check_pool_ranges(questions: list[dict], report: Report) -> None:
    """Chaque identifiant dans la plage de son pool (p5-0142 : entraînement ; p5-9001 : test blanc)."""
    seen: set[str] = set()
    for q in questions:
        unit_id = q["_stimulus"]["id"] if q.get("_stimulus") else q["id"]
        if unit_id in seen:
            continue
        seen.add(unit_id)
        number = int(unit_id.split("-")[1])
        low, high = POOL_ID_RANGES[q["_pool"]]
        if not low <= number <= high:
            report.error(q["_source"], f"{unit_id} : le pool {q['_pool']!r} utilise les numéros {low:04d} à {high:04d}")


def check_duplicates(questions: list[dict], report: Report) -> None:
    """Doublons sur toute la banque, tous pools confondus : une question de test blanc
    ne doit pas non plus exister à l'entraînement."""
    by_id: dict[str, dict] = {}
    for q in questions:
        if q["id"] in by_id:
            report.error(f"{q['_source']} › {q['id']}", f"id déjà utilisé dans {by_id[q['id']]['_source']}")
        else:
            by_id[q["id"]] = q

    # Part 5 : énoncés identiques ou quasi identiques.
    unique = [q for q in by_id.values() if not q.get("_stimulus")]
    stems = [normalize(q["stem"]) for q in unique]
    option_sets: dict[tuple, str] = {}
    for i, q in enumerate(unique):
        where = f"{q['_source']} › {q['id']}"
        # Variété de la révision : seulement au sein d'un même pool.
        key = (q["_pool"], *sorted(o.lower() for o in q["options"]))
        if key in option_sets:
            report.warning(where, f"mêmes 4 options que {option_sets[key]} : peu de variété pour la révision")
        else:
            option_sets[key] = q["id"]
        for j in range(i):
            if stems[i] == stems[j]:
                report.error(where, f"énoncé identique à {unique[j]['id']}")
                continue
            ratio = _near_duplicate(stems[i], stems[j])
            if ratio is not None:
                report.error(where, f"énoncé quasi identique à {unique[j]['id']} ({ratio:.0%} de ressemblance)")

    # Parts 6 et 7 : documents identiques ou quasi identiques.
    documents = [
        (f"{s['_source']} › {s['id']}", s["id"], normalize(doc["text"]))
        for s in stimuli_of(list(by_id.values()))
        for doc in s["documents"]
    ]
    for i, (where, stimulus_id, text) in enumerate(documents):
        for other_where, other_id, other_text in documents[:i]:
            if other_id == stimulus_id:
                continue
            if text == other_text:
                report.error(where, f"document identique à un document de {other_id}")
            else:
                ratio = _near_duplicate(text, other_text)
                if ratio is not None:
                    report.error(where, f"document quasi identique à un document de {other_id} ({ratio:.0%} de ressemblance)")


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
            if part == 5:
                # La Part 5 sert une question à la fois : il en faut assez par compétence.
                for tag, count in sorted(primary.items()):
                    if count < MIN_QUESTIONS_PER_TAG:
                        report.error(where, f"le tag {tag!r} n'a que {count} question(s) : il en faut au moins {MIN_QUESTIONS_PER_TAG} pour varier les révisions")
            for tag, definition in known_tags.items():
                # En Part 6, les tags de grammaire sont permis mais pas tous attendus.
                expected = part in definition["parts"] and (part != 6 or tag == TAG_SENTENCE_INSERTION)
                if expected and min(definition["parts"]) == part and tag not in primary:
                    report.warning(where, f"aucune question n'a {tag!r} pour tag principal")


def find_bank_files(bank_dir: Path = BANK_DIR) -> list[Path]:
    return sorted(bank_dir.rglob("*.json"))


def find_fiche_files(fiches_dir: Path = FICHES_DIR) -> list[Path]:
    return sorted(fiches_dir.glob("*.json")) if fiches_dir.is_dir() else []


def _source_name(path: Path) -> str:
    try:
        return path.resolve().relative_to(CONTENT_DIR).as_posix()
    except ValueError:
        return path.name


def _load_json(path: Path, report: Report) -> object | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        report.error(_source_name(path), f"fichier illisible ou JSON invalide : {exc}")
        return None


def validate_bank(paths: list[Path], known_tags: dict[str, dict] | None = None,
                  report: Report | None = None) -> tuple[Report, list[dict]]:
    """Valide un ensemble de lots. Renvoie le rapport et les questions valides."""
    known_tags = load_tags() if known_tags is None else known_tags
    report = Report() if report is None else report
    questions: list[dict] = []
    for path in paths:
        data = _load_json(path, report)
        if data is not None:
            questions.extend(validate_file_data(data, known_tags, _source_name(path), report))
    check_pool_ranges(questions, report)
    check_duplicates(questions, report)
    check_distributions(questions, known_tags, report)
    return report, questions


# ───────────────────────────── Fiches de cours ─────────────────────────────

def _check_markup(text: str, label: str, where: str, report: Report) -> None:
    """Le seul balisage permis dans une fiche : **mot** pour mettre en avant."""
    if text.count("**") % 2 != 0:
        report.error(where, f"{label} : un ** n'est pas refermé")


def _check_lesson_text(value: object, bounds: tuple[int, int], label: str, where: str, report: Report) -> bool:
    if not _is_text(value) or not _in_range(len(value), bounds):
        size = len(value) if isinstance(value, str) else 0
        report.error(where, f"{label} doit être un texte de {bounds[0]} à {bounds[1]} caractères (reçu : {size})")
        return False
    _check_markup(value, label, where, report)
    return True


def _check_lesson_list(value: object, bounds: tuple[int, int], label: str, where: str, report: Report) -> bool:
    if not isinstance(value, list) or not _in_range(len(value), bounds):
        report.error(where, f"{label} doit être une liste de {bounds[0]} à {bounds[1]} éléments")
        return False
    return True


def validate_lesson_language(content: object, where: str, report: Report) -> bool:
    """Contrôle une fiche dans une langue. Renvoie True si sa structure est complète."""
    if not isinstance(content, dict) or set(content) != LESSON_LANG_FIELDS:
        report.error(where, f"la fiche doit contenir exactement {sorted(LESSON_LANG_FIELDS)}")
        return False
    before = len(report.errors)
    _check_lesson_text(content["title"], LESSON_TITLE, "title", where, report)
    _check_lesson_text(content["summary"], LESSON_SUMMARY, "summary", where, report)

    if _check_lesson_list(content["keyPoints"], LESSON_KEY_POINTS, "keyPoints", where, report):
        for index, point in enumerate(content["keyPoints"]):
            _check_lesson_text(point, LESSON_KEY_POINT, f"keyPoints[{index}]", where, report)

    if _check_lesson_list(content["rules"], LESSON_RULES, "rules", where, report):
        for index, rule in enumerate(content["rules"]):
            label = f"rules[{index}]"
            if not isinstance(rule, dict) or set(rule) != RULE_FIELDS:
                report.error(where, f"{label} doit contenir exactement {sorted(RULE_FIELDS)}")
                continue
            _check_lesson_text(rule["title"], LESSON_RULE_TITLE, f"{label}.title", where, report)
            _check_lesson_text(rule["text"], LESSON_RULE_TEXT, f"{label}.text", where, report)
            if _check_lesson_list(rule["examples"], LESSON_EXAMPLES, f"{label}.examples", where, report):
                for e_index, example in enumerate(rule["examples"]):
                    e_label = f"{label}.examples[{e_index}]"
                    if not isinstance(example, dict) or set(example) != EXAMPLE_FIELDS:
                        report.error(where, f"{e_label} doit contenir exactement {sorted(EXAMPLE_FIELDS)}")
                        continue
                    _check_lesson_text(example["en"], LESSON_EXAMPLE_EN, f"{e_label}.en", where, report)
                    _check_lesson_text(example["note"], LESSON_EXAMPLE_NOTE, f"{e_label}.note", where, report)

    if _check_lesson_list(content["traps"], LESSON_TRAPS, "traps", where, report):
        for index, trap in enumerate(content["traps"]):
            label = f"traps[{index}]"
            if not isinstance(trap, dict) or set(trap) != TRAP_FIELDS:
                report.error(where, f"{label} doit contenir exactement {sorted(TRAP_FIELDS)}")
                continue
            _check_lesson_text(trap["title"], LESSON_RULE_TITLE, f"{label}.title", where, report)
            _check_lesson_text(trap["text"], LESSON_TRAP_TEXT, f"{label}.text", where, report)
    return len(report.errors) == before


def validate_lesson(data: object, known_tags: dict[str, dict], source: str, report: Report,
                    file_stem: str | None = None) -> bool:
    """Contrôle une fiche de cours : une compétence, deux langues de même structure."""
    if not isinstance(data, dict) or set(data) != LESSON_FIELDS:
        report.error(source, f"la fiche doit être un objet avec exactement {sorted(LESSON_FIELDS)}")
        return False
    before = len(report.errors)
    tag = data["tag"]
    if tag not in known_tags:
        report.error(source, f"tag inconnu de la taxonomie : {tag!r}")
    elif file_stem is not None and file_stem != tag:
        report.error(source, f"le fichier d'une fiche porte le nom de son tag : {tag}.json")

    complete = [validate_lesson_language(data[lang], f"{source} › {lang}", report) for lang in LANGS]
    if all(complete):
        fr, en = data["fr"], data["en"]
        if fr["summary"] == en["summary"]:
            report.error(source, "les versions fr et en sont identiques : l'une des deux n'est pas traduite")
        shape_fr = (len(fr["keyPoints"]), len(fr["traps"]), [len(r["examples"]) for r in fr["rules"]])
        shape_en = (len(en["keyPoints"]), len(en["traps"]), [len(r["examples"]) for r in en["rules"]])
        if shape_fr != shape_en:
            report.error(source, "les versions fr et en n'ont pas la même structure (points clés, règles, exemples, pièges)")
        else:
            for r_index, (rule_fr, rule_en) in enumerate(zip(fr["rules"], en["rules"])):
                for e_index, (ex_fr, ex_en) in enumerate(zip(rule_fr["examples"], rule_en["examples"])):
                    if ex_fr["en"] != ex_en["en"]:
                        report.error(source, f"rules[{r_index}].examples[{e_index}].en : la phrase d'exemple (en anglais) doit être la même dans les deux versions")
    return len(report.errors) == before


def validate_lessons(paths: list[Path], known_tags: dict[str, dict], report: Report) -> list[dict]:
    """Valide les fiches. Renvoie les fiches valides, dans l'ordre de la taxonomie."""
    lessons: dict[str, dict] = {}
    for path in paths:
        data = _load_json(path, report)
        if data is None:
            continue
        source = _source_name(path)
        if validate_lesson(data, known_tags, source, report, file_stem=path.stem):
            if data["tag"] in lessons:
                report.error(source, f"une fiche existe déjà pour le tag {data['tag']!r}")
            else:
                lessons[data["tag"]] = data
    return [lessons[tag] for tag in known_tags if tag in lessons]


# ───────────────────────────── Chemin de leçons ─────────────────────────────

def training_units(questions: list[dict]) -> list[dict]:
    """Les unités servies à l'entraînement : une question (Part 5), un texte (Part 6) ou un jeu
    de documents (Part 7). Pour chacune : sa part, tous ses tags, et ses tags principaux."""
    units: dict[str, dict] = {}
    for q in questions:
        if q["_pool"] != "training":
            continue
        unit_id = q["_stimulus"]["id"] if q.get("_stimulus") else q["id"]
        unit = units.setdefault(unit_id, {"id": unit_id, "part": q["part"], "tags": set(), "primary": set()})
        unit["tags"].update(q["tags"])
        unit["primary"].add(q["tags"][0])
    return list(units.values())


def validate_path(data: object, known_tags: dict[str, dict], lessons: list[dict], questions: list[dict],
                  report: Report, source: str = "lessons/path.json") -> dict | None:
    """Contrôle le chemin de leçons. Renvoie le chemin tel que l'app l'embarque : chaque étape
    porte la liste de ses tags (une leçon : son tag ; un bilan : les tags des leçons de l'unité)."""
    if not isinstance(data, dict) or set(data) != {"units"} or not isinstance(data["units"], list) or not data["units"]:
        report.error(source, "le chemin doit être un objet avec une liste « units » non vide")
        return None
    before = len(report.errors)
    lesson_tags = {lesson["tag"] for lesson in lessons}
    units_available = training_units(questions)
    seen_ids: set[str] = set()
    seen_lesson_tags: set[str] = set()
    result = []

    def check_id(value: object, where: str) -> None:
        if not isinstance(value, str) or not STEP_ID_PATTERN.match(value):
            report.error(where, f"id invalide : {value!r} (minuscules, chiffres et tirets)")
        elif value in seen_ids:
            report.error(where, f"id déjà utilisé dans le chemin : {value!r}")
        else:
            seen_ids.add(value)

    for u_index, unit in enumerate(data["units"]):
        where = f"{source} › unité n°{u_index + 1}"
        if not isinstance(unit, dict) or set(unit) != PATH_UNIT_FIELDS:
            report.error(where, f"l'unité doit contenir exactement {sorted(PATH_UNIT_FIELDS)}")
            continue
        check_id(unit["id"], where)
        for lang in LANGS:
            if not _is_text(unit[lang], 3) or len(unit[lang]) > 60:
                report.error(where, f"{lang} : titre de 3 à 60 caractères attendu")
        steps = unit["steps"]
        if not isinstance(steps, list) or not steps:
            report.error(where, "steps doit être une liste non vide")
            continue
        unit_tags = [s.get("tag") for s in steps if isinstance(s, dict) and s.get("kind") == "lesson"]
        out_steps = []
        for s_index, step in enumerate(steps):
            s_where = f"{where} › étape n°{s_index + 1}"
            kind = step.get("kind") if isinstance(step, dict) else None
            if kind not in STEP_KINDS:
                report.error(s_where, f"kind doit valoir {' ou '.join(STEP_KINDS)}")
                continue
            if set(step) != STEP_FIELDS[kind]:
                report.error(s_where, f"une étape « {kind} » contient exactement {sorted(STEP_FIELDS[kind])}")
                continue
            check_id(step["id"], s_where)
            part, count = step["part"], step["units"]
            if part not in FILE_KEYS:
                report.error(s_where, f"part non prise en charge : {part!r}")
                continue
            if not isinstance(count, int) or isinstance(count, bool) or not _in_range(count, STEP_UNITS):
                report.error(s_where, f"units doit être un entier de {STEP_UNITS[0]} à {STEP_UNITS[1]}")
                continue
            if kind == "lesson":
                tag = step["tag"]
                if tag not in known_tags:
                    report.error(s_where, f"tag inconnu de la taxonomie : {tag!r}")
                    continue
                if part not in known_tags[tag]["parts"]:
                    report.error(s_where, f"le tag {tag!r} n'est pas prévu pour la part {part}")
                if tag not in lesson_tags:
                    report.error(s_where, f"aucune fiche de cours pour le tag {tag!r} (content/lessons/fiches/{tag}.json)")
                if tag in seen_lesson_tags:
                    report.error(s_where, f"le tag {tag!r} a déjà une leçon dans le chemin")
                seen_lesson_tags.add(tag)
                tags = [tag]
                available = sum(1 for u in units_available if u["part"] == part and tag in u["tags"])
            else:
                tags = [t for t in unit_tags if t in known_tags]
                if not tags:
                    report.error(s_where, "un bilan suit au moins une leçon dans la même unité")
                available = sum(1 for u in units_available if u["part"] == part and u["primary"] & set(tags))
            if available < count:
                report.error(s_where, f"{count} exercice(s) demandé(s) en Part {part}, mais la banque d'entraînement n'en a que {available} pour {tags}")
            out_steps.append({"id": step["id"], "kind": kind, "tags": tags, "part": part, "units": count})
        result.append({"id": unit["id"], "fr": unit["fr"], "en": unit["en"], "steps": out_steps})

    for tag in lesson_tags - seen_lesson_tags:
        report.warning(source, f"la fiche {tag!r} n'a pas de leçon dans le chemin")
    return {"units": result} if len(report.errors) == before else None


def validate_mock_test(data: object, questions: list[dict], used: set[str], report: Report, source: str) -> dict | None:
    """Un test blanc : la liste ordonnée de ses unités (question Part 5, texte Part 6, jeu Part 7),
    toutes du pool mock, et sa durée. Une unité n'appartient qu'à un seul test."""
    if not isinstance(data, dict) or set(data) != MOCK_TEST_FIELDS:
        report.error(source, f"un test blanc contient exactement {sorted(MOCK_TEST_FIELDS)}")
        return None
    before = len(report.errors)
    if not isinstance(data["id"], str) or not STEP_ID_PATTERN.match(data["id"]):
        report.error(source, f"id invalide : {data['id']!r}")
    if data["section"] not in MOCK_SECTIONS:
        report.error(source, f"section non prise en charge : {data['section']!r}")
    for lang in LANGS:
        if not _is_text(data[lang], 3) or len(data[lang]) > 60:
            report.error(source, f"{lang} : titre de 3 à 60 caractères attendu")
    minutes = data["minutes"]
    if not isinstance(minutes, int) or isinstance(minutes, bool) or not _in_range(minutes, MOCK_MINUTES):
        report.error(source, f"minutes doit être un entier de {MOCK_MINUTES[0]} à {MOCK_MINUTES[1]}")
    units: dict[str, dict] = {}
    for q in questions:
        unit_id = q["_stimulus"]["id"] if q.get("_stimulus") else q["id"]
        units.setdefault(unit_id, {"pool": q["_pool"], "part": q["part"], "count": 0})["count"] += 1
    listed = data["units"]
    if not isinstance(listed, list) or not listed:
        report.error(source, "units doit être une liste non vide")
        return None
    parts = []
    count = 0
    for unit_id in listed:
        unit = units.get(unit_id)
        if unit is None:
            report.error(source, f"unité inconnue : {unit_id!r}")
            continue
        if unit["pool"] != "mock":
            report.error(source, f"{unit_id} n'est pas du pool mock (pool {unit['pool']!r})")
        if unit_id in used:
            report.error(source, f"{unit_id} figure déjà dans un test blanc")
        used.add(unit_id)
        parts.append(unit["part"])
        count += unit["count"]
    if parts != sorted(parts):
        report.error(source, "les unités suivent l'ordre de l'examen : Part 5, puis 6, puis 7")
    if len(report.errors) != before:
        return None
    return {**data, "questionCount": count}


def validate_mock_tests(paths: list[Path], questions: list[dict], report: Report) -> list[dict]:
    used: set[str] = set()
    tests = []
    for path in paths:
        data = _load_json(path, report)
        if data is not None:
            test = validate_mock_test(data, questions, used, report, _source_name(path))
            if test is not None:
                tests.append(test)
    ids = [t["id"] for t in tests]
    for duplicate in {i for i in ids if ids.count(i) > 1}:
        report.error("mock", f"id de test blanc en double : {duplicate!r}")
    mock_units = {q["_stimulus"]["id"] if q.get("_stimulus") else q["id"] for q in questions if q["_pool"] == "mock"}
    for unit_id in sorted(mock_units - used):
        report.warning("mock", f"{unit_id} est du pool mock mais n'appartient à aucun test blanc")
    return tests


def validate_content(bank_paths: list[Path] | None = None, fiche_paths: list[Path] | None = None,
                     path_file: Path | None = PATH_FILE, known_tags: dict[str, dict] | None = None,
                     mock_paths: list[Path] | None = None) -> Content:
    """Valide tout le contenu : banque, fiches, chemin, tests blancs."""
    known_tags = load_tags() if known_tags is None else known_tags
    report, questions = validate_bank(find_bank_files() if bank_paths is None else bank_paths, known_tags)
    lessons = validate_lessons(find_fiche_files() if fiche_paths is None else fiche_paths, known_tags, report)
    with_lesson = {lesson["tag"] for lesson in lessons}
    for tag in known_tags:
        if tag not in with_lesson:
            report.warning("lessons/fiches", f"pas encore de fiche de cours pour le tag {tag!r}")
    path = None
    if path_file is not None and path_file.exists():
        data = _load_json(path_file, report)
        if data is not None:
            path = validate_path(data, known_tags, lessons, questions, report, _source_name(path_file))
    else:
        report.warning("lessons/path.json", "pas de chemin de leçons")
    if mock_paths is None:
        mock_paths = sorted(MOCK_DIR.glob("*.json")) if MOCK_DIR.is_dir() else []
    mock_tests = validate_mock_tests(mock_paths, questions, report)
    return Content(report, questions, lessons, path, mock_tests)


def summarize(questions: list[dict], lessons: list[dict] | None = None, path: dict | None = None,
              mock_tests: list[dict] | None = None) -> str:
    lines = [f"{len(questions)} question(s) valide(s)"]
    by_part: dict[int, list[dict]] = defaultdict(list)
    for q in questions:
        by_part[q["part"]].append(q)
    for part, group in sorted(by_part.items()):
        answers = Counter(q["answer"] for q in group)
        levels = Counter(q["difficulty"] for q in group)
        tags = Counter(q["tags"][0] for q in group)
        stimuli = stimuli_of(group)
        units = f", {len(stimuli)} {'texte(s)' if part == 6 else 'jeu(x) de documents'}" if stimuli else ""
        lines.append(f"Part {part} : {len(group)} question(s){units}")
        lines.append("  réponses   : " + "  ".join(f"{l}={answers[l]}" for l in LETTERS))
        lines.append("  difficulté : " + "  ".join(f"{d}={levels[d]}" for d in DIFFICULTIES))
        lines.append("  tags       : " + ", ".join(f"{t} ({n})" for t, n in sorted(tags.items())))
    if lessons is not None:
        lines.append(f"{len(lessons)} fiche(s) de cours")
    if path is not None:
        steps = sum(len(unit["steps"]) for unit in path["units"])
        lines.append(f"Chemin : {len(path['units'])} unité(s), {steps} étape(s)")
    if mock_tests:
        lines.append("Tests blancs : " + ", ".join(f"{t['id']} ({t['questionCount']} questions, {t['minutes']} min)" for t in mock_tests))
    return "\n".join(lines)


def _is_fiche(path: Path) -> bool:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return False
    return isinstance(data, dict) and "tag" in data


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Valide le contenu de TOEIC 900.")
    parser.add_argument("files", nargs="*", type=Path, help="lots ou fiches à valider (par défaut : tout content/)")
    args = parser.parse_args(argv)

    if args.files:
        # Fichiers isolés : pas de contrôle du chemin, qui a besoin de toute la banque.
        tags = load_tags()
        fiches = [p for p in args.files if _is_fiche(p)]
        lots = [p for p in args.files if p not in fiches]
        report, questions = validate_bank(lots, tags)
        lessons = validate_lessons(fiches, tags, report)
        summary = summarize(questions, lessons if fiches else None)
    else:
        content = validate_content()
        report = content.report
        summary = summarize(content.questions, content.lessons, content.path, content.mock_tests)
        if not content.questions:
            print("Aucune question valide dans content/bank/.")
            return 1

    for issue in report.issues:
        print(issue)
    print(summary)
    print(f"{len(report.errors)} erreur(s), {len(report.warnings)} avertissement(s)")
    return 0 if report.ok else 1


if __name__ == "__main__":
    # Sous Windows, la console n'est pas toujours en UTF-8 : on évite les erreurs d'affichage.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
