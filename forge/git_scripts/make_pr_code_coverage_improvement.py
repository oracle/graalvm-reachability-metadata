# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Pull-request publisher for the code coverage improvement workflow
(§WF-code-coverage-improvement, §AR-forge-verification-publication-boundary).

Phase-9 helper. It stages the dedicated code coverage suite plus any metadata
the run touched, pushes the issue branch to a writable fork remote, and opens a
pull request whose body carries the evidence the spec requires: source issue,
coordinate, coverage suite path, baseline coverage, final coverage, coverage
delta, completed targets, skipped/exhausted targets, and validation commands.

The body is built from the finalization artifacts written in phase 8
(`final-metrics.json`, `final-summary.md`). `build_pull_request_body` is pure
and testable; the Git/`gh` orchestration around it is thin.

Usage:
  python3 git_scripts/make_pr_code_coverage_improvement.py \
    --repo-path <worktree> --coordinate group:artifact:version \
    --issue-number 8380 --finalization-dir runtime/code-coverage/finalization \
    --coverage-suite-path tests/src/<group>/<artifact>/<version>/code-coverage
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys

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

HUMAN_INTERVENTION_LABEL = "human-intervention"


def _format_coverage(label: str, snapshot: dict | None) -> str:
    if not snapshot:
        return f"{label}: n/a"
    parts: list[str] = []
    if "jvmPercent" in snapshot:
        parts.append(f"JVM JaCoCo {snapshot['jvmPercent']}%")
    if "pgoExecutedMethods" in snapshot:
        parts.append(f"PGO executed methods {snapshot['pgoExecutedMethods']}")
    return f"{label}: " + (", ".join(parts) if parts else "n/a")


def build_pull_request_body(coordinate: str, issue_number: int | None, metrics: dict) -> str:
    """Build the PR body from finalization metrics (§WF-code-coverage-improvement)."""
    suite_path = metrics.get("coverageSuitePath", "n/a")
    completed = metrics.get("completedTargets", []) or []
    skipped = metrics.get("skippedTargets", []) or []
    exhausted = metrics.get("exhaustedTargets", []) or []
    commands = metrics.get("validationCommands", []) or []
    delta = metrics.get("coverageDelta", {}) or {}

    issue_line = f"#{issue_number}" if issue_number else "n/a"
    lines = [
        "## Code coverage improvement",
        "",
        f"- Source issue: {issue_line}",
        f"- Coordinate: `{coordinate}`",
        f"- Coverage suite path: `{suite_path}`",
        f"- {_format_coverage('Baseline coverage', metrics.get('baselineCoverage'))}",
        f"- {_format_coverage('Final coverage', metrics.get('finalCoverage'))}",
        f"- Coverage delta: JVM JaCoCo "
        f"{delta.get('jvmPercent', 'n/a')}{'pp' if 'jvmPercent' in delta else ''}",
        "",
        f"## API targets completed ({len(completed)})",
        "",
    ]
    lines += [f"- `{target}`" for target in completed] or ["_None recorded._"]

    lines += ["", f"## API targets skipped/exhausted ({len(skipped) + len(exhausted)})", ""]
    skipped_lines = [f"- `{item.get('id', item)}` — skipped: {item.get('reason', 'no reason given')}"
                     if isinstance(item, dict) else f"- `{item}` — skipped" for item in skipped]
    exhausted_lines = [f"- `{item.get('id', item)}` — exhausted: {item.get('reason', 'no reason given')}"
                       if isinstance(item, dict) else f"- `{item}` — exhausted" for item in exhausted]
    lines += (skipped_lines + exhausted_lines) or ["_None recorded._"]

    lines += ["", "## Validation commands", "", "```console"]
    lines += commands or ["# none recorded"]
    lines += ["```", ""]

    if issue_number:
        # Link without auto-closing unless the run resolved the whole issue.
        keyword = "Closes" if metrics.get("resolvesIssue") else "Refs"
        lines.append(f"{keyword} #{issue_number}.")
    return "\n".join(lines) + "\n"


def load_finalization_metrics(finalization_dir: str) -> dict:
    metrics_path = os.path.join(finalization_dir, "final-metrics.json")
    if not os.path.isfile(metrics_path):
        print(f"ERROR: finalization metrics not found: {metrics_path}", file=sys.stderr)
        raise SystemExit(1)
    with open(metrics_path, "r", encoding="utf-8") as metrics_file:
        return json.load(metrics_file)


def stage_coverage_paths(repo_path: str, group: str, artifact: str, version: str,
                         coverage_suite_path: str, commit_message: str) -> None:
    """Stage the code coverage suite and any touched metadata, then commit."""
    candidate_paths = [
        coverage_suite_path,
        os.path.join("tests", "src", group, artifact, version),
        os.path.join("metadata", group, artifact, "index.json"),
        os.path.join("metadata", group, artifact, version),
    ]
    existing = [path for path in candidate_paths if os.path.exists(os.path.join(repo_path, path))]
    stage_and_commit(existing, commit_message, cwd=repo_path)


def create_pull_request(repo_path: str, branch: str, coordinate: str, issue_number: int | None,
                        metrics: dict, push_remote: str, head_owner: str | None,
                        base_branch: str) -> int | None:
    if shutil.which("gh") is None:
        print("gh CLI not found. Skipping PR creation.")
        return None
    owner = head_owner or get_origin_owner(cwd=repo_path)
    title = f"Improve code coverage for {coordinate}"
    body = build_pull_request_body(coordinate, issue_number, metrics)

    command = [
        "pr", "create", "--repo", REPO,
        "--title", title, "--body", body,
        "--base", base_branch, "--head", f"{owner}:{branch}",
        "--label", "improve-code-coverage",
    ]
    if metrics.get("needsHumanIntervention"):
        command += ["--label", HUMAN_INTERVENTION_LABEL]
    for reviewer in get_configured_reviewers():
        command += ["--reviewer", reviewer]
    result = gh(*command)
    return parse_pr_number(result.stdout)


def publish(repo_path: str, coordinate: str, issue_number: int | None, finalization_dir: str,
            coverage_suite_path: str, push_remote: str, head_owner: str | None,
            base_branch: str) -> int | None:
    group, artifact, version = coordinate.split(":")
    metrics = load_finalization_metrics(finalization_dir)
    metrics.setdefault("coverageSuitePath", coverage_suite_path)

    branch = build_ai_branch_name(f"code-coverage-{artifact}-{version}", cwd=repo_path)
    delete_remote_branch_if_exists(branch, remote=push_remote, cwd=repo_path)
    subprocess.run(["git", "switch", "-C", branch], check=True, cwd=repo_path)
    stage_coverage_paths(repo_path, group, artifact, version, coverage_suite_path,
                         f"Improve code coverage for {coordinate}")
    run_git_transport(["push", push_remote, branch], cwd=repo_path)
    return create_pull_request(repo_path, branch, coordinate, issue_number, metrics,
                               push_remote, head_owner, base_branch)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Publish a code coverage improvement PR.")
    parser.add_argument("--repo-path", required=True, help="Issue worktree / repo root.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--issue-number", type=int, default=None, help="Backing GitHub issue number.")
    parser.add_argument("--finalization-dir", required=True, help="runtime/code-coverage/finalization dir.")
    parser.add_argument("--coverage-suite-path", required=True, help="Relative code coverage suite path.")
    parser.add_argument("--push-remote", default="origin", help="Writable fork remote to push to.")
    parser.add_argument("--head-owner", default=None, help="GitHub owner for the PR head branch.")
    parser.add_argument("--base-branch", default=BASE_BRANCH, help="PR base branch.")
    args = parser.parse_args(argv)

    pr_number = publish(
        repo_path=args.repo_path,
        coordinate=args.coordinate,
        issue_number=args.issue_number,
        finalization_dir=args.finalization_dir,
        coverage_suite_path=args.coverage_suite_path,
        push_remote=args.push_remote,
        head_owner=args.head_owner,
        base_branch=args.base_branch,
    )
    if pr_number:
        print(f"Opened PR #{pr_number} for {args.coordinate}.")


if __name__ == "__main__":
    main()
