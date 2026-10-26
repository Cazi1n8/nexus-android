import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.request
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_mediapipe_runtime as runtime


class MediaPipeRuntimeTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.factory = b'\xca\xfe\xba\xbe\x00\x00\x00\x34TasksStatsDummyLogger'
        self.other_class = b'unchanged class payload'
        self.native = b'unchanged native ELF payload'
        classes = runtime.deterministic_zip({runtime.FACTORY: b'old logging factory', 'Other.class': self.other_class})
        self.original = runtime.deterministic_zip({'classes.jar': classes, 'AndroidManifest.xml': b'manifest',
                                                  'jni/arm64-v8a/libmediapipe_tasks_jni.so': self.native})

    def test_official_inputs_and_derivative_are_pinned(self):
        self.assertEqual(runtime.ORIGINAL_BYTES, 21_342_753)
        self.assertEqual(runtime.ORIGINAL_SHA256, '4edf2f33c840d682c751b3ca951b1ae0465373734776d7fb37dcef936de28ad0')
        self.assertEqual(runtime.UPSTREAM_SHA256, 'bab1d480c5bc5808e1788c91e1e067120d7348137d50fe2ca764611cf5e0a6d7')
        self.assertEqual(runtime.EXPECTED_OUTPUT_SHA256, '25ac823c37f77d2870c32bfbe0dfae30b2b1a921b7fce395e743399c3b638bf3')
        self.assertTrue(runtime.MAVEN_URL.startswith('https://dl.google.com/dl/android/maven2/'))

    def test_derivation_preserves_native_resources_and_all_other_classes(self):
        derived, proof = runtime.derive_aar(self.original, self.factory)
        entries = runtime.zip_entries(derived)
        classes = runtime.zip_entries(entries['classes.jar'])
        self.assertEqual(classes[runtime.FACTORY], self.factory)
        self.assertEqual(classes['Other.class'], self.other_class)
        self.assertEqual(entries['jni/arm64-v8a/libmediapipe_tasks_jni.so'], self.native)
        self.assertEqual(entries['AndroidManifest.xml'], b'manifest')
        self.assertEqual(proof['only_changed_class'], runtime.FACTORY)
        self.assertEqual(proof['unchanged_jar_entries'], 1)

    def test_repeated_build_is_byte_reproducible_independent_of_entry_order(self):
        first, _ = runtime.derive_aar(self.original, self.factory)
        second, _ = runtime.derive_aar(self.original, self.factory)
        self.assertEqual(first, second)
        self.assertEqual(runtime.deterministic_zip({'b': b'2', 'a': b'1'}),
                         runtime.deterministic_zip({'a': b'1', 'b': b'2'}))

    def test_audit_rejects_any_native_or_resource_payload_change(self):
        derived, _ = runtime.derive_aar(self.original, self.factory)
        entries = runtime.zip_entries(derived)
        entries['jni/arm64-v8a/libmediapipe_tasks_jni.so'] = b'changed'
        with self.assertRaisesRegex(ValueError, 'Payload de AAR'):
            runtime.verify_derivation(self.original, runtime.deterministic_zip(entries))

    def test_audit_rejects_changes_to_unrelated_classes(self):
        derived, _ = runtime.derive_aar(self.original, self.factory)
        entries = runtime.zip_entries(derived)
        classes = runtime.zip_entries(entries['classes.jar'])
        classes['Other.class'] = b'changed'
        entries['classes.jar'] = runtime.deterministic_zip(classes)
        with self.assertRaisesRegex(ValueError, 'somente TasksStatsLoggerFactory'):
            runtime.verify_derivation(self.original, runtime.deterministic_zip(entries))

    def test_remote_logger_or_wrong_bytecode_is_rejected(self):
        for factory in (b'not class', self.factory[:6] + b'\x00\x3d' + self.factory[8:],
                        self.factory + b'TasksStatsProtoLogger', self.factory + b'RemoteLoggingClient'):
            with self.subTest(factory=factory), self.assertRaises(ValueError):
                runtime.derive_aar(self.original, factory)

    def test_atomic_install_refuses_unknown_existing_file_and_symbolic_file(self):
        target = self.root / 'runtime.aar'
        runtime.atomic_install(b'first verified payload', target)
        runtime.atomic_install(b'first verified payload', target)
        with self.assertRaises(ValueError): runtime.atomic_install(b'other bytes', target)
        self.assertEqual(target.read_bytes(), b'first verified payload')
        link = self.root / 'linked.aar'; link.symlink_to(target)
        with self.assertRaises(ValueError): runtime.atomic_install(b'first verified payload', link)
        self.assertTrue(link.is_symlink())

    def test_bad_original_and_upstream_are_refused_without_overwriting(self):
        original = self.root / 'original.aar'; original.write_bytes(self.original)
        upstream = self.root / 'BUILD'; upstream.write_bytes(b'untrusted content')
        with self.assertRaises(ValueError): runtime.validate_original(original)
        with self.assertRaises(ValueError): runtime.validate_upstream(upstream)
        self.assertEqual(upstream.read_bytes(), b'untrusted content')

    def test_verified_local_cache_avoids_network_and_corruption_is_preserved(self):
        path = self.root / 'asset'; path.write_bytes(b'verified')
        checksum = hashlib.sha256(b'verified').hexdigest()
        with patch.object(urllib.request, 'build_opener', side_effect=AssertionError('no network')):
            self.assertEqual(runtime.fetch_verified('https://example.invalid/', path, checksum, 8), b'verified')
            path.write_bytes(b'corrupt!')
            with self.assertRaises(ValueError): runtime.fetch_verified('https://example.invalid/', path, checksum, 8)
        self.assertEqual(path.read_bytes(), b'corrupt!')

    def test_https_cannot_downgrade_to_plain_http(self):
        with self.assertRaises(ValueError):
            runtime.HttpsOnlyRedirect().redirect_request(urllib.request.Request('https://example.invalid/'),
                                                        None, 302, 'Found', {}, 'http://example.invalid/')

    def test_zip_rejects_duplicate_entries_and_path_traversal(self):
        duplicate = io.BytesIO()
        with zipfile.ZipFile(duplicate, 'w') as archive:
            archive.writestr('duplicate', b'first')
            with self.assertWarns(UserWarning): archive.writestr('duplicate', b'second')
        with self.assertRaises(ValueError): runtime.zip_entries(duplicate.getvalue())
        with self.assertRaises(ValueError): runtime.zip_entries(runtime.deterministic_zip({'../escape': b'invalid'}))


if __name__ == '__main__': unittest.main()
