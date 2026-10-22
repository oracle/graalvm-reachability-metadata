# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Entry point for every program state of the `forge-metadata` workflow.

Each subcommand is one program state of §AR-forge-workflow-pipeline.4; its exit
code is the transition Rhei takes (§AR-forge-workflow-pipeline.2). The working
directory is the Rhei workspace root. Every exit writes Rhei's result file,
which an edge into a final state requires, and a terminal outcome is recorded
for the launcher's close-out (§AR-forge-workflow-pipeline.1).
"""
from __future__ import annotations

import argparse
import os
import sys
import traceback
from typing import Callable

# The programs reuse Forge's utility and git modules from the source checkout.
FORGE_DIR: str = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
if FORGE_DIR not in sys.path:
    sys.path.insert(0, FORGE_DIR)

import explore  # noqa: E402
import finalize  # noqa: E402
import fix_route  # noqa: E402
import native_check  # noqa: E402
import prepare  # noqa: E402
import publish  # noqa: E402
import repair  # noqa: E402
from exit_codes import (  # noqa: E402
    AGENT_WORK,
    BAD_INPUT,
    CANNOT_FIX,
    INFRASTRUCTURE,
    JVM_TESTS_FAIL,
    NOTHING_TO_DO,
    SKIP_RECORDED,
    OK,
    RETURN_CODES,
    STILL_FAILING,
)
from run_state import runtime_path, write_json  # noqa: E402

STEPS: dict[str, Callable[[str], int]] = {
    "repair": repair.repair,
    "analyze": explore.analyze,
    "measure": explore.measure,
    "native": native_check.native,
    "finalize": finalize.finalize,
    "fix-route": fix_route.fix_route,
    "publish": publish.publish,
}
OUTCOME: str = "outcome.json"
# Every other exit a program takes is a declared route to a non-final state.
KNOWN_ROUTES: frozenset[int] = frozenset(
    {OK, AGENT_WORK, JVM_TESTS_FAIL, STILL_FAILING, NOTHING_TO_DO, SKIP_RECORDED, CANNOT_FIX,
     *RETURN_CODES.values()})


def build_parser() -> argparse.ArgumentParser:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="step", required=True)
    prepare.add_arguments(commands.add_parser("prepare"))
    for step in STEPS:
        commands.add_parser(step)
    return parser


def outcome(step: str, code: int) -> dict[str, str] | None:
    """The final state an exit leads to, for the launcher's close-out, or `None` when the run goes on.

    §FS-forge-run-status
    """
    if code == STILL_FAILING:
        return {"outcome": "human-intervention", "reason": f"{step} still fails after its fix"}
    if step == "fix-route" and code == CANNOT_FIX:
        return {"outcome": "human-intervention", "reason": "the fix reported that nothing within the rules helps"}
    if step == "publish" and code == OK:
        return {"outcome": "completed", "reason": "the publication branch is pushed"}
    if step == "publish" and code == NOTHING_TO_DO:
        return {"outcome": "nothing-to-publish", "reason": "the coordinate's paths are unchanged"}
    if code == BAD_INPUT:
        return {"outcome": "failed", "reason": f"{step}: bad input", "kind": "bad-input"}
    if code == INFRASTRUCTURE or (code != OK and code not in KNOWN_ROUTES):
        return {"outcome": "failed", "reason": f"{step}: infrastructure failure", "kind": "infrastructure"}
    return None


def _write_result(workspace: str, step: str, code: int) -> None:
    result: dict[str, str] | None = outcome(step, code)
    if result is not None:
        write_json(runtime_path(workspace, OUTCOME), {"step": step, "exit": str(code), **result})
    result_path: str | None = os.environ.get("RHEI_RESULT_PATH")
    if result_path:
        os.makedirs(os.path.dirname(result_path), exist_ok=True)
        with open(result_path, "w", encoding="utf-8") as handle:
            reason: str = result["reason"] if result else "the run continues"
            handle.write(f"{step} exited {code}: {reason}; see runtime/forge/ and runtime/logs/.\n")


def main(argv: list[str] | None = None) -> int:
    args: argparse.Namespace = build_parser().parse_args(argv)
    args.workspace = os.getcwd()
    try:
        code: int = prepare.run(args) if args.step == "prepare" else STEPS[args.step](args.workspace)
    except Exception:
        traceback.print_exc()
        code = INFRASTRUCTURE
    _write_result(args.workspace, args.step, code)
    return code


if __name__ == "__main__":
    sys.exit(main())
