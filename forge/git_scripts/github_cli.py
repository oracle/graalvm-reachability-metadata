# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""GitHub CLI transport: `gh` invocation, retries, and query logging.

Owns the typed GitHub failures, transient-failure and rate-limit detection, the
retrying `gh` runners, and redacted query tracing. Git and repository helpers
stay in `common_git.py`, which re-exports this module's public names.
"""

import inspect
import json
import os
import shutil
import subprocess
import sys
import time
from typing import Any, Callable, Iterable


class GitHubError(RuntimeError):
    """A GitHub API failure that survived common_git's retries.

    Treated as an external failure: the issue automation should release its claim
    for a later retry rather than apply a `human-intervention` follow-up.
    """


class GitHubRateLimitExceeded(GitHubError):
    """GitHub reported an exhausted API rate-limit bucket.

    A `GitHubError` whose quota semantics also tell the top-level run to stop and
    retry after the reset (exit code 75), unlike a one-off transient failure.
    """


GITHUB_TRANSIENT_RETRY_ATTEMPTS = 5
GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS = 2.0
GITHUB_LOG_REDACTED_FLAGS = {
    "--body",
    "--body-file",
    "--input",
}
GITHUB_LOG_REDACTED_KEYS = {
    "body",
}
GITHUB_QUERY_LOG_ENV_VAR = "FORGE_LOG_GITHUB_QUERIES"


def ensure_gh_authenticated() -> None:
    """Ensure the GitHub CLI (gh) is installed and authenticated for github.com."""
    if shutil.which("gh") is None:
        print(
            "The GitHub CLI (gh) is required to create a PR. "
            "Install it from https://cli.github.com/ and run 'gh auth login'."
        )
        sys.exit(1)
    try:
        gh("auth", "status", "--hostname", "github.com")
    except subprocess.CalledProcessError:
        print("GitHub CLI is not authenticated for github.com. Run 'gh auth login' and try again.")
        sys.exit(1)


def is_github_rate_limit_text(text: str) -> bool:
    """Return True when GitHub CLI output describes an exhausted API rate limit."""
    return (
        "API rate limit exceeded" in text
        or "API rate limit already exceeded" in text
        or "secondary rate limit" in text
        or "RATE_LIMITED" in text
    )


def is_github_rate_limit_errors(errors: object) -> bool:
    """Return True when a GraphQL errors payload reports an exhausted API rate limit."""
    if not isinstance(errors, list):
        return False
    for error in errors:
        if not isinstance(error, dict):
            continue
        if error.get("type") == "RATE_LIMITED":
            return True
        message = error.get("message")
        if isinstance(message, str) and is_github_rate_limit_text(message):
            return True
    return False


def is_github_transient_failure_text(text: str) -> bool:
    """Return True when GitHub CLI output describes a retryable server or network failure."""
    normalized_text = text.lower()
    return (
        "http 500" in normalized_text
        or "http 502" in normalized_text
        or "http 503" in normalized_text
        or "http 504" in normalized_text
        or "bad gateway" in normalized_text
        or "gateway timeout" in normalized_text
        or "service unavailable" in normalized_text
        or "temporarily unavailable" in normalized_text
        or "connection reset" in normalized_text
        or "tls handshake timeout" in normalized_text
        or "i/o timeout" in normalized_text
        or "context deadline exceeded" in normalized_text
    )


def is_github_transient_errors(errors: object) -> bool:
    """Return True when a GraphQL errors payload reports a retryable GitHub failure."""
    if not isinstance(errors, list):
        return False
    for error in errors:
        if not isinstance(error, dict):
            continue
        error_type = error.get("type")
        if error_type in ("INTERNAL", "SERVICE_UNAVAILABLE", "TIMEOUT"):
            return True
        message = error.get("message")
        if isinstance(message, str) and is_github_transient_failure_text(message):
            return True
    return False


def github_error_text_from_exception(exc: subprocess.CalledProcessError) -> str:
    return "\n".join(
        text.strip()
        for text in (exc.stderr, exc.stdout)
        if isinstance(text, str) and text.strip()
    )


def format_github_retry_reason(reason: str) -> str:
    for line in reason.splitlines():
        line = line.strip()
        if line:
            return line[:200]
    return "empty GitHub response"


def github_retry_delay_seconds(attempt: int) -> float:
    return GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS * (2 ** (attempt - 1))


def log_github_transient_retry(reason: str, attempt: int, max_attempts: int, quiet: bool) -> None:
    if quiet:
        return
    delay = github_retry_delay_seconds(attempt)
    print(
        "ERROR: GitHub API transient failure: "
        f"{format_github_retry_reason(reason)}. "
        f"Retrying in {delay:.1f}s (attempt {attempt + 1}/{max_attempts}).",
        file=sys.stderr,
    )


def split_github_arg_key_value(arg: str) -> tuple[str, str] | None:
    if "=" not in arg:
        return None
    key, value = arg.split("=", 1)
    if not key or not value:
        return None
    return key, value


def format_github_log_arg(arg: str) -> str:
    key_value = split_github_arg_key_value(arg)
    if key_value is None:
        return arg
    key, value = key_value
    if key.lower() in GITHUB_LOG_REDACTED_KEYS:
        return f"{key}=<redacted>"
    return f"{key}={value}"


def format_github_log_args(args: Iterable[str]) -> list[str]:
    formatted_args: list[str] = []
    redact_next = False
    for arg in args:
        if redact_next:
            formatted_args.append("<redacted>")
            redact_next = False
            continue
        formatted_args.append(format_github_log_arg(arg))
        if arg in GITHUB_LOG_REDACTED_FLAGS:
            redact_next = True
    return formatted_args


def should_log_github_queries() -> bool:
    """Return True when GitHub CLI query tracing is explicitly enabled."""
    return os.environ.get(GITHUB_QUERY_LOG_ENV_VAR) == "1"


def log_github_query(args: Iterable[str]) -> None:
    """Print one console line for a GitHub CLI query."""
    if not should_log_github_queries():
        return
    formatted_args = format_github_log_args(args)
    print(f"[github-query] gh {' '.join(formatted_args)}")


def gh_runner_accepts_max_attempts(gh_runner: Callable[..., subprocess.CompletedProcess]) -> bool:
    try:
        signature = inspect.signature(gh_runner)
    except (TypeError, ValueError):
        return False
    return (
        "max_attempts" in signature.parameters
        or any(parameter.kind == inspect.Parameter.VAR_KEYWORD for parameter in signature.parameters.values())
    )


def run_gh_runner_once(
        gh_runner: Callable[..., subprocess.CompletedProcess],
        args: tuple[str, ...],
        quiet: bool,
) -> subprocess.CompletedProcess:
    if gh_runner_accepts_max_attempts(gh_runner):
        return gh_runner(*args, quiet=quiet, max_attempts=1)
    return gh_runner(*args, quiet=quiet)


def gh(
        *args: str,
        check: bool = True,
        input_text: str | None = None,
        cwd=None,
        quiet: bool = False,
        max_attempts: int = GITHUB_TRANSIENT_RETRY_ATTEMPTS,
) -> subprocess.CompletedProcess:
    """Run a gh CLI command and return the completed process."""
    if max_attempts < 1:
        raise ValueError("max_attempts must be at least 1")
    cmd = ["gh", *args]
    env = {**os.environ, "GH_PROMPT_DISABLED": "1", "GH_PAGER": ""}
    if not quiet:
        log_github_query(args)
    for attempt in range(1, max_attempts + 1):
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
            input=input_text,
            cwd=cwd,
        )
        if result.returncode == 0:
            return result

        error_text = "\n".join(part for part in (result.stderr, result.stdout) if part)
        if check and is_github_rate_limit_text(error_text):
            raise GitHubRateLimitExceeded("GitHub API rate limit exceeded")
        if is_github_transient_failure_text(error_text):
            if attempt < max_attempts:
                log_github_transient_retry(error_text, attempt, max_attempts, quiet)
                time.sleep(github_retry_delay_seconds(attempt))
                continue
            # Only type the failure when this loop owns the retries; a single attempt
            # (max_attempts == 1) is driven by an outer retrier that needs the raw
            # CalledProcessError so it can retry and decide.
            if max_attempts > 1:
                raise GitHubError(
                    f"GitHub transient failure after {max_attempts} attempts: "
                    f"{format_github_retry_reason(error_text)}"
                )
        if check:
            if not quiet:
                print(f"ERROR: {' '.join(cmd)}\n{result.stderr}", file=sys.stderr)
            result.check_returncode()
        return result

    raise RuntimeError("GitHub command exhausted retries")


def run_github_json_with_retries(
        gh_runner: Callable[..., subprocess.CompletedProcess],
        args: tuple[str, ...],
        *,
        quiet: bool = False,
        max_attempts: int = GITHUB_TRANSIENT_RETRY_ATTEMPTS,
) -> Any:
    """Run a read-only gh command, retrying transient GitHub failures before parsing JSON."""
    for attempt in range(1, max_attempts + 1):
        command_quiet = quiet or attempt < max_attempts
        try:
            result = run_gh_runner_once(gh_runner, args, command_quiet)
        except subprocess.CalledProcessError as exc:
            error_text = github_error_text_from_exception(exc)
            if is_github_transient_failure_text(error_text):
                if attempt < max_attempts:
                    log_github_transient_retry(error_text, attempt, max_attempts, quiet)
                    time.sleep(github_retry_delay_seconds(attempt))
                    continue
                raise GitHubError(
                    f"GitHub transient failure after {max_attempts} attempts: "
                    f"{format_github_retry_reason(error_text)}"
                ) from exc
            raise

        data = json.loads(result.stdout)
        if isinstance(data, dict) and data.get("errors"):
            if is_github_rate_limit_errors(data["errors"]):
                raise GitHubRateLimitExceeded("GitHub API rate limit exceeded")
            if is_github_transient_errors(data["errors"]):
                if attempt < max_attempts:
                    log_github_transient_retry(str(data["errors"]), attempt, max_attempts, quiet)
                    time.sleep(github_retry_delay_seconds(attempt))
                    continue
                raise GitHubError(f"GitHub transient GraphQL failure after {max_attempts} attempts")
            if not quiet:
                print(f"ERROR: GraphQL errors: {data['errors']}", file=sys.stderr)
            raise RuntimeError(f"GraphQL error: {data['errors']}")
        return data

    raise RuntimeError("GitHub JSON command exhausted retries")


def run_github_command_with_retries(
        gh_runner: Callable[..., subprocess.CompletedProcess],
        args: tuple[str, ...],
        *,
        quiet: bool = False,
        max_attempts: int = GITHUB_TRANSIENT_RETRY_ATTEMPTS,
) -> subprocess.CompletedProcess:
    """Run an idempotent gh command, retrying transient GitHub failures."""
    for attempt in range(1, max_attempts + 1):
        command_quiet = quiet or attempt < max_attempts
        try:
            return run_gh_runner_once(gh_runner, args, command_quiet)
        except subprocess.CalledProcessError as exc:
            error_text = github_error_text_from_exception(exc)
            if is_github_transient_failure_text(error_text):
                if attempt < max_attempts:
                    log_github_transient_retry(error_text, attempt, max_attempts, quiet)
                    time.sleep(github_retry_delay_seconds(attempt))
                    continue
                raise GitHubError(
                    f"GitHub transient failure after {max_attempts} attempts: "
                    f"{format_github_retry_reason(error_text)}"
                ) from exc
            raise

    raise RuntimeError("GitHub command exhausted retries")


def run_github_with_retries(
        gh_runner: Callable[..., subprocess.CompletedProcess],
        args: tuple[str, ...],
        *,
        quiet: bool = False,
        max_attempts: int = GITHUB_TRANSIENT_RETRY_ATTEMPTS,
) -> subprocess.CompletedProcess:
    """Run a read-only gh command, retrying transient GitHub failures."""
    return run_github_command_with_retries(
        gh_runner,
        args,
        quiet=quiet,
        max_attempts=max_attempts,
    )


def gh_json(*args: str) -> Any:
    """Run a gh CLI command and parse its stdout as JSON."""
    return run_github_json_with_retries(gh, args)


def gh_with_retries(*args: str, quiet: bool = False, cwd: str | None = None) -> subprocess.CompletedProcess:
    """Run a read-only gh CLI command with retries for transient failures."""
    return run_github_with_retries(
        lambda *gh_args, quiet=False, max_attempts=1: gh(
            *gh_args,
            cwd=cwd,
            quiet=quiet,
            max_attempts=max_attempts,
        ),
        args,
        quiet=quiet,
    )


def get_authenticated_login(cwd=None) -> str:
    """Return the authenticated GitHub login used by gh."""
    result = gh_with_retries("api", "user", "--jq", ".login", cwd=cwd)
    return result.stdout.strip()
