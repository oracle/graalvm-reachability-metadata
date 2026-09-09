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

from jsonschema import Draft202012Validator, FormatChecker

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)
SCHEMA_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "schema.json",
)


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


def _result() -> dict[str, Any]:
    return {
        "schemaVersion": "1.0.0",
        "runId": "run-1",
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


def _descriptor() -> dict[str, Any]:
    result = _result()
    descriptor: dict[str, Any] = {
        "schema_version": 1,
        "publication_id": "pending",
        "timestamp": result["timestamp"],
        "branch": "pending",
        "producer": "kimeta",
        "base_commit": "0" * 40,
        "issue_number": None,
        "benchmark_run_id": result["runId"],
        "library": {
            "group": "com.example",
            "artifact": "demo",
            "version": "1.0.0",
            "coordinates": result["coordinate"],
        },
        "task_type": "code-coverage-benchmark-result",
        "template_type": "code-coverage-benchmark-result",
        "metrics": None,
        "local_ci_verification": {
            "status": "success",
            "base_commit": "0" * 40,
            "final_commit": "1" * 40,
            "commands": [],
            "fixups": [],
            "repo_fix_paths": [],
            "human_intervention_required": False,
        },
        "forge": {"monitored_branch": "master", "branch": "master", "commit": "2" * 40},
        "modifiers": {
            "chunked_dynamic_access": False,
            "chunk_final": True,
            "human_intervention": False,
        },
        "follow_ups": [],
        "render": {"benchmark_result": result},
    }
    publication_id = publisher._build_publication_id(descriptor)
    descriptor["publication_id"] = publication_id
    descriptor["branch"] = f"ai/kimeta/benchmark-demo-{publication_id}"
    return descriptor


class BenchmarkResultPublisherTests(unittest.TestCase):

    def test_descriptor_is_schema_valid_and_has_no_issue(self) -> None:
        descriptor = _descriptor()
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)

        Draft202012Validator(schema, format_checker=FormatChecker()).validate(descriptor)
        self.assertIsNone(descriptor["issue_number"])
        self.assertTrue(descriptor["publication_id"].startswith("forge-benchmark-"))

    def test_renders_result_without_an_issue_reference(self) -> None:
        title, body = publisher.render_publication(_descriptor())

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

    def test_accepts_exactly_one_result_appended_to_the_base(self) -> None:
        descriptor = _descriptor()
        result_path = "code-coverage-benchmarks/com.example/demo/1.0.0.json"
        descriptor_path = "stats/com.example/demo/1.0.0/forge-publication.json"

        with patch.object(
                publisher,
                "_json_value_at_commit",
                side_effect=lambda commit, _path: [descriptor["render"]["benchmark_result"]]
                if commit == "head" else [],
        ), patch.object(publisher, "_git_object_exists", return_value=True):
            publisher._validate_benchmark_result_publication(
                descriptor=descriptor,
                descriptor_path=descriptor_path,
                head_sha="head",
                base_commit="base",
                changed_paths=sorted((descriptor_path, result_path)),
            )

    def test_rejects_an_unrelated_changed_path(self) -> None:
        descriptor = _descriptor()

        with self.assertRaisesRegex(ValueError, "exactly"):
            publisher._validate_benchmark_result_publication(
                descriptor=descriptor,
                descriptor_path="stats/com.example/demo/1.0.0/forge-publication.json",
                head_sha="head",
                base_commit="base",
                changed_paths=["README.md"],
            )


if __name__ == "__main__":
    unittest.main()
