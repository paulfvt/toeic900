"""Tests de validate.py et build.py pour les Parts 6 et 7, les fiches de cours et le chemin.

Chaque test part d'un exemple valide du dépôt (content/examples/, fiche modèle) et l'abîme
sur un seul point.

Lancer depuis la racine du dépôt :
    python -m unittest discover -s content/tools/tests -v
"""
import copy
import json
import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

import build  # noqa: E402
import validate  # noqa: E402
from validate import Report  # noqa: E402

EXAMPLES_DIR = validate.CONTENT_DIR / "examples"
TAGS = validate.load_tags()


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


PART6 = load(EXAMPLES_DIR / "part6-exemple.json")
PART7 = load(EXAMPLES_DIR / "part7-exemple.json")
FICHE = load(validate.FICHES_DIR / "participle-adjectives.json")


def errors_of(report: Report) -> list[str]:
    return [issue.message for issue in report.errors]


class ReadingTestCase(unittest.TestCase):
    def check_file(self, data: dict) -> tuple[Report, list[dict]]:
        report = Report()
        questions = validate.validate_file_data(data, TAGS, "lot.json", report)
        validate.check_duplicates(questions, report)
        return report, questions

    def assertRejected(self, data: dict, fragment: str) -> None:
        report, _ = self.check_file(data)
        self.assertTrue(
            any(fragment in message for message in errors_of(report)),
            f"erreur attendue contenant {fragment!r}, obtenu : {errors_of(report)}",
        )


class Part6Test(ReadingTestCase):
    def setUp(self):
        self.data = copy.deepcopy(PART6)
        self.text = self.data["texts"][0]

    def test_example_is_valid(self):
        report, questions = self.check_file(self.data)
        self.assertEqual([], [str(i) for i in report.issues])
        self.assertEqual(4, len(questions))
        self.assertEqual(["p6-9001"] * 4, [q["_stimulus"]["id"] for q in questions])
        self.assertEqual([0, 1, 2, 3], [q["_position"] for q in questions])
        self.assertEqual({6}, {q["part"] for q in questions})

    def test_a_missing_blank(self):
        self.text["text"] = self.text["text"].replace("[[3]], the", "In addition, the")
        self.assertRejected(self.data, "exactement 4 trous")

    def test_blanks_out_of_order(self):
        swapped = self.text["text"].replace("[[1]]", "[[x]]").replace("[[2]]", "[[1]]").replace("[[x]]", "[[2]]")
        self.text["text"] = swapped
        self.assertRejected(self.data, "exactement 4 trous")

    def test_part5_style_blank_is_rejected(self):
        self.text["text"] = self.text["text"].replace("[[1]]", "-------")
        self.assertRejected(self.data, "exactement 4 trous")

    def test_exactly_four_questions(self):
        self.text["questions"].pop()
        self.assertRejected(self.data, "exactement 4 questions")

    def test_question_ids_follow_the_text_id(self):
        self.text["questions"][1]["id"] = "p6-9001-5"
        self.assertRejected(self.data, "doit avoir l'id 'p6-9001-2'")

    def test_exactly_one_sentence_insertion(self):
        self.text["questions"][1]["tags"] = ["vocabulary-in-context"]
        self.assertRejected(self.data, "exactement une question")

    def test_insertion_options_are_full_sentences(self):
        self.text["questions"][1]["options"][0] = "The sauna"
        self.assertRejected(self.data, "phrase complète")

    def test_insertion_blank_replaces_a_whole_sentence(self):
        self.text["text"] = self.text["text"].replace("[[2]] Simply", "[[2]]. Simply")
        self.assertRejected(self.data, "phrase entière")

    def test_word_options_stay_short(self):
        self.text["questions"][0]["options"][0] = "x" * 61
        self.assertRejected(self.data, "dépasse 60 caractères")

    def test_evidence_must_be_quoted_from_the_text(self):
        self.text["questions"][2]["evidence"] = ["the fee will be cut by 20 percent"]
        self.assertRejected(self.data, "ne se retrouve pas mot pour mot")

    def test_evidence_is_optional(self):
        self.assertNotIn("evidence", self.text["questions"][0])

    def test_reading_tags_are_not_allowed(self):
        self.text["questions"][0]["tags"] = ["detail"]
        self.assertRejected(self.data, "n'est pas prévu pour la part 6")

    def test_curly_quotes_are_rejected(self):
        self.text["text"] = self.text["text"].replace("We know", "We’ve heard")
        self.assertRejected(self.data, "guillemets et apostrophes droits")

    def test_text_length(self):
        self.text["text"] = "Dear members, the pool is [[1]]. [[2]] [[3]], thanks. See [[4]] soon."
        self.assertRejected(self.data, "mots (attendu")

    def test_unknown_genre(self):
        self.text["genre"] = "poem"
        self.assertRejected(self.data, "genre inconnu")

    def test_wrong_file_key(self):
        self.assertRejected({"part": 6, "pool": "training", "questions": self.data["texts"]}, "exactement")


class Part7Test(ReadingTestCase):
    def setUp(self):
        self.data = copy.deepcopy(PART7)
        self.single, self.double = self.data["sets"]

    def test_examples_are_valid(self):
        report, questions = self.check_file(self.data)
        self.assertEqual([], [str(i) for i in report.issues])
        self.assertEqual(8, len(questions))
        self.assertEqual(2, len(validate.stimuli_of(questions)))
        self.assertEqual(2, len(validate.stimuli_of(questions)[1]["documents"]))

    def test_single_document_question_count(self):
        self.single["questions"] = self.single["questions"][:1]
        self.assertRejected(self.data, "un document seul a 2 à 4 questions")

    def test_multiple_documents_have_five_questions(self):
        self.double["questions"].pop()
        self.assertRejected(self.data, "exactement 5 questions")

    def test_evidence_is_required(self):
        del self.single["questions"][0]["evidence"]
        self.assertRejected(self.data, "champs manquants")
        self.setUp()
        self.single["questions"][0]["evidence"] = []
        self.assertRejected(self.data, "evidence doit être une liste de 1 à 3")

    def test_evidence_must_be_quoted_from_a_document(self):
        self.single["questions"][1]["evidence"] = ["must contact the management office"]
        self.assertRejected(self.data, "ne se retrouve pas mot pour mot")

    def test_cross_reference_needs_several_documents(self):
        self.single["questions"][2]["tags"] = ["cross-reference"]
        self.assertRejected(self.data, "réservé aux jeux de plusieurs documents")

    def test_cross_reference_evidence_spans_two_documents(self):
        self.double["questions"][2]["evidence"] = ["I would also like to add the workshop on hiring", "I would rather not drive."]
        self.assertRejected(self.data, "au moins deux documents différents")

    def test_multiple_documents_need_a_cross_reference_question(self):
        self.double["questions"][2]["tags"] = ["detail"]
        self.double["questions"][3]["tags"] = ["inference"]
        self.assertRejected(self.data, "au moins une question avec le tag 'cross-reference'")

    def test_synonym_question_quotes_a_word_of_the_document(self):
        self.double["questions"][4]["stem"] = 'In the advertisement, the word "guarantee" in the last sentence is closest in meaning to'
        self.assertRejected(self.data, "doit citer entre guillemets droits")

    def test_stem_ending(self):
        self.single["questions"][0]["stem"] = "The purpose of the notice"
        self.assertRejected(self.data, "stem doit être une question")

    def test_sentence_insertion(self):
        question = self.single["questions"][2]
        question["tags"] = ["sentence-insertion"]
        question["stem"] = ('In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? '
                            '"This schedule may change."')
        self.assertRejected(self.data, "les options sont exactement")
        question["options"] = ["[1]", "[2]", "[3]", "[4]"]
        self.assertRejected(self.data, "un seul document doit contenir les repères")
        doc = self.single["documents"][0]
        doc["text"] = doc["text"].replace("Carver Lift", "[1] Carver Lift").replace("Tenants who", "[2] Tenants who") \
            .replace("Waiting times", "[3] Waiting times").replace("Thank you", "[4] Thank you")
        report, _ = self.check_file(self.data)
        self.assertEqual([], errors_of(report))

    def test_markers_without_an_insertion_question(self):
        doc = self.single["documents"][0]
        doc["text"] = doc["text"].replace("Carver Lift", "[1] Carver Lift")
        self.assertRejected(self.data, "réservés aux jeux qui ont une question d'insertion")

    def test_reading_tags_only(self):
        self.single["questions"][0]["tags"] = ["verb-tense"]
        self.assertRejected(self.data, "n'est pas prévu pour la part 7")

    def test_at_most_three_documents(self):
        self.double["documents"] = self.double["documents"] * 2
        self.assertRejected(self.data, "1 à 3 documents")

    def test_duplicate_documents_across_sets(self):
        twin = copy.deepcopy(self.single)
        twin["id"] = "p7-9003"
        for index, question in enumerate(twin["questions"]):
            question["id"] = f"p7-9003-{index + 1}"
        self.data["sets"].append(twin)
        self.assertRejected(self.data, "document identique")

    def test_identical_stems_are_fine_in_part7(self):
        self.double["questions"][0]["stem"] = self.single["questions"][0]["stem"]
        report, _ = self.check_file(self.data)
        self.assertFalse(any("énoncé" in message for message in errors_of(report)))


class LessonTest(unittest.TestCase):
    def setUp(self):
        self.fiche = copy.deepcopy(FICHE)

    def check(self, file_stem: str = "participle-adjectives") -> Report:
        report = Report()
        validate.validate_lesson(self.fiche, TAGS, "fiche.json", report, file_stem=file_stem)
        return report

    def assertRejected(self, fragment: str, **kwargs) -> None:
        messages = errors_of(self.check(**kwargs))
        self.assertTrue(any(fragment in m for m in messages), f"erreur attendue contenant {fragment!r}, obtenu : {messages}")

    def test_model_is_valid(self):
        self.assertEqual([], [str(i) for i in self.check().issues])

    def test_unknown_tag(self):
        self.fiche["tag"] = "time-travel"
        self.assertRejected("tag inconnu")

    def test_file_is_named_after_its_tag(self):
        self.assertRejected("porte le nom de son tag", file_stem="participes")

    def test_both_languages_are_required(self):
        del self.fiche["en"]
        self.assertRejected("exactement ['en', 'fr', 'tag']")

    def test_missing_section(self):
        del self.fiche["fr"]["traps"]
        self.assertRejected("la fiche doit contenir exactement")

    def test_too_few_rules(self):
        self.fiche["fr"]["rules"] = self.fiche["fr"]["rules"][:1]
        self.assertRejected("rules doit être une liste de 2 à 5")

    def test_languages_must_have_the_same_structure(self):
        self.fiche["en"]["rules"][0]["examples"].pop()
        self.assertRejected("pas la même structure")

    def test_example_sentence_is_the_same_in_both_languages(self):
        self.fiche["fr"]["rules"][0]["examples"][0]["en"] = "Les chiffres étaient **décevants**."
        self.assertRejected("doit être la même dans les deux versions")

    def test_untranslated_version(self):
        self.fiche["en"] = copy.deepcopy(self.fiche["fr"])
        self.assertRejected("identiques")

    def test_unclosed_markup(self):
        self.fiche["fr"]["keyPoints"][0] = "**-ing = actif : la chose provoque l'émotion."
        self.assertRejected("n'est pas refermé")

    def test_summary_length(self):
        self.fiche["fr"]["summary"] = "Trop court."
        self.assertRejected("summary doit être un texte de 40 à 400")


def bank_questions() -> list[dict]:
    """Une petite banque : 6 questions Part 5 sur deux tags, plus les exemples Part 6 et 7."""
    report = Report()
    questions = []
    for n in range(6):
        tag = ("participle-adjectives", "verb-tense")[n % 2]
        questions.append({"id": f"p5-{n:04d}", "part": 5, "tags": [tag], "_pool": "training", "_source": "lot.json"})
    questions += validate.validate_file_data(copy.deepcopy(PART6), TAGS, "p6.json", report)
    questions += validate.validate_file_data(copy.deepcopy(PART7), TAGS, "p7.json", report)
    assert report.ok
    return questions


def make_path() -> dict:
    return {
        "units": [
            {
                "id": "u1",
                "fr": "Formes verbales",
                "en": "Verb forms",
                "steps": [
                    {"id": "l-participle-adjectives", "kind": "lesson", "tag": "participle-adjectives", "part": 5, "units": 3},
                    {"id": "r-u1", "kind": "review", "part": 5, "units": 3},
                ],
            },
        ],
    }


class PathTest(unittest.TestCase):
    def setUp(self):
        self.path = make_path()
        self.questions = bank_questions()

    def check(self, lessons: list[dict] | None = None) -> tuple[Report, dict | None]:
        report = Report()
        result = validate.validate_path(self.path, TAGS, [FICHE] if lessons is None else lessons, self.questions, report)
        return report, result

    def assertRejected(self, fragment: str, **kwargs) -> None:
        report, result = self.check(**kwargs)
        self.assertIsNone(result)
        self.assertTrue(any(fragment in m for m in errors_of(report)), f"erreur attendue contenant {fragment!r}, obtenu : {errors_of(report)}")

    def test_valid_path(self):
        report, result = self.check()
        self.assertEqual([], errors_of(report))
        steps = result["units"][0]["steps"]
        self.assertEqual({"id": "l-participle-adjectives", "kind": "lesson", "tags": ["participle-adjectives"], "part": 5, "units": 3}, steps[0])
        # Le bilan porte les tags des leçons de son unité.
        self.assertEqual(["participle-adjectives"], steps[1]["tags"])

    def test_a_lesson_needs_its_fiche(self):
        self.assertRejected("aucune fiche de cours", lessons=[])

    def test_a_lesson_needs_enough_exercises(self):
        self.path["units"][0]["steps"][0]["units"] = 4
        self.assertRejected("la banque d'entraînement n'en a que 3")

    def test_reading_units_are_counted_per_document_set(self):
        # « detail » apparaît dans deux jeux de documents de l'exemple Part 7.
        fiche = {**copy.deepcopy(FICHE), "tag": "detail"}
        self.path["units"].append({"id": "u2", "fr": "Lecture", "en": "Reading", "steps": [
            {"id": "l-detail", "kind": "lesson", "tag": "detail", "part": 7, "units": 2},
        ]})
        report, result = self.check(lessons=[FICHE, fiche])
        self.assertEqual([], errors_of(report))
        self.path["units"][1]["steps"][0]["units"] = 3
        self.assertRejected("n'en a que 2", lessons=[FICHE, fiche])

    def test_step_ids_are_unique(self):
        self.path["units"][0]["steps"][1]["id"] = "l-participle-adjectives"
        self.assertRejected("id déjà utilisé")

    def test_step_id_format(self):
        self.path["units"][0]["steps"][0]["id"] = "Leçon 1"
        self.assertRejected("id invalide")

    def test_unknown_kind(self):
        self.path["units"][0]["steps"][0]["kind"] = "quiz"
        self.assertRejected("kind doit valoir")

    def test_tag_must_fit_the_part(self):
        self.path["units"][0]["steps"][0]["part"] = 7
        self.assertRejected("n'est pas prévu pour la part 7")

    def test_one_lesson_per_tag(self):
        duplicate = dict(self.path["units"][0]["steps"][0], id="l-again")
        self.path["units"][0]["steps"].insert(1, duplicate)
        self.assertRejected("a déjà une leçon")

    def test_a_review_follows_lessons(self):
        self.path["units"][0]["steps"] = [self.path["units"][0]["steps"][1]]
        self.assertRejected("un bilan suit au moins une leçon")


class BundleTest(unittest.TestCase):
    def setUp(self):
        report = Report()
        self.questions = validate.validate_file_data(copy.deepcopy(PART6), TAGS, "p6.json", report)
        self.questions += validate.validate_file_data(copy.deepcopy(PART7), TAGS, "p7.json", report)
        self.assertTrue(report.ok)

    def test_bundle_carries_stimuli_lessons_and_path(self):
        path = {"units": [{"id": "u1", "fr": "Unité", "en": "Unit", "steps": []}]}
        bundle = build.build_bundle(self.questions, TAGS, [FICHE], path)
        self.assertEqual(build.FORMAT_VERSION, bundle["format"])
        self.assertEqual(["p6-9001", "p7-9001", "p7-9002"], [s["id"] for s in bundle["stimuli"]])
        self.assertEqual(2, len(bundle["stimuli"][2]["documents"]))
        self.assertEqual({"genre", "title", "text"}, set(bundle["stimuli"][0]["documents"][0]))
        self.assertIsNone(bundle["stimuli"][0]["documents"][0]["title"])
        self.assertEqual(["participle-adjectives"], [lesson["tag"] for lesson in bundle["lessons"]])
        self.assertEqual(path, bundle["path"])

    def test_bundle_questions_point_to_their_stimulus(self):
        bundle = build.build_bundle(self.questions, TAGS)
        by_id = {q["id"]: q for q in bundle["questions"]}
        blank = by_id["p6-9001-3"]
        self.assertEqual(("p6-9001", 2, ""), (blank["stimulusId"], blank["position"], blank["stem"]))
        self.assertEqual(["the monthly fee for March will be reduced by 20 percent"], blank["evidence"])
        self.assertEqual([], by_id["p6-9001-1"]["evidence"])
        self.assertEqual("p7-9002", by_id["p7-9002-5"]["stimulusId"])
        self.assertFalse(any(key.startswith("_") for q in bundle["questions"] for key in q))
        self.assertEqual({"units": []}, bundle["path"])

    def test_bundle_version_changes_with_a_document(self):
        first = build.build_bundle(self.questions, TAGS)
        self.assertEqual(first, build.build_bundle(list(reversed(self.questions)), TAGS))
        changed = copy.deepcopy(self.questions)
        changed[0]["_stimulus"]["documents"][0]["text"] += " Thank you."
        self.assertNotEqual(first["version"], build.build_bundle(changed, TAGS)["version"])
        self.assertNotEqual(first["version"], build.build_bundle(self.questions, TAGS, [FICHE])["version"])


class RepositoryContentTest(unittest.TestCase):
    """Contrôles sur le vrai contenu du dépôt : banque, fiches et chemin ensemble."""

    @classmethod
    def setUpClass(cls):
        cls.content = validate.validate_content()

    def test_content_has_no_error(self):
        self.assertEqual([], [str(issue) for issue in self.content.report.errors])

    def test_examples_of_the_guide_are_valid(self):
        report, questions = validate.validate_bank(sorted(EXAMPLES_DIR.glob("*.json")), TAGS)
        self.assertEqual([], [str(issue) for issue in report.errors])
        self.assertEqual(12, len(questions))

    def test_examples_are_not_part_of_the_bank(self):
        self.assertFalse(any("-90" in q["id"] for q in self.content.questions))

    def test_schemas_match_the_validator(self):
        schemas = validate.CONTENT_DIR / "schemas"
        part6 = load(schemas / "part6.schema.json")["$defs"]
        self.assertEqual(validate.TEXT_FIELDS, set(part6["text"]["required"]))
        self.assertEqual(validate.TEXT_FIELDS | validate.TITLE_OPTIONAL, set(part6["text"]["properties"]))
        self.assertEqual(validate.PART6_QUESTION_FIELDS, set(part6["question"]["required"]))
        self.assertEqual(validate.PART6_QUESTION_FIELDS | validate.PART6_QUESTION_OPTIONAL, set(part6["question"]["properties"]))
        self.assertEqual(list(validate.GENRES), part6["genre"]["enum"])
        part7 = load(schemas / "part7.schema.json")["$defs"]
        self.assertEqual(validate.SET_FIELDS, set(part7["set"]["required"]))
        self.assertEqual(validate.DOCUMENT_FIELDS, set(part7["document"]["required"]))
        self.assertEqual(validate.PART7_QUESTION_FIELDS, set(part7["question"]["required"]))
        lesson = load(schemas / "lesson.schema.json")
        self.assertEqual(validate.LESSON_FIELDS, set(lesson["required"]))
        self.assertEqual(validate.LESSON_LANG_FIELDS, set(lesson["$defs"]["content"]["required"]))
        path = load(schemas / "path.schema.json")["$defs"]
        self.assertEqual(validate.STEP_FIELDS["lesson"], set(path["lesson"]["required"]))
        self.assertEqual(validate.STEP_FIELDS["review"], set(path["review"]["required"]))


if __name__ == "__main__":
    unittest.main()
