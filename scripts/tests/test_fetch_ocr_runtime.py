from __future__ import annotations

import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request

SCRIPT = Path(__file__).resolve().parents[1] / "fetch_ocr_runtime.py"
loader = importlib.util.spec_from_file_location("fetch_ocr_runtime", SCRIPT)
module = importlib.util.module_from_spec(loader)
loader.loader.exec_module(module)


class FakeResponse(io.BytesIO):
    def geturl(self):
        return "https://jitpack.io/official.aar"


class OfficialArtifactCatalogTest(unittest.TestCase):
    def test_exact_official_archive_is_fixed(self):
        self.assertEqual(module.EXPECTED_BYTES, 12_848_454)
        self.assertEqual(module.EXPECTED_SHA256, "bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a")
        self.assertEqual(module.URL, "https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar")
        self.assertEqual(module.MAX_ATTEMPTS, 3)


class OcrRuntimePreparationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.output = self.root / "deps/runtime.aar"
        self.payload = b"synthetic-archive-for-build-tests"
        self.digest = hashlib.sha256(self.payload).hexdigest()
        for key, value in [("EXPECTED_BYTES", len(self.payload)), ("EXPECTED_SHA256", self.digest)]:
            mock = patch.object(module, key, value)
            mock.start()
            self.addCleanup(mock.stop)

    def assert_clean(self):
        self.assertEqual(list(self.output.parent.glob(".ocr-runtime-*")), [])

    def test_verified_download_reused_without_network(self):
        with patch.object(module, "_open_url", return_value=FakeResponse(self.payload)) as network:
            self.assertEqual(module.prepare(self.output), self.digest)
            self.assertEqual(module.prepare(self.output), self.digest)
        self.assertEqual(network.call_count, 1)
        self.assertEqual(self.output.read_bytes(), self.payload)
        self.assert_clean()

    def test_retries_transient_http_status_with_bounded_delays(self):
        errors = [urllib.error.HTTPError(module.URL, code, "transient", {}, None) for code in (404, 429)]
        with patch.object(module, "_open_url", side_effect=errors + [FakeResponse(self.payload)]) as network:
            with patch.object(module.time, "sleep") as sleep:
                module.prepare(self.output)
        self.assertEqual(network.call_count, 3)
        self.assertEqual([call.args[0] for call in sleep.call_args_list], [1, 2])
        self.assert_clean()

    def test_retries_5xx_only_three_times_and_cleans_failure(self):
        with patch.object(module, "_open_url", side_effect=lambda _: (_ for _ in ()).throw(
                urllib.error.HTTPError(module.URL, 503, "unavailable", {}, None))) as network:
            with patch.object(module.time, "sleep") as sleep:
                with self.assertRaises(urllib.error.HTTPError):
                    module.prepare(self.output)
        self.assertEqual(network.call_count, 3)
        self.assertEqual(sleep.call_count, 2)
        self.assertFalse(self.output.exists())
        self.assert_clean()

    def test_partial_http_failure_is_truncated_before_retry(self):
        class PartialFailure(FakeResponse):
            def read(self, count=-1):
                if self.tell() == 0:
                    return super().read(5)
                raise urllib.error.HTTPError(module.URL, 502, "interrupted", {}, None)
        with patch.object(module, "_open_url", side_effect=[PartialFailure(self.payload), FakeResponse(self.payload)]):
            with patch.object(module.time, "sleep"):
                module.prepare(self.output)
        self.assertEqual(self.output.read_bytes(), self.payload)
        self.assert_clean()

    def test_permanent_http_error_is_not_retried(self):
        with patch.object(module, "_open_url", side_effect=urllib.error.HTTPError(module.URL, 403, "denied", {}, None)) as network:
            with patch.object(module.time, "sleep") as sleep:
                with self.assertRaises(urllib.error.HTTPError):
                    module.prepare(self.output)
        self.assertEqual(network.call_count, 1)
        sleep.assert_not_called()
        self.assert_clean()

    def test_integrity_failure_is_not_retried_or_installed(self):
        for body in [b"x" * len(self.payload), self.payload[:-1], self.payload + b"x"]:
            with self.subTest(body=body):
                with patch.object(module, "_open_url", return_value=FakeResponse(body)) as network:
                    with self.assertRaises(ValueError):
                        module.prepare(self.output)
                self.assertEqual(network.call_count, 1)
                self.assertFalse(self.output.exists())
                self.assert_clean()

    def test_https_downgrade_rejected_before_following_redirect(self):
        handler = module.HttpsOnlyRedirectHandler()
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            handler.redirect_request(urllib.request.Request(module.URL), None, 302, "found", {}, "http://example.invalid/runtime.aar")

    def test_insecure_final_response_is_rejected(self):
        response = FakeResponse(self.payload)
        response.geturl = lambda: "http://example.invalid/runtime.aar"
        with patch.object(module, "_open_url", return_value=response):
            with self.assertRaisesRegex(ValueError, "HTTPS"):
                module.prepare(self.output)
        self.assertFalse(self.output.exists())
        self.assert_clean()

    def test_offline_file_requires_same_hash_and_reuses_without_network(self):
        source = self.root / "source.aar"
        source.write_bytes(self.payload)
        with patch.object(module, "_open_url") as network:
            self.assertEqual(module.prepare(self.output, source), self.digest)
        network.assert_not_called()
        self.assert_clean()

    def test_invalid_existing_file_is_preserved_without_network(self):
        self.output.parent.mkdir()
        self.output.write_bytes(b"developer file")
        with patch.object(module, "_open_url") as network:
            with self.assertRaises(ValueError):
                module.prepare(self.output)
        self.assertEqual(self.output.read_bytes(), b"developer file")
        network.assert_not_called()

    def test_symlink_source_and_destination_are_rejected(self):
        source = self.root / "source.aar"
        source.write_bytes(self.payload)
        link = self.root / "source-link.aar"
        link.symlink_to(source)
        with self.assertRaisesRegex(ValueError, "regular"):
            module.prepare(self.output, link)
        self.output.symlink_to(source)
        with self.assertRaisesRegex(ValueError, "regular"):
            module.prepare(self.output)
        self.assert_clean()

    def test_interruption_does_not_leave_partial_dependency(self):
        class InterruptedResponse(FakeResponse):
            def read(self, count=-1):
                raise KeyboardInterrupt()
        with patch.object(module, "_open_url", return_value=InterruptedResponse(self.payload)):
            with self.assertRaises(KeyboardInterrupt):
                module.prepare(self.output)
        self.assertFalse(self.output.exists())
        self.assert_clean()

    def test_competing_unknown_file_is_not_overwritten(self):
        original_link = module.os.link
        def install_other(source, target):
            Path(target).write_bytes(b"other build")
            original_link(source, target)
        with patch.object(module, "_open_url", return_value=FakeResponse(self.payload)):
            with patch.object(module.os, "link", side_effect=install_other):
                with self.assertRaises(ValueError):
                    module.prepare(self.output)
        self.assertEqual(self.output.read_bytes(), b"other build")
        self.assert_clean()


if __name__ == "__main__":
    unittest.main()
