"""Session/account/allowance tests use no real credentials or external model."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from contextlib import redirect_stdout
from dataclasses import replace
import io
import os
from pathlib import Path
import sqlite3
import stat
import tempfile
import threading
import unittest

from server.session_store import (
    InvalidInvitation, InvalidSession, Principal, QuotaExceeded, SessionStore,
    StoreError, main,
)


class Clock:
    def __init__(self):
        self.value = 1_800_000_000.0

    def __call__(self):
        return self.value


class SessionStoreTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "private" / "sessions.sqlite3"
        self.clock = Clock()
        self.store = SessionStore(self.path, clock=self.clock)

    def connect(self, store=None):
        store = store or self.store
        return store.redeem(store.create_invitation().code)

    def test_redeem_authentication_has_separate_random_account_and_session(self):
        tokens = self.connect()
        principal = self.store.authenticate(tokens.access_token)
        self.assertIsInstance(principal, Principal)
        self.assertEqual(32, len(principal.account_id))
        self.assertEqual(32, len(principal.session_id))
        self.assertNotEqual(principal.account_id, principal.session_id)
        self.assertEqual(int(self.clock.value) + 900, tokens.access_expires_at)
        self.assertEqual(int(self.clock.value) + 2_592_000, tokens.refresh_expires_at)

    def test_no_raw_credential_in_database_or_representations(self):
        invitation = self.store.create_invitation()
        tokens = self.store.redeem(invitation.code)
        with sqlite3.connect(self.path) as db:
            contents = repr(list(db.iterdump()))
        raw = self.path.read_bytes()
        for secret in (invitation.code, tokens.access_token, tokens.refresh_token):
            self.assertNotIn(secret, contents)
            self.assertNotIn(secret.encode(), raw)
            self.assertNotIn(secret, repr(tokens))
            self.assertNotIn(secret, repr(invitation))

    def test_database_and_created_parent_have_private_permissions(self):
        self.assertEqual(0o600, stat.S_IMODE(self.path.stat().st_mode))
        self.assertEqual(0o700, stat.S_IMODE(self.path.parent.stat().st_mode))

    def test_existing_database_permissions_are_restricted(self):
        os.chmod(self.path, 0o644)
        SessionStore(self.path, clock=self.clock)
        self.assertEqual(0o600, stat.S_IMODE(self.path.stat().st_mode))

    def test_symlink_database_is_rejected_without_modifying_target(self):
        target = Path(self.directory.name) / "target"
        target.write_text("unrelated")
        link = Path(self.directory.name) / "linked.sqlite3"
        link.symlink_to(target)
        with self.assertRaises(StoreError):
            SessionStore(link)
        self.assertEqual("unrelated", target.read_text())

    def test_unrelated_or_corrupt_database_not_accepted(self):
        unrelated = Path(self.directory.name) / "unrelated.sqlite3"
        with sqlite3.connect(unrelated) as db:
            db.execute("CREATE TABLE other(value TEXT)")
        with self.assertRaises(StoreError):
            SessionStore(unrelated)
        corrupt = Path(self.directory.name) / "corrupt.sqlite3"
        corrupt.write_text("not a database")
        with self.assertRaises(StoreError):
            SessionStore(corrupt)

    def test_unknown_schema_version_is_rejected(self):
        with sqlite3.connect(self.path) as db:
            db.execute("PRAGMA user_version=987")
        with self.assertRaises(StoreError):
            SessionStore(self.path)

    def test_invitation_single_use_and_expiration_boundary(self):
        invitation = self.store.create_invitation(ttl_seconds=5)
        self.store.redeem(invitation.code)
        with self.assertRaises(InvalidInvitation):
            self.store.redeem(invitation.code)
        another = self.store.create_invitation(ttl_seconds=5)
        self.clock.value += 5
        with self.assertRaises(InvalidInvitation):
            self.store.redeem(another.code)

    def test_wrong_tokens_are_generic_and_never_echoed(self):
        for value in (None, 1, "x", "a" * 129, "a" * 40 + "\n", "ç" * 40, "a" * 40 + "'", "not_a_valid_credential_000000000000"):
            with self.subTest(kind=type(value).__name__):
                with self.assertRaises(InvalidSession) as context:
                    self.store.authenticate(value)
                if isinstance(value, str) and len(value) >= 24:
                    self.assertNotIn(value, str(context.exception))
                with self.assertRaises(InvalidSession):
                    self.store.refresh(value)
                self.assertFalse(self.store.revoke(value))
                with self.assertRaises(InvalidInvitation):
                    self.store.redeem(value)

    def test_access_expiration_and_token_purpose_separation(self):
        tokens = self.connect()
        for value in (tokens.refresh_token, self.store.create_invitation().code):
            with self.assertRaises(InvalidSession):
                self.store.authenticate(value)
        with self.assertRaises(InvalidSession):
            self.store.refresh(tokens.access_token)
        self.clock.value = tokens.access_expires_at
        with self.assertRaises(InvalidSession):
            self.store.authenticate(tokens.access_token)
        renewed = self.store.refresh(tokens.refresh_token)
        self.assertTrue(self.store.authenticate(renewed.access_token))

    def test_refresh_rotates_both_tokens_and_keeps_absolute_family_lifetime(self):
        first = self.connect()
        principal = self.store.authenticate(first.access_token)
        self.clock.value += 100
        second = self.store.refresh(first.refresh_token)
        self.assertNotEqual(first.access_token, second.access_token)
        self.assertNotEqual(first.refresh_token, second.refresh_token)
        self.assertEqual(first.refresh_expires_at, second.refresh_expires_at)
        self.assertEqual(principal, self.store.authenticate(second.access_token))
        with self.assertRaises(InvalidSession):
            self.store.authenticate(first.access_token)

    def test_refresh_replay_commits_revocation_of_entire_family(self):
        first = self.connect()
        second = self.store.refresh(first.refresh_token)
        with self.assertRaises(InvalidSession):
            self.store.refresh(first.refresh_token)
        with self.assertRaises(InvalidSession):
            self.store.authenticate(second.access_token)
        with self.assertRaises(InvalidSession):
            self.store.refresh(second.refresh_token)
        restarted = SessionStore(self.path, clock=self.clock)
        with self.assertRaises(InvalidSession):
            restarted.authenticate(second.access_token)

    def test_refresh_expiration_and_final_access_clip(self):
        store = SessionStore(self.path, access_ttl_seconds=10, refresh_ttl_seconds=20, clock=self.clock)
        tokens = self.connect(store)
        self.clock.value += 15
        renewed = store.refresh(tokens.refresh_token)
        self.assertEqual(tokens.refresh_expires_at, renewed.access_expires_at)
        self.clock.value = tokens.refresh_expires_at
        with self.assertRaises(InvalidSession):
            store.refresh(renewed.refresh_token)
        with self.assertRaises(InvalidSession):
            store.authenticate(renewed.access_token)

    def test_logout_revokes_family_and_is_idempotent(self):
        first = self.connect()
        second = self.store.refresh(first.refresh_token)
        self.assertTrue(self.store.revoke(first.refresh_token))
        self.assertTrue(self.store.revoke(second.refresh_token))
        with self.assertRaises(InvalidSession):
            self.store.authenticate(second.access_token)
        with self.assertRaises(InvalidSession):
            self.store.refresh(second.refresh_token)

    def test_account_suspension_does_not_revoke_another_account(self):
        first = self.connect()
        other = self.connect()
        account = self.store.authenticate(first.access_token).account_id
        self.assertTrue(self.store.revoke_account(account))
        self.assertTrue(self.store.revoke_account(account))
        self.assertFalse(self.store.revoke_account("invalid"))
        with self.assertRaises(InvalidSession):
            self.store.authenticate(first.access_token)
        with self.assertRaises(InvalidSession):
            self.store.refresh(first.refresh_token)
        self.assertTrue(self.store.authenticate(other.access_token))

    def test_sessions_and_quota_survive_server_restart(self):
        tokens = self.connect()
        principal = self.store.authenticate(tokens.access_token)
        self.store.reserve(principal, 50)
        restarted = SessionStore(self.path, clock=self.clock)
        self.assertEqual(principal, restarted.authenticate(tokens.access_token))
        self.assertEqual(1, restarted.usage(principal).account_requests)
        self.assertEqual(50, restarted.usage(principal).account_units)
        self.assertTrue(restarted.refresh(tokens.refresh_token))

    def test_account_request_quota_has_no_partial_global_increment(self):
        store = SessionStore(self.path, account_daily_requests=2, clock=self.clock)
        p = store.authenticate(self.connect(store).access_token)
        for _ in range(2):
            store.reserve(p, 10)
        with self.assertRaises(QuotaExceeded) as context:
            store.reserve(p, 10)
        self.assertEqual("account", context.exception.scope)
        self.assertGreater(context.exception.retry_after_seconds, 0)
        usage = store.usage(p)
        self.assertEqual(2, usage.account_requests)
        self.assertEqual(2, usage.global_requests)
        self.assertEqual(20, usage.account_units)

    def test_global_request_quota_is_shared_across_accounts(self):
        store = SessionStore(self.path, global_daily_requests=1, clock=self.clock)
        first = store.authenticate(self.connect(store).access_token)
        second = store.authenticate(self.connect(store).access_token)
        store.reserve(first, 1)
        with self.assertRaises(QuotaExceeded) as context:
            store.reserve(second, 1)
        self.assertEqual("global", context.exception.scope)
        self.assertEqual(0, store.usage(second).account_requests)

    def test_unit_allowance_enforced_before_request_is_reserved(self):
        store = SessionStore(self.path, account_daily_units=20, global_daily_units=25, clock=self.clock)
        first = store.authenticate(self.connect(store).access_token)
        second = store.authenticate(self.connect(store).access_token)
        store.reserve(first, 20)
        with self.assertRaises(QuotaExceeded) as context:
            store.reserve(first, 1)
        self.assertEqual("account", context.exception.scope)
        with self.assertRaises(QuotaExceeded) as context:
            store.reserve(second, 6)
        self.assertEqual("global", context.exception.scope)
        store.reserve(second, 5)
        self.assertEqual(25, store.usage(first).global_units)

    def test_quota_rollover_uses_utc_day(self):
        tokens = self.connect()
        p = self.store.authenticate(tokens.access_token)
        before = self.store.reserve(p, 100)
        self.clock.value = ((int(self.clock.value) // 86_400) + 1) * 86_400
        after = self.store.reserve(p, 20)
        self.assertNotEqual(before.day, after.day)
        self.assertEqual(1, after.account_requests)
        self.assertEqual(20, after.account_units)

    def test_principal_cannot_impersonate_another_account(self):
        p = self.store.authenticate(self.connect().access_token)
        other = self.store.authenticate(self.connect().access_token)
        with self.assertRaises(InvalidSession):
            self.store.reserve(replace(p, account_id=other.account_id), 1)
        with self.assertRaises(InvalidSession):
            self.store.usage(None)
        self.store.revoke_account(p.account_id)
        with self.assertRaises(InvalidSession):
            self.store.reserve(p, 1)

    def test_concurrent_invitation_redemptions_succeed_once(self):
        invitation = self.store.create_invitation()
        barrier = threading.Barrier(8)
        def redeem(_):
            barrier.wait()
            try:
                return self.store.redeem(invitation.code)
            except InvalidInvitation:
                return None
        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(redeem, range(8)))
        self.assertEqual(1, sum(x is not None for x in results))

    def test_concurrent_refresh_reuse_cannot_leave_a_live_family(self):
        tokens = self.connect()
        barrier = threading.Barrier(2)
        def renew(_):
            barrier.wait()
            try:
                return self.store.refresh(tokens.refresh_token)
            except InvalidSession:
                return None
        with ThreadPoolExecutor(max_workers=2) as pool:
            result = list(pool.map(renew, range(2)))
        winners = [value for value in result if value is not None]
        self.assertEqual(1, len(winners))
        # The replay invalidates even the credential returned by the first call.
        with self.assertRaises(InvalidSession):
            self.store.authenticate(winners[0].access_token)
        with self.assertRaises(InvalidSession):
            self.store.refresh(winners[0].refresh_token)

    def test_concurrent_quota_requests_do_not_exceed_persistent_limit(self):
        store = SessionStore(self.path, account_daily_requests=3, clock=self.clock)
        p = store.authenticate(self.connect(store).access_token)
        barrier = threading.Barrier(10)
        def reserve(_):
            barrier.wait()
            try:
                store.reserve(p, 2)
                return True
            except QuotaExceeded:
                return False
        with ThreadPoolExecutor(max_workers=10) as pool:
            results = list(pool.map(reserve, range(10)))
        self.assertEqual(3, sum(results))
        self.assertEqual(3, store.usage(p).global_requests)
        self.assertEqual(6, store.usage(p).account_units)

    def test_two_store_instances_share_revocation_and_allowance(self):
        other = SessionStore(self.path, clock=self.clock)
        tokens = self.connect()
        p = other.authenticate(tokens.access_token)
        other.reserve(p, 7)
        self.assertEqual(7, self.store.usage(p).account_units)
        other.revoke(tokens.refresh_token)
        with self.assertRaises(InvalidSession):
            self.store.authenticate(tokens.access_token)

    def test_prune_keeps_replay_protection_until_family_expiry(self):
        first = self.connect()
        second = self.store.refresh(first.refresh_token)
        self.clock.value += 900
        self.store.prune()
        with self.assertRaises(InvalidSession):
            self.store.refresh(first.refresh_token)
        with self.assertRaises(InvalidSession):
            self.store.refresh(second.refresh_token)
        self.clock.value = second.refresh_expires_at
        self.store.prune()
        with sqlite3.connect(self.path) as db:
            for table in ("nexus_accounts", "nexus_sessions", "nexus_access", "nexus_refresh"):
                self.assertEqual(0, db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0])

    def test_clock_and_configuration_reject_invalid_values(self):
        for key in ("access_ttl_seconds", "refresh_ttl_seconds", "account_daily_requests", "global_daily_requests", "account_daily_units", "global_daily_units"):
            for value in (True, 0, -1, 1.5):
                with self.subTest(key=key, value=value):
                    with self.assertRaises(ValueError):
                        SessionStore(self.path, **{key: value})
        for value in (True, -1, float("nan"), float("inf")):
            with self.assertRaises(StoreError):
                SessionStore(self.path, clock=lambda: value)
        with self.assertRaises(ValueError):
            SessionStore(":memory:")
        with self.assertRaises(ValueError):
            SessionStore(self.path, access_ttl_seconds=60, refresh_ttl_seconds=59)
        p = self.store.authenticate(self.connect().access_token)
        for units in (True, 0, -1, 1.5):
            with self.assertRaises(ValueError):
                self.store.reserve(p, units)

    def test_operator_cli_writes_new_private_invite_without_stdout_secret(self):
        output = Path(self.directory.name) / "invitation.private"
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            code = main(["--db", str(self.path), "invite", "--output", str(output)])
        self.assertEqual(0, code)
        value = output.read_text().strip()
        self.assertNotIn(value, stdout.getvalue())
        self.assertEqual(0o600, stat.S_IMODE(output.stat().st_mode))
        self.assertTrue(SessionStore(self.path).redeem(value))
        with redirect_stdout(io.StringIO()):
            self.assertEqual(1, main(["--db", str(self.path), "invite", "--output", str(output)]))
        self.assertEqual(value, output.read_text().strip())

    def test_operator_cli_refuses_symlink_and_invalid_lifetime(self):
        target = Path(self.directory.name) / "existing"
        target.write_text("must remain")
        link = Path(self.directory.name) / "output"
        link.symlink_to(target)
        with redirect_stdout(io.StringIO()):
            self.assertEqual(1, main(["--db", str(self.path), "invite", "--output", str(link)]))
            invalid = Path(self.directory.name) / "invalid"
            self.assertEqual(1, main(["--db", str(self.path), "invite", "--output", str(invalid), "--ttl-seconds", "0"]))
            self.assertFalse(invalid.exists())
        self.assertEqual("must remain", target.read_text())


if __name__ == "__main__":
    unittest.main()
