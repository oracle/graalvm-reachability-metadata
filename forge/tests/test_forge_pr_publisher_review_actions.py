# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover authoritative local-review publication actions. §FS-automated-pr-review"""

import importlib.util
import json
import os
import sys
import unittest
from typing import Any
from unittest.mock import call, patch

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)


def _load_publisher() -> Any:
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_review_actions", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _descriptor(action: str | None = None) -> dict[str, Any]:
    review: dict[str, Any] = {
        "decision": "approved" if action is None else "rejected",
        "review_comment": "The generated change was reviewed.",
        "finding_title": "" if action is None else "Unsupported library behavior",
        "finding_body": "" if action is None else "Native Image cannot support this version.",
        "fix_note": "",
        "model": "gpt-5.6-terra",
        "session_log_path": "task-logs/review.log",
        "changed_paths": [],
    }
    if action is not None:
        review["action"] = action
    return {
        "task_type": "library-new-request",
        "issue_number": 9962,
        "modifiers": {
            "chunked_dynamic_access": False,
            "chunk_final": True,
            "human_intervention": True,
        },
        "local_review": review,
    }


class LocalReviewPublisherActionTests(unittest.TestCase):
    def test_only_explicit_human_intervention_action_adds_label(self) -> None:
        with patch.object(publisher, "run") as run, patch.object(
                publisher, "_reconcile_rejected_close",
            ):
            publisher._ensure_pull_request_metadata({"number": 41}, _descriptor(), "")
            publisher._ensure_pull_request_metadata(
                {"number": 42}, _descriptor("human-intervention"), "",
            )
            publisher._ensure_pull_request_metadata({"number": 43}, _descriptor("close"), "")

        label_payloads = [
            json.loads(item.kwargs["input_text"])["labels"]
            for item in run.call_args_list
            if item.kwargs.get("input_text")
        ]
        self.assertNotIn("human-intervention", label_payloads[0])
        self.assertIn("human-intervention", label_payloads[1])
        self.assertNotIn("human-intervention", label_payloads[2])

    def test_close_action_comments_once_and_closes_pr_and_issue(self) -> None:
        descriptor = _descriptor("close")
        with (
                patch.object(publisher, "gh_json", return_value=[]) as gh_json,
                patch.object(publisher, "run") as run,
        ):
            publisher._reconcile_rejected_close({"number": 77}, descriptor)

        gh_json.assert_called_once()
        commands = [item.args[0] for item in run.call_args_list]
        self.assertTrue(any(command[-1].startswith("body=") for command in commands))
        self.assertIn(
            [
                "gh", "api", f"repos/{publisher.REPOSITORY}/pulls/77",
                "--method", "PATCH", "-f", "state=closed",
            ],
            commands,
        )
        self.assertIn(
            [
                "gh", "api", f"repos/{publisher.REPOSITORY}/issues/9962",
                "--method", "PATCH", "-f", "state=closed",
            ],
            commands,
        )
        label_call = next(
            item for item in run.call_args_list
            if item.kwargs.get("input_text")
        )
        self.assertEqual(
            {"labels": ["library-unsupported-version"]},
            json.loads(label_call.kwargs["input_text"]),
        )
        issue_close = [
            "gh", "api", f"repos/{publisher.REPOSITORY}/issues/9962",
            "--method", "PATCH", "-f", "state=closed",
        ]
        pull_request_close = [
            "gh", "api", f"repos/{publisher.REPOSITORY}/pulls/77",
            "--method", "PATCH", "-f", "state=closed",
        ]
        self.assertLess(
            commands.index(issue_close),
            commands.index(pull_request_close),
        )

    def test_existing_marked_comment_is_not_duplicated(self) -> None:
        comments = [{"body": "Already handled. <!-- forge-local-review-close -->"}]
        with (
                patch.object(publisher, "gh_json", return_value=comments),
                patch.object(publisher, "run") as run,
        ):
            publisher._reconcile_rejected_close({"number": 77}, _descriptor("close"))

        self.assertFalse(any(
            command[-1].startswith("body=")
            for command in (item.args[0] for item in run.call_args_list)
        ))


if __name__ == "__main__":
    unittest.main()
