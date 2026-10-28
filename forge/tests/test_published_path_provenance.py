# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Keep operator-only log paths out of what Forge publishes.

Every record here leaves the machine that produced it: the descriptor and the
pull-request body are read by maintainers, and the metrics record is committed to
a public repository. A path into `forge/logs/` resolves for none of them, and an
absolute one carries the operator's home directory along with it.
§FS-durable-generation-logs, §AR-publication-descriptor
"""

import glob
import importlib.util
import json
import os
import sys
import unittest
from typing import Any

from utility_scripts.local_ci_verification import (
    CommandRecord,
    FixupRecord,
    LocalCIVerificationResult,
)
from utility_scripts.task_logs import new_session_id

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)


def _load_publisher() -> Any:
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_path_provenance", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _string_values(value: Any, path: str = "") -> list[tuple[str, str]]:
    """Flatten every string a JSON-shaped value carries, with its key path."""
    if isinstance(value, dict):
        return [
            found
            for key, item in value.items()
            for found in _string_values(item, f"{path}.{key}")
        ]
    if isinstance(value, list):
        return [found for item in value for found in _string_values(item, f"{path}[]")]
    return [(path, value)] if isinstance(value, str) else []


class PublishedPathProvenanceTests(unittest.TestCase):
    def test_committed_execution_metrics_carry_no_absolute_path(self) -> None:
        """The 336 committed home directories of issue #9985 must not come back."""
        offenders: list[str] = []
        for metrics_path in glob.glob(
                os.path.join(REPOSITORY_ROOT, "stats", "*", "*", "*", "execution-metrics.json"),
        ):
            with open(metrics_path, encoding="utf-8") as metrics_file:
                entries = json.load(metrics_file)
            offenders.extend(
                f"{os.path.relpath(metrics_path, REPOSITORY_ROOT)}{key_path}: {value}"
                for key_path, value in _string_values(entries)
                if value.startswith("/") or value[1:3] == ":\\"
            )

        self.assertEqual(offenders, [])

    def test_committed_execution_metrics_carry_no_preflight_path_field(self) -> None:
        offenders: list[str] = []
        for metrics_path in glob.glob(
                os.path.join(REPOSITORY_ROOT, "stats", "*", "*", "*", "execution-metrics.json"),
        ):
            with open(metrics_path, encoding="utf-8") as metrics_file:
                entries = json.load(metrics_file)
            for key, entry in entries.items():
                preflight = entry.get("library_preparation_preflight")
                if not isinstance(preflight, dict):
                    continue
                offenders.extend(
                    f"{os.path.relpath(metrics_path, REPOSITORY_ROOT)}[{key}].{field}"
                    for field in ("prompt_path", "raw_response_path", "session_log_path")
                    if field in preflight
                )

        self.assertEqual(offenders, [])


if __name__ == "__main__":
    unittest.main()
