"""Private, persistent account sessions for an operator-managed NEXUS AI pilot.

This module never stores provider credentials or conversation content. Invitations
are single-use operator-issued credentials. They are not an unrestricted public
signup mechanism. Token hashes, revocation and daily allowances survive restart.
The HTTP/TLS layer must also limit unauthenticated enrollment/refresh requests.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime, timezone
import hashlib
import math
import os
from pathlib import Path
import re
import secrets
import sqlite3
import stat
import time
from typing import Callable, Iterator


class StoreError(RuntimeError):
    """Safe error with no SQLite path, token, provider key or request content."""


class InvalidSession(StoreError):
    pass


class InvalidInvitation(StoreError):
    pass


class QuotaExceeded(StoreError):
    def __init__(self, *, scope: str, retry_after_seconds: int):
        super().__init__("A cota diária de IA foi atingida. Tente novamente após a renovação.")
        self.scope = scope
        self.retry_after_seconds = retry_after_seconds


@dataclass(frozen=True)
class Invitation:
    code: str = field(repr=False)
    expires_at: int


@dataclass(frozen=True)
class SessionTokens:
    access_token: str = field(repr=False)
    refresh_token: str = field(repr=False)
    access_expires_at: int
    refresh_expires_at: int



@dataclass(frozen=True)
class Principal:
    account_id: str
    session_id: str


@dataclass(frozen=True)
class QuotaReservation:
    day: str
    reserved_units: int
    account_requests: int
    account_units: int
    global_requests: int
    global_units: int


_TOKEN = re.compile(r"[A-Za-z0-9_-]{24,128}\Z", re.ASCII)
_ACCOUNT = re.compile(r"[a-f0-9]{32}\Z", re.ASCII)
_SCHEMA_VERSION = 1
_GENERIC_SESSION = "Sessão ausente, inválida, revogada ou expirada. Conecte o NEXUS AI novamente."
_GENERIC_INVITATION = "Convite inválido, expirado ou já utilizado."


def _digest(kind: str, value: str) -> str:
    return hashlib.sha256((kind + ":" + value).encode("ascii")).hexdigest()


def _integer(value: int, name: str, maximum: int = 1_000_000_000) -> int:
    if type(value) is not int or not 1 <= value <= maximum:
        raise ValueError(f"{name} deve ser um inteiro positivo dentro do limite suportado.")
    return value


class SessionStore:
    """One database per deployment, one SQLite connection per transaction.

    `units` are a conservative operator-defined allowance, NOT measured billable
    tokens or dollars. Reserve input UTF-8 bytes + four times the maximum output tokens before
    each upstream request, including tool continuations. Reservations are never
    refunded automatically on timeout, disconnect or upstream error because an
    upstream may already have billed that work. A provider-side spending limit
    remains essential. Multiple replicas must share this exact database on a
    filesystem with reliable SQLite locking; use a proper shared database before
    horizontal deployment, rather than a per-replica local copy.
    """

    def __init__(
        self,
        path: str | Path,
        *,
        access_ttl_seconds: int = 900,
        refresh_ttl_seconds: int = 2_592_000,
        account_daily_requests: int = 100,
        global_daily_requests: int = 1_000,
        account_daily_units: int = 8_000_000,
        global_daily_units: int = 80_000_000,
        clock: Callable[[], float] = time.time,
    ) -> None:
        if str(path) == ":memory:":
            raise ValueError("Use um arquivo privado e persistente para as sessões.")
        self.path = Path(path).absolute()
        self.access_ttl_seconds = _integer(access_ttl_seconds, "access_ttl_seconds", 3_600)
        self.refresh_ttl_seconds = _integer(refresh_ttl_seconds, "refresh_ttl_seconds", 2_592_000)
        if self.refresh_ttl_seconds < self.access_ttl_seconds:
            raise ValueError("A sessão de renovação deve durar pelo menos o prazo de acesso.")
        self.account_daily_requests = _integer(account_daily_requests, "account_daily_requests")
        self.global_daily_requests = _integer(global_daily_requests, "global_daily_requests")
        self.account_daily_units = _integer(account_daily_units, "account_daily_units")
        self.global_daily_units = _integer(global_daily_units, "global_daily_units")
        self._clock = clock
        self._now()
        self._prepare_file()
        self._initialize()

    def _now(self) -> int:
        value = self._clock()
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 253_402_300_799:
            raise StoreError("O relógio do servidor de sessões não está disponível.")
        return int(value)

    def _prepare_file(self) -> None:
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            descriptor = os.open(self.path, os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0), 0o600)
            try:
                info = os.fstat(descriptor)
                if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid():
                    raise StoreError("O armazenamento de sessões deve ser um arquivo privado do operador.")
                os.fchmod(descriptor, 0o600)
            finally:
                os.close(descriptor)
        except OSError:
            raise StoreError("Não foi possível preparar o armazenamento privado de sessões.") from None

    @contextmanager
    def _transaction(self) -> Iterator[sqlite3.Connection]:
        connection: sqlite3.Connection | None = None
        try:
            connection = sqlite3.connect(self.path, timeout=5, isolation_level=None)
            connection.row_factory = sqlite3.Row
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("BEGIN IMMEDIATE")
            yield connection
            connection.commit()
        except sqlite3.Error:
            if connection is not None:
                connection.rollback()
            raise StoreError("O armazenamento de sessões está indisponível. Tente novamente.") from None
        except BaseException:
            if connection is not None:
                connection.rollback()
            raise
        finally:
            if connection is not None:
                connection.close()

    def _initialize(self) -> None:
        with self._transaction() as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
            expected = {"nexus_accounts", "nexus_invitations", "nexus_sessions", "nexus_access", "nexus_refresh", "nexus_usage"}
            if (version == 0 and tables) or version not in (0, _SCHEMA_VERSION) or (version == _SCHEMA_VERSION and tables != expected):
                raise StoreError("O esquema do armazenamento de sessões não é compatível.")
            # No executescript: it implicitly commits and would break initialization atomicity.
            statements = (
                "CREATE TABLE IF NOT EXISTS nexus_accounts (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, revoked_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS nexus_invitations (digest TEXT PRIMARY KEY, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, used_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS nexus_sessions (id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES nexus_accounts(id), created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS nexus_access (digest TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES nexus_sessions(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS nexus_refresh (digest TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES nexus_sessions(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL, used_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS nexus_usage (day TEXT NOT NULL, scope TEXT NOT NULL, requests INTEGER NOT NULL, units INTEGER NOT NULL, PRIMARY KEY(day, scope))",
                "CREATE INDEX IF NOT EXISTS nexus_access_session ON nexus_access(session_id)",
                "CREATE INDEX IF NOT EXISTS nexus_refresh_session ON nexus_refresh(session_id)",
                "CREATE INDEX IF NOT EXISTS nexus_sessions_account ON nexus_sessions(account_id)",
            )
            for statement in statements:
                db.execute(statement)
            db.execute(f"PRAGMA user_version={_SCHEMA_VERSION}")

    def create_invitation(self, *, ttl_seconds: int = 86_400) -> Invitation:
        ttl_seconds = _integer(ttl_seconds, "ttl_seconds", 604_800)
        now = self._now()
        code = "nx_i_" + secrets.token_urlsafe(20)  # 160 random bits; never a predictable short PIN.
        with self._transaction() as db:
            db.execute("INSERT INTO nexus_invitations VALUES (?, ?, ?, NULL)", (_digest("invite", code), now, now + ttl_seconds))
        return Invitation(code, now + ttl_seconds)

    def _mint(self, db: sqlite3.Connection, session_id: str, now: int, expires_at: int) -> SessionTokens:
        access = "nx_a_" + secrets.token_urlsafe(32)
        refresh = "nx_r_" + secrets.token_urlsafe(32)
        access_expires = min(now + self.access_ttl_seconds, expires_at)
        db.execute("INSERT INTO nexus_access VALUES (?, ?, ?)", (_digest("access", access), session_id, access_expires))
        db.execute("INSERT INTO nexus_refresh VALUES (?, ?, ?, NULL)", (_digest("refresh", refresh), session_id, expires_at))
        return SessionTokens(access, refresh, access_expires, expires_at)

    def redeem(self, invitation_code: str) -> SessionTokens:
        if not isinstance(invitation_code, str) or not _TOKEN.fullmatch(invitation_code):
            raise InvalidInvitation(_GENERIC_INVITATION)
        now = self._now()
        with self._transaction() as db:
            used = db.execute("UPDATE nexus_invitations SET used_at=? WHERE digest=? AND used_at IS NULL AND expires_at>?", (now, _digest("invite", invitation_code), now))
            if used.rowcount != 1:
                raise InvalidInvitation(_GENERIC_INVITATION)
            account = secrets.token_hex(16)
            session = secrets.token_hex(16)
            db.execute("INSERT INTO nexus_accounts VALUES (?, ?, NULL)", (account, now))
            db.execute("INSERT INTO nexus_sessions VALUES (?, ?, ?, ?, NULL)", (session, account, now, now + self.refresh_ttl_seconds))
            return self._mint(db, session, now, now + self.refresh_ttl_seconds)

    def authenticate(self, access_token: str) -> Principal:
        if not isinstance(access_token, str) or not _TOKEN.fullmatch(access_token):
            raise InvalidSession(_GENERIC_SESSION)
        now = self._now()
        with self._transaction() as db:
            row = db.execute(
                "SELECT s.id, s.account_id FROM nexus_access t JOIN nexus_sessions s ON t.session_id=s.id JOIN nexus_accounts a ON s.account_id=a.id WHERE t.digest=? AND t.expires_at>? AND s.expires_at>? AND s.revoked_at IS NULL AND a.revoked_at IS NULL",
                (_digest("access", access_token), now, now),
            ).fetchone()
            if row is None:
                raise InvalidSession(_GENERIC_SESSION)
            return Principal(row["account_id"], row["id"])

    def refresh(self, refresh_token: str) -> SessionTokens:
        if not isinstance(refresh_token, str) or not _TOKEN.fullmatch(refresh_token):
            raise InvalidSession(_GENERIC_SESSION)
        now = self._now()
        tokens: SessionTokens | None = None
        # On replay, revocation MUST commit. Raising inside the transaction would roll it back.
        with self._transaction() as db:
            row = db.execute(
                "SELECT r.session_id, r.used_at, r.expires_at, s.revoked_at, a.revoked_at AS account_revoked FROM nexus_refresh r JOIN nexus_sessions s ON r.session_id=s.id JOIN nexus_accounts a ON s.account_id=a.id WHERE r.digest=?",
                (_digest("refresh", refresh_token),),
            ).fetchone()
            if row is not None and row["used_at"] is not None:
                db.execute("UPDATE nexus_sessions SET revoked_at=COALESCE(revoked_at, ?) WHERE id=?", (now, row["session_id"]))
                db.execute("DELETE FROM nexus_access WHERE session_id=?", (row["session_id"],))
            elif row is not None and row["expires_at"] > now and row["revoked_at"] is None and row["account_revoked"] is None:
                db.execute("UPDATE nexus_refresh SET used_at=? WHERE digest=?", (now, _digest("refresh", refresh_token)))
                db.execute("DELETE FROM nexus_access WHERE session_id=?", (row["session_id"],))
                tokens = self._mint(db, row["session_id"], now, row["expires_at"])
        if tokens is None:
            raise InvalidSession(_GENERIC_SESSION)
        return tokens

    def revoke(self, refresh_token: str) -> bool:
        """Idempotent logout by a refresh credential; no authentication oracle."""
        if not isinstance(refresh_token, str) or not _TOKEN.fullmatch(refresh_token):
            return False
        now = self._now()
        with self._transaction() as db:
            row = db.execute("SELECT session_id FROM nexus_refresh WHERE digest=?", (_digest("refresh", refresh_token),)).fetchone()
            if row is None:
                return False
            db.execute("UPDATE nexus_sessions SET revoked_at=COALESCE(revoked_at, ?) WHERE id=?", (now, row["session_id"]))
            db.execute("DELETE FROM nexus_access WHERE session_id=?", (row["session_id"],))
            return True

    def revoke_account(self, account_id: str) -> bool:
        """Operator-side account suspension also prevents future refreshes."""
        if not isinstance(account_id, str) or not _ACCOUNT.fullmatch(account_id):
            return False
        now = self._now()
        with self._transaction() as db:
            result = db.execute("UPDATE nexus_accounts SET revoked_at=COALESCE(revoked_at, ?) WHERE id=?", (now, account_id))
            db.execute("UPDATE nexus_sessions SET revoked_at=COALESCE(revoked_at, ?) WHERE account_id=?", (now, account_id))
            db.execute("DELETE FROM nexus_access WHERE session_id IN (SELECT id FROM nexus_sessions WHERE account_id=?)", (account_id,))
            return result.rowcount == 1

    def _valid_principal(self, db: sqlite3.Connection, principal: Principal, now: int) -> None:
        if not isinstance(principal, Principal):
            raise InvalidSession(_GENERIC_SESSION)
        row = db.execute("SELECT s.id FROM nexus_sessions s JOIN nexus_accounts a ON s.account_id=a.id WHERE s.id=? AND s.account_id=? AND s.expires_at>? AND s.revoked_at IS NULL AND a.revoked_at IS NULL", (principal.session_id, principal.account_id, now)).fetchone()
        if row is None:
            raise InvalidSession(_GENERIC_SESSION)

    @staticmethod
    def _usage(db: sqlite3.Connection, day: str, scope: str) -> tuple[int, int]:
        row = db.execute("SELECT requests, units FROM nexus_usage WHERE day=? AND scope=?", (day, scope)).fetchone()
        return (row[0], row[1]) if row is not None else (0, 0)

    def reserve(self, principal: Principal, units: int) -> QuotaReservation:
        units = _integer(units, "units")
        now = self._now()
        day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
        retry_after = 86_400 - now % 86_400
        with self._transaction() as db:
            self._valid_principal(db, principal, now)
            ar, au = self._usage(db, day, principal.account_id)
            gr, gu = self._usage(db, day, "global")
            if ar + 1 > self.account_daily_requests or au + units > self.account_daily_units:
                raise QuotaExceeded(scope="account", retry_after_seconds=retry_after)
            if gr + 1 > self.global_daily_requests or gu + units > self.global_daily_units:
                raise QuotaExceeded(scope="global", retry_after_seconds=retry_after)
            for scope in (principal.account_id, "global"):
                db.execute("INSERT INTO nexus_usage VALUES (?, ?, 1, ?) ON CONFLICT(day, scope) DO UPDATE SET requests=requests+1, units=units+excluded.units", (day, scope, units))
            return QuotaReservation(day, units, ar + 1, au + units, gr + 1, gu + units)

    def usage(self, principal: Principal) -> QuotaReservation:
        now = self._now()
        day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
        with self._transaction() as db:
            self._valid_principal(db, principal, now)
            ar, au = self._usage(db, day, principal.account_id)
            gr, gu = self._usage(db, day, "global")
            return QuotaReservation(day, 0, ar, au, gr, gu)

    def prune(self) -> None:
        """Operator maintenance. Keep spent refresh hashes until family expiry."""
        now = self._now()
        oldest_day = datetime.fromtimestamp(max(0, now - 31 * 86_400), timezone.utc).date().isoformat()
        with self._transaction() as db:
            db.execute("DELETE FROM nexus_sessions WHERE expires_at<=?", (now,))
            db.execute("DELETE FROM nexus_accounts WHERE id NOT IN (SELECT account_id FROM nexus_sessions)")
            db.execute("DELETE FROM nexus_access WHERE expires_at<=?", (now,))
            db.execute("DELETE FROM nexus_invitations WHERE expires_at<=?", (now,))
            db.execute("DELETE FROM nexus_usage WHERE day<?", (oldest_day,))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Administração local de sessões NEXUS AI; não imprime credenciais.")
    parser.add_argument("--db", type=Path, required=True, help="Banco privado persistente do servidor")
    commands = parser.add_subparsers(dest="command", required=True)
    invite = commands.add_parser("invite", help="Criar convite único em arquivo privado")
    invite.add_argument("--output", type=Path, required=True, help="Novo arquivo privado; nunca um caminho publicado")
    invite.add_argument("--ttl-seconds", type=int, default=86_400)
    revoke = commands.add_parser("revoke-account", help="Revogar todas as sessões de uma conta")
    revoke.add_argument("--account-id", required=True)
    commands.add_parser("prune", help="Remover sessões e contadores expirados")
    args = parser.parse_args(argv)
    try:
        store = SessionStore(args.db)
        if args.command == "invite":
            # Reserve an exclusive file before consuming/creating any invite.
            descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            try:
                value = store.create_invitation(ttl_seconds=args.ttl_seconds)
                with os.fdopen(descriptor, "w", encoding="utf-8") as target:
                    descriptor = -1
                    target.write(value.code + "\n")
            except BaseException:
                if descriptor != -1:
                    os.close(descriptor)
                args.output.unlink(missing_ok=True)
                raise
            print("Convite criado em arquivo privado; compartilhe-o somente com a pessoa autorizada.")
        elif args.command == "revoke-account":
            if not store.revoke_account(args.account_id):
                raise ValueError("A conta indicada não existe ou é inválida.")
            print("Conta revogada.")
        else:
            store.prune()
            print("Registros expirados removidos.")
        return 0
    except (OSError, StoreError, ValueError):
        print("Não foi possível concluir a operação. Confira o banco privado, os limites e o arquivo de destino.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
