# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover trusted local-review attestation publication. §FS-automated-pr-review"""

import importlib.util
import os
import sys
import tempfile
import unittest
from typing import Any
from unittest.mock import patch

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)
HEAD_SHA = "a" * 40
BRANCH = "ai/kimeta/example-forge-9656-20260831120000-0123456789ab"


def _load_publisher() -> Any:
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_attestation", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _safe_descriptor() -> dict[str, Any]:
    return {
        "local_review": {
            "status": "completed",
            "decision": "approved",
            "repair_reverted": False,
        },
        "local_ci_verification": {"status": "success"},
    }


def _validated(descriptor: dict[str, Any] | None = None) -> Any:
    return publisher.ValidatedPublication(
        _safe_descriptor() if descriptor is None else descriptor,
        "stats/org.example/demo/1.0.0/forge-publication.json",
        HEAD_SHA,
    )


def _main_argv() -> list[str]:
    return [
        "publisher.py",
        "validate",
        "--sha",
        HEAD_SHA,
        "--branch",
        BRANCH,
        "--actor",
        "kimeta",
        "--repository",
        publisher.REPOSITORY,
    ]


class LocalReviewAttestationPublisherTests(unittest.TestCase):
    def test_safe_validated_descriptor_is_eligible(self) -> None:
        self.assertTrue(publisher.local_review_attestation_eligible(_validated()))

    def test_unsafe_local_review_or_ci_is_not_eligible(self) -> None:
        cases = {
            "unavailable": ("local_review", "status", "unavailable"),
            "changes-requested": ("local_review", "decision", "changes_requested"),
            "repair-reverted": ("local_review", "repair_reverted", True),
            "failed-local-ci": ("local_ci_verification", "status", "failure"),
        }
        for name, (section, key, value) in cases.items():
            with self.subTest(name=name):
                descriptor = _safe_descriptor()
                descriptor[section][key] = value
                self.assertFalse(
                    publisher.local_review_attestation_eligible(_validated(descriptor))
                )

        self.assertFalse(publisher.local_review_attestation_eligible(_validated({})))

    def test_strictly_validated_publication_outputs_attestation(self) -> None:
        validated = _validated()
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = os.path.join(temp_dir, "github-output")
            with (
                    patch.object(sys, "argv", _main_argv()),
                    patch.dict(os.environ, {"GITHUB_OUTPUT": output_path}),
                    patch.object(publisher, "find_existing_publication", return_value=None),
                    patch.object(publisher, "validate_publication", return_value=validated),
                    patch.object(publisher, "render_publication", return_value=("title", "body")),
                    patch.object(publisher, "write_evidence"),
            ):
                result = publisher.main()

            self.assertEqual(0, result)
            with open(output_path, "r", encoding="utf-8") as output_file:
                self.assertEqual("local_review_attestation=true\n", output_file.read())

    def test_descriptor_not_changed_in_head_outputs_no_attestation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = os.path.join(temp_dir, "github-output")
            with (
                    patch.object(sys, "argv", _main_argv()),
                    patch.dict(os.environ, {"GITHUB_OUTPUT": output_path}),
                    patch.object(publisher, "find_existing_publication", return_value=None),
                    patch.object(
                        publisher,
                        "validate_publication",
                        side_effect=ValueError(
                            "Exactly one tip-committed forge-publication.json is required"
                        ),
                    ),
            ):
                result = publisher.main()

            self.assertEqual(1, result)
            with open(output_path, "r", encoding="utf-8") as output_file:
                self.assertEqual("local_review_attestation=false\n", output_file.read())

    def test_existing_publication_noop_outputs_no_attestation(self) -> None:
        pull_request = {
            "number": 9656,
            "html_url": f"https://github.com/{publisher.REPOSITORY}/pull/9656",
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = os.path.join(temp_dir, "github-output")
            with (
                    patch.object(sys, "argv", _main_argv()),
                    patch.dict(os.environ, {"GITHUB_OUTPUT": output_path}),
                    patch.object(
                        publisher,
                        "find_existing_publication",
                        return_value=pull_request,
                    ),
                    patch.object(publisher, "write_existing_publication_evidence"),
                    patch.object(publisher, "validate_publication") as validate_publication,
            ):
                result = publisher.main()

            self.assertEqual(0, result)
            validate_publication.assert_not_called()
            with open(output_path, "r", encoding="utf-8") as output_file:
                self.assertEqual("local_review_attestation=false\n", output_file.read())


if __name__ == "__main__":
    unittest.main()
