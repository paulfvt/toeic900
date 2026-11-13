"""Tests de validate.py et build.py.

Lancer depuis la racine du dépôt :
    python -m unittest discover -s content/tools/tests -v
"""
import copy
import json
import random
import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

import build  # noqa: E402
import validate  # noqa: E402
from validate import Report  # noqa: E402

TAGS = {
    "verb-tense": {"id": "verb-tense", "parts": [5, 6], "group": "grammar", "fr": "Temps", "en": "Tenses"},
    "pronouns": {"id": "pronouns", "parts": [5], "group": "grammar", "fr": "Pronoms", "en": "Pronouns"},
    "inference": {"id": "inference", "parts": [7], "group": "reading", "fr": "Inférence", "en": "Inference"},
}

# Des énoncés réellement différents les uns des autres (mots tirés au hasard, graine fixe),
# pour ne pas déclencher la détection de quasi-doublons. Leur sens n'a pas d'importance ici.
SUBJECTS = ["The warehouse", "Our accountant", "Every visitor", "The travel agency", "Ms. Okafor", "The city council",
            "A local bakery", "The software vendor", "Mr. Lindqvist", "The night shift"]
WORDS = ("budget audit pallets tiles harbor permit desk uniforms catering projector hall price retailers chairs "
         "dinner alarm drill itinerary delegation flowers lobby invoice contract supplier meeting schedule printer "
         "laptop voucher shuttle badge survey intern memo brochure elevator").split()


def make_stem(n: int) -> str:
    words = random.Random(n).sample(WORDS, 8)
    return f"{SUBJECTS[n % 10]} ------- the {' '.join(words)}."


def make_question(n: int = 1, **overrides) -> dict:
    """Une question valide, que chaque test abîme sur un seul point."""
    answer = overrides.get("answer", "B")
    wrong = [letter for letter in "ABCD" if letter != answer]
    q = {
        "id": f"p5-{n:04d}",
        "part": 5,
        "tags": ["verb-tense"],
        "difficulty": 2,
        "stem": make_stem(n),
        "options": ["send", "sent", "sending", "sends"],
        "answer": answer,
        "explanation": {
            "fr": {
                "rule": "Le marqueur de temps impose le prétérit dans cette phrase.",
                "distractors": {letter: f"La forme {letter} ne convient pas ici." for letter in wrong},
                "trap": "Repère d'abord le marqueur de temps.",
            },
            "en": {
                "rule": "The time marker requires the simple past in this sentence.",
                "distractors": {letter: f"Form {letter} does not fit here." for letter in wrong},
                "trap": "Find the time marker first.",
            },
        },
    }
    q.update(overrides)
    return q


def check(question: dict) -> Report:
    report = Report()
    validate.validate_question(question, 5, TAGS, "test", report)
    return report


def check_bank(questions: list[dict], pool: str = "training") -> Report:
    report = Report()
    valid = validate.validate_file_data({"part": 5, "pool": pool, "questions": questions}, TAGS, "lot.json", report)
    validate.check_duplicates(valid, report)
    validate.check_distributions(valid, TAGS, report)
    return report


def balanced_bank(count: int = 40) -> list[dict]:
    """Une banque équilibrée : lettres, difficultés et tags bien répartis."""
    return [
        make_question(n, answer="ABCD"[n % 4], difficulty=(1, 2, 2, 3)[(n // 4) % 4], tags=[("verb-tense", "pronouns")[n % 2]])
        for n in range(count)
    ]


class QuestionStructureTest(unittest.TestCase):
    def assertRejected(self, question: dict, fragment: str) -> None:
        report = check(question)
        self.assertTrue(
            any(fragment in issue.message for issue in report.errors),
            f"erreur attendue contenant {fragment!r}, obtenu : {[i.message for i in report.issues]}",
        )

    def test_valid_question_passes(self):
        self.assertEqual([], check(make_question()).issues)

    def test_missing_field(self):
        q = make_question()
        del q["answer"]
        self.assertRejected(q, "champs manquants")

    def test_unknown_field(self):
        self.assertRejected(make_question(hint="x"), "champs inconnus")

    def test_bad_id(self):
        self.assertRejected(make_question(id="p5-12"), "id invalide")
        self.assertRejected(make_question(id="p6-0001"), "id invalide")

    def test_part_mismatch(self):
        self.assertRejected(make_question(part=6), "part = 6")

    def test_unknown_tag(self):
        self.assertRejected(make_question(tags=["time-travel"]), "tag inconnu")

    def test_tag_not_allowed_for_part(self):
        self.assertRejected(make_question(tags=["inference"]), "n'est pas prévu pour la part 5")

    def test_tags_must_be_non_empty_and_distinct(self):
        self.assertRejected(make_question(tags=[]), "tags doit être")
        self.assertRejected(make_question(tags=["verb-tense", "verb-tense"]), "tags doit être")

    def test_difficulty_range(self):
        for bad in (0, 4, "2", True):
            self.assertRejected(make_question(difficulty=bad), "difficulty")

    def test_stem_needs_exactly_one_blank(self):
        self.assertRejected(make_question(stem="The manager sent the report to the head office yesterday."), "exactement un trou")
        self.assertRejected(make_question(stem="The ------- sent the ------- to the head office yesterday."), "exactement un trou")
        self.assertRejected(make_question(stem="The manager ----- the report to the head office yesterday."), "exactement un trou")
        self.assertRejected(make_question(stem="The manager -------- the report to the head office yesterday."), "exactement un trou")

    def test_stem_length_and_punctuation(self):
        self.assertRejected(make_question(stem="He ------- it."), "caractères")
        self.assertRejected(make_question(stem="The manager ------- the report to the head office yesterday"), "se terminer")

    def test_options_count(self):
        self.assertRejected(make_question(options=["send", "sent", "sending"]), "exactement 4")

    def test_duplicate_options_break_answer_uniqueness(self):
        self.assertRejected(make_question(options=["send", "sent", "Sent", "sends"]), "deux options sont identiques")

    def test_option_padding_rejected(self):
        self.assertRejected(make_question(options=["send", " sent", "sending", "sends"]), "chaque option")

    def test_answer_must_be_a_letter(self):
        self.assertRejected(make_question(answer="E"), "answer doit être")
        self.assertRejected(make_question(answer=1), "answer doit être")

    def test_each_distractor_must_be_explained(self):
        q = make_question()
        del q["explanation"]["fr"]["distractors"]["A"]
        self.assertRejected(q, "explanation.fr.distractors")

    def test_answer_letter_is_not_a_distractor(self):
        q = make_question()
        q["explanation"]["en"]["distractors"]["B"] = "This is actually the right answer."
        self.assertRejected(q, "explanation.en.distractors")

    def test_both_languages_required(self):
        q = make_question()
        del q["explanation"]["en"]
        self.assertRejected(q, "fr et en")

    def test_untranslated_explanation(self):
        q = make_question()
        q["explanation"]["en"] = copy.deepcopy(q["explanation"]["fr"])
        self.assertRejected(q, "identiques")

    def test_empty_trap(self):
        q = make_question()
        q["explanation"]["fr"]["trap"] = ""
        self.assertRejected(q, "trap")


class BankLevelTest(unittest.TestCase):
    def errors(self, report: Report) -> list[str]:
        return [issue.message for issue in report.errors]

    def test_balanced_bank_passes(self):
        self.assertEqual([], self.errors(check_bank(balanced_bank())))

    def test_duplicate_id(self):
        report = check_bank([make_question(1), make_question(1, stem="Our accountant ------- a parking permit from the desk upstairs.")])
        self.assertTrue(any("id déjà utilisé" in m for m in self.errors(report)))

    def test_identical_stem(self):
        report = check_bank([make_question(1), make_question(2, stem=make_question(1)["stem"])])
        self.assertTrue(any("énoncé identique" in m for m in self.errors(report)))

    def test_near_duplicate_stem(self):
        base = make_question(1)
        twin = make_question(2, stem=base["stem"].replace(" the ", " that ", 1))
        report = check_bank([base, twin])
        self.assertTrue(any("quasi identique" in m for m in self.errors(report)))

    def test_same_options_is_only_a_warning(self):
        report = check_bank([make_question(1), make_question(2)])
        self.assertEqual([], self.errors(report))
        self.assertTrue(any("mêmes 4 options" in issue.message for issue in report.warnings))

    def test_small_set_skips_distribution_checks(self):
        report = check_bank([make_question(n, answer="A") for n in range(10)])
        self.assertEqual([], self.errors(report))

    def test_skewed_answers(self):
        bank = balanced_bank()
        for q in bank[:30]:
            fixed = make_question(int(q["id"][3:]), answer="A", difficulty=q["difficulty"], tags=q["tags"])
            q.update(fixed)
        self.assertTrue(any("la bonne réponse est A" in m for m in self.errors(check_bank(bank))))

    def test_skewed_difficulty(self):
        bank = balanced_bank()
        for q in bank:
            q["difficulty"] = 2
        messages = self.errors(check_bank(bank))
        self.assertTrue(any("difficulté 1" in m for m in messages))
        self.assertTrue(any("difficulté 2" in m and "maximum" in m for m in messages))

    def test_tag_with_too_few_questions(self):
        bank = balanced_bank()
        for q in bank:
            q["tags"] = ["verb-tense"]
        for q in bank[:2]:
            q["tags"] = ["pronouns"]
        self.assertTrue(any("'pronouns' n'a que 2 question(s)" in m for m in self.errors(check_bank(bank))))

    def test_mock_pool_has_no_per_tag_minimum(self):
        bank = balanced_bank()
        for q in bank[2:]:
            q["tags"] = ["verb-tense"]
        self.assertFalse(any("n'a que" in m for m in self.errors(check_bank(bank, pool="mock"))))

    def test_bad_file_shape(self):
        report = Report()
        self.assertEqual([], validate.validate_file_data({"part": 5, "questions": []}, TAGS, "lot.json", report))
        self.assertFalse(report.ok)
        report = Report()
        validate.validate_file_data({"part": 5, "pool": "bonus", "questions": [make_question()]}, TAGS, "lot.json", report)
        self.assertTrue(any("pool inconnu" in m for m in self.errors(report)))


class RepositoryContentTest(unittest.TestCase):
    """Contrôles sur le vrai contenu du dépôt."""

    @classmethod
    def setUpClass(cls):
        cls.tags = validate.load_tags()
        cls.report, cls.questions = validate.validate_bank(validate.find_bank_files(), cls.tags)

    def test_bank_has_no_error(self):
        self.assertEqual([], [str(issue) for issue in self.report.errors])

    def test_bank_has_first_part5_batch(self):
        part5 = [q for q in self.questions if q["part"] == 5 and q["_pool"] == "training"]
        self.assertGreaterEqual(len(part5), 120)

    def test_schema_matches_validator(self):
        schema = json.loads((validate.CONTENT_DIR / "schemas" / "part5.schema.json").read_text(encoding="utf-8"))
        question = schema["$defs"]["question"]
        self.assertEqual(validate.QUESTION_FIELDS, set(question["required"]))
        self.assertEqual(validate.QUESTION_FIELDS, set(question["properties"]))
        self.assertEqual(validate.EXPLANATION_FIELDS, set(schema["$defs"]["explanation"]["required"]))
        self.assertEqual(list(validate.POOLS), schema["properties"]["pool"]["enum"])
        self.assertEqual(list(validate.DIFFICULTIES), question["properties"]["difficulty"]["enum"])
        self.assertEqual(validate.STEM_MIN, question["properties"]["stem"]["minLength"])
        self.assertEqual(validate.STEM_MAX, question["properties"]["stem"]["maxLength"])

    def test_bundle_is_deterministic_and_versioned(self):
        first = build.build_bundle(self.questions, self.tags)
        second = build.build_bundle(list(reversed(self.questions)), self.tags)
        self.assertEqual(first, second)
        self.assertEqual(len(self.questions), len(first["questions"]))
        self.assertNotIn("_pool", first["questions"][0])

        changed = copy.deepcopy(self.questions)
        changed[0]["stem"] = changed[0]["stem"].replace(".", "!")
        self.assertNotEqual(first["version"], build.build_bundle(changed, self.tags)["version"])

    def test_embedded_asset_is_up_to_date(self):
        """Échoue si la banque a changé sans relancer build.py."""
        if not build.OUTPUT_FILE.exists():
            self.skipTest("content.json pas encore généré")
        embedded = json.loads(build.OUTPUT_FILE.read_text(encoding="utf-8"))
        content = validate.validate_content(known_tags=self.tags)
        expected = build.build_bundle(content.questions, self.tags, content.lessons, content.path)
        self.assertEqual(expected["version"], embedded["version"], "relance : python content/tools/build.py")


if __name__ == "__main__":
    unittest.main()
