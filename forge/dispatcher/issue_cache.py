# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Issue claim and search caches with local locks
(§AR-forge-dispatcher-decomposition). The caches only reduce redundant GitHub
API calls; claiming stays optimistic and authoritative against live GitHub
state (§FS-forge-issue-resolution-goal).
"""

import errno
import hashlib
import json
import os
import sys
import tempfile
import threading
import time
import uuid
from urllib.parse import quote

from utility_scripts.stage_logger import log_stage

from dispatcher.config import (
    DEFAULT_ISSUE_CLAIM_CACHE_TTL_SECONDS,
    DEFAULT_ISSUE_SEARCH_CACHE_TTL_SECONDS,
    ISSUE_CLAIM_CACHE_FILENAME,
    ISSUE_CLAIM_CACHE_LOCK_FILENAME,
    ISSUE_CLAIM_CACHE_REASONS,
    ISSUE_CLAIM_CACHE_VERSION,
    ISSUE_CLAIM_LOCK_DIRNAME,
    ISSUE_SEARCH_CACHE_FILENAME,
    ISSUE_SEARCH_CACHE_LOCK_FILENAME,
    ISSUE_SEARCH_CACHE_VERSION,
    REPO,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.records import CachedIssueClaimSkip, IssueClaimCacheObservation

try:
    import fcntl
except ImportError:
    fcntl = None


held_issue_claim_lock_numbers: set[int] = set()
held_issue_claim_lock_guard = threading.Lock()

class LocalIssueClaimLock:
    """Non-blocking per-issue lock for scripts running as the same GitHub user."""

    def __init__(self, issue_number: int):
        self.issue_number = issue_number
        self.lock_path = get_issue_claim_lock_path(issue_number)
        self.lock_file = None
        self.fallback_lock_path = f"{self.lock_path}.exclusive"

    def acquire(self) -> bool:
        with held_issue_claim_lock_guard:
            if self.issue_number in held_issue_claim_lock_numbers:
                return False
            held_issue_claim_lock_numbers.add(self.issue_number)

        try:
            os.makedirs(os.path.dirname(self.lock_path), exist_ok=True)
            if fcntl is None:
                return self._acquire_exclusive_file_lock()
            return self._acquire_fcntl_lock()
        except Exception:
            self._forget_process_lock()
            raise

    def release(self) -> None:
        if self.lock_file is None:
            self._forget_process_lock()
            return

        try:
            if fcntl is not None:
                fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        finally:
            self.lock_file.close()
            self.lock_file = None
            if fcntl is None:
                if os.path.exists(self.fallback_lock_path):
                    os.unlink(self.fallback_lock_path)
            self._forget_process_lock()

    def _acquire_fcntl_lock(self) -> bool:
        self.lock_file = open(self.lock_path, "a+", encoding="utf-8")
        try:
            fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            self.lock_file.close()
            self.lock_file = None
            self._forget_process_lock()
            if exc.errno in (errno.EACCES, errno.EAGAIN):
                return False
            raise

        self._write_lock_owner()
        return True

    def _acquire_exclusive_file_lock(self) -> bool:
        try:
            fd = os.open(self.fallback_lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        except FileExistsError:
            self._forget_process_lock()
            return False

        self.lock_file = os.fdopen(fd, "w", encoding="utf-8")
        self._write_lock_owner()
        return True

    def _write_lock_owner(self) -> None:
        self.lock_file.seek(0)
        self.lock_file.truncate()
        self.lock_file.write(f"pid={os.getpid()} issue={self.issue_number}\n")
        self.lock_file.flush()

    def _forget_process_lock(self) -> None:
        with held_issue_claim_lock_guard:
            held_issue_claim_lock_numbers.discard(self.issue_number)


class LocalIssueClaimCacheWriterLock:
    """Short-lived exclusive lock for atomic issue-claim cache updates."""

    def __init__(self):
        self.lock_path = get_issue_claim_cache_lock_path()
        self.lock_file = None
        self.fallback_lock_path = f"{self.lock_path}.exclusive"

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, _exc_type, _exc, _traceback) -> None:
        self.release()

    def acquire(self) -> None:
        os.makedirs(os.path.dirname(self.lock_path), exist_ok=True)
        if fcntl is None:
            self._acquire_exclusive_file_lock()
            return
        self.lock_file = open(self.lock_path, "a+", encoding="utf-8")
        fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX)

    def release(self) -> None:
        if self.lock_file is None:
            return
        try:
            if fcntl is not None:
                fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        finally:
            self.lock_file.close()
            self.lock_file = None
            if fcntl is None and os.path.exists(self.fallback_lock_path):
                os.unlink(self.fallback_lock_path)

    def _acquire_exclusive_file_lock(self) -> None:
        while True:
            try:
                fd = os.open(self.fallback_lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                self.lock_file = os.fdopen(fd, "w", encoding="utf-8")
                return
            except FileExistsError:
                time.sleep(0.05)


class LocalIssueSearchCacheWriterLock:
    """Short-lived exclusive lock for shared issue-search cache updates."""

    def __init__(self):
        self.lock_path = get_issue_search_cache_lock_path()
        self.lock_file = None
        self.fallback_lock_path = f"{self.lock_path}.exclusive"

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, _exc_type, _exc, _traceback) -> None:
        self.release()

    def acquire(self) -> None:
        os.makedirs(os.path.dirname(self.lock_path), exist_ok=True)
        if fcntl is None:
            self._acquire_exclusive_file_lock()
            return
        self.lock_file = open(self.lock_path, "a+", encoding="utf-8")
        fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX)

    def release(self) -> None:
        if self.lock_file is None:
            return
        try:
            if fcntl is not None:
                fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        finally:
            self.lock_file.close()
            self.lock_file = None
            if fcntl is None and os.path.exists(self.fallback_lock_path):
                os.unlink(self.fallback_lock_path)

    def _acquire_exclusive_file_lock(self) -> None:
        while True:
            try:
                fd = os.open(self.fallback_lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                self.lock_file = os.fdopen(fd, "w", encoding="utf-8")
                return
            except FileExistsError:
                time.sleep(0.05)


def get_issue_claim_locks_root() -> str:
    """Return the shared local directory for issue claim locks."""
    return os.path.join(
        tempfile.gettempdir(),
        ISSUE_CLAIM_LOCK_DIRNAME,
        quote(REPO, safe=""),
    )


def get_issue_claim_lock_path(issue_number: int) -> str:
    """Return the local lock file path for an issue number."""
    return os.path.join(get_issue_claim_locks_root(), f"issue-{issue_number}.lock")


def get_issue_claim_cache_path() -> str:
    """Return the shared local issue-claim cache path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_CLAIM_CACHE_FILENAME)


def get_issue_claim_cache_lock_path() -> str:
    """Return the shared local issue-claim cache writer lock path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_CLAIM_CACHE_LOCK_FILENAME)


def get_issue_search_cache_path() -> str:
    """Return the shared local issue-search cache path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_SEARCH_CACHE_FILENAME)


def get_issue_search_cache_lock_path() -> str:
    """Return the shared local issue-search cache writer lock path."""
    return os.path.join(get_issue_claim_locks_root(), ISSUE_SEARCH_CACHE_LOCK_FILENAME)


def try_acquire_issue_claim_lock(issue_number: int) -> LocalIssueClaimLock | None:
    """Acquire a local per-issue claim lock, or return None if another local runner holds it."""
    claim_lock = LocalIssueClaimLock(issue_number)
    if claim_lock.acquire():
        return claim_lock
    return None


def is_issue_claim_cache_enabled() -> bool:
    """Return True when the shared local issue-claim cache is enabled."""
    if is_fixture_testing_enabled():
        return False
    return os.environ.get("FORGE_ISSUE_CLAIM_CACHE", "1") != "0"


def get_issue_claim_cache_ttl_seconds() -> int:
    """Return the issue-claim cache TTL in seconds."""
    raw_value = os.environ.get("FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS")
    if raw_value is None or raw_value == "":
        return DEFAULT_ISSUE_CLAIM_CACHE_TTL_SECONDS
    try:
        value = int(raw_value)
    except ValueError:
        print("ERROR: FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    if value < 0:
        print("ERROR: FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    return value


def _read_issue_claim_cache_payload() -> dict | None:
    cache_path = get_issue_claim_cache_path()
    try:
        with open(cache_path, "r", encoding="utf-8") as cache_file:
            payload = json.load(cache_file)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("version") != ISSUE_CLAIM_CACHE_VERSION:
        return None
    if payload.get("repo") != REPO:
        return None
    if not isinstance(payload.get("entries"), dict):
        return None
    return payload


def _parse_issue_claim_cache_entry(
        issue_number: int,
        entry: dict,
        now: float,
        ttl_seconds: int,
) -> CachedIssueClaimSkip | None:
    if not isinstance(entry, dict):
        return None
    reason = entry.get("reason")
    if reason not in ISSUE_CLAIM_CACHE_REASONS:
        return None
    try:
        observed_at_epoch = float(entry.get("observed_at_epoch"))
    except (TypeError, ValueError):
        return None
    if now - observed_at_epoch > ttl_seconds:
        return None

    assignee_values = entry.get("assignees", [])
    if not isinstance(assignee_values, list):
        assignee_values = []
    assignees = tuple(
        assignee
        for assignee in assignee_values
        if isinstance(assignee, str) and assignee
    )
    blocker_values = entry.get("open_blockers", [])
    if not isinstance(blocker_values, list):
        blocker_values = []
    open_blockers = tuple(
        blocker
        for blocker in blocker_values
        if isinstance(blocker, int)
    )
    project_status = entry.get("project_status")
    if project_status is not None and not isinstance(project_status, str):
        project_status = None

    return CachedIssueClaimSkip(
        issue_number=issue_number,
        reason=reason,
        observed_at_epoch=observed_at_epoch,
        assignees=assignees,
        project_status=project_status,
        open_blockers=open_blockers,
    )


def read_issue_claim_cache(now: float | None = None, require_fresh_cache: bool = True) -> dict[int, CachedIssueClaimSkip]:
    """Read fresh issue-claim cache entries without taking a lock."""
    if not is_issue_claim_cache_enabled():
        return {}
    ttl_seconds = get_issue_claim_cache_ttl_seconds()
    if ttl_seconds <= 0:
        return {}
    now = time.time() if now is None else now
    payload = _read_issue_claim_cache_payload()
    if payload is None:
        return {}
    try:
        updated_at_epoch = float(payload.get("updated_at_epoch"))
    except (TypeError, ValueError):
        return {}
    if require_fresh_cache and now - updated_at_epoch > ttl_seconds:
        return {}

    cache: dict[int, CachedIssueClaimSkip] = {}
    for issue_number_text, entry in payload.get("entries", {}).items():
        try:
            issue_number = int(issue_number_text)
        except (TypeError, ValueError):
            continue
        cached_skip = _parse_issue_claim_cache_entry(issue_number, entry, now, ttl_seconds)
        if cached_skip is not None:
            cache[issue_number] = cached_skip
    return cache


def _write_issue_claim_cache_entries(entries: dict[int, CachedIssueClaimSkip], updated_at_epoch: float) -> None:
    cache_path = get_issue_claim_cache_path()
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    payload = {
        "version": ISSUE_CLAIM_CACHE_VERSION,
        "repo": REPO,
        "updated_at_epoch": updated_at_epoch,
        "entries": {
            str(issue_number): {
                "observed_at_epoch": cached_skip.observed_at_epoch,
                "reason": cached_skip.reason,
                "assignees": list(cached_skip.assignees),
                "project_status": cached_skip.project_status,
                "open_blockers": list(cached_skip.open_blockers),
            }
            for issue_number, cached_skip in sorted(entries.items())
        },
    }
    temp_path = f"{cache_path}.{os.getpid()}.{uuid.uuid4().hex}.tmp"
    try:
        with open(temp_path, "w", encoding="utf-8") as cache_file:
            json.dump(payload, cache_file, sort_keys=True)
            cache_file.write("\n")
            cache_file.flush()
            os.fsync(cache_file.fileno())
        os.replace(temp_path, cache_path)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)


def record_issue_claim_cache_observations(
        observations: list[IssueClaimCacheObservation],
        now: float | None = None,
) -> None:
    """Record negative issue-claim observations in the shared local cache."""
    observations = [
        observation
        for observation in observations
        if observation.reason in ISSUE_CLAIM_CACHE_REASONS
    ]
    if not observations or not is_issue_claim_cache_enabled() or get_issue_claim_cache_ttl_seconds() <= 0:
        return
    now = time.time() if now is None else now
    with LocalIssueClaimCacheWriterLock():
        cache = read_issue_claim_cache(now, require_fresh_cache=False)
        for observation in observations:
            cache[observation.issue_number] = CachedIssueClaimSkip(
                issue_number=observation.issue_number,
                reason=observation.reason,
                observed_at_epoch=now,
                assignees=observation.assignees,
                project_status=observation.project_status,
                open_blockers=observation.open_blockers,
            )
        _write_issue_claim_cache_entries(cache, now)


def invalidate_issue_claim_cache_entry(issue_number: int, now: float | None = None) -> None:
    """Remove one issue from the shared local issue-claim cache."""
    if not is_issue_claim_cache_enabled() or get_issue_claim_cache_ttl_seconds() <= 0:
        return
    now = time.time() if now is None else now
    with LocalIssueClaimCacheWriterLock():
        cache = read_issue_claim_cache(now, require_fresh_cache=False)
        if issue_number not in cache:
            return
        cache.pop(issue_number, None)
        _write_issue_claim_cache_entries(cache, now)


def _remove_file_if_exists(path: str) -> bool:
    try:
        os.unlink(path)
        return True
    except FileNotFoundError:
        return False


def clear_issue_claim_cache() -> bool:
    """Delete the shared local issue-claim cache file."""
    with LocalIssueClaimCacheWriterLock():
        return _remove_file_if_exists(get_issue_claim_cache_path())


def is_issue_search_cache_enabled() -> bool:
    """Return True when the shared local issue-search cache is enabled."""
    return os.environ.get("FORGE_ISSUE_SEARCH_CACHE", "1") != "0"


def get_issue_search_cache_ttl_seconds() -> int:
    """Return the issue-search cache TTL in seconds."""
    raw_value = os.environ.get("FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS")
    if raw_value is None or raw_value == "":
        return DEFAULT_ISSUE_SEARCH_CACHE_TTL_SECONDS
    try:
        value = int(raw_value)
    except ValueError:
        print("ERROR: FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    if value < 0:
        print("ERROR: FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    return value


def read_issue_search_cache_payload() -> dict | None:
    cache_path = get_issue_search_cache_path()
    try:
        with open(cache_path, "r", encoding="utf-8") as cache_file:
            payload = json.load(cache_file)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("version") != ISSUE_SEARCH_CACHE_VERSION:
        return None
    if payload.get("repo") != REPO:
        return None
    if not isinstance(payload.get("pages"), dict):
        return None
    if not isinstance(payload.get("counts"), dict):
        return None
    return payload


def _empty_issue_search_cache_payload(now: float) -> dict:
    return {
        "version": ISSUE_SEARCH_CACHE_VERSION,
        "repo": REPO,
        "updated_at_epoch": now,
        "pages": {},
        "counts": {},
    }


def read_issue_search_cache_payload_or_empty(now: float) -> dict:
    return read_issue_search_cache_payload() or _empty_issue_search_cache_payload(now)


def write_issue_search_cache_payload(payload: dict, updated_at_epoch: float) -> None:
    cache_path = get_issue_search_cache_path()
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    payload["version"] = ISSUE_SEARCH_CACHE_VERSION
    payload["repo"] = REPO
    payload["updated_at_epoch"] = updated_at_epoch
    payload.setdefault("pages", {})
    payload.setdefault("counts", {})

    temp_path = f"{cache_path}.{os.getpid()}.{uuid.uuid4().hex}.tmp"
    try:
        with open(temp_path, "w", encoding="utf-8") as cache_file:
            json.dump(payload, cache_file, sort_keys=True)
            cache_file.write("\n")
            cache_file.flush()
            os.fsync(cache_file.fileno())
        os.replace(temp_path, cache_path)
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)


def clear_issue_search_cache() -> bool:
    """Delete the shared local issue-search cache file."""
    with LocalIssueSearchCacheWriterLock():
        return _remove_file_if_exists(get_issue_search_cache_path())


def clear_issue_caches() -> None:
    """Delete local issue queue caches used by work-queue scanning."""
    removed_claim_cache = clear_issue_claim_cache()
    removed_search_cache = clear_issue_search_cache()
    print()
    log_stage(
        "issue-cache",
        f"Cleared issue claim cache at {get_issue_claim_cache_path()} "
        f"({'removed' if removed_claim_cache else 'not present'})",
    )
    log_stage(
        "issue-cache",
        f"Cleared issue search cache at {get_issue_search_cache_path()} "
        f"({'removed' if removed_search_cache else 'not present'})",
    )


def build_issue_search_cache_key(*parts: object) -> str:
    """Return a stable compact cache key for a GitHub issue-search request."""
    key_payload = json.dumps(parts, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(key_payload.encode("utf-8")).hexdigest()


def _is_fresh_issue_search_entry(entry: dict, now: float, ttl_seconds: int) -> bool:
    try:
        observed_at_epoch = float(entry.get("observed_at_epoch"))
    except (TypeError, ValueError):
        return False
    return now - observed_at_epoch <= ttl_seconds


def get_cached_issue_search_page(
        payload: dict,
        cache_key: str,
        now: float,
        ttl_seconds: int,
) -> list[dict] | None:
    entry = payload.get("pages", {}).get(cache_key)
    if not isinstance(entry, dict) or not _is_fresh_issue_search_entry(entry, now, ttl_seconds):
        return None
    issues = entry.get("issues")
    if not isinstance(issues, list):
        return None
    return [
        issue
        for issue in issues
        if isinstance(issue, dict) and isinstance(issue.get("number"), int)
    ]


def set_cached_issue_search_page(payload: dict, cache_key: str, issues: list[dict], now: float) -> None:
    payload.setdefault("pages", {})[cache_key] = {
        "observed_at_epoch": now,
        "issues": issues,
    }


def get_cached_issue_search_count(
        payload: dict,
        cache_key: str,
        now: float,
        ttl_seconds: int,
) -> int | None:
    entry = payload.get("counts", {}).get(cache_key)
    if not isinstance(entry, dict) or not _is_fresh_issue_search_entry(entry, now, ttl_seconds):
        return None
    total_count = entry.get("total_count")
    if not isinstance(total_count, int):
        return None
    return total_count


def set_cached_issue_search_count(payload: dict, cache_key: str, total_count: int, now: float) -> None:
    payload.setdefault("counts", {})[cache_key] = {
        "observed_at_epoch": now,
        "total_count": total_count,
    }
