# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the trusted coverage template that renders the descriptor (§AR-actions-publication)."""

import importlib.util
import json
import os
import sys
import unittest
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

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
            "runCoverage",
            "apiJacoco",
            "deepJacoco",
            "stopDecisions",
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
        "worker_thinking_level": "high",
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


def _local_review() -> dict[str, Any]:
    return {
        "status": "completed",
        "decision": "approved",
        "review_comment": "Checked the publication rules; no blocking issue remains.",
        "finding_title": "",
        "finding_body": "",
        "fix_note": "",
        "model": "gpt-5.6-terra",
        "session_log_path": "task-logs/review.log",
        "changed_paths": [],
        "repair_reverted": False,
        "failed_step": None,
        "published_tree": "verified",
    }


class CoveragePublisherTemplateTests(unittest.TestCase):

    def test_descriptor_is_schema_valid(self) -> None:
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)

        Draft202012Validator(schema, format_checker=FormatChecker()).validate(_descriptor())

    def test_unavailable_review_has_no_reviewer_words(self) -> None:
        review = _local_review()
        review["status"] = "unavailable"
        for field in (
                "decision", "review_comment", "finding_title", "finding_body", "fix_note",
        ):
            review.pop(field)
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)

        Draft202012Validator(
            schema["properties"]["local_review"], format_checker=FormatChecker(),
        ).validate(review)
        body = publisher._render_local_review({"local_review": review})

        self.assertIn("reviewer was unavailable", body)
        self.assertIn("no reviewer verdict or finding", body)
        self.assertNotIn("Checked the publication rules", body)

    def test_nonapproval_requires_a_complete_finding(self) -> None:
        review = _local_review()
        review["decision"] = "changes_requested"
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)

        with self.assertRaises(ValidationError):
            Draft202012Validator(
                schema["properties"]["local_review"], format_checker=FormatChecker(),
            ).validate(review)

    def test_code_coverage_descriptor_rejects_local_review(self) -> None:
        descriptor = _descriptor()
        descriptor["local_review"] = _local_review()
        with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
            schema = json.load(schema_file)

        with self.assertRaises(ValidationError):
            Draft202012Validator(schema, format_checker=FormatChecker()).validate(descriptor)

    def test_title_names_the_coordinate_and_the_model(self) -> None:
        title, _ = publisher.render_publication(_descriptor())

        self.assertEqual(
            title, "[GenAI] Improve code coverage for com.example:demo:1.0.0 using gpt-5.6-luna"
        )

    def test_body_reports_the_worker_thinking_level(self) -> None:
        _, body = publisher.render_publication(_descriptor())

        self.assertIn("- Thinking level: high", body)

    def test_render_validation_rejects_a_missing_thinking_level(self) -> None:
        descriptor = _descriptor()
        descriptor["render"].pop("worker_thinking_level")

        with self.assertRaisesRegex(ValueError, "thinking level"):
            publisher._validate_render_inputs(descriptor)

    def test_body_reports_every_checkpoint_on_one_denominator(self) -> None:
        """One universe of 30: 10 reportable API entries plus 20 deep methods."""
        _, body = publisher.render_publication(_descriptor())

        # The lead paragraph is wrapped, so it reads as text rather than as one
        # line of it.
        paragraph = body.split("## JaCoCo coverage\n\n", 1)[1].split("\n\n", 1)[0]
        self.assertGreater(len(paragraph.splitlines()), 1)
        self.assertLessEqual(
            max(len(line) for line in paragraph.splitlines()),
            publisher.BODY_TEXT_WIDTH,
        )
        self.assertIn(
            "the 30 library methods JaCoCo can rule on", " ".join(paragraph.split())
        )
        self.assertIn("| Run start | 9/30 | 30.0% |", body)
        self.assertIn("| After Simple Jacoco guidance phase | 16/30 | 53.33% |", body)
        self.assertIn("| After PGO guidance phase (final) | 21/30 | 70.0% |", body)
        self.assertNotIn("Sampled PGO", body)

    def test_body_reports_phase_gains_that_sum_to_the_run(self) -> None:
        """Each gain is the distance from the previous checkpoint, so they add up."""
        _, body = publisher.render_publication(_descriptor())

        self.assertIn("- Simple Jacoco guidance phase: +7 methods, +23.33pp", body)
        self.assertIn("- PGO guidance phase: +5 methods, +16.67pp", body)
        self.assertIn("- Run total: +12 methods, +40.0pp", body)
        self.assertIn("- Remaining uncovered: 9 of 30", body)
        self.assertNotIn("Both phases combined", body)

    def test_body_credits_the_deep_phase_with_the_api_methods_it_covered(self) -> None:
        """The old accounting stopped counting API coverage at the phase boundary."""
        _, body = publisher.render_publication(_descriptor())

        # 8 API methods at the boundary, 9 at the end: the deep phase covered one.
        self.assertIn("| Public API | 4/10 | 9/10 | 1 |", body)
        self.assertIn("| Internal | 5/20 | 12/20 | 8 |", body)

    def test_body_says_why_each_phase_stopped(self) -> None:
        """A phase short of its budget reads as broken unless the body explains it."""
        _, body = publisher.render_publication(_descriptor())

        self.assertIn(
            "- Simple Jacoco guidance phase: yield collapsed, after 9 of 15 passes",
            body,
        )
        self.assertIn(
            "- PGO guidance phase: pass budget spent, after 15 of 15 passes", body
        )

    def test_body_omits_the_stop_section_for_an_older_descriptor(self) -> None:
        _, body = publisher.render_publication(
            _descriptor(code_coverage=_coverage_evidence(stopDecisions=[]))
        )

        self.assertNotIn("Why each phase stopped", body)

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
        # issue it claimed (§AR-issue-linking).
        _, body = publisher.render_publication(_descriptor())

        self.assertIn("Fixes: #8380", body)
        self.assertNotIn("Refs: #8380", body)

    def test_body_reports_human_intervention_and_local_verification(self) -> None:
        evidence = _coverage_evidence(needsHumanIntervention=True)

        _, body = publisher.render_publication(_descriptor(code_coverage=evidence))

        self.assertIn("Needs human intervention: yes", body)
        self.assertIn("### Local CI Verification", body)
        self.assertIn("Forge-Publication-ID: forge-8380-20260820101530-0123456789ab", body)

    def test_body_omits_the_local_agent_review(self) -> None:
        _, body = publisher.render_publication(_descriptor())

        self.assertNotIn("## Local Agent Review", body)

    def test_review_nonapproval_or_reverted_repair_requires_intervention(self) -> None:
        descriptor = {"local_review": _local_review()}
        self.assertFalse(publisher._local_review_requires_human_intervention(descriptor))
        descriptor["local_review"]["decision"] = "changes_requested"
        self.assertTrue(publisher._local_review_requires_human_intervention(descriptor))
        descriptor["local_review"]["decision"] = "approved"
        descriptor["local_review"]["repair_reverted"] = True
        descriptor["local_review"]["failed_step"] = "checkMetadataFiles"
        self.assertTrue(publisher._local_review_requires_human_intervention(descriptor))

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

    def test_render_validation_rejects_missing_run_coverage(self) -> None:
        """Without the frozen universe the body has no denominator to render."""
        evidence = _coverage_evidence()
        evidence.pop("runCoverage")

        with self.assertRaises(ValueError):
            publisher._validate_render_inputs(_descriptor(code_coverage=evidence))

    def test_render_validation_rejects_a_truncated_checkpoint_list(self) -> None:
        evidence = _coverage_evidence()
        evidence["runCoverage"] = dict(evidence["runCoverage"])
        evidence["runCoverage"]["checkpoints"] = evidence["runCoverage"]["checkpoints"][:2]

        with self.assertRaises(ValueError):
            publisher._validate_render_inputs(_descriptor(code_coverage=evidence))

    def test_render_validation_rejects_a_half_built_phase(self) -> None:
        evidence = _coverage_evidence()
        evidence["deepJacoco"] = {"baseline": evidence["deepJacoco"]["baseline"]}

        with self.assertRaises(ValueError):
            publisher._validate_render_inputs(_descriptor(code_coverage=evidence))


if __name__ == "__main__":
    unittest.main()
