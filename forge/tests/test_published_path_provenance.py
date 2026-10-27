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

from git_scripts.local_branch_review import LocalBranchReviewOutcome, LocalReviewVerdict
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


def _outcome() -> LocalBranchReviewOutcome:
    verification = LocalCIVerificationResult(
        status="success",
        base_commit="base",
        final_commit="head",
        commands=[
            CommandRecord(
                gate="checkstyle",
                command=["./gradlew", "checkstyle"],
                returncode=0,
                output_excerpt="BUILD SUCCESSFUL",
            ),
        ],
        fixups=[
            FixupRecord(
                gate="checkstyle",
                command=["pi", "gpt-5.6-terra"],
                commit="fixup",
                changed_paths=["tests/src/org.example/demo/1.0.0/build.gradle"],
            ),
        ],
    )
    return LocalBranchReviewOutcome(
        model="gpt-5.6-terra",
        session_id=new_session_id(),
        local_ci_verification=verification,
        verdict=LocalReviewVerdict(
            decision="approved",
            review_comment="Checked every applicable rule.",
            finding_title="",
            finding_body="",
            fix_note="",
        ),
        changed_paths=["metadata/org.example/demo/reflect-config.json"],
    )


class PublishedPathProvenanceTests(unittest.TestCase):
    def test_rendered_pull_request_body_names_no_log_path(self) -> None:
        outcome = _outcome()
        body = publisher._render_local_review(
            {"local_review": outcome.to_descriptor_payload()},
        )

        self.assertIn(f"- Review session: `{outcome.session_id}`", body)
        self.assertNotIn("logs/", body)
        self.assertNotIn(".log", body)

    def test_descriptor_review_evidence_carries_no_filesystem_path(self) -> None:
        outcome = _outcome()
        evidence = {
            "local_review": outcome.to_descriptor_payload(),
            "local_ci_verification": outcome.local_ci_verification.to_metrics(),
        }

        for key_path, value in _string_values(evidence):
            self.assertFalse(key_path.endswith("log_path"), key_path)
            self.assertNotIn("logs/", value, key_path)

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
