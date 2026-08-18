# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Publish schema-validated code coverage improvement evidence.

The PR body reports each guidance phase against its JaCoCo-reportable method
count, the two phases combined, and per-phase token usage. Per-target rosters
and validation commands stay in the finalization artifacts, which is where a
reviewer reads them from
(§WF-code-coverage-improvement, §AR-forge-verification-publication-boundary).
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from typing import Any

from git_scripts.common_git import (
    build_ai_branch_name,
    delete_remote_branch_if_exists,
    gh,
    get_configured_reviewers,
    get_origin_owner,
    run_git_transport,
    stage_and_commit,
)
from git_scripts.pr_publication import BASE_BRANCH, REPO, parse_pr_number
from utility_scripts.code_coverage_finalize import (
    FinalizationError,
    load_validated_final_metrics,
)
from utility_scripts.metadata_index import (
    resolve_metadata_version,
    resolve_test_dir,
)

HUMAN_INTERVENTION_LABEL = "human-intervention"
MAX_COMMIT_SUBJECT_LENGTH = 60

#: Workflow order for the token-usage table; unknown phases sort after these.
TOKEN_PHASE_ORDER: tuple[str, ...] = (
    "convert",
    "prepare",
    "api-inventory",
    "api-coverage",
    "prepare-native-metadata",
    "deep-coverage",
    "finalization",
    "publication",
)


def _signed(value: int | float) -> str:
    return f"{'+' if value > 0 else ''}{value}"


def _total_percent(covered: int, total: int) -> float:
    return round(100 * covered / total, 2) if total else 0.0


def _reportable_total(snapshot: dict[str, Any]) -> int:
    """The denominator JaCoCo can actually rule on.

    The API inventory carries both `total`, every inventory entry, and
    `measured`, the entries JaCoCo reports at all. The difference is methods no
    run can ever cover, so dividing by `total` understates the phase and
    contradicts the `coveragePercent` the same document records. The deep
    snapshot has no such split and falls back to `total`.
    """
    return snapshot.get("measured", snapshot["total"])


def _jacoco_lines(label: str, evidence: dict[str, Any]) -> list[str]:
    baseline: dict[str, Any] = evidence["baseline"]
    final: dict[str, Any] = evidence["final"]
    total: int = _reportable_total(final)
    baseline_percent: float = _total_percent(baseline["covered"], total)
    final_percent: float = _total_percent(final["covered"], total)
    return [
        f"### {label}",
        "",
        f"- Baseline: {baseline['covered']}/{total} ({baseline_percent}%)",
        f"- Final: {final['covered']}/{total} ({final_percent}%)",
        f"- Delta: {_signed(round(final_percent - baseline_percent, 2))}pp",
        f"- Remaining uncovered: {total - final['covered']}",
    ]


def _combined_lines(api: dict[str, Any], deep: dict[str, Any]) -> list[str]:
    """API and deep coverage as one number.

    The two universes are disjoint by construction: the deep universe holds
    exactly the library methods the API inventory does not, so their measured
    counts and covered counts add without double counting.
    """
    total: int = _reportable_total(api["final"]) + _reportable_total(deep["final"])
    baseline: int = api["baseline"]["covered"] + deep["baseline"]["covered"]
    final: int = api["final"]["covered"] + deep["final"]["covered"]
    baseline_percent: float = _total_percent(baseline, total)
    final_percent: float = _total_percent(final, total)
    return [
        "### Both phases combined",
        "",
        f"- Baseline: {baseline}/{total} ({baseline_percent}%)",
        f"- Final: {final}/{total} ({final_percent}%)",
        f"- Delta: {_signed(round(final_percent - baseline_percent, 2))}pp",
        f"- Remaining uncovered: {total - final}",
    ]


def _phase_name(file_name: str) -> str:
    """`<workspace>.code-coverage-<phase>.json` -> `<phase>`."""
    task: str = file_name.removesuffix(".json").rsplit(".", 1)[-1]
    return task.removeprefix("code-coverage-")


def load_token_usage(accounting_dir: str) -> list[dict[str, Any]]:
    """One row per workflow phase from Rhei per-task accounting.

    A phase with no accounting file is omitted rather than reported as zero.
    Publication is normally among them: its own invocations are still running
    when this body is built, so the table states what is known at that point.
    """
    tasks_dir: str = os.path.join(accounting_dir, "tasks")
    if not os.path.isdir(tasks_dir):
        return []
    rows: list[dict[str, Any]] = []
    for file_name in sorted(os.listdir(tasks_dir)):
        if not file_name.endswith(".json"):
            continue
        try:
            with open(os.path.join(tasks_dir, file_name), encoding="utf-8") as handle:
                direct: dict[str, Any] = json.load(handle)["direct"]
            rows.append({
                "phase": _phase_name(file_name),
                "input": direct["input_total"]["value"],
                "cached": direct["input_cached_read"]["value"],
                "output": direct["output_total"]["value"],
            })
        except (OSError, ValueError, KeyError, TypeError):
            continue
    order: dict[str, int] = {name: index for index, name in enumerate(TOKEN_PHASE_ORDER)}
    rows.sort(key=lambda row: (order.get(row["phase"], len(order)), row["phase"]))
    return rows


def _token_cell(value: Any) -> str:
    """`n/a` keeps an unmeasured phase from reading as a free one."""
    return f"{value:,}" if isinstance(value, int) else "n/a"


def _token_lines(rows: list[dict[str, Any]]) -> list[str]:
    if not rows:
        return []
    lines: list[str] = [
        "## Token usage",
        "",
        "| Phase | Input | Input (cached) | Output |",
        "|---|--:|--:|--:|",
    ]
    totals: dict[str, int] = {"input": 0, "cached": 0, "output": 0}
    for row in rows:
        for key in totals:
            if isinstance(row[key], int):
                totals[key] += row[key]
        lines.append(
            f"| {row['phase']} | {_token_cell(row['input'])} | "
            f"{_token_cell(row['cached'])} | {_token_cell(row['output'])} |"
        )
    lines.append(
        f"| **Total** | **{totals['input']:,}** | "
        f"**{totals['cached']:,}** | **{totals['output']:,}** |"
    )
    lines += [
        "",
        "Input is uncached input tokens; Input (cached) is cache reads.",
    ]
    return lines


def build_pull_request_body(
        coordinate: str,
        issue_number: int | None,
        metrics: dict[str, Any],
        token_usage: list[dict[str, Any]] | None = None,
) -> str:
    """Build a PR body from the final metrics contract."""
    lines: list[str] = [
        "## Code coverage improvement",
        "",
        f"- Source issue: #{issue_number}" if issue_number else "- Source issue: n/a",
        f"- Coordinate: `{coordinate}`",
        f"- Coverage suite path: `{metrics['coverageSuitePath']}`",
        f"- Needs human intervention: {'yes' if metrics['needsHumanIntervention'] else 'no'}",
        "",
        "## JaCoCo coverage",
        "",
    ]
    lines += _jacoco_lines("Simple Jacoco guidance phase", metrics["apiJacoco"])
    lines += [""] + _jacoco_lines(
        "PGO guidance phase", metrics["deepJacoco"]
    )
    lines += [""] + _combined_lines(metrics["apiJacoco"], metrics["deepJacoco"])
    lines += [""]
    token_lines: list[str] = _token_lines(token_usage or [])
    if token_lines:
        lines += token_lines + [""]
    if issue_number:
        keyword: str = "Closes" if metrics.get("resolvesIssue") else "Refs"
        lines.append(f"{keyword} #{issue_number}.")
    return "\n".join(lines) + "\n"


def load_finalization_metrics(finalization_dir: str) -> dict[str, Any]:
    metrics_path: str = os.path.join(finalization_dir, "final-metrics.json")
    if not os.path.isfile(metrics_path):
        print(f"ERROR: finalization metrics not found: {metrics_path}", file=sys.stderr)
        raise SystemExit(1)
    try:
        return load_validated_final_metrics(metrics_path)
    except (FinalizationError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1) from error


def _coverage_commit_subject(coordinate: str) -> str:
    subject: str = f"Improve code coverage for {coordinate}"
    if len(subject) <= MAX_COMMIT_SUBJECT_LENGTH:
        return subject
    return subject[:MAX_COMMIT_SUBJECT_LENGTH - 3] + "..."


def stage_coverage_paths(
        repo_path: str,
        group: str,
        artifact: str,
        version: str,
        coverage_suite_path: str,
) -> None:
    """Stage the code coverage suite and any touched metadata, then commit."""
    test_dir: str = os.path.relpath(
        resolve_test_dir(repo_path, group, artifact, version),
        repo_path,
    )
    metadata_version: str = resolve_metadata_version(
        repo_path,
        group,
        artifact,
        version,
    )
    candidates: list[str] = [
        coverage_suite_path,
        test_dir,
        os.path.join("metadata", group, artifact, "index.json"),
        os.path.join("metadata", group, artifact, metadata_version),
    ]
    existing: list[str] = [
        path for path in candidates if os.path.exists(os.path.join(repo_path, path))
    ]
    stage_and_commit(
        existing,
        _coverage_commit_subject(f"{group}:{artifact}:{version}"),
        cwd=repo_path,
    )


def create_pull_request(
        repo_path: str,
        branch: str,
        coordinate: str,
        issue_number: int | None,
        metrics: dict[str, Any],
        push_remote: str,
        head_owner: str | None,
        base_branch: str,
        token_usage: list[dict[str, Any]] | None = None,
) -> int | None:
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return None
    owner: str = head_owner or get_origin_owner(cwd=repo_path)
    command: list[str] = [
        "pr",
        "create",
        "--repo",
        REPO,
        "--title",
        f"Improve code coverage for {coordinate}",
        "--body",
        build_pull_request_body(coordinate, issue_number, metrics, token_usage),
        "--base",
        base_branch,
        "--head",
        f"{owner}:{branch}",
        "--label",
        "code-coverage-improvement",
    ]
    if metrics.get("needsHumanIntervention"):
        command += ["--label", HUMAN_INTERVENTION_LABEL]
    for reviewer in get_configured_reviewers():
        command += ["--reviewer", reviewer]
    result: subprocess.CompletedProcess[str] = gh(*command)
    return parse_pr_number(result.stdout)


def publish(
        repo_path: str,
        coordinate: str,
        issue_number: int | None,
        finalization_dir: str,
        coverage_suite_path: str,
        push_remote: str,
        head_owner: str | None,
        base_branch: str,
        accounting_dir: str | None = None,
        branch_suffix: str | None = None,
) -> int | None:
    group: str
    artifact: str
    version: str
    group, artifact, version = coordinate.split(":")
    metrics: dict[str, Any] = load_finalization_metrics(finalization_dir)
    if metrics["coordinate"] != coordinate:
        print(
            f"ERROR: final metrics coordinate is '{metrics['coordinate']}', "
            f"expected '{coordinate}'.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    if os.path.normpath(metrics["coverageSuitePath"]) != os.path.normpath(
            coverage_suite_path
    ):
        print(
            f"ERROR: final metrics coverage suite does not match "
            f"'{coverage_suite_path}'.",
            file=sys.stderr,
        )
        raise SystemExit(1)

    # Publication force-replaces the remote head branch, so runs that must
    # coexist on the same coordinate discriminate their branch
    # (§WF-code-coverage-improvement.4).
    suffix: str = f"-{branch_suffix}" if branch_suffix else ""
    branch: str = build_ai_branch_name(
        f"code-coverage-{artifact}-{version}{suffix}", cwd=repo_path
    )
    delete_remote_branch_if_exists(branch, remote=push_remote, cwd=repo_path)
    subprocess.run(["git", "switch", "-C", branch], check=True, cwd=repo_path)
    stage_coverage_paths(
        repo_path,
        group,
        artifact,
        version,
        coverage_suite_path,
    )
    run_git_transport(["push", push_remote, branch], cwd=repo_path)
    # Rhei writes accounting beside the workflow runtime, two levels above the
    # finalization directory it hands this helper.
    resolved_accounting: str = accounting_dir or os.path.join(
        finalization_dir, os.pardir, os.pardir, "accounting"
    )
    return create_pull_request(
        repo_path,
        branch,
        coordinate,
        issue_number,
        metrics,
        push_remote,
        head_owner,
        base_branch,
        load_token_usage(resolved_accounting),
    )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        description="Publish a schema-validated code coverage improvement PR.",
        epilog=(
            "Example:\n"
            "  python3 git_scripts/make_pr_code_coverage_improvement.py "
            "--repo-path <worktree> --coordinate group:artifact:version "
            "--issue-number 8380 "
            "--finalization-dir runtime/code-coverage/finalization "
            "--coverage-suite-path tests/src/group/artifact/version/code-coverage-improvement"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--repo-path", required=True, help="Issue worktree / repository root."
    )
    parser.add_argument(
        "--coordinate", required=True, help="group:artifact:version."
    )
    parser.add_argument(
        "--issue-number", type=int, default=None, help="Backing GitHub issue."
    )
    parser.add_argument(
        "--finalization-dir",
        required=True,
        help="Directory containing schema-valid final-metrics.json.",
    )
    parser.add_argument(
        "--coverage-suite-path",
        required=True,
        help="Repository-relative dedicated coverage suite path.",
    )
    parser.add_argument(
        "--push-remote", default="origin", help="Writable fork remote."
    )
    parser.add_argument(
        "--head-owner", default=None, help="GitHub owner for the PR head."
    )
    parser.add_argument(
        "--base-branch", default=BASE_BRANCH, help="Pull request base branch."
    )
    parser.add_argument(
        "--accounting-dir",
        default=None,
        help="Rhei accounting directory; defaults beside the workflow runtime.",
    )
    parser.add_argument(
        "--branch-suffix",
        default=None,
        help=(
            "Run discriminator appended to the head branch name. Required when "
            "this run must coexist with an earlier pull request for the same "
            "coordinate, since publication force-replaces the remote head "
            "branch."
        ),
    )
    args: argparse.Namespace = parser.parse_args(argv)
    pr_number: int | None = publish(
        args.repo_path,
        args.coordinate,
        args.issue_number,
        args.finalization_dir,
        args.coverage_suite_path,
        args.push_remote,
        args.head_owner,
        args.base_branch,
        args.accounting_dir,
        args.branch_suffix,
    )
    if pr_number:
        print(f"Opened PR #{pr_number} for {args.coordinate}.")


if __name__ == "__main__":
    main()
