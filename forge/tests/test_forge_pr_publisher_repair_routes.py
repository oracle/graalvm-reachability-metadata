# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the labels, title, and follow-up trailer of the four repair routes' pull requests.

Every route carries `GenAI` (§AR-issue-linking), every repair takes the one
`[GenAI] Test fix for` title (§AR-pr-body.3), and a repair that deferred its
exploration unblocks the parked follow-up issue on merge (§AR-pr-body.2,
§AR-pr-body.3).
"""

import importlib.util
import json
import os
import sys
import unittest
from typing import Any
from unittest.mock import patch

from dispatcher.pr_merge import pull_request_needs_merge_follow_up
from utility_scripts.library_update_follow_up_issue import extract_follow_up_issue_numbers

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)
REPAIR_ROUTES: tuple[str, ...] = (
    "fixes-javac-fail",
    "fixes-java-run-fail",
    "fixes-native-image-build-fail",
    "fixes-native-image-run-fail",
)
COORDINATES: str = "org.example:lib:2.0.0"
FOLLOW_UP_ISSUE: int = 99


def _load_publisher() -> Any:
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_repair_routes", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _deferred_coverage() -> dict[str, Any]:
    """The follow-up fact `publish` records when a repair defers its exploration."""
    return {
        "type": "deferred_dynamic_access_coverage",
        "coordinate": COORDINATES,
        "uncovered_class_count": 20,
        "class_threshold": 15,
        "issue_number": FOLLOW_UP_ISSUE,
    }


def _descriptor(task_type: str, follow_ups: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "task_type": task_type,
        "template_type": task_type,
        "publication_id": "forge-7-20261030120000-0123456789ab",
        "issue_number": 7,
        "library": {"coordinates": COORDINATES},
        "previous_library": {"coordinates": "org.example:lib:1.5.0"},
        "modifiers": {"chunked_dynamic_access": False, "chunk_final": True},
        "forge": {"monitored_branch": "master", "branch": "forge", "commit": "c" * 40},
        "metrics": {"agent": "pi", "model": "gpt-5.6-luna", "summary": {"input_tokens_used": 10}},
        "render": {"baseline_metadata_entries": 4, "current_metadata_entries": 5},
        "follow_ups": follow_ups,
        "local_ci_verification": {"status": "passed", "commands": [], "fixups": []},
    }


class RepairRouteLabelTests(unittest.TestCase):

    def test_every_route_labels_its_pull_request_genai(self) -> None:
        for task_type, labels in publisher.ROUTE_LABELS.items():
            with self.subTest(task_type=task_type):
                self.assertIn("GenAI", labels)

    def test_a_repair_pull_request_carries_genai_and_its_route_label(self) -> None:
        for task_type in REPAIR_ROUTES:
            with (
                    self.subTest(task_type=task_type),
                    patch.object(publisher, "run") as run,
                    patch.object(publisher, "_reconcile_rejected_close"),
            ):
                publisher._ensure_pull_request_metadata(
                    {"number": 41}, _descriptor(task_type, []), "",
                )
                payload: str = run.call_args_list[0].kwargs["input_text"]
                labels: list[str] = json.loads(payload)["labels"]
                self.assertEqual(["GenAI", task_type], labels)

    def test_every_repair_route_titles_its_pull_request_as_a_genai_test_fix(self) -> None:
        # The title the hand-review rule of AGENTS.md skips as Forge's own output.
        for task_type in REPAIR_ROUTES:
            with self.subTest(task_type=task_type):
                title, _ = publisher.render_publication(_descriptor(task_type, []))
                self.assertEqual(f"[GenAI] Test fix for {COORDINATES} using gpt-5.6-luna", title)


class RepairRouteFollowUpTests(unittest.TestCase):

    def test_a_deferred_repair_unblocks_its_follow_up_issue_on_merge(self) -> None:
        for task_type in REPAIR_ROUTES:
            with self.subTest(task_type=task_type):
                descriptor = _descriptor(task_type, [_deferred_coverage()])
                _, body = publisher.render_publication(descriptor)
                self.assertIn("### Deferred Dynamic-Access Exploration", body)
                self.assertIn(f"Refs: #{FOLLOW_UP_ISSUE}\n", body)
                # The trailer is what merge follow-up marks and releases the parked issue by.
                self.assertTrue(pull_request_needs_merge_follow_up({"body": body}))
                self.assertEqual([FOLLOW_UP_ISSUE], extract_follow_up_issue_numbers(body))

    def test_a_repair_that_explored_publishes_no_trailer(self) -> None:
        for task_type in REPAIR_ROUTES:
            with self.subTest(task_type=task_type):
                _, body = publisher.render_publication(_descriptor(task_type, []))
                self.assertNotIn("Deferred Dynamic-Access Exploration", body)
                self.assertFalse(pull_request_needs_merge_follow_up({"body": body}))

    def test_a_deferred_run_fix_keeps_the_entry_counts_of_its_severe_drop_note(self) -> None:
        descriptor = _descriptor("fixes-native-image-run-fail", [_deferred_coverage()])
        _, body = publisher.render_publication(descriptor)
        self.assertIn(f"- Metadata entries (new `{COORDINATES}`): 5\n", body)
        deferred: int = body.index("### Deferred Dynamic-Access Exploration")
        self.assertLess(deferred, body.index("### Forge\n"))


if __name__ == "__main__":
    unittest.main()
