# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the trusted coverage template that renders the descriptor (§GIT-actions-publication)."""

import importlib.util
import json
import os
import sys
import unittest
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)
SCHEMA_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "schema.json",
)
FINAL_METRICS = os.path.join(
    os.path.dirname(__file__), "fixtures", "code_coverage", "final_metrics.json",
)


def _load_publisher():
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location("forge_pr_publisher", PUBLISHER_PATH)
    module = importlib.util.module_from_spec(spec)
    # Its dataclasses resolve their own module while it executes.
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _final_metrics() -> dict[str, Any]:
    with open(FINAL_METRICS, encoding="utf-8") as fixture:
        return json.load(fixture)


def _coverage_evidence(**overrides: Any) -> dict[str, Any]:
    metrics = _final_metrics()
    evidence = {
        key: metrics[key]
        for key in (
            "coordinate",
            "coverageSuitePath",
            "apiJacoco",
            "deepJacoco",
            "needsHumanIntervention",
        )
    }
    evidence.update(overrides)
    return evidence


def _descriptor(**render_overrides: Any) -> dict[str, Any]:
    render: dict[str, Any] = {
        "code_coverage": _coverage_evidence(),
        "token_usage": [],
        "worker_model": "gpt-5.6-luna",
    }
    render.update(render_overrides)
    return {
        "schema_version": 1,
        "publication_id": "forge-8380-20260820101530-0123456789ab",
        "timestamp": "2026-08-20T10:15:30Z",
        "branch": "ai/kimeta/code-coverage-demo-1.0.0-gpt-5.6-luna-forge-8380-20260820101530-0123456789ab",
        "producer": "kimeta",
        "base_commit": "0" * 40,
        "issue_number": 8380,
        "library": {
            "group": "com.example",
            "artifact": "demo",
            "version": "1.0.0",
            "coordinates": "com.example:demo:1.0.0",
        },
        "task_type": "code-coverage-improvement",
        "template_type": "code-coverage-improvement",
        "metrics": None,
        "local_ci_verification": {
            "status": "success",
            "base_commit": "1" * 40,
            "final_commit": "2" * 40,
            "commands": [],
            "fixups": [],
            "repo_fix_paths": [],
            "human_intervention_required": False,
        },
        "forge": {
            "monitored_branch": "master",
            "branch": "master",
            "commit": "3" * 40,
        },
        "modifiers": {
            "chunked_dynamic_access": False,
            "chunk_final": True,
            "human_intervention": False,
        },
        "follow_ups": [],
        "render": render,
    }


class CoveragePublisherTemplateTests(unittest.TestCase):

    def test_descriptor_is_schema_valid(self) -> None:
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)

        Draft202012Validator(schema, format_checker=FormatChecker()).validate(_descriptor())

    def test_title_names_the_coordinate_and_the_model(self) -> None:
        title, _ = publisher.render_publication(_descriptor())

        self.assertEqual(
            title, "[GenAI] Improve code coverage for com.example:demo:1.0.0 using gpt-5.6-luna"
        )

    def test_body_reports_phase_totals(self) -> None:
        _, body = publisher.render_publication(_descriptor())

        self.assertIn("### Simple Jacoco guidance phase", body)
        # `measured`, not `total`: the 6 not-reported entries are methods JaCoCo
        # can never rule on, so counting them against the run understates it.
        self.assertIn("Baseline: 4/10 (40.0%)", body)
        self.assertIn("Final: 8/10 (80.0%)", body)
        self.assertIn("Delta: +40.0pp", body)
        self.assertIn("Remaining uncovered: 2", body)
        self.assertIn("### PGO guidance phase", body)
        self.assertIn("Final: 12/20 (60.0%)", body)
        self.assertNotIn("Sampled PGO", body)

    def test_body_combines_both_phases(self) -> None:
        _, body = publisher.render_publication(_descriptor())

        # Disjoint universes, so 10 measured API + 20 deep methods add up.
        self.assertIn("### Both phases combined", body)
        self.assertIn("Baseline: 9/30 (30.0%)", body)
        self.assertIn("Final: 20/30 (66.67%)", body)
        self.assertIn("Delta: +36.67pp", body)

    def test_body_reports_token_usage_in_the_order_the_descriptor_carries(self) -> None:
        usage = [
            {"phase": "convert", "input": 1000, "cached": 2000, "output": 30},
            {"phase": "api-coverage", "input": 5000, "cached": 6000, "output": None},
        ]

        _, body = publisher.render_publication(_descriptor(token_usage=usage))

        self.assertIn("| convert | 1,000 | 2,000 | 30 |", body)
        self.assertIn("| api-coverage | 5,000 | 6,000 | n/a |", body)
        self.assertIn("| **Total** | **6,000** | **8,000** | **30** |", body)

    def test_body_omits_the_token_section_without_accounting(self) -> None:
        _, body = publisher.render_publication(_descriptor(token_usage=[]))

        self.assertNotIn("## Token usage", body)

    def test_body_closes_the_issue(self) -> None:
        # A coverage run is a single-PR workflow, so merging its PR closes the
        # issue it claimed (§GIT-issue-linking).
        _, body = publisher.render_publication(_descriptor())

        self.assertIn("Fixes: #8380", body)
        self.assertNotIn("Refs: #8380", body)

    def test_body_reports_human_intervention_and_local_verification(self) -> None:
        evidence = _coverage_evidence(needsHumanIntervention=True)

        _, body = publisher.render_publication(_descriptor(code_coverage=evidence))

        self.assertIn("Needs human intervention: yes", body)
        self.assertIn("### Local CI Verification", body)
        self.assertIn("Forge-Publication-ID: forge-8380-20260820101530-0123456789ab", body)

    def test_body_omits_the_forge_revision_block(self) -> None:
        """A Rhei-template workflow has no Forge strategy revision to report."""
        _, body = publisher.render_publication(_descriptor())

        self.assertNotIn("### Forge\n", body)
        self.assertNotIn("Forge monitored branch", body)

    def test_route_labels_mark_the_coverage_queue(self) -> None:
        self.assertEqual(
            publisher.ROUTE_LABELS["code-coverage-improvement"],
            ["GenAI", "code-coverage-improvement", "rhei"],
        )

    def test_render_validation_rejects_missing_coverage_evidence(self) -> None:
        descriptor = _descriptor()
        descriptor["render"].pop("code_coverage")

        with self.assertRaises(ValueError):
            publisher._validate_render_inputs(descriptor)

    def test_render_validation_rejects_a_half_built_phase(self) -> None:
        evidence = _coverage_evidence()
        evidence["deepJacoco"] = {"baseline": evidence["deepJacoco"]["baseline"]}

        with self.assertRaises(ValueError):
            publisher._validate_render_inputs(_descriptor(code_coverage=evidence))


if __name__ == "__main__":
    unittest.main()
