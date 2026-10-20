# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `style-check` program: the repository's style checks, once, before publication.

`spotlessCheck` holds the suite to the CC0 header and the JSON format, and
`checkstyle` runs over the coordinate project (§AR-real-use-test-harness.2). A
failure hands the findings to the publication fix agent; nothing is removed
(§FS-real-use-test-generation.2.7).
"""
from __future__ import annotations

import prompts
import scenarios
from build_tools import TaskResult, run_gradle
from exit_codes import NO_SCENARIO_LEFT, OK, STYLE_FAILED
from run_state import RunState, load, runtime_path

STYLE_TASKS: tuple[str, ...] = ("spotlessCheck", "checkstyle")


def style_check(workspace: str) -> int:
    state: RunState = load(workspace)
    prompt_path: str = runtime_path(workspace, "prompts", "publication-fix.md")
    with open(prompt_path, "w", encoding="utf-8") as handle:
        # The prompt is a declared output, so it exists on every exit.
        handle.write("The style checks passed; no publication fix runs.\n")
    if not scenarios.scenario_files(state.test_source_path):
        print("[style-check] no scenario remains")
        return NO_SCENARIO_LEFT
    failures: dict[str, str] = {}
    for task in STYLE_TASKS:
        result: TaskResult = run_gradle(
            state.worktree, task, state.coordinate, runtime_path(workspace, "logs", f"{task}.log"),
        )
        if not result.ok:
            failures[task] = result.tail
    if not failures:
        print("[style-check] the style checks pass")
        return OK
    with open(prompt_path, "w", encoding="utf-8") as handle:
        handle.write(prompts.publication_fix(state, failures))
    print(f"[style-check] {', '.join(failures)} failed; the publication fix follows")
    return STYLE_FAILED
