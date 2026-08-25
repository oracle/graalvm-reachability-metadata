# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run location: the one phase/step vocabulary for progress and failure output.

The pipeline (§AR-forge-workflow-pipeline) names every step; this module turns
those names into the single vocabulary that both the progress logger and the
failure reporter use, so they cannot disagree
(§AR-forge-run-location, §FS-forge-run-location-reporting).
"""

import functools
import inspect
import sys
import threading
from collections.abc import Callable, Iterator, Mapping
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Any, TypeVar

from utility_scripts.continuation_marker import (
    PHASE_EXPLORE,
    PHASE_FINALIZATION,
    PHASE_FIX,
    PHASE_PUBLICATION,
    PHASE_SETUP,
    ContinuationMarker,
    save_phase_update,
)
from utility_scripts.stage_logger import log_phase_banner, log_stage

# The dispatcher-side segment that runs before a run — and therefore before any
# continuation state — exists. §FS-forge-run-location-reporting.1
PHASE_CLAIM = "claim"

# The durable phases are imported, never redeclared. §FS-forge-run-continuation.1
ORDERED_RUN_PHASES: tuple[str, ...] = (
    PHASE_CLAIM,
    PHASE_SETUP,
    PHASE_FIX,
    PHASE_EXPLORE,
    PHASE_FINALIZATION,
    PHASE_PUBLICATION,
)

STEP_CHECK_HOST_REQUIREMENTS = "check_host_requirements()"
STEP_CHECK_STRATEGY_AND_MODEL = "check_strategy_and_model()"
STEP_CHECK_ISSUE_FORM = "check_issue_form()"
STEP_CLAIM_ISSUE = "claim_issue()"
STEP_CREATE_ISSUE_WORKSPACE = "create_issue_workspace()"
STEP_ROUTE_TO_DRIVER = "route_to_driver()"
STEP_NEURAL_SETUP = "neural_setup()"
STEP_NORMAL_SETUP = "normal_setup()"
STEP_RUN_WORKFLOW_ENGINE = "run_workflow_engine()"
STEP_FIX_REPORTED_FAILURE = "fix_reported_failure()"
STEP_GENERATE_TESTS = "generate_tests()"
STEP_NATIVE_TRACE_GATE = "native_trace_gate()"
STEP_AGENT_FIX = "agent_fix()"
STEP_FINALIZE_RUN = "finalize_run()"
STEP_LOCAL_CI_CHECK = "local_ci_check()"
STEP_PUBLISH_BRANCH = "publish_branch()"

# The only place a step name is bound to a phase; `(<n>/<total>)` is derived from
# it rather than hand-counted, and a step the pipeline does not enter is not
# listed. §FS-forge-run-location-reporting.1
PHASE_STEPS: dict[str, tuple[str, ...]] = {
    PHASE_CLAIM: (
        STEP_CHECK_HOST_REQUIREMENTS,
        STEP_CHECK_STRATEGY_AND_MODEL,
        STEP_CHECK_ISSUE_FORM,
        STEP_CLAIM_ISSUE,
        STEP_CREATE_ISSUE_WORKSPACE,
        STEP_ROUTE_TO_DRIVER,
    ),
    PHASE_SETUP: (
        STEP_NEURAL_SETUP,
        STEP_NORMAL_SETUP,
        STEP_RUN_WORKFLOW_ENGINE,
    ),
    PHASE_FIX: (
        STEP_FIX_REPORTED_FAILURE,
        STEP_GENERATE_TESTS,
        STEP_NATIVE_TRACE_GATE,
        STEP_AGENT_FIX,
    ),
    PHASE_EXPLORE: (
        STEP_GENERATE_TESTS,
        STEP_NATIVE_TRACE_GATE,
        STEP_AGENT_FIX,
    ),
    PHASE_FINALIZATION: (
        STEP_FINALIZE_RUN,
        STEP_AGENT_FIX,
    ),
    PHASE_PUBLICATION: (
        STEP_PUBLISH_BRANCH,
        STEP_LOCAL_CI_CHECK,
    ),
}

UNLOCATED_STEP = "<unlocated-step>"
LOCATION_ATTRIBUTE = "forge_run_location"
FAILURE_LINE_PREFIX = "run failed in "
UNLOCATED_FAILURE_DEFECT = (
    "DEFECT: this failure was raised outside every pipeline step boundary; "
    "the raising path must be marked with run_step()/pipeline_step()."
)

T = TypeVar("T")


@dataclass(frozen=True)
class RunLocation:
    """Where a run is: the phase, the step inside it, and the step's operand.

    Rendered as `<phase>/<step>`, or `<phase>/<step>[<operand>]` when the step
    has an operand. §FS-forge-run-location-reporting.1
    """

    phase: str
    step: str
    operand: str | None = None

    def __str__(self) -> str:
        rendered = f"{self.phase}/{self.step}"
        return rendered if self.operand is None else f"{rendered}[{self.operand}]"

    @property
    def is_located(self) -> bool:
        """Return False for the placeholder used when no step claimed the failure."""
        return self.step != UNLOCATED_STEP

    def to_dict(self) -> dict[str, Any]:
        """Return the marker payload for this location."""
        return {"phase": self.phase, "step": self.step, "operand": self.operand}


class _RunLocationState(threading.local):
    """Per-thread run location; issue runs are processed on a thread pool."""

    def __init__(self) -> None:
        self.stack: list[RunLocation] = []
        self.failure: RunLocation | None = None
        self.marker_path: str | None = None
        self.phase: str | None = None
        self.context: str | None = None
        self.reported: bool = False


_STATE = _RunLocationState()


def step_position(phase: str, step: str) -> tuple[int, int]:
    """Return the 1-based position of a step and the phase's step total."""
    steps = require_phase_steps(phase)
    if step not in steps:
        raise ValueError(f"Unknown step {step!r} for phase {phase!r}; expected one of {list(steps)}")
    return steps.index(step) + 1, len(steps)


def require_phase_steps(phase: str) -> tuple[str, ...]:
    """Return the ordered steps of a phase, rejecting an unregistered phase name."""
    steps = PHASE_STEPS.get(phase)
    if steps is None:
        raise ValueError(f"Unknown run phase: {phase!r}; expected one of {list(ORDERED_RUN_PHASES)}")
    return steps


def current_run_location() -> RunLocation | None:
    """Return the innermost step currently executing on this thread."""
    return _STATE.stack[-1] if _STATE.stack else None


def failed_run_location() -> RunLocation | None:
    """Return the location recorded for this thread's failure, if any."""
    return _STATE.failure


def bind_continuation_marker(marker_path: str | None) -> None:
    """Point recorded failures at the run's continuation marker."""
    _STATE.marker_path = marker_path


def bind_run_context(context: str | None) -> None:
    """Name the run whose phases this thread announces.

    Runs execute concurrently on a pool (§AR-forge-control-plane), so a phase
    banner states which run entered the phase. §FS-forge-run-location-reporting.2
    """
    _STATE.context = context


def reset_run_location() -> None:
    """Clear all run-location state; called at the start of each issue run."""
    _STATE.stack = []
    _STATE.failure = None
    _STATE.marker_path = None
    _STATE.phase = None
    _STATE.context = None
    _STATE.reported = False


def enter_phase(phase: str) -> None:
    """Announce a phase the run is entering, once per transition into it.

    Re-announcing the phase the run is already in is not a transition, so the
    banner stays one per phase entry. §FS-forge-run-location-reporting.2
    """
    require_phase_steps(phase)
    if _STATE.phase == phase:
        return
    _STATE.phase = phase
    log_phase_banner(phase, context=_STATE.context)


@contextmanager
def run_step(phase: str, step: str, operand: str | None = None) -> Iterator[RunLocation]:
    """Mark a pipeline step: announce it, and locate anything that fails inside it.

    Entering prints the progress line; a propagating exception is annotated with
    this location — never wrapped, because `forge_metadata` classifies external
    failures by exception type. A user interrupt is not a run failure, so it
    travels unmarked. §FS-forge-run-location-reporting.2
    """
    location = announce_step(phase, step, operand)
    _STATE.stack.append(location)
    try:
        yield location
    except BaseException as exc:
        if not isinstance(exc, (KeyboardInterrupt, GeneratorExit)):
            annotate_exception_location(exc, location)
            record_step_failure(location=location)
        raise
    finally:
        _STATE.stack.pop()


def announce_step(phase: str, step: str, operand: str | None = None) -> RunLocation:
    """Print the progress line that opens a step and return its location."""
    position, total = step_position(phase, step)
    enter_phase(phase)
    suffix = "" if operand is None else f" on {operand}"
    log_stage(phase, f"Running step {step} ({position}/{total}) of phase {phase}{suffix}")
    return RunLocation(phase=phase, step=step, operand=operand)


def pipeline_step(
        phase: str,
        step: str,
        operand: Callable[[Mapping[str, Any]], str | None] | None = None,
) -> Callable[[Callable[..., T]], Callable[..., T]]:
    """Mark a whole function as one pipeline step.

    `operand` receives the wrapped call's arguments by parameter name — never by
    position, so a keyword call locates the same as a positional one — and
    returns the operand the step failed on. §FS-forge-run-location-reporting.1
    """
    step_position(phase, step)

    def decorator(function: Callable[..., T]) -> Callable[..., T]:
        signature = inspect.signature(function)

        @functools.wraps(function)
        def wrapper(*args: Any, **kwargs: Any) -> T:
            resolved_operand = None
            if operand is not None:
                bound = signature.bind(*args, **kwargs)
                bound.apply_defaults()
                resolved_operand = operand(bound.arguments)
            with run_step(phase, step, resolved_operand):
                return function(*args, **kwargs)

        return wrapper

    return decorator


def annotate_exception_location(exc: BaseException, location: RunLocation) -> None:
    """Attach a location to an exception, letting the innermost step win."""
    if getattr(exc, LOCATION_ATTRIBUTE, None) is None:
        setattr(exc, LOCATION_ATTRIBUTE, location)


def exception_run_location(exc: BaseException | None) -> RunLocation | None:
    """Return the location an exception was raised in, when a step marked it."""
    if exc is None:
        return None
    location = getattr(exc, LOCATION_ATTRIBUTE, None)
    return location if isinstance(location, RunLocation) else None


def record_step_failure(
        location: RunLocation | None = None,
        operand: str | None = None,
) -> RunLocation | None:
    """Record where this run failed, for status-code and raised failures alike.

    Status-code paths that return `RUN_STATUS_FAILURE` instead of raising call
    this so the lifecycle boundary reads one location either way. The first
    recorded failure wins. §FS-forge-run-location-reporting.3
    """
    resolved = location or current_run_location()
    if resolved is None:
        return _STATE.failure
    if operand is not None and resolved.operand != operand:
        resolved = RunLocation(phase=resolved.phase, step=resolved.step, operand=operand)
    if _STATE.failure is None:
        _STATE.failure = resolved
        _record_failure_in_marker(resolved)
    return _STATE.failure


def resolve_failure_location(exc: BaseException | None = None) -> RunLocation:
    """Return the location to report for a terminal failure.

    Falls back to the unlocated placeholder so a report always has both
    coordinates. §FS-forge-run-location-reporting.3
    """
    located = exception_run_location(exc) or failed_run_location() or current_run_location()
    if located is not None:
        return located
    return RunLocation(phase=_STATE.phase or PHASE_CLAIM, step=UNLOCATED_STEP)


def marker_failure_location(marker: ContinuationMarker | None) -> RunLocation | None:
    """Return the failure location a continuation marker carries."""
    if marker is None or not isinstance(marker.failure, dict):
        return None
    phase = marker.failure.get("phase")
    step = marker.failure.get("step")
    if not isinstance(phase, str) or not isinstance(step, str) or not phase or not step:
        return None
    operand = marker.failure.get("operand")
    return RunLocation(phase=phase, step=step, operand=operand if isinstance(operand, str) else None)


def format_run_failure_line(location: RunLocation) -> str:
    """Return the one line every terminal failure prints before its error detail."""
    return f"{FAILURE_LINE_PREFIX}{location}"


def report_run_failure(location: RunLocation, detail: str | None = None) -> str:
    """Print the failure location once per run, then its error detail.

    A run has one terminal failure, so the first reporter on the way out owns the
    line and later boundaries do not repeat it. An unlocated failure means a code
    path raised outside the pipeline's own step boundaries, which is a Forge
    defect rather than a formatting gap. §FS-forge-run-location-reporting.3
    """
    line = format_run_failure_line(location)
    if _STATE.reported:
        return line
    _STATE.reported = True
    print(line, file=sys.stderr)
    if not location.is_located:
        print(UNLOCATED_FAILURE_DEFECT, file=sys.stderr)
    if detail:
        print(detail, file=sys.stderr)
    return line


def _record_failure_in_marker(location: RunLocation) -> None:
    save_phase_update(
        _STATE.marker_path,
        lambda marker: marker.record_failure(location.phase, location.step, location.operand),
    )
