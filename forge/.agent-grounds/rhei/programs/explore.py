# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The explore programs: `analyze` once, then `measure` after every cover pass.

`analyze` builds the native test image once to list the dynamic-access call
sites, keeps a copy, correlates JaCoCo against it, and picks the mode;
`measure` reruns only the JVM tests with JaCoCo against that copy, keeps a pass
that gained, resets one that did not, and advances the mode
(§AR-forge-workflow-pipeline.4.2, §AR-forge-workflow-pipeline.7). The
dynamic-access report is the only oracle (§FS-forge-chunked-dynamic-access).
"""
from __future__ import annotations

import os
import shutil
import sys
from dataclasses import dataclass

from utility_scripts.dynamic_access_report import DynamicAccessCoverageReport, load_dynamic_access_coverage_report

import cover_prompts
import handoff
from build_tools import TaskResult, checkpoint, coordinate_task, head, reset_to, run_gradle
from cover_prompts import MODE_BASIC, MODE_BULK, MODE_CLASS
from exit_codes import AGENT_WORK, INFRASTRUCTURE, JVM_TESTS_FAIL, NOTHING_TO_DO, OK, STILL_FAILING
from run_state import ROUTE_NEW, ROUTE_REPAIR, Budgets, Chunk, Explore, RunState, load, runtime_path, save

SENDER: str = "measure"
MODE_SKIP: str = "skip"
MODE_DEFER: str = "defer"
CALL_SITES: str = "call-sites"
ACCEPTED_REPORT: str = "coverage.json"
CALL_SITES_PROPERTY: str = "tck.dynamicAccess.callSites"


@dataclass(frozen=True)
class Decision:
    """`analyze`'s explore decision (§AR-forge-workflow-pipeline.7)."""

    mode: str
    targets: tuple[str, ...]
    # Whether this run starts a chunk: the issue was not chunked and is too large for one run.
    start_chunk: bool
    reason: str


def open_classes(report: DynamicAccessCoverageReport, skip: set[str]) -> list[str]:
    """Classes with uncovered calls, in report order, less those already processed."""
    return [entry.class_name for entry in report.classes if entry.uncovered_calls > 0 and entry.class_name not in skip]


def goal_met(report: DynamicAccessCoverageReport, goal_covered_calls: int | None, goal_total_calls: int | None) -> bool:
    """Whether the report reaches the previous version's coverage ratio (§AR-forge-workflow-pipeline.7).

    The ratio is what the finalize gate checks, so the goal and the gate agree
    however many call sites the new version has. Stats without a total fall
    back to the covered-call count.
    """
    if goal_covered_calls is None:
        return False
    if not goal_total_calls:
        return report.covered_calls >= goal_covered_calls
    return report.covered_calls * goal_total_calls >= goal_covered_calls * report.total_calls


def decide(route: str, report: DynamicAccessCoverageReport, budgets: Budgets, chunk: Chunk,
           goal_covered_calls: int | None, goal_total_calls: int | None, has_reporter_request: bool) -> Decision:
    """Pick the explore mode from the first measured report."""
    targets: tuple[str, ...] = tuple(open_classes(report, set(chunk.processed)))
    nothing_left: str = MODE_BASIC if has_reporter_request else MODE_SKIP
    if not report.has_dynamic_access or report.total_calls == 0:
        mode: str = MODE_BASIC if route == ROUTE_NEW else nothing_left
        return Decision(mode, (), False, "the library reports no dynamic-access calls")
    if not targets:
        return Decision(nothing_left, (), False, "every dynamic-access class is covered or processed")
    if route != ROUTE_NEW and goal_met(report, goal_covered_calls, goal_total_calls):
        return Decision(nothing_left, (), False,
                        f"{report.covered_calls}/{report.total_calls} covered calls already meet the previous "
                        f"version's {goal_covered_calls}/{goal_total_calls}")
    if route == ROUTE_REPAIR and len(targets) > budgets.defer_classes:
        return Decision(MODE_DEFER, targets, False,
                        f"{len(targets)} classes to cover exceed defer_classes {budgets.defer_classes}")
    start_chunk: bool = not chunk.active and len(targets) > budgets.defer_classes
    mode = MODE_BULK if len(targets) >= budgets.bulk_min_classes else MODE_CLASS
    return Decision(mode, targets, start_chunk, f"{len(targets)} classes with uncovered calls")


def class_covered(report: DynamicAccessCoverageReport, class_name: str) -> bool:
    entry = report.get_class(class_name)
    return entry is None or entry.uncovered_calls == 0


def remaining(explore: Explore, report: DynamicAccessCoverageReport) -> list[str]:
    """This run's targets that still have uncovered calls and are not exhausted."""
    return [name for name in explore.targets if not class_covered(report, name) and name not in explore.exhausted]


def gained(explore: Explore, before: DynamicAccessCoverageReport, after: DynamicAccessCoverageReport) -> bool:
    """Whether the last pass covered more: the active class in class mode, the whole report otherwise."""
    if explore.mode == MODE_BASIC:
        return True
    if explore.mode == MODE_CLASS and explore.class_name:
        previous = before.get_class(explore.class_name)
        current = after.get_class(explore.class_name)
        return (current.covered_calls if current else 0) > (previous.covered_calls if previous else 0)
    return after.covered_calls > before.covered_calls


def advance(state: RunState, report: DynamicAccessCoverageReport, pass_gained: bool) -> int:
    """Move the mode after one measured pass; return `AGENT_WORK`, `OK` (goal met), or `NOTHING_TO_DO`."""
    explore: Explore = state.explore
    explore.completed = [name for name in explore.targets if class_covered(report, name)]
    if explore.mode == MODE_BASIC:
        return OK
    if state.route != ROUTE_NEW and goal_met(report, state.goal_covered_calls, state.goal_total_calls):
        return OK
    if explore.mode == MODE_BULK:
        explore.bulk_passes += 1
        if not (pass_gained and explore.bulk_passes < state.budgets.bulk_passes):
            explore.mode = MODE_CLASS
            explore.class_name = None
    elif explore.class_name is not None:
        if class_covered(report, explore.class_name):
            explore.class_name = None
        elif explore.class_attempt >= state.budgets.class_attempts:
            explore.exhausted.append(explore.class_name)
            explore.class_name = None
        else:
            explore.class_attempt += 1
    left: list[str] = remaining(explore, report)
    if not left:
        return OK
    if state.chunk.active and state.chunk.budget is not None \
            and len(explore.completed) + len(explore.exhausted) >= state.chunk.budget:
        state.chunk.final = False
        return NOTHING_TO_DO
    if explore.passes >= state.budgets.explore_passes:
        return NOTHING_TO_DO
    if explore.mode == MODE_CLASS and explore.class_name is None:
        explore.class_name = left[0]
        explore.class_attempt = 1
        explore.feedback = ""
    return AGENT_WORK


def call_sites_dir(workspace: str) -> str:
    """The workspace copy of the call sites `analyze` listed."""
    return os.path.dirname(runtime_path(workspace, CALL_SITES, "placeholder"))


def correlate(workspace: str, state: RunState, log_name: str) -> TaskResult:
    """JaCoCo over the JVM tests, correlated against the stored call sites, with no native build."""
    return run_gradle(
        state.worktree,
        coordinate_task("generateDynamicAccessCoverageReport", state.coordinate,
                        f"-P{CALL_SITES_PROPERTY}={call_sites_dir(workspace)}"),
        runtime_path(workspace, "logs", log_name),
    )


def report_path(state: RunState) -> str:
    return state.path(os.path.join(state.test_dir, "build", "reports", "dynamic-access", "dynamic-access-coverage.json"))


def accept_report(workspace: str, state: RunState) -> DynamicAccessCoverageReport:
    """Keep the freshly written report as the one every later prompt and comparison reads."""
    shutil.copyfile(report_path(state), runtime_path(workspace, "reports", ACCEPTED_REPORT))
    return accepted_report(workspace)


def accepted_report(workspace: str) -> DynamicAccessCoverageReport:
    return load_dynamic_access_coverage_report(runtime_path(workspace, "reports", ACCEPTED_REPORT))


def list_call_sites(workspace: str, state: RunState) -> bool:
    """Build the native test image once and keep a copy of its dynamic-access output."""
    listed: TaskResult = run_gradle(
        state.worktree, coordinate_task("generateDynamicAccessReport", state.coordinate),
        runtime_path(workspace, "logs", "analyze-generateDynamicAccessReport.log"),
    )
    output: str = state.path(os.path.join(state.test_dir, "build", "native", "nativeTestCompile", "dynamic-access"))
    if not listed.ok or not os.path.isdir(output):
        print(f"ERROR: the dynamic-access call sites could not be listed:\n{listed.tail}", file=sys.stderr)
        return False
    copy: str = call_sites_dir(workspace)
    shutil.rmtree(copy, ignore_errors=True)
    shutil.copytree(output, copy)
    return True


def analyze(workspace: str) -> int:
    state: RunState = load(workspace)
    cover_prompts.write_placeholder(workspace)
    if not list_call_sites(workspace, state):
        return INFRASTRUCTURE
    measured: TaskResult = correlate(workspace, state, "analyze-coverage.log")
    if not measured.ok:
        print(f"ERROR: the first coverage report failed:\n{measured.tail}", file=sys.stderr)
        return INFRASTRUCTURE
    report: DynamicAccessCoverageReport = accept_report(workspace, state)
    decision: Decision = decide(state.route, report, state.budgets, state.chunk,
                                state.goal_covered_calls, state.goal_total_calls, state.has_reporter_request)
    state.explore = Explore(mode=decision.mode, targets=list(decision.targets), covered_calls=report.covered_calls,
                            total_calls=report.total_calls, checkpoint=head(state.worktree))
    print(f"[analyze] {decision.mode}: {decision.reason}")
    if decision.mode == MODE_DEFER:
        state.explore.deferred_classes = len(decision.targets)
    if decision.mode in (MODE_SKIP, MODE_DEFER):
        save(workspace, state)
        return NOTHING_TO_DO
    if decision.start_chunk:
        state.chunk = Chunk(active=True, processed=[], budget=state.budgets.defer_classes, final=True)
    if decision.mode == MODE_CLASS:
        state.explore.class_name = decision.targets[0]
        state.explore.class_attempt = 1
    cover_prompts.write_prompt(workspace, state, report)
    save(workspace, state)
    return AGENT_WORK


def measure(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    cover_prompts.write_placeholder(workspace)
    before: DynamicAccessCoverageReport = accepted_report(workspace)
    measured: TaskResult = correlate(workspace, state, f"measure-{state.explore.passes + 1}.log")
    explore: Explore = state.explore
    if measured.ok:
        state.pending_request = None
        report: DynamicAccessCoverageReport = load_dynamic_access_coverage_report(report_path(state))
        pass_gained: bool = gained(explore, before, report)
    else:
        tested: TaskResult = run_gradle(
            state.worktree, coordinate_task("javaTest", state.coordinate),
            runtime_path(workspace, "logs", f"measure-{explore.passes + 1}-javaTest.log"),
        )
        if tested.ok:
            print(f"ERROR: the coverage report failed while the JVM tests pass:\n{measured.tail}", file=sys.stderr)
            return INFRASTRUCTURE
        if not handoff.guard_tripped(state, SENDER):
            handoff.write_fix_request(workspace, state, SENDER, [handoff.Failure(tested.command, tested.tail)])
            save(workspace, state)
            print("[measure] the cover pass broke the JVM tests; a fix request follows")
            return JVM_TESTS_FAIL
        if not explore.checkpoint:
            return STILL_FAILING
        # The loop guard's one exception: an unfixable pass costs that pass, not the run.
        state.pending_request = None
        report, pass_gained = before, False
    explore.passes += 1
    if pass_gained:
        explore.checkpoint = checkpoint(state.worktree, state.coordinate_paths,
                                        f"Cover {state.coordinate}, pass {explore.passes}")
        report = accept_report(workspace, state) if measured.ok else report
        explore.covered_calls = report.covered_calls
        explore.feedback = ""
    else:
        discarded: list[str] = reset_to(state.worktree, explore.checkpoint, [state.test_dir])
        names: str = ", ".join(os.path.basename(path) for path in discarded) or "nothing"
        why: str = "gained nothing" if measured.ok else "broke the JVM tests and could not be fixed"
        explore.feedback = f"it {why}; these files were reset: {names}."
        report = before
    state.checkpoint = explore.checkpoint
    code: int = advance(state, report, pass_gained)
    if code == AGENT_WORK:
        cover_prompts.write_prompt(workspace, state, report)
    save(workspace, state)
    print(f"[measure] pass {explore.passes}: {report.covered_calls}/{report.total_calls} covered, "
          f"{'kept' if pass_gained else 'reset'}; next: {explore.mode if code == AGENT_WORK else 'native'}")
    return code
