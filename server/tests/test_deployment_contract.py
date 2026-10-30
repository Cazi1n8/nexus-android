"""Static deployment security invariants; not nginx/systemd runtime validation."""
from __future__ import annotations

import configparser
import inspect
from pathlib import Path
import re
import unittest

from server.session_store import SessionStore


ROOT = Path(__file__).resolve().parents[2]


def nginx_location(text: str, start_marker: str) -> str:
    start = text.index(start_marker)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        if end >= len(text):
            raise ValueError("Unbalanced block")
        char = text[end]
        depth += (char == "{") - (char == "}")
        end += 1
    return text[opening + 1:end - 1]


class DeploymentContractTest(unittest.TestCase):
    def setUp(self):
        self.nginx = (ROOT / "server/deploy/nginx.conf.example").read_text()
        self.unit = configparser.ConfigParser(interpolation=None, strict=False)
        self.unit.read(ROOT / "server/deploy/nexus-ai.service")
        self.env = {}
        for line in (ROOT / "server/.env.online.example").read_text().splitlines():
            if line and not line.startswith("#"):
                key, value = line.split("=", 1)
                self.assertNotIn(key, self.env)
                self.env[key] = value

    def test_no_credentials_or_shared_app_secret_in_environment_example(self):
        self.assertEqual("", self.env["UPSTREAM_API_KEY"])
        self.assertNotIn("NEXUS_ACCESS_TOKEN", self.env)
        self.assertEqual("127.0.0.1", self.env["NEXUS_BIND"])
        self.assertTrue(self.env["NEXUS_SESSION_DB"].startswith("/var/lib/nexus-ai/"))
        defaults = inspect.signature(SessionStore).parameters
        self.assertEqual(defaults["account_daily_units"].default, int(self.env["NEXUS_ACCOUNT_DAILY_UNITS"]))
        self.assertEqual(defaults["global_daily_units"].default, int(self.env["NEXUS_GLOBAL_DAILY_UNITS"]))
        self.assertGreaterEqual(int(self.env["NEXUS_ACCOUNT_DAILY_UNITS"]), 4 * 1024 * 1024)

    def test_service_runs_unprivileged_with_private_persistent_state(self):
        service = self.unit["Service"]
        self.assertEqual("nexus-ai", service["User"])
        self.assertEqual("/opt/nexus-ai", service["WorkingDirectory"])
        self.assertEqual("/usr/bin/python3 -m server.nexus_gateway", service["ExecStart"])
        self.assertEqual("/etc/nexus-ai/gateway.env", service["EnvironmentFile"])
        self.assertEqual("0077", service["UMask"])
        self.assertEqual("0700", service["StateDirectoryMode"])
        self.assertEqual("/var/lib/nexus-ai", service["ReadWritePaths"])
        self.assertEqual("strict", service["ProtectSystem"])
        self.assertEqual("true", service["NoNewPrivileges"])
        self.assertEqual("true", service["ProtectHome"])
        self.assertNotIn("UPSTREAM_API_KEY=", service["ExecStart"])

    def test_proxy_auth_route_matches_gateway_contract_and_is_limited(self):
        marker = "location ~ ^/v1/sessions/(redeem|refresh|revoke)$"
        block = nginx_location(self.nginx, marker)
        gateway = (ROOT / "server/nexus_gateway.py").read_text()
        for action in ("redeem", "refresh", "revoke"):
            self.assertIn(f'"/v1/sessions/{action}"', gateway)
        self.assertNotIn("/v1/auth/", self.nginx)
        self.assertIn("limit_except POST { deny all; }", block)
        self.assertIn("limit_req zone=nexus_auth", block)
        self.assertIn("client_max_body_size 8k;", block)
        self.assertIn("proxy_pass http://127.0.0.1:8787;", block)
        self.assertIn('add_header Cache-Control "no-store" always;', block)

    def test_sse_proxy_disables_buffering_cache_compression_and_keeps_auth(self):
        block = nginx_location(self.nginx, "location = /v1/chat/completions")
        for expected in ("proxy_buffering off;", "proxy_cache off;", "gzip off;",
                         "proxy_set_header Authorization $http_authorization;",
                         "limit_except POST { deny all; }", "limit_req zone=nexus_chat"):
            self.assertIn(expected, block)
        self.assertNotIn("proxy_ignore_client_abort on", block)

    def test_public_proxy_exposes_only_https_without_access_logs_or_query_auth(self):
        self.assertIn("listen 443 ssl;", self.nginx)
        self.assertIn("ssl_protocols TLSv1.2 TLSv1.3;", self.nginx)
        self.assertIn("access_log off;", self.nginx)
        self.assertIn('if ($args != "") { return 400; }', self.nginx)
        self.assertNotIn("listen 8787", self.nginx)
        self.assertNotIn("Access-Control-Allow-Origin", self.nginx)
        self.assertIn("location = /healthz { return 404; }", self.nginx)
        self.assertIn("location / { return 404; }", self.nginx)


if __name__ == "__main__":
    unittest.main()
