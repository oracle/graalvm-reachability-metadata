# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Publish the verified code coverage branch and its publication descriptor.

This helper owns the local half only: expected-path staging, the shared
publication pipeline, and a descriptor carrying the finalized JaCoCo evidence
and per-phase token accounting. Trusted GitHub Actions render that data and open
the pull request (§GIT-shared-publication-pipeline, §GIT-publication-descriptor,
§AR-forge-verification-publication-boundary, §WF-code-coverage-improvement.4).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from typing import Any

from git_scripts.branch_publication import BASE_BRANCH, REPO, publish_branch
from git_scripts.common_git import ensure_gh_authenticated, stage_and_commit
from git_scripts.publication_descriptor import PublicationDescriptorInput
from utility_scripts.code_coverage_finalize import (
    FinalizationError,
    load_validated_final_metrics,
)
from utility_scripts.metadata_index import (
    resolve_metadata_version,
    resolve_test_dir,
)
from utility_scripts.repo_path_resolver import resolve_repo_roots

TASK_TYPE = "code-coverage-improvement"
MAX_COMMIT_SUBJECT_LENGTH = 60

#: The finalized evidence the trusted coverage template renders. `runCoverage`
#: is the whole-run accounting the body is built from; the per-phase blocks ride
#: along as each phase's own guidance record (§WF-code-coverage-improvement.4.1).
#: Everything else `final-metrics.json` records — per-target rosters, sampled PGO
#: guidance, the validation command list — stays in the finalization artifacts a
#: reviewer reads from the run, so the descriptor holds render inputs and nothing else.
COVERAGE_RENDER_KEYS: tuple[str, ...] = (
    "coordinate",
    "coverageSuitePath",
    "runCoverage",
    "apiJacoco",
    "deepJacoco",
    "needsHumanIntervention",
)

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


def _phase_name(file_name: str) -> str:
    """`<workspace>.code-coverage-<phase>.json` -> `<phase>`."""
    task: str = file_name.removesuffix(".json").rsplit(".", 1)[-1]
    return task.removeprefix("code-coverage-")


def load_token_usage(accounting_dir: str) -> list[dict[str, Any]]:
    """One row per workflow phase from Rhei per-task accounting.

    A phase with no accounting file is omitted rather than reported as zero.
    Publication is normally among them: its own invocations are still running
    when this descriptor is written, so the table states what is known then.
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


def model_slug(worker_agent: str) -> str:
    """Return the branch segment naming the model a Rhei target generates with.

    A Rhei target reads `<agent>[<mode>]:<provider>/<model>`, and only the model
    identifies what produced the run (§WF-code-coverage-improvement.4).
    """
    target: str = worker_agent.split(":", 1)[-1]
    model: str = re.split(r"[/:]", target)[-1]
    slug: str = re.sub(r"[^A-Za-z0-9._-]+", "-", model).strip("-.")
    if not slug:
        print(
            f"ERROR: worker agent '{worker_agent}' names no model.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return slug


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
    """Stage the code coverage suite, touched metadata, and stats, then commit.

    The coverage route's expected paths (§GIT-expected-paths): the dedicated
    suite, the coordinate's test directory, the metadata the suite justified, and
    the coverage stats finalization regenerated from the combined main-JAR-only
    JaCoCo report. Leaving the stats unstaged publishes tests whose effect the
    repository's own coverage record never shows (§root/TCK-test-harness.8).
    """
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
        os.path.join("stats", group, artifact, metadata_version, "stats.json"),
    ]
    existing: list[str] = [
        path for path in candidates if os.path.exists(os.path.join(repo_path, path))
    ]
    stage_and_commit(
        existing,
        _coverage_commit_subject(f"{group}:{artifact}:{version}"),
        cwd=repo_path,
    )


def build_descriptor_input(
        issue_number: int,
        metrics: dict[str, Any],
        model: str,
        token_usage: list[dict[str, Any]],
) -> PublicationDescriptorInput:
    """Carry the finalized coverage evidence to the trusted renderer as data.

    The run timestamp is `generatedAt` from the finalization artifacts rather
    than the wall clock, so a retried publication rebuilds the same publication
    ID, branch, and pull request (§GIT-publication-descriptor).
    """
    return PublicationDescriptorInput(
        issue_number=issue_number,
        task_type=TASK_TYPE,
        template_type=TASK_TYPE,
        status="success",
        timestamp=str(metrics["generatedAt"]),
        render={
            "code_coverage": {
                key: metrics[key] for key in COVERAGE_RENDER_KEYS if key in metrics
            },
            "token_usage": token_usage,
            "worker_model": model,
        },
    )


def publish(
        repo_path: str,
        coordinate: str,
        worker_agent: str,
        issue_number: int,
        finalization_dir: str,
        coverage_suite_path: str,
        metrics_repo_path: str | None = None,
        accounting_dir: str | None = None,
        branch_suffix: str | None = None,
) -> str:
    """Push the verified coverage branch with its descriptor; return the branch."""
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

    # The branch names the model that generated the run, so runs of one
    # coordinate on different models never collide, and the publication ID the
    # pipeline appends separates repeated runs of one model
    # (§WF-code-coverage-improvement.4).
    model: str = model_slug(worker_agent)
    suffix: str = f"-{branch_suffix}" if branch_suffix else ""
    # Rhei writes accounting beside the workflow runtime, two levels above the
    # finalization directory it hands this helper.
    resolved_accounting: str = accounting_dir or os.path.join(
        finalization_dir, os.pardir, os.pardir, "accounting"
    )
    descriptor_input: PublicationDescriptorInput = build_descriptor_input(
        issue_number,
        metrics,
        model,
        load_token_usage(resolved_accounting),
    )

    branch: str
    branch, _ = publish_branch(
        repo_path=repo_path,
        branch_suffix=f"code-coverage-{artifact}-{version}-{model}{suffix}",
        coordinates=coordinate,
        stage=lambda: stage_coverage_paths(
            repo_path, group, artifact, version, coverage_suite_path,
        ),
        metrics_repo_path=metrics_repo_path,
        descriptor_input=descriptor_input,
    )
    return branch


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="publish_code_coverage_improvement.py",
        description=(
            f"Create and push a verified code coverage branch and its publication "
            f"descriptor to '{REPO}'; trusted GitHub Actions open the pull request "
            f"against base branch '{BASE_BRANCH}'."
        ),
        epilog=(
            "Example:\n"
            "  python3 git_scripts/publish_code_coverage_improvement.py "
            "--repo-path <worktree> --coordinate group:artifact:version "
            "--worker-agent pi[high]:openai-codex/gpt-5.6-luna "
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
        "--issue-number",
        type=int,
        required=True,
        help="Backing GitHub issue the publication descriptor references.",
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
        "--worker-agent",
        required=True,
        help=(
            "Rhei target the run generated with, as "
            "<agent>[<mode>]:<provider>/<model>. Its model names the head "
            "branch, so each model owns its own branch for a coordinate."
        ),
    )
    parser.add_argument(
        "--metrics-repo-path",
        dest="metrics_repo_path",
        default=None,
        help=(
            "Path to the metrics storage root. "
            "If omitted, the forge directory in the selected worktree is used."
        ),
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
            "Readable run discriminator appended to the head branch name before "
            "the publication ID. The publication ID already keeps two runs of "
            "one coordinate and model apart; this only labels which run is which."
        ),
    )
    return parser


def main(argv: list[str] | None = None) -> None:
    args: argparse.Namespace = build_parser().parse_args(argv)
    ensure_gh_authenticated()
    repo_path: str
    metrics_repo_path: str
    repo_path, metrics_repo_path = resolve_repo_roots(
        args.repo_path, args.metrics_repo_path,
    )
    branch: str = publish(
        repo_path,
        args.coordinate,
        args.worker_agent,
        args.issue_number,
        args.finalization_dir,
        args.coverage_suite_path,
        metrics_repo_path,
        args.accounting_dir,
        args.branch_suffix,
    )
    print(f"Pushed {branch} for {args.coordinate}.")


if __name__ == "__main__":
    main()
