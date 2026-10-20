# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover trusted real-use publication from the stats record. §AR-real-use-tests-publication"""

import importlib.util
import os
import sys
import unittest
from typing import Any
from unittest.mock import patch

from jsonschema import exceptions

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)

STATS_PATH = "real-use-tests/stats/com.example/demo/1.0.0/stats.json"
PROJECT_PATH = "real-use-tests/src/com.example/demo/1.0.0/src/test/java/com_example/demo/ClientTest.java"


def _load_publisher() -> Any:
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_real_use", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _record(**overrides: Any) -> dict[str, Any]:
    record: dict[str, Any] = {
        "issue_number": 9447,
        "timestamp": "2026-10-16T03:07:43Z",
        "library_coverage": {
            "line": {"covered": 5340, "total": 11427, "ratio": 0.4673},
            "instruction": {"covered": 19924, "total": 45344, "ratio": 0.4394},
            "method": {"covered": 1587, "total": 3449, "ratio": 0.4601},
        },
        "tokens": {"input": 328, "cached_input": 9726270, "cache_write_input": 770494, "output": 160000},
        "iterations": 10,
        "removed": [],
    }
    record.update(overrides)
    return record


def _branch(record: dict[str, Any], actor: str = "kimeta") -> str:
    publication_id = publisher._build_publication_id({
        "task_type": "real-use-tests",
        "issue_number": record["issue_number"],
        "timestamp": record["timestamp"],
        "library": {"coordinates": "com.example:demo:1.0.0"},
    })
    return f"ai/{actor}/real-use-demo-1.0.0-{publication_id}"


def _validate(
        record: dict[str, Any],
        *,
        changed_paths: list[str] | None = None,
        branch: str | None = None,
        actor: str = "kimeta",
) -> Any:
    with patch.object(publisher, "_json_value_at_commit", return_value=record):
        return publisher._validate_real_use_diff_publication(
            head_sha="head",
            changed_paths=changed_paths if changed_paths is not None else [PROJECT_PATH, STATS_PATH],
            branch=branch if branch is not None else _branch(record, actor),
            actor=actor,
        )


class RealUsePublisherTests(unittest.TestCase):

    def test_stats_record_renders_the_pull_request(self) -> None:
        validated = _validate(_record())

        descriptor = validated.descriptor
        self.assertEqual(STATS_PATH, validated.descriptor_path)
        self.assertTrue(descriptor["publication_id"].startswith("forge-9447-"))
        title, body = publisher.render_publication(descriptor)
        self.assertEqual("[GenAI] Add real-use tests for com.example:demo:1.0.0", title)
        self.assertIn("Fixes: #9447", body)
        self.assertIn("- Library coverage: 43.94%", body)
        self.assertIn("- Iterations: 10", body)
        self.assertIn(f"Forge-Publication-ID: {descriptor['publication_id']}", body)
        self.assertEqual(["GenAI", "real-use-test", "rhei"], publisher.ROUTE_LABELS[descriptor["task_type"]])

    def test_removed_scenarios_are_listed(self) -> None:
        record = _record(removed=[{"scenario": "ProxyTest.java", "reason": "defines classes at run time"}])

        _title, body = publisher.render_publication(_validate(record).descriptor)

        self.assertIn("| `ProxyTest.java` | defines classes at run time |", body)

    def test_a_path_outside_the_coordinate_project_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside real-use-tests/src/com.example/demo/1.0.0/"):
            _validate(_record(), changed_paths=[STATS_PATH, "real-use-tests/src/com.example/other/1.0.0/x"])

    def test_exactly_one_stats_record_must_change(self) -> None:
        with self.assertRaisesRegex(ValueError, "exactly one stats record"):
            _validate(_record(), changed_paths=[PROJECT_PATH])
        with self.assertRaisesRegex(ValueError, "exactly one stats record"):
            _validate(_record(), changed_paths=[
                STATS_PATH, "real-use-tests/stats/com.example/other/1.0.0/stats.json",
            ])

    def test_the_branch_must_belong_to_the_actor_and_carry_the_rebuilt_id(self) -> None:
        with self.assertRaisesRegex(ValueError, "triggering actor"):
            _validate(_record(), branch=_branch(_record(), actor="someone"))
        with self.assertRaisesRegex(ValueError, "real-use publication ID"):
            _validate(_record(), branch=_branch(_record(issue_number=1)))

    def test_a_record_outside_the_schema_is_rejected(self) -> None:
        with self.assertRaises(exceptions.ValidationError):
            _validate(_record(iterations=-1))
        with self.assertRaises(exceptions.ValidationError):
            _validate(_record(extra="field"))

    def test_the_descriptor_schema_no_longer_accepts_a_real_use_route(self) -> None:
        schema = publisher.load_schema()

        self.assertNotIn("real-use-tests", schema["properties"]["task_type"]["enum"])
        self.assertNotIn("real_use", schema["$defs"])


if __name__ == "__main__":
    unittest.main()
