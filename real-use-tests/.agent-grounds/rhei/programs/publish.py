# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `publish` program: record the native fix's deletions, commit, measure, and push.

Publication checks nothing itself: `style-check` and the optional publication
fix ran before it, and the coverage run after the commit only measures
(§FS-real-use-test-generation.2.7). Only the coordinate project and its stats
record are staged (§FS-real-use-test-contract.2.2), the commits go on top of the
run's checkpoints, and the pushed branch is the whole handoff: the trusted
publisher opens the pull request from the stats record
(§AR-real-use-test-workflow.6).
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
from typing import Any

import run_summary
import scenarios
from build_tools import TaskResult, git, run_gradle
from exit_codes import INFRASTRUCTURE, NO_SCENARIO_LEFT, OK
from run_state import RunState, load, read_json, runtime_path, save, write_json

MAX_SUBJECT: int = 60
# The stats schema's bound on one removal reason.
MAX_REASON: int = 2000
# The task type the trusted publisher rebuilds the publication ID with.
TASK_TYPE: str = "real-use-tests"
REPOSITORY: str = "oracle/graalvm-reachability-metadata"
BASE_BRANCH: str = "master"


def _bounded(subject: str) -> str:
    return subject if len(subject) <= MAX_SUBJECT else subject[: MAX_SUBJECT - 3] + "..."


def _subject(state: RunState) -> str:
    return _bounded(f"Add real-use tests for {state.artifact} {state.version}")


def _record_native_removals(workspace: str, state: RunState) -> None:
    """Record the scenarios the native fix agent deleted, with its reasons.

    §FS-real-use-test-generation.2.6
    """
    report_path: str = runtime_path(workspace, "reports", "native-agent-fix.json")
    if not os.path.isfile(report_path):
        return
    for item in read_json(report_path).get("deleted", []):
        path: str = scenarios.class_file(state.test_source_path, item.get("class", ""))
        if item.get("class") and not os.path.exists(path):
            state.record_removal(os.path.relpath(path, state.worktree), item.get("reason", ""))


def stats_dir(state: RunState) -> str:
    """The coordinate's stats record directory, relative to the repository root.

    §FS-real-use-tests.1
    """
    return os.path.join("real-use-tests", "stats", state.group, state.artifact, state.version)


def stats_record(workspace: str, state: RunState) -> dict[str, Any]:
    """The issue, start time, coverage, tokens, scenario passes, and removals the PR reports.

    §FS-real-use-test-generation.2.7
    """
    return {
        "issue_number": state.issue_number,
        "timestamp": state.started_at,
        "library_coverage": run_summary.library_coverage(
            os.path.join(state.project_path, run_summary.JACOCO_REPORT)
        ),
        "tokens": run_summary.agent_tokens(workspace),
        "iterations": state.scenario_passes,
        "removed": [
            {"scenario": removal["scenario"], "reason": removal["reason"][:MAX_REASON]}
            for removal in state.removals
        ],
    }


def publication_id(state: RunState) -> str:
    """The ID the trusted publisher rebuilds from the stats record.

    §forge/AR-real-use-tests-publication
    """
    identity: str = json.dumps(
        {"issue_number": state.issue_number, "timestamp": state.started_at,
         "coordinates": state.coordinate, "task_type": TASK_TYPE},
        sort_keys=True, separators=(",", ":"),
    )
    digest: str = hashlib.sha256(identity.encode("utf-8")).hexdigest()[:12]
    return f"forge-{state.issue_number}-{re.sub(r'[^0-9]', '', state.started_at)[:20]}-{digest}"


def _upstream_remote(worktree: str) -> str:
    """The remote that points at the upstream repository, `origin` when none names it."""
    for line in git(worktree, "remote", "-v").splitlines():
        name, url = line.split()[:2]
        if REPOSITORY in url.removesuffix(".git"):
            return name
    return "origin"


def _push(workspace: str, state: RunState) -> int:
    """Rebase the run's commits onto upstream `master` and push the publication branch."""
    login: subprocess.CompletedProcess[str] = subprocess.run(
        ["gh", "api", "user", "--jq", ".login"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
    )
    if login.returncode != 0 or not login.stdout.strip():
        print(f"ERROR: cannot read the gh login for the branch: {login.stderr.strip()}", file=sys.stderr)
        return INFRASTRUCTURE
    remote: str = _upstream_remote(state.worktree)
    branch: str = f"ai/{login.stdout.strip()}/real-use-{state.artifact}-{state.version}-{publication_id(state)}"
    try:
        git(state.worktree, "fetch", "--quiet", remote, BASE_BRANCH)
        git(state.worktree, "rebase", "--quiet", f"{remote}/{BASE_BRANCH}")
        git(state.worktree, "push", "--quiet", "--force", remote, f"HEAD:refs/heads/{branch}")
    except subprocess.CalledProcessError as error:
        subprocess.run(["git", "rebase", "--abort"], cwd=state.worktree, check=False, capture_output=True)
        print(f"ERROR: cannot push {branch}: {error}", file=sys.stderr)
        return INFRASTRUCTURE
    pushed: str = f"Pushed {branch} for {state.coordinate}."
    with open(runtime_path(workspace, "publication", "branch.md"), "w", encoding="utf-8") as handle:
        handle.write(pushed + "\n")
    print(f"[publish] {pushed}")
    return OK


def publish(workspace: str) -> int:
    state: RunState = load(workspace)
    _record_native_removals(workspace, state)
    save(workspace, state)
    git(state.worktree, "add", "-A", "--", state.project_dir)
    if git(state.worktree, "diff", "--cached", "--name-only"):
        git(state.worktree, "commit", "--quiet", "-m", _subject(state))
        print(f"[publish] committed {git(state.worktree, 'rev-parse', 'HEAD').strip()} on {state.branch}")
    elif _subject(state) not in git(state.worktree, "log", "--format=%s", f"{state.base_commit}..HEAD").splitlines():
        # A retried publish finds its own commit and pushes again; otherwise nothing changed.
        print("[publish] the coordinate project is unchanged; nothing to publish")
        return NO_SCENARIO_LEFT
    coverage: TaskResult = run_gradle(
        state.worktree, "realUseJacocoReport", state.coordinate, runtime_path(workspace, "logs", "realUseJacocoReport.log")
    )
    if not coverage.ok:
        print(f"ERROR: the coverage run failed; see {coverage.log_path}:\n{coverage.tail}", file=sys.stderr)
        return INFRASTRUCTURE
    write_json(os.path.join(state.worktree, stats_dir(state), "stats.json"), stats_record(workspace, state))
    git(state.worktree, "add", "-A", "--", stats_dir(state))
    if git(state.worktree, "diff", "--cached", "--name-only"):
        git(state.worktree, "commit", "--quiet", "-m",
            _bounded(f"Record the real-use run for {state.artifact} {state.version}"))
    return _push(workspace, state)
