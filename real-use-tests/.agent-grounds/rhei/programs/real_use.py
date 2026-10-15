# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Entry point for every program state of the real-use workflow.

Each subcommand is one program state of §AR-real-use-test-workflow.4; its exit
code is the transition Rhei takes. The working directory is the Rhei workspace
root. Every exit writes Rhei's result file, which an edge into a final state
requires (§FS-real-use-test-generation.3).
"""
from __future__ import annotations

import argparse
import os
import sys
import traceback
from typing import Callable

import native_phase
import preflight
import prepare
import publish
import style_check
from exit_codes import INFRASTRUCTURE

STEPS: dict[str, Callable[[str], int]] = {
    "apply-preflight": preflight.apply_preflight,
    "native": native_phase.native,
    "collect-metadata": native_phase.collect_metadata,
    "style-check": style_check.style_check,
    "publish": publish.publish,
}


def build_parser() -> argparse.ArgumentParser:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="step", required=True)
    prepare_parser: argparse.ArgumentParser = commands.add_parser("prepare")
    prepare_parser.add_argument("--issue-number", type=int, required=True)
    prepare_parser.add_argument("--scenario-passes", type=int, required=True)
    prepare_parser.add_argument("--repo-checkout", required=True)
    prepare_parser.add_argument("--worktree-root", required=True)
    for step in STEPS:
        commands.add_parser(step)
    return parser


def _write_result(step: str, code: int) -> None:
    result_path: str | None = os.environ.get("RHEI_RESULT_PATH")
    if result_path:
        os.makedirs(os.path.dirname(result_path), exist_ok=True)
        with open(result_path, "w", encoding="utf-8") as handle:
            handle.write(f"{step} exited {code}; see runtime/real-use/ and runtime/logs/.\n")


def main(argv: list[str] | None = None) -> int:
    args: argparse.Namespace = build_parser().parse_args(argv)
    args.workspace = os.getcwd()
    try:
        code: int = prepare.run(args) if args.step == "prepare" else STEPS[args.step](args.workspace)
    except Exception:
        traceback.print_exc()
        code = INFRASTRUCTURE
    _write_result(args.step, code)
    return code


if __name__ == "__main__":
    sys.exit(main())
