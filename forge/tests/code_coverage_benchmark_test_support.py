# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fixtures shared by the coverage benchmark test modules.

§FS-code-coverage-benchmarking
"""

import json
import subprocess
from pathlib import Path


FORGE_ROOT = Path(__file__).resolve().parents[1]
FINAL_METRICS = (
    FORGE_ROOT / "tests" / "fixtures" / "code_coverage" / "final_metrics.json"
)


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _git(repo: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *arguments],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    )
