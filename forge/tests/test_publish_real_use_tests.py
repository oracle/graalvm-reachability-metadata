# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the real-use publication route end to end (§AR-real-use-tests-publication)."""

import importlib.util
import json
import os
import subprocess
import sys
import unittest
from typing import Any
from unittest.mock import patch

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

from git_scripts import branch_publication, publish_real_use_tests
from git_scripts.publication_descriptor import build_publication_id
from utility_scripts.local_ci_verification import LocalCIVerificationResult

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py")
SCHEMA_PATH = os.path.join(REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "schema.json")
COORDINATES = "com.example:demo:1.0.0"
TIMESTAMP = "2026-10-15T10:15:30+00:00"
ISSUE = 10900


def _load_publisher():
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location("forge_pr_publisher_real_use", PUBLISHER_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _real_use() -> dict[str, Any]:
    return {
        "scenarios": [{"class": "realuse.demo.OrderRoundTripTest", "subsystem": "Serializes orders"}],
        "removed": [{"scenario": "src/test/java/realuse/demo/ProxyTest.java", "reason": "defines classes at run time"}],
        "test_local_metadata": [
            {"library": "Target library", "entries": ["reflection demo.Order when `demo.Codec` is reached"]},
        ],
    }


def _descriptor() -> dict[str, Any]:
    publication_id = build_publication_id(ISSUE, TIMESTAMP, COORDINATES, "real-use-tests")
    return {
        "schema_version": 1,
        "publication_id": publication_id,
        "timestamp": TIMESTAMP,
        "branch": f"ai/octocat/real-use-demo-1.0.0-{publication_id}",
        "producer": "octocat",
        "base_commit": "0" * 40,
        "issue_number": ISSUE,
        "library": {"group": "com.example", "artifact": "demo", "version": "1.0.0", "coordinates": COORDINATES},
        "task_type": "real-use-tests",
        "template_type": "real-use-tests",
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
        "forge": {"monitored_branch": "master", "branch": "master", "commit": "3" * 40},
        "modifiers": {"chunked_dynamic_access": False, "chunk_final": True, "human_intervention": False},
        "follow_ups": [],
        "render": {"real_use": _real_use()},
    }


def _validator() -> Draft202012Validator:
    with open(SCHEMA_PATH, encoding="utf-8") as schema_file:
        return Draft202012Validator(json.load(schema_file), format_checker=FormatChecker())


class RealUseDescriptorTests(unittest.TestCase):

    def test_descriptor_is_schema_valid(self) -> None:
        _validator().validate(_descriptor())

    def test_real_use_descriptor_rejects_no_issue_a_review_or_missing_facts(self) -> None:
        for mutate in (
                lambda d: d.pop("issue_number"),
                lambda d: d.update(local_review={"decision": "approved"}),
                lambda d: d["render"].pop("real_use"),
                lambda d: d.update(template_type="library-new-request"),
        ):
            descriptor = _descriptor()
            mutate(descriptor)
            with self.assertRaises(ValidationError):
                _validator().validate(descriptor)

    def test_local_and_trusted_identity_agree_and_labels_match_the_issue(self) -> None:
        descriptor = _descriptor()
        self.assertTrue(descriptor["publication_id"].startswith("forge-10900-20261015101530"))
        self.assertEqual(descriptor["publication_id"], publisher._build_publication_id(descriptor))
        self.assertIsNotNone(publisher.PUBLICATION_ID_SUFFIX.search(descriptor["branch"]))
        self.assertEqual(["GenAI", "real-use-test", "rhei"], publisher.ROUTE_LABELS["real-use-tests"])

    def test_template_resolves_the_issue_and_reports_scenarios_removals_and_entries(self) -> None:
        title, body = publisher.render_publication(_descriptor())

        self.assertEqual("[GenAI] Add real-use tests for com.example:demo:1.0.0", title)
        self.assertIn("Fixes: #10900", body)
        self.assertIn("| `realuse.demo.OrderRoundTripTest` | Serializes orders |", body)
        self.assertIn("| `src/test/java/realuse/demo/ProxyTest.java` | defines classes at run time |", body)
        self.assertIn("#### Target library", body)
        self.assertIn(f"Forge-Publication-ID: {_descriptor()['publication_id']}", body)


class PublishRealUseTestsTests(unittest.TestCase):

    def test_push_skips_the_review_and_carries_the_issue(self) -> None:
        result = LocalCIVerificationResult(status="success", base_commit="FETCH_HEAD")
        events: list[str] = []
        written: dict[str, Any] = {}

        def fake_subprocess_run(command: list[str], check: bool = False, cwd: str | None = None, **kwargs):
            del check, cwd, kwargs
            if command[:2] == ["git", "rebase"]:
                events.append("rebase")
            if command[:2] == ["git", "rev-parse"]:
                return subprocess.CompletedProcess(command, 0, stdout="deadbeef\n")
            return subprocess.CompletedProcess(command, 0, stdout="")

        def fake_run_git_transport(args: list[str], cwd: str | None = None):
            del cwd
            if args[:1] == ["push"]:
                events.append("push")
            return subprocess.CompletedProcess(["git", *args], 0)

        def fake_write_publication_descriptor(**kwargs):
            written.update(kwargs)
            return "/repo/stats/com.example/demo/1.0.0/forge-publication.json"

        with patch.object(branch_publication, "find_remote_for_github_repo", return_value="origin"), \
                patch.object(branch_publication, "get_authenticated_login", return_value="octocat"), \
                patch.object(branch_publication, "write_publication_descriptor",
                             side_effect=fake_write_publication_descriptor), \
                patch.object(branch_publication, "stage_and_commit_common"), \
                patch.object(branch_publication, "delete_remote_branch_if_exists"), \
                patch.object(publish_real_use_tests, "stage_and_commit") as stage, \
                patch.object(branch_publication, "fetch_pr_base_ref", return_value="FETCH_HEAD"), \
                patch.object(branch_publication, "run_local_ci_verification",
                             side_effect=lambda **kwargs: events.append("local-ci") or result), \
                patch.object(branch_publication, "run_local_branch_review",
                             side_effect=AssertionError("the real-use route has no pre-push review")), \
                patch.object(branch_publication, "run_git_transport", side_effect=fake_run_git_transport), \
                patch.object(branch_publication.subprocess, "run", side_effect=fake_subprocess_run):
            branch = publish_real_use_tests.push_real_use_branch("/repo", COORDINATES, ISSUE, TIMESTAMP, _real_use())

        self.assertTrue(branch.startswith("ai/octocat/real-use-demo-1.0.0-forge-10900-"), branch)
        self.assertEqual(["rebase", "local-ci", "push"], events)
        stage.assert_called_once_with(
            [os.path.join("real-use-tests", "src", "com.example", "demo", "1.0.0")],
            "Add real-use tests for demo 1.0.0",
            cwd="/repo",
        )
        self.assertEqual(ISSUE, written["descriptor_input"].issue_number)
        self.assertIsNone(written["local_review"])
        self.assertEqual({"real_use": _real_use()}, written["descriptor_input"].render)


if __name__ == "__main__":
    unittest.main()
