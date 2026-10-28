# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `audit` program: the local pre-push audit as one ordinary fix request.

The first visit checkpoints and asks `fix` to check every test and metadata
change against the repository rules; the return visit lets the tree decide: an
unchanged tree publishes, and a changed one reruns the native check and the
`finalize` lane once (§FS-local-branch-review, §AR-forge-workflow-pipeline.4.3).
There is no verdict and no second pass.
"""
from __future__ import annotations

import os

import finalize
import gates
import handoff
import native_check
from build_tools import changed_paths, checkpoint
from exit_codes import AGENT_WORK, OK, STILL_FAILING
from run_state import RunState, load, save

SENDER: str = "audit"


PATTERNS: str = """
Check against the test contract (§root/FS-test-contract.1, §root/FS-test-contract.2,
§root/FS-test-contract.3, §root/FS-test-contract.4, §root/FS-test-contract.2.7) and the
cheating patterns of §root/FS-contribution-contract.4, each at the bar it sets:
- scaffold: only a test whose body is still the unmodified TCK placeholder; never rewrite a test
  because it looks thin;
- Native Image dodging: native skips, early returns, a bare `catch (Error)`, or
  `isUnsupportedFeatureError` outside open-ended dynamic class loading;
- fake requested coverage: the metadata in `reporter-request.md` must be reached through the
  library's public API, not by direct reflection, class literals, or `getResource`;
- coverage bought by asserting a known bug, pinned tested-library versions, tests in the library's
  own package, and stubs of library types;
- fix by weakening: deleted or disabled tests, removed assertions, swallowed exceptions;
- a `typeReached` condition reached after the access, test-only types in shipped metadata,
  hand-added entries beyond what 2.7 allows, forbidden flags, and legacy `*-config.json` files.
Fix each violation, then rerun the commands; change nothing that follows the rules.
""".strip()


def brief(state: RunState) -> str:
    """The audit's one brief (§FS-local-branch-review.2).

    The rules themselves are the `fix` instruction's.
    """
    parts: list[str] = []
    if state.previous_coordinate is None:
        parts.append(f"Check every test and metadata change since `{state.base_commit}`.")
    else:
        previous: str = state.previous_coordinate.split(":")[2]
        parts.append(f"Check only the tests and metadata this run changed or added: tests carried over "
                     f"unchanged from `{previous}` (compare with `tests/src/{state.group}/{state.artifact}/"
                     f"{previous}/`) are not yours to review.")
    if gates.scopes(gates.dynamic_access(state, state.version))[gates.OVERALL].total == 0:
        parts.append("This version reports no dynamic-access calls, so its tests matter less: test "
                     "depth is not a violation, and you add or deepen no test for it.")
    return "\n\n".join([*parts, PATTERNS])


def commands(workspace: str, state: RunState) -> list[handoff.Failure]:
    """The native check and the `finalize` lane, every one passing, as the request's commands.

    The lane opens with the harness split and routing, so they follow the regeneration
    (§AR-forge-workflow-pipeline.5).
    """
    native: list[str] = list(native_check.commands(state))
    return [handoff.Failure(command, "") for command in native + finalize.lane_commands(workspace, state)]


def changed(state: RunState) -> list[str]:
    """Every path that differs from the audit checkpoint: an edit outside the file set fails the rerun."""
    return changed_paths(state.worktree, state.audit_checkpoint)


def audit(workspace: str) -> int:
    state: RunState = load(workspace)
    handoff.write_placeholder(workspace, SENDER)
    if state.audit_passed:
        print("[audit] already passed")
        return OK
    if not state.audit_checkpoint:
        state.audit_checkpoint = checkpoint(state.worktree, state.contribution_paths(), f"Audit {state.coordinate}")
        handoff.write_fix_request(workspace, state, SENDER, commands(workspace, state), [brief(state)])
        save(workspace, state)
        print(f"[audit] the audit request follows, from {state.audit_checkpoint}")
        return AGENT_WORK
    state.pending_request = None
    edited: list[str] = changed(state)
    if edited:
        print(f"[audit] the audit changed {', '.join(os.path.basename(path) for path in edited)}; rerunning the checks")
        failures: list[handoff.Failure] = (native_check.run_native_check(workspace, state, SENDER)
                                           or finalize.run_lane(workspace, state, SENDER))
        if failures:
            save(workspace, state)
            print(f"[audit] `{failures[0].command}` fails over the audit's edit")
            return STILL_FAILING
    state.audit_passed = True
    save(workspace, state)
    print(f"[audit] passes at {state.checkpoint}")
    return OK
