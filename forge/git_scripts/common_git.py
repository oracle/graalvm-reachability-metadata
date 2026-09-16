# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import sys
from typing import Any, Iterable, List

# The GitHub CLI transport and the stats formatting moved to sibling modules;
# their names stay importable from here for existing callers.
from git_scripts.github_cli import (
    GITHUB_LOG_REDACTED_FLAGS,
    GITHUB_LOG_REDACTED_KEYS,
    GITHUB_QUERY_LOG_ENV_VAR,
    GITHUB_TRANSIENT_RETRY_ATTEMPTS,
    GITHUB_TRANSIENT_RETRY_BASE_DELAY_SECONDS,
    GitHubError,
    GitHubRateLimitExceeded,
    ensure_gh_authenticated,
    get_authenticated_login,
    gh,
    gh_json,
    gh_with_retries,
    is_github_rate_limit_errors,
    is_github_rate_limit_text,
    is_github_transient_errors,
    is_github_transient_failure_text,
    log_github_query,
    run_github_command_with_retries,
    run_github_json_with_retries,
    run_github_with_retries,
    should_log_github_queries,
)
from git_scripts.stats_formatting import (
    format_coverage_entry,
    format_dynamic_access_entry,
    format_dynamic_access_section,
    format_library_coverage_section,
    format_stats_before_after,
    format_stats_diff,
    format_stats_section,
    is_dynamic_access_stats_entry,
    load_library_stats,
)
from utility_scripts.stage_logger import debug_logging_enabled
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
        "### Forge\n\n"
        f"- Forge monitored branch: `{monitored_branch}`\n"
        f"- Forge branch: `{branch}`\n"
        f"- Forge commit hash: `{commit_hash}`\n"
    )


class GitTransportError(RuntimeError):
    """A `git` remote transport operation failed.

    Treated as an external failure for the same reason as `GitHubError`.
    """

    def __init__(
            self,
            message: str,
            *,
            returncode: int | None = None,
            command: list[str] | None = None,
            output: str | None = None,
    ):
        super().__init__(message)
        self.returncode = returncode
        self.command = command
        self.output = output


def build_ai_branch_name(branch_suffix: str, cwd=None) -> str:
    """Build an AI branch name scoped to the authenticated GitHub login."""
    authenticated_login = get_authenticated_login(cwd=cwd)
    return f"ai/{authenticated_login}/{branch_suffix}"


def switch_branch_quietly(branch: str, cwd: str | None = None) -> None:
    """Reset a branch without leaking Git narration in compact output.

    §FS-forge-run-output-legibility.5
    """
    result = subprocess.run(
        ["git", "switch", "-C", branch],
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    if debug_logging_enabled() and result.stdout:
        print(result.stdout, end="")


def git_remote_branch_exists(branch: str, remote: str = "origin", cwd: str | None = None) -> bool:
    """Return True when the remote has a branch with this name."""
    result = run_git_transport(
        ["ls-remote", "--exit-code", "--heads", remote, branch],
        cwd=cwd,
        allowed_returncodes=(0, 2),
    )
    if result.returncode == 0:
        return True
    if result.returncode == 2:
        return False
    return False


def run_git_transport(
        args: list[str],
        *,
        cwd: str | None = None,
        env: dict[str, str] | None = None,
        timeout: int | None = None,
        allowed_returncodes: Iterable[int] = (0,),
) -> subprocess.CompletedProcess:
    """Run a `git` remote transport command, typing its failures.

    Captures output so a transport failure surfaces as a typed `GitTransportError`
    instead of a bare `CalledProcessError`, letting the issue automation treat it
    as external (release the claim, no `human-intervention`).
    §FS-human-intervention-policy
    """
    result = subprocess.run(
        ["git", *args],
        cwd=cwd,
        env=env,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    if result.returncode not in set(allowed_returncodes):
        detail = "\n".join(
            stream.strip()
            for stream in (result.stderr, result.stdout)
            if stream and stream.strip()
        )
        raise GitTransportError(
            f"git {' '.join(args)} failed (exit {result.returncode}): {detail}",
            returncode=result.returncode,
            command=["git", *args],
            output=detail,
        )
    return result


def delete_remote_branch_if_exists(branch: str, remote: str = "origin", cwd: str | None = None) -> bool:
    """Delete a remote branch when it exists, returning True if deletion ran."""
    if not git_remote_branch_exists(branch, remote=remote, cwd=cwd):
        return False
    print(f"[git-branch] Deleting existing remote branch {remote}/{branch}.")
    run_git_transport(["push", remote, "--delete", branch], cwd=cwd)
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


def find_remote_for_github_repo(repo: str, cwd: str | None = None) -> str:
    """Return the local remote that points at the requested GitHub repository."""
    result = subprocess.run(
        ["git", "remote"],
        capture_output=True,
        text=True,
        check=True,
        cwd=cwd,
    )
    remote_names = result.stdout.splitlines()
    ordered_names = [
        *[name for name in ("origin", "upstream") if name in remote_names],
        *[name for name in remote_names if name not in {"origin", "upstream"}],
    ]
    for remote_name in ordered_names:
        remote_url = get_remote_url(remote_name, cwd=cwd)
        remote_repo = _parse_github_repo_slug(remote_url or "")
        if remote_repo is not None and remote_repo.lower() == repo.lower():
            return remote_name
    raise RuntimeError(f"No Git remote points at required upstream repository {repo}")


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


def _issue_contains_exact_coordinate(issue: dict[str, Any], coordinates: str) -> bool:
    """Return true when a GitHub issue title or body contains the exact coordinates."""
    title = str(issue.get("title") or "")
    body = str(issue.get("body") or "")
    return coordinates in title or coordinates in body


def find_issue_for_coordinates(search_string: str, repo: str):
    """
    Finds the open issue number in a GitHub repository matching the given search string in the title.

    Returns:
        The issue number if found.
    """
    gh_search_query = f'"{search_string}" in:title,body'

    # Build the gh issue list command
    command = [
        "issue",
        "list",
        "--repo", repo,
        "--state", "open",
        "--search", gh_search_query,
        "--limit", "50",
        "--json", "number,title,body"
    ]

    print(f"Executing command: gh {' '.join(command)}")

    issue_data = gh_json(*command)

    exact_matches = [
        issue
        for issue in issue_data
        if isinstance(issue, dict) and _issue_contains_exact_coordinate(issue, search_string)
    ]

    if len(exact_matches) == 1 and 'number' in exact_matches[0]:
        return exact_matches[0]['number']

    if len(exact_matches) > 1:
        sorted_matches = sorted(
            exact_matches,
            key=lambda issue: int(issue.get("number") or 0),
        )
        return sorted_matches[0]['number']

    raise RuntimeError(f"No open issue found for exact coordinates {search_string} in repo {repo}")


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

