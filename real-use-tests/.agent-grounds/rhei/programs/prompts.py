# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The prompts programs hand to the two fix agents.

The agent states' instructions carry the fixed rules; the renderers add only
what this run observed: the failing output and, for the native fix, the
build file that takes native-image options and the packages a condition may
name (§AR-real-use-test-workflow.3).
"""
from __future__ import annotations

import os

from run_state import RunState


def native_fix(state: RunState, prefixes: list[str], tail: str) -> str:
    """The native fix prompt (§FS-real-use-test-generation.2.5)."""
    lines: list[str] = [
        "# Native fix",
        "",
        f"- Coordinate: `{state.coordinate}`",
        f"- Worktree: `{state.worktree}`",
        f"- Coordinate project: `{state.project_path}`",
        f"- Test-local metadata file: `{state.metadata_path}`",
        f"- Build file, for native-image options: `{os.path.join(state.project_path, 'build.gradle')}`",
        "",
        "Conditions may name types from these packages or the test's own packages: "
        + ", ".join(f"`{prefix.rstrip('.')}`" for prefix in prefixes),
        "",
    ]
    lines += [
        "The file already holds what the native-image agent collected on the JVM; add only what",
        "the native run still reports missing.",
        "",
    ]
    lines += ["Last lines of the failing native run:", "", "```text", tail.rstrip(), "```", ""]
    return "\n".join(lines)


def publication_fix(state: RunState, failures: dict[str, str]) -> str:
    """The publication fix prompt: each failing style check's output.

    §FS-real-use-test-generation.2.7
    """
    lines: list[str] = [
        "# Publication fix",
        "",
        f"- Coordinate: `{state.coordinate}`",
        f"- Worktree: `{state.worktree}`",
        f"- Coordinate project: `{state.project_path}`",
        "",
    ]
    for task, tail in failures.items():
        lines += [f"Last lines of the failing `{task}`:", "", "```text", tail.rstrip(), "```", ""]
    return "\n".join(lines)
