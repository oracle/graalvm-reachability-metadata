# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Pre-merge index validation, merge follow-ups, and conflict resolution
(§AR-forge-dispatcher-decomposition, §FS-automated-pr-review)."""


import json
import os
import re
import subprocess
import sys
import uuid
from git_scripts.common_git import GitTransportError
from git_scripts.common_git import run_git_transport
from utility_scripts.library_update_follow_up_issue import extract_follow_up_issue_numbers
from utility_scripts.repo_path_resolver import get_repo_root
from utility_scripts.stage_logger import log_stage
from dispatcher.config import (
    DEFAULT_WORKTREE_BASE_REF,
    LABEL_FORGE_MERGE_FOLLOW_UP,
    LABEL_HUMAN_INTERVENTION,
    LABEL_RESUMABLE,
    PROJECT_NUMBER,
    SCRATCH_CONFLICT_RESOLUTION_WORKTREE_DIRNAME,
    SCRATCH_FINAL_INDEX_VALIDATION_WORKTREE_DIRNAME,
    STATUS_TODO,
)
from dispatcher.issue_admin import (
    add_pull_request_label,
    clear_issue_assignees,
    remove_issue_label,
    remove_pull_request_label,
)
from dispatcher.issue_cache import invalidate_issue_claim_cache_entry
from dispatcher.issue_queue import (
    get_issue_claim_payload,
    issue_has_label,
)
from dispatcher.pr_publication import ensure_pull_request_unapproved
from dispatcher.pr_state import (
    get_pull_request_changed_index_files,
    get_pull_requests_with_labels,
    pull_request_has_label,
)
from dispatcher.project_board import (
    get_project_item_id,
    set_item_status,
)
from dispatcher.worktrees import fetch_review_base_ref
from dispatcher.worktrees import (
    create_detached_worktree,
    remove_worktree,
)

from dispatcher.run_logs import run_checked_command

def validate_index_files_on_current_master_candidate(
        pr_number: int,
        head_ref_oid: str,
        reachability_metadata_path: str,
) -> None:
    """Validate index files after applying the pull request head to current master."""
    repo_root = get_repo_root()
    validation_worktrees_root = os.path.join(
        repo_root,
        "local_repositories",
        SCRATCH_FINAL_INDEX_VALIDATION_WORKTREE_DIRNAME,
    )
    os.makedirs(validation_worktrees_root, exist_ok=True)

    validation_run_id = f"index-pr-{pr_number}-{uuid.uuid4().hex[:8]}"
    validation_worktree_path = os.path.join(validation_worktrees_root, validation_run_id)

    fetch_review_base_ref(reachability_metadata_path)
    create_detached_worktree(
        reachability_metadata_path,
        validation_worktree_path,
        f"origin/{DEFAULT_WORKTREE_BASE_REF}",
        f"Failed to create final index validation worktree for PR #{pr_number}",
    )
    try:
        try:
            run_git_transport(
                ["fetch", "--quiet", "origin", f"refs/pull/{pr_number}/head"],
                cwd=validation_worktree_path,
            )
        except GitTransportError as exc:
            print(
                f"ERROR: Failed to fetch PR #{pr_number} head for final index validation: {exc}",
                file=sys.stderr,
            )
            raise
        fetched_head = run_checked_command(
            ["git", "rev-parse", "FETCH_HEAD"],
            validation_worktree_path,
            f"Failed to resolve fetched PR #{pr_number} head for final index validation",
        ).stdout.strip()
        if fetched_head != head_ref_oid:
            print(
                (
                    f"ERROR: PR #{pr_number} head changed before final index validation: "
                    f"expected {head_ref_oid}, fetched {fetched_head}."
                ),
                file=sys.stderr,
            )
            raise RuntimeError(f"Pull request #{pr_number} head changed before final index validation")

        run_checked_command(
            ["git", "merge", "--no-commit", "--no-ff", "FETCH_HEAD"],
            validation_worktree_path,
            (
                f"Failed to merge PR #{pr_number} into current "
                f"{DEFAULT_WORKTREE_BASE_REF} for final index validation"
            ),
        )
        print(
            f"[Validating all index files on current {DEFAULT_WORKTREE_BASE_REF} "
            f"plus PR #{pr_number}.]"
        )
        run_checked_command(
            ["./gradlew", "validateIndexFiles", "-Pcoordinates=all", "--stacktrace"],
            validation_worktree_path,
            f"Final index validation failed for PR #{pr_number}",
        )
    finally:
        remove_worktree(reachability_metadata_path, validation_worktree_path)


def validate_pull_request_indexes_before_merge(
        pr_number: int,
        head_ref_oid: str,
        reachability_metadata_path: str,
) -> None:
    """Run final-tree index validation for pull requests that change index files."""
    changed_index_files = get_pull_request_changed_index_files(pr_number)
    if not changed_index_files:
        return

    print(
        f"[PR #{pr_number} changes {len(changed_index_files)} index file(s); "
        f"running final index validation before merge.]"
    )
    validate_index_files_on_current_master_candidate(
        pr_number,
        head_ref_oid,
        reachability_metadata_path,
    )


def resolve_non_final_chunked_dynamic_access_issue(pr: dict) -> int | None:
    """Return the linked issue for a non-final chunked dynamic-access PR body."""
    body = pr.get("body")
    if not isinstance(body, str):
        return None
    if "Chunked dynamic-access: yes" not in body:
        return None
    if re.search(r"(?m)^Fixes:\s*#\d+\b", body):
        return None
    match = re.search(r"(?m)^Refs:\s*#(\d+)\b", body)
    if match is None:
        return None
    return int(match.group(1))


def apply_chunked_dynamic_access_merge_follow_up(pr: dict) -> None:
    """Restore a merged non-final chunk issue for a clean next chunk.

    §FS-forge-chunked-dynamic-access
    """
    issue_number = resolve_non_final_chunked_dynamic_access_issue(pr)
    if issue_number is None:
        return

    item_id = get_project_item_id(issue_number)
    if item_id is None:
        raise RuntimeError(
            f"Chunked dynamic-access PR #{pr.get('number')} references issue #{issue_number}, "
            f"but the issue is not linked to project {PROJECT_NUMBER}."
        )
    issue: dict = get_issue_claim_payload(issue_number)
    for label_name in (LABEL_HUMAN_INTERVENTION, LABEL_RESUMABLE):
        if issue_has_label(issue, label_name):
            remove_issue_label(issue_number, label_name)
    set_item_status(item_id, STATUS_TODO)
    clear_issue_assignees(issue_number)
    invalidate_issue_claim_cache_entry(issue_number)
    log_stage(
        "chunked-dynamic-access",
        f"Released issue #{issue_number} for the next chunk after PR #{pr.get('number')} merged.",
    )


def pull_request_needs_merge_follow_up(pull_request: dict) -> bool:
    """Return whether a GitHub-completed merge needs Forge issue bookkeeping."""
    return (
        resolve_non_final_chunked_dynamic_access_issue(pull_request) is not None
        or bool(extract_follow_up_issue_numbers(pull_request.get("body")))
    )


def mark_pull_request_merge_follow_up_pending(pull_request: dict) -> None:
    """Persist post-merge work before auto-merge completes. §FS-automated-pr-review"""
    if not pull_request_needs_merge_follow_up(pull_request):
        return
    if pull_request_has_label(pull_request, LABEL_FORGE_MERGE_FOLLOW_UP):
        return
    pr_number = pull_request.get("number")
    if not isinstance(pr_number, int):
        raise RuntimeError("Cannot mark merge follow-up without a pull request number")
    add_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
    labels = pull_request.setdefault("labels", [])
    if isinstance(labels, list):
        labels.append({"name": LABEL_FORGE_MERGE_FOLLOW_UP})


def apply_unblocked_issue_merge_follow_up(pr: dict) -> None:
    """Release issues parked behind a PR after that PR merges.

    The shared `Forge-Unblocks-Issue` trailer releases both library-update alias
    successors and deferred Java-fix coverage issues from `In Progress` to
    `Todo`. §FS-library-update-tested-version-split,
    §AR-forge-driver-queues.3
    """
    for issue_number in extract_follow_up_issue_numbers(pr.get("body")):
        item_id = get_project_item_id(issue_number)
        if item_id is None:
            raise RuntimeError(
                f"PR #{pr.get('number')} unblocks issue #{issue_number}, "
                f"but the issue is not linked to project {PROJECT_NUMBER}."
            )
        set_item_status(item_id, STATUS_TODO)
        clear_issue_assignees(issue_number)
        invalidate_issue_claim_cache_entry(issue_number)
        log_stage(
            "issue-unblock",
            f"Released issue #{issue_number} after PR #{pr.get('number')} merged.",
        )


def reconcile_auto_merged_pull_request_follow_ups(fetch_limit: int = 100) -> None:
    """Apply durable follow-ups for auto-merged PRs. §FS-automated-pr-review"""
    merged_pull_requests = get_pull_requests_with_labels(
        [LABEL_FORGE_MERGE_FOLLOW_UP],
        fetch_limit,
        state="merged",
    )
    for pull_request in merged_pull_requests:
        pr_number = pull_request.get("number")
        if not isinstance(pr_number, int):
            continue
        try:
            apply_chunked_dynamic_access_merge_follow_up(pull_request)
            apply_unblocked_issue_merge_follow_up(pull_request)
            remove_pull_request_label(pr_number, LABEL_FORGE_MERGE_FOLLOW_UP)
            print(f"[Reconciled linked issues after auto-merge of PR #{pr_number}.]")
        except Exception as exc:
            print(
                f"ERROR: Failed auto-merge follow-up for PR #{pr_number}: {exc!r}",
                file=sys.stderr,
            )


def is_pull_request_conflicting(pull_request: dict) -> bool:
    """Return True when GitHub reports the pull request as conflicting with its base."""
    return pull_request.get("mergeable") == "CONFLICTING"


_BENCHMARK_RESULTS_PATH_PATTERN = re.compile(
    r"^code-coverage-benchmarks/[^/]+/[^/]+/[^/]+\.json$"
)


def _entries_by_run_id(entries: list) -> dict:
    """Key benchmark result entries by runId, rejecting malformed shapes."""
    keyed: dict = {}
    for entry in entries:
        run_id = entry.get("runId") if isinstance(entry, dict) else None
        if not isinstance(run_id, str) or run_id in keyed:
            raise ValueError("entries are not uniquely keyed by runId")
        keyed[run_id] = entry
    return keyed


def resolve_benchmark_results_conflict(worktree_path: str, path: str) -> bool:
    """Union-merge one conflicted benchmark results file by run ID.

    The file's entries are keyed by run ID and the only legal edit is adding
    one, so concurrent publications conflict textually while never actually
    disagreeing; the resolution is the base side's entries plus the entries
    the head added (§FS-automated-pr-review). It may never modify or drop an
    existing entry, and a run ID on both sides with different content is a
    real disagreement, so this returns False and the caller escalates.
    """
    try:
        stages = [
            json.loads(
                run_checked_command(
                    ["git", "show", f":{stage}:{path}"],
                    worktree_path,
                    f"Failed to read merge stage {stage} of {path}",
                ).stdout
            )
            for stage in (1, 2, 3)
        ]
        if not all(isinstance(stage, list) for stage in stages):
            return False
        ancestor, head, base = (_entries_by_run_id(stage) for stage in stages)
    except (RuntimeError, ValueError, json.JSONDecodeError):
        return False

    added = {run_id: entry for run_id, entry in head.items() if run_id not in ancestor}
    # Everything the head did not add must match the merge ancestor exactly.
    if {run_id: entry for run_id, entry in head.items() if run_id not in added} != ancestor:
        return False
    for run_id, entry in added.items():
        if run_id in base and base[run_id] != entry:
            return False

    merged = list(base.values()) + [
        entry for run_id, entry in added.items() if run_id not in base
    ]
    merged.sort(key=lambda entry: (entry["timestamp"], entry["runId"]))
    absolute_path = os.path.join(worktree_path, path)
    with open(absolute_path, "w", encoding="utf-8") as destination:
        json.dump(merged, destination, indent=2, ensure_ascii=False)
        destination.write("\n")
    run_checked_command(
        ["git", "add", path],
        worktree_path,
        f"Failed to stage the resolved {path}",
    )
    return True


def resolve_pull_request_merge_conflict(
        pull_request: dict,
        reachability_metadata_path: str,
) -> bool:
    """Merge the base branch into a conflicting PR head and push what git resolved.

    Returns True only when the merge left no conflict behind and the result
    reached the head branch. Anything git could not resolve on its own is a real
    disagreement over content and stays for a maintainer (§FS-automated-pr-review).
    """
    pr_number = pull_request.get("number")
    head_ref_name = pull_request.get("headRefName")
    head_ref_oid = pull_request.get("headRefOid")
    if not isinstance(pr_number, int) or not isinstance(head_ref_name, str) or not head_ref_name:
        print(f"ERROR: Missing head branch metadata for pull request #{pr_number}.", file=sys.stderr)
        raise RuntimeError(f"Missing head branch metadata for pull request #{pr_number}")
    if pull_request.get("isCrossRepository"):
        print(
            f"[Leaving PR #{pr_number} conflicting: its head branch lives in a fork "
            "that Forge cannot push to.]"
        )
        return False

    repo_root = get_repo_root()
    conflict_worktrees_root = os.path.join(
        repo_root,
        "local_repositories",
        SCRATCH_CONFLICT_RESOLUTION_WORKTREE_DIRNAME,
    )
    os.makedirs(conflict_worktrees_root, exist_ok=True)
    worktree_path = os.path.join(
        conflict_worktrees_root,
        f"conflict-pr-{pr_number}-{uuid.uuid4().hex[:8]}",
    )

    fetch_review_base_ref(reachability_metadata_path)
    run_git_transport(
        ["fetch", "--quiet", "origin", f"refs/pull/{pr_number}/head"],
        cwd=reachability_metadata_path,
    )
    create_detached_worktree(
        reachability_metadata_path,
        worktree_path,
        "FETCH_HEAD",
        f"Failed to create conflict resolution worktree for PR #{pr_number}",
    )
    try:
        fetched_head = run_checked_command(
            ["git", "rev-parse", "HEAD"],
            worktree_path,
            f"Failed to resolve fetched PR #{pr_number} head for conflict resolution",
        ).stdout.strip()
        if fetched_head != head_ref_oid:
            print(
                (
                    f"[Leaving PR #{pr_number} conflicting: head changed before resolution, "
                    f"expected {head_ref_oid}, fetched {fetched_head}.]"
                )
            )
            return False

        # `--no-ff` so a head that is merely behind the base can never fast-forward
        # onto it, which would leave the pull request with nothing to merge.
        merge_result = subprocess.run(
            [
                "git", "merge", "--no-ff",
                f"origin/{DEFAULT_WORKTREE_BASE_REF}",
                "-m", f"Merge {DEFAULT_WORKTREE_BASE_REF} into {head_ref_name}",
            ],
            cwd=worktree_path,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        if merge_result.returncode != 0:
            conflicted_paths = run_checked_command(
                ["git", "diff", "--name-only", "--diff-filter=U"],
                worktree_path,
                f"Failed to list unresolved conflicts for PR #{pr_number}",
            ).stdout.split()
            # Benchmark results conflict textually while never disagreeing, so
            # they are resolved, not escalated (§FS-automated-pr-review).
            resolvable = bool(conflicted_paths) and all(
                _BENCHMARK_RESULTS_PATH_PATTERN.match(path)
                for path in conflicted_paths
            )
            if resolvable and all(
                resolve_benchmark_results_conflict(worktree_path, path)
                for path in conflicted_paths
            ):
                run_checked_command(
                    ["git", "commit", "--no-edit"],
                    worktree_path,
                    f"Failed to conclude the resolved merge for PR #{pr_number}",
                )
            else:
                subprocess.run(["git", "merge", "--abort"], cwd=worktree_path, check=False)
                print(
                    f"[Leaving PR #{pr_number} conflicting: git could not resolve "
                    f"{', '.join(conflicted_paths) or 'the merge'}.]"
                )
                return False

        ensure_pull_request_unapproved(pull_request)
        run_git_transport(
            ["push", "origin", f"HEAD:refs/heads/{head_ref_name}"],
            cwd=worktree_path,
        )
        print(
            f"[Merged {DEFAULT_WORKTREE_BASE_REF} into PR #{pr_number} and pushed it; "
            "its checks restart, so the merge belongs to a later pass.]"
        )
        return True
    finally:
        remove_worktree(reachability_metadata_path, worktree_path)
