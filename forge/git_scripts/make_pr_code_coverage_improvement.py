# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Publish schema-validated code coverage improvement evidence.

JaCoCo evidence for public API and deep methods stays separate. Sampled PGO is
shown only as navigation guidance (§WF-code-coverage-improvement,
§AR-forge-verification-publication-boundary).
"""

from __future__ import annotations

import argparse
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


def _signed(value: int | float) -> str:
    return f"{'+' if value > 0 else ''}{value}"


def _jacoco_lines(label: str, evidence: dict[str, Any]) -> list[str]:
    baseline: dict[str, Any] = evidence["baseline"]
    final: dict[str, Any] = evidence["final"]
    denominator: str = "measured" if "measured" in baseline else "total"
    return [
        f"### {label}",
        "",
        f"- Baseline: {baseline['covered']}/{baseline[denominator]} "
        f"({baseline['coveragePercent']}%)",
        f"- Final: {final['covered']}/{final[denominator]} "
        f"({final['coveragePercent']}%)",
        f"- Delta: {_signed(evidence['delta']['coveragePercentagePoints'])}pp",
        f"- Remaining uncovered: {final['uncovered']}",
    ]


def _pgo_lines(guidance: dict[str, Any]) -> list[str]:
    baseline: dict[str, Any] = guidance["baseline"]
    final: dict[str, Any] = guidance["final"]
    return [
        "## Sampled PGO guidance only",
        "",
        guidance["note"],
        "",
        f"- Baseline: {baseline['samplingContexts']} contexts, "
        f"{baseline['sampledMethods']} sampled methods, "
        f"{baseline['sampleCount']} samples, {baseline['sampledJoins']} sampled joins",
        f"- Final: {final['samplingContexts']} contexts, "
        f"{final['sampledMethods']} sampled methods, "
        f"{final['sampleCount']} samples, {final['sampledJoins']} sampled joins",
    ]


def _target_lines(
        targets: list[dict[str, Any]], include_reason: bool
) -> list[str]:
    if not targets:
        return ["_None recorded._"]
    lines: list[str] = []
    for target in targets:
        line: str = f"- [{target['phase']}] `{target['id']}`"
        if "attemptCount" in target:
            last_iteration: int | None = target["lastAttemptedIteration"]
            last: str = str(last_iteration) if last_iteration is not None else "none"
            line += (
                f" — attempts: {target['attemptCount']}, "
                f"last attempted iteration: {last}"
            )
        if include_reason:
            line += f" — {target['reason']}"
        lines.append(line)
    return lines


def build_pull_request_body(
        coordinate: str,
        issue_number: int | None,
        metrics: dict[str, Any],
) -> str:
    """Build a PR body from the final metrics contract."""
    targets: dict[str, list[dict[str, Any]]] = metrics["targets"]
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
    lines += _jacoco_lines("Public API entries", metrics["apiJacoco"])
    lines += [""] + _jacoco_lines(
        "Deep internal methods", metrics["deepJacoco"]
    )
    lines += [""] + _pgo_lines(metrics["pgoGuidance"])
    for status in ("completed", "skipped", "exhausted", "failed"):
        entries: list[dict[str, Any]] = targets[status]
        lines += ["", f"## {status.title()} targets ({len(entries)})", ""]
        lines += _target_lines(entries, status != "completed")
    lines += ["", "## Validation commands", "", "```console"]
    lines += metrics["validationCommands"]
    lines += ["```", ""]
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
        build_pull_request_body(coordinate, issue_number, metrics),
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

    branch: str = build_ai_branch_name(
        f"code-coverage-{artifact}-{version}", cwd=repo_path
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
    return create_pull_request(
        repo_path,
        branch,
        coordinate,
        issue_number,
        metrics,
        push_remote,
        head_owner,
        base_branch,
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
            "--coverage-suite-path tests/group/artifact/version/code-coverage"
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
    )
    if pr_number:
        print(f"Opened PR #{pr_number} for {args.coordinate}.")


if __name__ == "__main__":
    main()
