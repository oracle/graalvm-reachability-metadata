# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import sys

from utility_scripts.task_logs import build_task_log_path, display_log_path
from utility_scripts.repo_path_resolver import require_complete_reachability_repo

CODEX_MODEL_NAME = "oca/gpt-5.4"
CODEX_TIMEOUT_SECONDS = 1200


def run_codex_metadata_fix(
        reachability_metadata_path: str,
        coordinates: str,
        reproduction_command: str | None = None,
) -> tuple[int, str, bool]:
    """Run Codex to update metadata entries for the target library.

    Requires the ``fix-missing-reachability-metadata`` skill. The skill definition lives at:
    https://github.com/oracle/graalvm-reachability-metadata/blob/master/skills/fix-missing-reachability-metadata/SKILL.md
    """
    reachability_metadata_path = require_complete_reachability_repo(reachability_metadata_path)
    prompt = f"Fix the metadata entries for {coordinates}"
    if reproduction_command:
        prompt += f"\n\nReproduce the failure with:\n{reproduction_command}"
    cmd = [
        "codex", "exec",
        "--dangerously-bypass-approvals-and-sandbox",
        "--json",
        "-c", 'reasoning.effort="high"',
        "-m", CODEX_MODEL_NAME,
        prompt,
    ]
    log_path = build_task_log_path("metadata-fix", coordinates, "codex.log")
    log_path_display = display_log_path(log_path)
    print(f"[Codex running... Output: {log_path_display}]")
    try:
        with open(log_path, "w", encoding="utf-8") as log_file:
            result = subprocess.run(
                cmd,
                cwd=reachability_metadata_path,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                timeout=CODEX_TIMEOUT_SECONDS,
                check=False,
            )
    except subprocess.TimeoutExpired:
        print(
            f"ERROR: Codex metadata fix timed out after {CODEX_TIMEOUT_SECONDS} seconds for {coordinates}.",
            file=sys.stderr,
        )
        return (1, log_path, True)

    if result.returncode != 0:
        print(
            f"ERROR: Codex metadata fix failed for {coordinates} with exit code {result.returncode}.",
            file=sys.stderr,
        )
    return (result.returncode, log_path, False)
