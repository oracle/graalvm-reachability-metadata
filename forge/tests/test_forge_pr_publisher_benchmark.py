# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover trusted benchmark-result publication. §FS-code-coverage-benchmarking.3"""

import importlib.util
import json
import os
import sys
import unittest
from typing import Any
from unittest.mock import patch

from jsonschema import Draft202012Validator, FormatChecker, exceptions

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)
SCHEMA_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "schema.json",
)

RESULT_PATH = "code-coverage-benchmarks/com.example/demo/1.0.0.json"


def _load_publisher() -> Any:
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_benchmark", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _phase(before: int, after: int) -> dict[str, Any]:
    return {
        "coverPasses": 1,
        "fixInvocations": 0,
        "tokens": {"input": 10, "cachedInputRead": 20, "output": 3},
        "coverage": {
            "coveredBefore": before,
            "coveredAfter": after,
            "methodsGained": after - before,
            "percentagePointsGained": float(after - before),
            "allMethods": 100,
        },
    }


def _result(run_id: str = "run-1") -> dict[str, Any]:
    return {
        "schemaVersion": "1.0.0",
        "runId": run_id,
        "timestamp": "2026-09-08T13:03:29Z",
        "benchmarkSuiteCommit": "a" * 40,
        "runnerCommit": "b" * 40,
        "coordinate": "com.example:demo:1.0.0",
        "workspaceName": "code-coverage-99000",
        "agent": "pi",
        "configuredModel": "gpt-5.6-sol",
        "observedModel": "openai-codex/gpt-5.6-sol",
        "thinking": "high",
        "status": "failure",
        "failure": {"phase": "api", "exitCode": 1},
        "checkedInAllMethods": 100,
        "measuredAllMethodsDifference": 0,
        "api": _phase(10, 20),
        "deep": _phase(20, 25),
        "total": _phase(10, 25),
    }


def _publication_id(result: dict[str, Any]) -> str:
    return publisher._build_publication_id({
        "task_type": "code-coverage-benchmark-result",
        "timestamp": result["timestamp"],
        "benchmark_run_id": result["runId"],
        "library": {"coordinates": result["coordinate"]},
    })


def _branch(result: dict[str, Any], actor: str = "kimeta") -> str:
    return f"ai/{actor}/benchmark-demo-{_publication_id(result)}"


def _validate_diff(
        head_entries: list[dict[str, Any]],
        base_entries: list[dict[str, Any]] | None,
        *,
        changed_paths: list[str] | None = None,
        branch: str | None = None,
        actor: str = "kimeta",
) -> Any:
    result = head_entries[-1] if head_entries else _result()
    with patch.object(
            publisher,
            "_json_value_at_commit",
            side_effect=lambda commit, _path: head_entries
            if commit == "head" else base_entries,
    ), patch.object(
            publisher, "_git_object_exists", return_value=base_entries is not None,
    ):
        return publisher._validate_benchmark_diff_publication(
            head_sha="head",
            merge_base="base",
            changed_paths=changed_paths if changed_paths is not None else [RESULT_PATH],
            branch=branch if branch is not None else _branch(result, actor),
            actor=actor,
        )


class BenchmarkResultPublisherTests(unittest.TestCase):

    def test_diff_route_synthesizes_a_renderable_descriptor(self) -> None:
        validated = _validate_diff([_result()], None)

        descriptor = validated.descriptor
        self.assertEqual(descriptor["task_type"], "code-coverage-benchmark-result")
        self.assertTrue(descriptor["publication_id"].startswith("forge-benchmark-"))
        title, body = publisher.render_publication(descriptor)
        self.assertIn("[Benchmark] Record com.example:demo:1.0.0 result", title)
        self.assertIn("- Status: `failure`", body)
        self.assertIn("- Covered methods: 10 → 25", body)
        self.assertIn("- Failure phase: `api`", body)
        self.assertNotIn("Fixes:", body)

    def test_route_labels_join_the_coverage_queue(self) -> None:
        """A benchmark result is triaged next to the coverage work it measures."""
        self.assertEqual(
            publisher.ROUTE_LABELS["code-coverage-benchmark-result"],
            ["GenAI", "code-coverage-improvement", "rhei"],
        )

    def test_accepts_one_result_appended_to_a_populated_base(self) -> None:
        earlier = _result("run-0")
        appended = _result("run-1")

        validated = _validate_diff([earlier, appended], [earlier])

        self.assertEqual(
            validated.descriptor["benchmark_run_id"], appended["runId"],
        )

    def test_accepts_a_refreshed_head_without_a_recorded_base(self) -> None:
        """The merge base is computed fresh, so a refresh cannot stale it."""
        merged_elsewhere = _result("run-merged")
        own = _result("run-own")

        validated = _validate_diff(
            [merged_elsewhere, own], [merged_elsewhere],
            branch=_branch(own),
        )

        self.assertEqual(validated.descriptor["benchmark_run_id"], "run-own")

    def test_rejects_more_than_one_changed_path(self) -> None:
        with self.assertRaisesRegex(ValueError, "exactly one coordinate result list"):
            _validate_diff(
                [_result()], None,
                changed_paths=[RESULT_PATH, "code-coverage-benchmarks/other/lib/1.0.0.json"],
            )

    def test_rejects_a_path_outside_the_results_layout(self) -> None:
        with self.assertRaisesRegex(ValueError, "Unexpected benchmark result path"):
            _validate_diff(
                [_result()], None,
                changed_paths=["code-coverage-benchmarks/README.md"],
            )

    def test_rejects_more_than_one_appended_result(self) -> None:
        with self.assertRaisesRegex(ValueError, "append exactly one result"):
            _validate_diff([_result("run-1"), _result("run-2")], [])

    def test_rejects_a_coordinate_result_mismatch(self) -> None:
        mismatched = _result()
        mismatched["coordinate"] = "com.example:other:1.0.0"

        with self.assertRaisesRegex(ValueError, "does not match its list path"):
            _validate_diff([mismatched], None)

    def test_rejects_a_branch_owned_by_another_actor(self) -> None:
        result = _result()

        with self.assertRaisesRegex(ValueError, "triggering actor"):
            _validate_diff([result], None, branch=_branch(result, "someone-else"))

    def test_rejects_a_branch_without_the_publication_id(self) -> None:
        with self.assertRaisesRegex(ValueError, "publication ID"):
            _validate_diff([_result()], None, branch="ai/kimeta/benchmark-demo-other")

    def test_descriptor_schema_no_longer_accepts_benchmark_descriptors(self) -> None:
        """Old-runner benchmark descriptors are rejected instead of half-supported."""
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)
        descriptor = {"task_type": "code-coverage-benchmark-result"}

        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        with self.assertRaises(exceptions.ValidationError):
            validator.validate(descriptor)


if __name__ == "__main__":
    unittest.main()
