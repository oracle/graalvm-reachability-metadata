# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""GitHub CLI plumbing (§AR-forge-dispatcher-decomposition).

Rate-limit and transient failures surface as typed exceptions at this
boundary (§FS-human-intervention-policy).
"""

import os
import subprocess
import sys
import time

from git_scripts.github_cli import (
    GITHUB_TRANSIENT_RETRY_ATTEMPTS,
    GitHubError,
    GitHubRateLimitExceeded,
    _format_github_retry_reason,
    _github_retry_delay_seconds,
    _log_github_transient_retry,
    is_github_rate_limit_text,
    is_github_transient_failure_text,
    log_github_query,
    run_github_json_with_retries,
)


def gh(
        *args: str,
        check: bool = True,
        input_text: str | None = None,
        cwd: str | None = None,
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
                _log_github_transient_retry(error_text, attempt, max_attempts, quiet)
                time.sleep(_github_retry_delay_seconds(attempt))
                continue
            # Only type the failure when this loop owns the retries; a single attempt
            # (max_attempts == 1) is driven by an outer retrier that needs the raw
            # CalledProcessError so it can retry and decide.
            if max_attempts > 1:
                raise GitHubError(
                    f"GitHub transient failure after {max_attempts} attempts: "
                    f"{_format_github_retry_reason(error_text)}"
                )
        if check:
            if not quiet:
                print(f"ERROR: {' '.join(cmd)}\n{result.stderr}", file=sys.stderr)
            result.check_returncode()
        return result

    raise RuntimeError("GitHub command exhausted retries")


def gh_json(*args: str, quiet: bool = False) -> any:
    """Run a gh CLI command and parse its stdout as JSON."""
    return run_github_json_with_retries(gh, args, quiet=quiet)


def format_github_exception_details(exc: Exception) -> str:
    """Return concise GitHub command failure details without embedding long commands."""
    if isinstance(exc, subprocess.CalledProcessError):
        output = "\n".join(
            text.strip()
            for text in (exc.stderr, exc.stdout)
            if isinstance(text, str) and text.strip()
        )
        if output:
            return output
        return f"gh command exited with code {exc.returncode}"
    return repr(exc)
