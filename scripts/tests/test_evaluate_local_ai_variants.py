import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "evaluate_local_ai_variants.py"
spec = importlib.util.spec_from_file_location("evaluate_local_ai_variants", path)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class FixtureBoundaryTest(unittest.TestCase):
    def fixture(self):
        return {"variants": [{"id": "baseline", "system": "Synthetic assistant identity"}],
                "cases": [{"id": "apology", "turns": ["Write a synthetic apology"]}]}

    def test_accepts_real_generation_conversation_and_name_change_instructions(self):
        fixture = self.fixture()
        fixture["cases"][0]["turns"].append({"query": "Now revise it", "system": "Updated user identity"})
        runner.validate_fixture(fixture)

    def test_rejects_assistant_answer_fixture_and_invalid_user_query(self):
        for value in ({"query": "hi", "assistant": "canned answer"}, {"role": "assistant", "content": "answer"}, "", "\0"):
            fixture = self.fixture()
            fixture["cases"][0]["turns"] = [value]
            with self.subTest(value=value), self.assertRaises(ValueError):
                runner.validate_fixture(fixture)

    def test_rejects_duplicate_identifiers_and_unbounded_contexts(self):
        fixture = self.fixture()
        fixture["variants"].append(fixture["variants"][0].copy())
        with self.assertRaises(ValueError):
            runner.validate_fixture(fixture)
        for change in ({"threads": 0}, {"threads": True}, {"contextTokens": 128}, {"maxOutputTokens": 2048}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.validate_fixture(self.fixture() | change)


if __name__ == "__main__":
    unittest.main()
