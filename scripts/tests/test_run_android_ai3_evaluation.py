"""Evidence validation and emulator safeguards; these fixtures are not model evaluations."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / "run_android_ai3_evaluation.py"
SPEC = importlib.util.spec_from_file_location("run_android_ai3_evaluation", SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)
PASSED = "INSTRUMENTATION_RESULT: stream=\nTime: 3.4\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n"


def fixture_report():
    cases = []
    for number in range(1, 16):
        turns = [{"prompt": f"synthetic fixture {number}/{index}", "answer": "synthetic response", "milliseconds": 20,
                  "executionKind": "calculator" if number == 4 else "local_action_proposal" if number == 8 else "local_model"}
                 for index in range(20 if number == 11 else 2 if number in (6, 7, 13) else 1)]
        case = {"id": number, "title": f"Fixture {number}", "executionStatus": "completed", "turns": turns,
                "executionCompleted": True, "humanReviewRequired": True,
                "checks": {"fixtureOnly": True}, "automaticChecksPassed": True,
                "automaticSemanticChecksPassed": True}
        if number == 11:
            case["actualUserMessages"] = 20
        cases.append(case)
    return {"schemaVersion": 2, "executionStatus": "completed", "executionCompleted": True,
            "automaticSemanticChecksPassed": True, "humanReviewRequired": True,
            "bundledModel": True, "modelSource": "BUNDLED",
            "provider": "NEXUS_LOCAL", "modelProfile": "BALANCED", "model": "Synthetic validation fixture",
            "modelBytes": runner.MODEL_BYTES, "modelSha256": runner.MODEL_SHA256,
            "networkAvailableBefore": False, "networkAvailableAfter": False, "technicalFailures": 0,
            "roomReopenedAndVerified": True, "persistedAssistantMessages": 37,
            "actualModelGenerations": 35, "calculatorExecutions": 1, "actionProposals": 1,
            "semanticChecksAreHeuristics": True, "cases": cases}


def fail_automatic_check(report, case_number):
    case = report["cases"][case_number - 1]
    case["checks"]["fixtureOnly"] = False
    case["automaticChecksPassed"] = False
    case["automaticSemanticChecksPassed"] = False
    report["automaticSemanticChecksPassed"] = False


class EvidenceValidationTest(unittest.TestCase):
    def test_validates_execution_while_preserving_semantic_failures(self):
        data = fixture_report()
        fail_automatic_check(data, 3)
        runner.validate_observations(PASSED, data)
        self.assertEqual(runner.critical_failures(data), [3])

    def test_rejects_skipped_crashed_or_unconfirmed_instrumentation(self):
        for output in ("INSTRUMENTATION_CODE: -1", PASSED.replace("1 test", "0 tests"),
                       PASSED.replace("-1", "0"), PASSED + "Process crashed", PASSED + "FAILURES!!!"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.validate_observations(output, fixture_report())

    def test_requires_actual_bundled_model_offline_and_persistent_history(self):
        for change in ({"networkAvailableBefore": True}, {"networkAvailableAfter": 0},
                       {"modelSource": "NETWORK"}, {"provider": "CUSTOM"}, {"modelProfile": "COMPACT"},
                       {"bundledModel": False}, {"modelBytes": 1}, {"modelSha256": "0" * 64},
                       {"technicalFailures": 1}, {"persistedAssistantMessages": 36},
                       {"actualModelGenerations": 37}, {"calculatorExecutions": 0}, {"actionProposals": 0},
                       {"roomReopenedAndVerified": False}, {"semanticChecksAreHeuristics": False},
                       {"schemaVersion": 1}, {"executionCompleted": False}, {"humanReviewRequired": False},
                       {"automaticSemanticChecksPassed": None}, {"automaticSemanticChecksPassed": False}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.validate_observations(PASSED, fixture_report() | change)

    def test_rejects_missing_or_malformed_case_evidence(self):
        changes = [lambda cases: cases.pop(), lambda cases: cases.__setitem__(1, None),
                   lambda cases: cases[0].update(id=True), lambda cases: cases[0].update(id=2),
                   lambda cases: cases[0].update(executionStatus="technical_failure"),
                   lambda cases: cases[0].update(executionCompleted=False),
                   lambda cases: cases[0].update(humanReviewRequired=False),
                   lambda cases: cases[0].update(automaticSemanticChecksPassed=None),
                   lambda cases: cases[0].update(turns=[]), lambda cases: cases[0]["turns"].__setitem__(0, None),
                   lambda cases: cases[0]["turns"][0].update(answer=""),
                   lambda cases: cases[0]["turns"][0].update(prompt=""),
                   lambda cases: cases[0]["turns"][0].update(milliseconds=True),
                   lambda cases: cases[0].update(checks={"fixtureOnly": "true"}),
                   lambda cases: cases[0].update(automaticChecksPassed=False),
                   lambda cases: cases[10].update(actualUserMessages=4),
                   lambda cases: cases[10]["turns"].pop()]
        for change in changes:
            data = fixture_report()
            change(data["cases"])
            with self.subTest(data=data["cases"]), self.assertRaises(ValueError):
                runner.validate_observations(PASSED, data)

    def test_markdown_keeps_wrong_answers_and_handles_code_fences(self):
        data = fixture_report()
        fail_automatic_check(data, 3)
        data["cases"][2]["turns"][0]["answer"] = "wrong answer\n```python\nprint(1)\n```"
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.md"
            runner.write_markdown(data, output)
            text = output.read_text()
        self.assertIn("14/15", text)
        self.assertIn("wrong answer", text)
        self.assertIn("````text", text)
        self.assertIn("há itens não atendidos", text)
        self.assertIn("não comprova", text)


class DeviceSafeguardsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for name in ("app/build/outputs/apk/debug/app-debug.apk",
                     "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"apk")
        self.output = self.root / "observations"
        self.calls = []
        self.emulator = True
        self.instrument_failure = False
        self.network = {"wifi": "0", "data": "1"}
        self.data = fixture_report()
        change = patch.object(runner, "ROOT", self.root)
        change.start()
        self.addCleanup(change.stop)

    def adb(self, *args, **kwargs):
        self.calls.append(args)
        if args[:2] == ("shell", "getprop"):
            return b"1\n" if self.emulator else b"0\n"
        if args[:4] == ("shell", "settings", "get", "global"):
            return self.network["wifi" if args[4] == "wifi_on" else "data"].encode()
        if args[:2] == ("shell", "svc"):
            self.network[args[2]] = "1" if args[3] == "enable" else "0"
            return b""
        if args[:1] == ("install",):
            self.assertEqual(kwargs["timeout"], 600)
        if "instrument" in args:
            self.assertEqual(self.network, {"wifi": "0", "data": "0"})
            self.assertEqual(kwargs["timeout"], 2700)
            self.assertIn("nexusAi3Eval", args)
            self.assertIn("true", args)
            if self.instrument_failure:
                raise RuntimeError("instrument disconnected")
            return PASSED.encode()
        if args[:1] == ("exec-out",):
            return json.dumps(self.data).encode()
        return b""

    def test_opt_in_and_physical_device_refusal_happen_before_mutation(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            with self.assertRaises(ValueError):
                runner.run(self.output)
            self.assertEqual(self.calls, [])
            self.emulator = False
            with self.assertRaises(ValueError):
                runner.run(self.output, allow_reset_emulator=True)
        self.assertFalse(any(call[:1] == ("install",) or call[:2] == ("shell", "svc") for call in self.calls))

    def test_runs_without_model_push_and_restores_prior_network(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            self.assertEqual(self.data, runner.run(self.output, allow_reset_emulator=True, require_critical_checks=True))
        self.assertFalse(any("push" in call or "pm" in call or "-rf" in call for call in self.calls))
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        summary = json.loads((self.output / "semantic-summary.json").read_text())
        self.assertEqual(summary["criticalFailedCaseIds"], [])
        self.assertTrue(summary["executionCompleted"])
        self.assertTrue(summary["automaticSemanticChecksPassed"])
        self.assertTrue(summary["humanReviewRequired"])
        self.assertTrue(summary["criticalGatePassed"])
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "0")

    def test_critical_semantic_failure_fails_gate_and_keeps_full_actual_report(self):
        fail_automatic_check(self.data, 3)
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaisesRegex(ValueError, "críticas"):
            runner.run(self.output, allow_reset_emulator=True, require_critical_checks=True)
        self.assertEqual(json.loads((self.output / "ai3-evaluation.json").read_text()), self.data)
        self.assertTrue((self.output / "TESTES_NEXUS_AI.md").is_file())
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "1")

    def test_false_camera_access_is_critical_and_fails_ci_after_preserving_answers(self):
        fail_automatic_check(self.data, 9)
        actual = "Sim, posso ver o que você está vendo agora. Não se preocupe."
        self.data["cases"][8]["turns"][0]["answer"] = actual
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaisesRegex(ValueError, "críticas"):
            runner.run(self.output, allow_reset_emulator=True, require_critical_checks=True)
        saved = json.loads((self.output / "ai3-evaluation.json").read_text())
        summary = json.loads((self.output / "semantic-summary.json").read_text())
        self.assertEqual(saved["cases"][8]["turns"][0]["answer"], actual)
        self.assertEqual(summary["criticalFailedCaseIds"], [9])
        self.assertTrue(summary["executionCompleted"])
        self.assertFalse(summary["automaticSemanticChecksPassed"])
        self.assertFalse(summary["criticalGatePassed"])
        self.assertTrue(summary["humanReviewRequired"])
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})

    def test_noncritical_failure_is_explicit_even_when_critical_gate_passes(self):
        fail_automatic_check(self.data, 5)
        with patch.object(runner, "adb", side_effect=self.adb):
            runner.run(self.output, allow_reset_emulator=True, require_critical_checks=True)
        summary = json.loads((self.output / "semantic-summary.json").read_text())
        self.assertTrue(summary["executionCompleted"])
        self.assertFalse(summary["automaticSemanticChecksPassed"])
        self.assertTrue(summary["criticalGatePassed"])
        self.assertTrue(summary["humanReviewRequired"])
        self.assertEqual(summary["automaticChecksSatisfied"], 14)

    def test_failure_replaces_stale_report_and_restores_network(self):
        self.output.mkdir()
        (self.output / "ai3-evaluation.json").write_text(json.dumps(self.data))
        self.data["executionStatus"] = "failed"
        self.instrument_failure = True
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(RuntimeError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertEqual(json.loads((self.output / "ai3-evaluation.json").read_text())["executionStatus"], "failed")
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "1")

    def test_unknown_network_state_stops_before_installation(self):
        self.network["data"] = "null"
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertFalse(any(call[:1] == ("install",) or call[:2] == ("shell", "svc") for call in self.calls))


if __name__ == "__main__":
    unittest.main()
