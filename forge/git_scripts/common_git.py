# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import shutil
import subprocess
import sys
import time
from typing import Any, Callable, Iterable, List
from utility_scripts.library_stats import load_library_stats_entry
from utility_scripts.strategy_loader import load_strategy_by_name


def _parse_github_repo_slug(remote_url: str) -> str | None:
    """Extract an ``owner/repo`` slug from a GitHub remote URL."""
    url = (remote_url or "").strip()
    if not url:
        return None

    if url.startswith("git@github.com:"):
        slug = url.split(":", 1)[1]
    elif url.startswith("ssh://git@github.com/"):
        slug = url.split("github.com/", 1)[1]
    elif url.startswith("https://github.com/") or url.startswith("http://github.com/"):
        slug = url.split("github.com/", 1)[1]
    else:
        return None

    if slug.endswith(".git"):
        slug = slug[:-4]

    parts = slug.split("/")
    if len(parts) != 2 or not parts[0] or not parts[1]:
        return None
    return slug


def get_repo_root():
    """Return the root directory of the metadata-forge project."""
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def get_forge_revision_info() -> tuple[str, str]:
    """Return the metadata-forge branch name and commit hash used by this script."""
    repo_root = get_repo_root()
    branch = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    if not branch:
        branch = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=repo_root,
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    commit_hash = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    return branch, commit_hash


def format_forge_revision_section() -> str:
    """Format the metadata-forge revision details for a PR body."""
    branch, commit_hash = get_forge_revision_info()
    monitored_branch = os.environ.get("FORGE_MONITORED_BRANCH") or branch
    return (
        "### Metadata Forge\n\n"
        f"- Forge monitored branch: `{monitored_branch}`\n"
        f"- Forge branch: `{branch}`\n"
        f"- Forge commit hash: `{commit_hash}`\n"
    )


def ensure_gh_authenticated() -> None:
    """Ensure the GitHub CLI (gh) is installed and authenticated for github.com."""
    if shutil.which("gh") is None:
        print(
            "The GitHub CLI (gh) is required to create a PR. "
            "Install it from https://cli.github.com/ and run 'gh auth login'."
        )
        sys.exit(1)
    try:
        log_github_query(("auth", "status", "--hostname", "github.com"))
        subprocess.run(
            ["gh", "auth", "status", "--hostname", "github.com"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True,
        )
    except subprocess.CalledProcessError:
        print("GitHub CLI is not authenticated for github.com. Run 'gh auth login' and try again.")
        sys.exit(1)


class GitHubRateLimitExceeded(RuntimeError):
    """Raised when GitHub reports an exhausted API rate-limit bucket."""


GITHUB_TRANSIENT_RETRY_ATTEMPTS = 3
GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS = 2.0
GITHUB_LOG_REDACTED_FLAGS = {
    "--body",
    "--body-file",
    "--input",
}
GITHUB_LOG_REDACTED_KEYS = {
    "body",
}


def is_github_rate_limit_text(text: str) -> bool:
    """Return True when GitHub CLI output describes an exhausted API rate limit."""
    return (
        "API rate limit exceeded" in text
        or "API rate limit already exceeded" in text
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


def _github_error_text_from_exception(exc: subprocess.CalledProcessError) -> str:
    return "\n".join(
        text.strip()
        for text in (exc.stderr, exc.stdout)
        if isinstance(text, str) and text.strip()
    )


def _format_github_retry_reason(reason: str) -> str:
    for line in reason.splitlines():
        line = line.strip()
        if line:
            return line[:200]
    return "empty GitHub response"


def _github_retry_delay_seconds(attempt: int) -> float:
    return GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS * attempt


def _log_github_transient_retry(reason: str, attempt: int, max_attempts: int, quiet: bool) -> None:
    if quiet:
        return
    delay = _github_retry_delay_seconds(attempt)
    print(
        "ERROR: GitHub API transient failure: "
        f"{_format_github_retry_reason(reason)}. "
        f"Retrying in {delay:.1f}s (attempt {attempt + 1}/{max_attempts}).",
        file=sys.stderr,
    )


def _split_github_arg_key_value(arg: str) -> tuple[str, str] | None:
    if "=" not in arg:
        return None
    key, value = arg.split("=", 1)
    if not key or not value:
        return None
    return key, value


def _format_github_log_arg(arg: str) -> str:
    key_value = _split_github_arg_key_value(arg)
    if key_value is None:
        return arg
    key, value = key_value
    if key.lower() in GITHUB_LOG_REDACTED_KEYS:
        return f"{key}=<redacted>"
    return f"{key}={value}"


def _format_github_log_args(args: Iterable[str]) -> list[str]:
    formatted_args: list[str] = []
    redact_next = False
    for arg in args:
        if redact_next:
            formatted_args.append("<redacted>")
            redact_next = False
            continue
        formatted_args.append(_format_github_log_arg(arg))
        if arg in GITHUB_LOG_REDACTED_FLAGS:
            redact_next = True
    return formatted_args


def log_github_query(args: Iterable[str]) -> None:
    """Print one console line for a GitHub CLI query."""
    formatted_args = _format_github_log_args(args)
    print(f"[github-query] gh {' '.join(formatted_args)}")


def gh(
        *args: str,
        check: bool = True,
        input_text: str | None = None,
        cwd=None,
        quiet: bool = False,
) -> subprocess.CompletedProcess:
    """Run a gh CLI command and return the completed process."""
    cmd = ["gh", *args]
    env = {**os.environ, "GH_PROMPT_DISABLED": "1", "GH_PAGER": ""}
    log_github_query(args)
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        env=env,
        input=input_text,
        cwd=cwd,
    )
    if check and result.returncode != 0:
        error_text = "\n".join(part for part in (result.stderr, result.stdout) if part)
        if is_github_rate_limit_text(error_text):
            raise GitHubRateLimitExceeded("GitHub API rate limit exceeded")
        if not quiet:
            print(f"ERROR: {' '.join(cmd)}\n{result.stderr}", file=sys.stderr)
        result.check_returncode()
    return result


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
            result = gh_runner(*args, quiet=command_quiet)
        except subprocess.CalledProcessError as exc:
            error_text = _github_error_text_from_exception(exc)
            if attempt < max_attempts and is_github_transient_failure_text(error_text):
                _log_github_transient_retry(error_text, attempt, max_attempts, quiet)
                time.sleep(_github_retry_delay_seconds(attempt))
                continue
            raise

        data = json.loads(result.stdout)
        if isinstance(data, dict) and data.get("errors"):
            if is_github_rate_limit_errors(data["errors"]):
                raise GitHubRateLimitExceeded("GitHub API rate limit exceeded")
            if attempt < max_attempts and is_github_transient_errors(data["errors"]):
                _log_github_transient_retry(str(data["errors"]), attempt, max_attempts, quiet)
                time.sleep(_github_retry_delay_seconds(attempt))
                continue
            if not quiet:
                print(f"ERROR: GraphQL errors: {data['errors']}", file=sys.stderr)
            raise RuntimeError(f"GraphQL error: {data['errors']}")
        return data

    raise RuntimeError("GitHub JSON command exhausted retries")


def gh_json(*args: str) -> Any:
    """Run a gh CLI command and parse its stdout as JSON."""
    return run_github_json_with_retries(gh, args)


def get_authenticated_login(cwd=None) -> str:
    """Return the authenticated GitHub login used by gh."""
    result = gh("api", "user", "--jq", ".login", cwd=cwd)
    return result.stdout.strip()


def build_ai_branch_name(branch_suffix: str, cwd=None) -> str:
    """Build an AI branch name scoped to the authenticated GitHub login."""
    authenticated_login = get_authenticated_login(cwd=cwd)
    return f"ai/{authenticated_login}/{branch_suffix}"


def git_remote_branch_exists(branch: str, remote: str = "origin", cwd: str | None = None) -> bool:
    """Return True when the remote has a branch with this name."""
    result = subprocess.run(
        ["git", "ls-remote", "--exit-code", "--heads", remote, branch],
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        return True
    if result.returncode == 2:
        return False
    print(
        f"ERROR: Failed to check whether `{remote}/{branch}` exists:\n{result.stderr.strip()}",
        file=sys.stderr,
    )
    result.check_returncode()
    return False


def delete_remote_branch_if_exists(branch: str, remote: str = "origin", cwd: str | None = None) -> bool:
    """Delete a remote branch when it exists, returning True if deletion ran."""
    if not git_remote_branch_exists(branch, remote=remote, cwd=cwd):
        return False
    print(f"[git-branch] Deleting existing remote branch {remote}/{branch}.")
    subprocess.run(["git", "push", remote, "--delete", branch], cwd=cwd, check=True)
    return True


def _project_item_status_fields() -> str:
    """Return the reusable GraphQL selection for project item status lookup."""
    return """
          projectItems(first: 5) {
            nodes {
              id
              project {
                number
              }
              fieldValues(first: 20) {
                nodes {
                  ... on ProjectV2ItemFieldSingleSelectValue {
                    name
                    field { ... on ProjectV2FieldCommon { name } }
                  }
                }
              }
            }
          }
    """


def _extract_project_item_status(
        issue_node: dict,
        project_number: int,
        status_field_name: str,
) -> tuple[str | None, str | None]:
    project_items = issue_node.get("projectItems", {}) if isinstance(issue_node, dict) else {}
    for node in project_items.get("nodes", []):
        if not isinstance(node, dict):
            continue
        if str(node.get("project", {}).get("number")) != str(project_number):
            continue
        status = None
        field_values = node.get("fieldValues", {})
        for field_value in field_values.get("nodes", []):
            field = field_value.get("field", {}) if isinstance(field_value, dict) else {}
            if field.get("name") == status_field_name:
                status = field_value.get("name")
                break
        return node.get("id"), status
    return None, None


def get_issue_project_item_statuses(
        repo: str,
        project_number: int,
        issue_numbers: list[int],
        status_field_name: str,
        chunk_size: int = 50,
) -> dict[int, tuple[str | None, str | None]]:
    """Fetch project item IDs and Status field values for issues with chunked GraphQL calls."""
    owner, repo_name = repo.split("/")
    states: dict[int, tuple[str | None, str | None]] = {}
    issue_numbers = list(dict.fromkeys(issue_numbers))

    for index in range(0, len(issue_numbers), chunk_size):
        batch = issue_numbers[index:index + chunk_size]
        issue_fields = "\n".join(
            f"""
        issue_{issue_number}: issue(number: {issue_number}) {{
{_project_item_status_fields()}
        }}
            """
            for issue_number in batch
        )
        query = f"""
        query {{
          repository(owner: "{owner}", name: "{repo_name}") {{
{issue_fields}
          }}
        }}
        """
        result = gh_json("api", "graphql", "-f", f"query={query}")
        repository = (
            result.get("data", {})
            .get("repository", {})
        ) or {}
        for issue_number in batch:
            states[issue_number] = _extract_project_item_status(
                repository.get(f"issue_{issue_number}", {}),
                project_number,
                status_field_name,
            )

    return states


def get_issue_project_item_status(
        repo: str,
        project_number: int,
        issue_number: int,
        status_field_name: str,
) -> tuple[str | None, str | None]:
    """Fetch the project item ID and Status field value for an issue in one GraphQL call."""
    return get_issue_project_item_statuses(
        repo,
        project_number,
        [issue_number],
        status_field_name,
    ).get(issue_number, (None, None))


def parse_coordinate_parts(coordinates: str):
    """Parse a coordinate string in the form group:artifact:version."""
    try:
        group, artifact, version = coordinates.split(":")
    except ValueError:
        print(f"ERROR: Invalid coordinates format: {coordinates}. Expected Group:Artifact:Version")
        sys.exit(1)
    return group, artifact, version


def get_remote_url(remote_name: str = "origin", cwd=None) -> str | None:
    """Return a git remote URL, or ``None`` when the remote is absent."""
    result = subprocess.run(
        ["git", "remote", "get-url", remote_name],
        capture_output=True,
        text=True,
        check=False,
        cwd=cwd,
    )
    if result.returncode != 0:
        return None
    return result.stdout.strip() or None


def git_remote_exists(remote_name: str = "origin", cwd=None) -> bool:
    """Return ``True`` when the requested git remote exists."""
    return get_remote_url(remote_name, cwd=cwd) is not None


def resolve_github_repo_slug(repo_path: str | None = None, explicit_repo: str | None = None) -> str:
    """Resolve the GitHub ``owner/repo`` slug for the target reachability repository."""
    if explicit_repo:
        return explicit_repo

    env_repo = os.environ.get("METADATA_FORGE_TARGET_REPO")
    if env_repo:
        return env_repo

    for remote_name in ("upstream", "origin"):
        remote_url = get_remote_url(remote_name, cwd=repo_path)
        repo_slug = _parse_github_repo_slug(remote_url or "")
        if repo_slug is not None:
            return repo_slug

    print(
        "ERROR: Could not resolve the target GitHub repository. "
        "Set `METADATA_FORGE_TARGET_REPO` or use a reachability-metadata checkout with a GitHub remote.",
        file=sys.stderr,
    )
    raise SystemExit(1)


def get_origin_owner(cwd=None):
    """Extract the repository owner from the ``origin`` remote URL.

    This intentionally reads only ``origin`` (not ``upstream``): callers use the
    returned value to attribute PRs to the contributor's fork. Use
    ``resolve_github_repo_slug`` when you need the upstream target repository.
    """
    remote_url = get_remote_url("origin", cwd=cwd)
    repo_slug = _parse_github_repo_slug(remote_url or "")
    if repo_slug is None:
        print(
            "ERROR: Could not resolve the `origin` GitHub repository. "
            "Configure an `origin` remote that points to GitHub.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return repo_slug.split("/", 1)[0]


def get_configured_reviewers() -> list[str]:
    """Return PR reviewers configured via ``METADATA_FORGE_PR_REVIEWERS``."""
    raw_value = os.environ.get("METADATA_FORGE_PR_REVIEWERS", "")
    if not raw_value.strip():
        return []
    return [reviewer.strip() for reviewer in raw_value.split(",") if reviewer.strip()]


def git_files_under(repo_path: str, directory: str) -> list[str]:
    """Return Git-tracked files under a directory relative to the repository root."""
    relative_dir = os.path.relpath(directory, repo_path).replace(os.sep, "/")
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", relative_dir],
        cwd=repo_path,
        capture_output=True,
        text=True,
        check=True,
    )
    return [path for path in result.stdout.split("\0") if path]


def is_java_fix_test_module_file(relative_file: str) -> bool:
    """Return True for test-module files relevant to javac/java-run PR body diffs."""
    normalized_file = relative_file.replace(os.sep, "/")
    if normalized_file.startswith("src/test/java/"):
        return True
    if normalized_file in {"build.gradle", "settings.gradle", "setting.gradle"}:
        return True
    return "/" not in normalized_file and "user-code-filter" in normalized_file


def stage_and_commit(paths: List[str], commit_message: str, cwd=None) -> None:
    """
    Stage the provided paths and commit with the given message if there are staged changes.
    """
    if not paths:
        print("No paths provided to stage.")
        return

    # Stage selected paths
    subprocess.run(["git", "add"] + paths, check=True, cwd=cwd)

    # Commit only if there are staged changes
    result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=cwd)
    if result.returncode != 0:
        subprocess.run(["git", "commit", "-m", commit_message], check=True, cwd=cwd)
    else:
        print("No staged changes to commit.")


def find_issue_for_coordinates(search_string: str, repo: str):
    """
    Finds the open issue number in a GitHub repository matching the given search string in the title.

    Returns:
        The issue number if found.
    """
    gh_search_query = f"{search_string} in:title"

    # Build the gh issue list command
    command = [
        "gh",
        "issue",
        "list",
        "--repo", repo,
        "--state", "open",
        "--search", gh_search_query,
        "--limit", "1",
        "--json", "number"
    ]

    print(f"Executing command: {' '.join(command)}")

    # Run the command and let exceptions propagate to fail fast
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        check=True
    )

    issue_data = json.loads(result.stdout)

    if isinstance(issue_data, list) and len(issue_data) > 0 and 'number' in issue_data[0]:
        return issue_data[0]['number']

    raise RuntimeError(f"No open issue found for search: {gh_search_query} in repo {repo}")


def get_model_display_name(strategy_name: str) -> str:
    """Resolve the model display name for a strategy. Strips 'oca/' prefix if present."""
    strategy = load_strategy_by_name(strategy_name)
    if strategy is None:
        return "Unknown"
    model = strategy.get("model", "Unknown")
    if model.startswith("oca/"):
        model = model[len("oca/"):]
    return model


def get_agent_name(strategy_name: str) -> str:
    """Resolve the agent name for a strategy."""
    strategy = load_strategy_by_name(strategy_name)
    if strategy is None:
        return "Unknown"
    return strategy.get("agent")


def load_library_stats(repo_path, coordinates):
    """Load the full stats entry for the given coordinates from exploded stats files."""
    group, artifact, version = coordinates.split(":")
    return load_library_stats_entry(repo_path, group, artifact, version)


def format_dynamic_access_entry(stats):
    """Format a single dynamic-access stats entry."""
    return "{covered}/{total} covered calls ({ratio:.2f}%)".format(
        covered=stats["coveredCalls"],
        total=stats["totalCalls"],
        ratio=stats["coverageRatio"] * 100,
    )


def is_dynamic_access_stats_entry(stats):
    """Return True when stats has the expected dynamic-access entry shape."""
    return isinstance(stats, dict) and all(
        key in stats for key in ("coveredCalls", "totalCalls", "coverageRatio")
    )


def format_coverage_entry(entry):
    """Format a single library coverage entry."""
    if entry == "N/A":
        return "N/A"
    return "{covered}/{total} ({ratio:.2f}%)".format(
        covered=entry["covered"],
        total=entry["total"],
        ratio=entry["ratio"] * 100,
    )


def format_dynamic_access_section(dynamic_access_stats):
    """Format the dynamic-access stats into a markdown section."""
    if not is_dynamic_access_stats_entry(dynamic_access_stats):
        return ""
    lines = [
        "Dynamic access coverage:",
        f"- Overall: {format_dynamic_access_entry(dynamic_access_stats)}",
    ]
    for category in sorted(dynamic_access_stats.get("breakdown", {})):
        if not is_dynamic_access_stats_entry(dynamic_access_stats["breakdown"][category]):
            continue
        display_name = category[0].upper() + category[1:]
        lines.append(f"- {display_name}: {format_dynamic_access_entry(dynamic_access_stats['breakdown'][category])}")
    return "\n".join(lines)


def format_library_coverage_section(library_coverage):
    """Format the library coverage stats into a markdown section."""
    lines = ["Library coverage:"]
    for metric in ("instruction", "line", "method"):
        entry = library_coverage.get(metric)
        if entry != "N/A" and not isinstance(entry, dict):
            continue
        display_name = metric[0].upper() + metric[1:]
        lines.append(f"- {display_name}: {format_coverage_entry(entry)}")
    return "\n".join(lines)


def format_stats_section(version_stats):
    """Format a single version's stats (dynamic access + library coverage) into markdown."""
    sections = []
    dynamic_access = version_stats.get("dynamicAccess")
    if dynamic_access:
        dynamic_access_section = format_dynamic_access_section(dynamic_access)
        if dynamic_access_section:
            sections.append(dynamic_access_section)
    library_coverage = version_stats.get("libraryCoverage")
    if library_coverage:
        sections.append(format_library_coverage_section(library_coverage))
    if not sections:
        return ""
    return "Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`:\n\n" + "\n\n".join(sections)


def _format_comparison_pair(old_coordinates, new_coordinates, old_entry, new_entry, formatter):
    """Return two bullet lines comparing an entry between old and new coordinates."""
    return [
        f"- {old_coordinates}: {formatter(old_entry) if old_entry else 'N/A'}",
        f"- {new_coordinates}: {formatter(new_entry) if new_entry else 'N/A'}",
    ]


def format_stats_diff(repo_path, old_coordinates, new_coordinates):
    """Format a comparison of stats between two library versions."""
    old_version_stats = load_library_stats(repo_path, old_coordinates)
    new_version_stats = load_library_stats(repo_path, new_coordinates)

    if old_version_stats is None and new_version_stats is None:
        return ""
    if old_version_stats == new_version_stats:
        return (
            "\n### Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`\n\n"
            f"Same entry for both `{old_coordinates}` and `{new_coordinates}`.\n"
        )

    lines = ["", "### Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`", ""]

    old_da = old_version_stats.get("dynamicAccess") if old_version_stats else None
    new_da = new_version_stats.get("dynamicAccess") if new_version_stats else None
    if old_da or new_da:
        lines.append("#### Dynamic access coverage")
        lines.append("")
        lines.extend(_format_comparison_pair(
            old_coordinates, new_coordinates, old_da, new_da, format_dynamic_access_entry
        ))

        all_categories = set()
        if old_da:
            all_categories.update(old_da.get("breakdown", {}).keys())
        if new_da:
            all_categories.update(new_da.get("breakdown", {}).keys())

        for category in sorted(all_categories):
            display_name = category[0].upper() + category[1:]
            old_cat = old_da.get("breakdown", {}).get(category) if old_da else None
            new_cat = new_da.get("breakdown", {}).get(category) if new_da else None
            lines.append("")
            lines.append(f"**{display_name}:**")
            lines.extend(_format_comparison_pair(
                old_coordinates, new_coordinates, old_cat, new_cat, format_dynamic_access_entry
            ))
        lines.append("")

    old_cov = old_version_stats.get("libraryCoverage") if old_version_stats else None
    new_cov = new_version_stats.get("libraryCoverage") if new_version_stats else None
    if old_cov or new_cov:
        lines.append("#### Library coverage")
        lines.append("")
        for metric in ("instruction", "line", "method"):
            display_name = metric[0].upper() + metric[1:]
            old_entry = old_cov.get(metric) if old_cov else None
            new_entry = new_cov.get(metric) if new_cov else None
            old_entry = old_entry if isinstance(old_entry, dict) or old_entry == "N/A" else None
            new_entry = new_entry if isinstance(new_entry, dict) or new_entry == "N/A" else None
            if old_entry or new_entry:
                lines.append(f"**{display_name}:**")
                lines.extend(_format_comparison_pair(
                    old_coordinates, new_coordinates, old_entry, new_entry, format_coverage_entry
                ))
                lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def format_stats_before_after(before_stats: dict | None, after_stats: dict | None, coordinates: str) -> str:
    """Format a before/after comparison of stats for the same library version."""
    if before_stats is None and after_stats is None:
        return ""
    if before_stats == after_stats:
        return (
            "\n### Stats comparison (before vs after)\n\n"
            f"No change in stats for `{coordinates}`.\n"
        )

    before_label = f"Before ({coordinates})"
    after_label = f"After ({coordinates})"
    lines = ["", f"### Stats comparison for `{coordinates}`", ""]

    before_da = before_stats.get("dynamicAccess") if before_stats else None
    after_da = after_stats.get("dynamicAccess") if after_stats else None
    if before_da or after_da:
        lines.append("#### Dynamic access coverage")
        lines.append("")
        lines.extend(_format_comparison_pair(
            before_label, after_label, before_da, after_da, format_dynamic_access_entry
        ))

        all_categories = set()
        if before_da:
            all_categories.update(before_da.get("breakdown", {}).keys())
        if after_da:
            all_categories.update(after_da.get("breakdown", {}).keys())

        for category in sorted(all_categories):
            display_name = category[0].upper() + category[1:]
            before_cat = before_da.get("breakdown", {}).get(category) if before_da else None
            after_cat = after_da.get("breakdown", {}).get(category) if after_da else None
            lines.append("")
            lines.append(f"**{display_name}:**")
            lines.extend(_format_comparison_pair(
                before_label, after_label, before_cat, after_cat, format_dynamic_access_entry
            ))
        lines.append("")

    before_cov = before_stats.get("libraryCoverage") if before_stats else None
    after_cov = after_stats.get("libraryCoverage") if after_stats else None
    if before_cov or after_cov:
        lines.append("#### Library coverage")
        lines.append("")
        for metric in ("instruction", "line", "method"):
            display_name = metric[0].upper() + metric[1:]
            before_entry = before_cov.get(metric) if before_cov else None
            after_entry = after_cov.get(metric) if after_cov else None
            before_entry = before_entry if isinstance(before_entry, dict) or before_entry == "N/A" else None
            after_entry = after_entry if isinstance(after_entry, dict) or after_entry == "N/A" else None
            if before_entry or after_entry:
                lines.append(f"**{display_name}:**")
                lines.extend(_format_comparison_pair(
                    before_label, after_label, before_entry, after_entry, format_coverage_entry
                ))
                lines.append("")

    return "\n".join(lines).rstrip() + "\n"
