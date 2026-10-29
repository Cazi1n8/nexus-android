"""Guards around the host-only evaluator, without a model or network request."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("remote_evaluator", Path(__file__).parents[1] / "evaluate_remote_ai.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RemoteEvaluationGuards(unittest.TestCase):
    def test_network_is_limited_to_explicit_loopback_without_embedded_secrets(self):
        for endpoint in ("http://127.0.0.1:8787/v1", "http://localhost:8080/v1", "http://[::1]:8787/v1"):
            module.validate_endpoint(endpoint)
        for endpoint in ("https://external.example/v1", "http://10.0.2.2:8787/v1", "http://localhost.evil.example/v1", "http://user:password@localhost/v1", "http://localhost/v1?token=secret", "http://localhost/v1#token"):
            with self.subTest(endpoint=endpoint), self.assertRaises(ValueError):
                module.validate_endpoint(endpoint)

    def test_fixture_cannot_inject_a_prepared_assistant_answer_or_fake_expected_success(self):
        for case in (
            {"id": "one", "turns": [{"text": "Olá", "answer": "Uma resposta preparada"}]},
            {"id": "one", "turns": [{"text": "Olá"}], "expected": "aprovado"},
            {"id": "one", "turns": [{"role": "assistant", "text": "Resposta"}]},
        ):
            with self.subTest(case=case), self.assertRaises(ValueError):
                module.validate_fixture({"cases": [case]})

    def test_prompt_only_image_and_followup_are_accepted_without_responses(self):
        module.validate_fixture({"userPreferredName": "Calebe", "cases": [{"id": "image", "turns": [
            {"text": "O que é isso?", "imagePath": "/private/developer-fixture.jpg"},
            {"text": "Para que serve?"},
        ]}]})
