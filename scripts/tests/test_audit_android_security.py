import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("audit_android_security", ROOT / "scripts/audit_android_security.py")
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)
A = audit.ANDROID


class AndroidSecurityAuditTest(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.fromstring((ROOT / "app/src/main/AndroidManifest.xml").read_text())
        self.accessibility = ET.fromstring((ROOT / "app/src/main/res/xml/nexus_accessibility_service.xml").read_text())
        self.network = ET.fromstring((ROOT / "app/src/main/res/xml/network_security_config.xml").read_text())

    def report(self, final=False):
        return audit.inspect_xml(*(ET.tostring(node, encoding="unicode") for node in (
            self.manifest, self.accessibility, self.network)), final=final)

    def codes(self):
        return {row["code"] for row in self.report()["violations"]}

    def test_current_source_preserves_minimal_mouse_and_voice_permissions(self):
        report = self.report()
        self.assertEqual("passed", report["status"], report["violations"])
        self.assertEqual(8, len(report["permissions"]))
        self.assertFalse(report["finalManifest"])

    def test_rejects_sensitive_permission_even_with_sdk23_spelling(self):
        ET.SubElement(self.manifest, "uses-permission-sdk-23", {A + "name": "android.permission.READ_SMS"})
        self.assertIn("unexpected_permission", self.codes())

    def test_rejects_special_overlay_or_usage_visibility_permissions(self):
        for name in ("SYSTEM_ALERT_WINDOW", "PACKAGE_USAGE_STATS", "QUERY_ALL_PACKAGES"):
            with self.subTest(name=name):
                node = ET.SubElement(self.manifest, "uses-permission", {A + "name": "android.permission." + name})
                self.assertIn("unexpected_permission", self.codes())
                self.manifest.remove(node)

    def test_rejects_unprotected_accessibility_binding(self):
        node = next(n for n in self.manifest.find("application").findall("service") if "NexusAccessibilityService" in audit.attr(n, "name"))
        node.attrib.pop(A + "permission")
        self.assertIn("accessibility_binding", self.codes())

    def test_rejects_exported_camera_microphone_and_unknown_receiver(self):
        for node in self.manifest.find("application").findall("service"):
            if audit.attr(node, "foregroundServiceType"):
                node.set(A + "exported", "true")
        ET.SubElement(self.manifest.find("application"), "receiver", {A + "name": ".UnexpectedReceiver", A + "exported": "true"})
        self.assertIn("unexpected_exported_component", self.codes())
        self.assertIn("foreground_service_boundary", self.codes())

    def test_implicit_export_with_intent_filter_is_detected(self):
        node = ET.SubElement(self.manifest.find("application"), "activity", {A + "name": ".UnexpectedActivity"})
        ET.SubElement(node, "intent-filter")
        self.assertIn("unexpected_exported_component", self.codes())

    def test_requires_separate_foreground_types(self):
        for node in self.manifest.find("application").findall("service"):
            if audit.attr(node, "foregroundServiceType"):
                node.set(A + "foregroundServiceType", "camera|microphone")
        self.assertIn("foreground_service_boundary", self.codes())

    def test_reading_windows_screenshots_or_keys_is_rejected(self):
        for field in ("canRetrieveWindowContent", "canTakeScreenshot", "canRequestFilterKeyEvents"):
            with self.subTest(field=field):
                self.accessibility.set(A + field, "true")
                self.assertTrue(self.codes().intersection({"accessibility_capability", "accessibility_extra_capability"}))
                self.accessibility.set(A + field, "false")

    def test_events_flags_and_false_accessibility_tool_claim_are_rejected(self):
        self.accessibility.set(A + "accessibilityEventTypes", "typeAllMask")
        self.accessibility.set(A + "accessibilityFlags", "flagRetrieveInteractiveWindows")
        self.accessibility.set(A + "isAccessibilityTool", "true")
        self.assertIn("accessibility_events_or_flags", self.codes())
        self.assertIn("accessibility_capability", self.codes())

    def test_network_exceptions_user_ca_and_missing_policy_are_rejected(self):
        domain = ET.SubElement(self.network, "domain-config", {"cleartextTrafficPermitted": "true"})
        ET.SubElement(domain, "certificates", {"src": "user"})
        self.assertIn("cleartext_exception", self.codes())
        self.assertIn("user_ca_trust", self.codes())
        self.network.remove(self.network.find("base-config"))
        self.assertIn("cleartext_base_config", self.codes())

    def test_debug_backup_and_test_only_builds_are_rejected(self):
        app = self.manifest.find("application")
        app.set(A + "debuggable", "true")
        app.set(A + "allowBackup", "true")
        app.set(A + "testOnly", "true")
        self.assertTrue({"debuggable_build", "personal_backup_enabled", "test_only_build"}.issubset(self.codes()))

    def test_final_requires_sdk_and_accepts_decoded_hex_types(self):
        self.assertIn("unexpected_sdk", {x["code"] for x in self.report(final=True)["violations"]})
        ET.SubElement(self.manifest, "uses-sdk", {A + "minSdkVersion": "26", A + "targetSdkVersion": "35"})
        for node in self.manifest.find("application").findall("service"):
            value = audit.attr(node, "foregroundServiceType")
            if value: node.set(A + "foregroundServiceType", {"camera": "0x40", "microphone": "0x80"}[value])
        self.assertEqual("passed", self.report(final=True)["status"])

    def test_internal_receiver_permission_needs_signature_protection(self):
        ET.SubElement(self.manifest, "uses-permission", {A + "name": audit.INTERNAL_PERMISSION})
        node = ET.SubElement(self.manifest, "permission", {A + "name": audit.INTERNAL_PERMISSION, A + "protectionLevel": "normal"})
        self.assertIn("internal_permission_not_signature", self.codes())
        node.set(A + "protectionLevel", "0x2")
        self.assertEqual("passed", self.report()["status"])

    def test_signing_summary_distinguishes_test_key_without_calling_it_production_identity(self):
        output = "Signer #1 certificate DN: C=US, O=Android, CN=Android Debug\nSigner #1 certificate SHA-256 digest: " + "ab" * 32 + "\n"
        result = audit.signing_summary(output)
        self.assertTrue(result["signatureVerified"])
        self.assertTrue(result["testDebugCertificate"])
        self.assertFalse(result["productionIdentityVerified"])
        output = output.replace("CN=Android Debug", "CN=Example Developer")
        self.assertFalse(audit.signing_summary(output)["testDebugCertificate"])
        with self.assertRaises(ValueError): audit.signing_summary("DOES NOT VERIFY")


if __name__ == "__main__":
    unittest.main()
