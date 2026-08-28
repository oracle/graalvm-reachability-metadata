# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover idempotent trusted publication after a Forge PR exists.

A force-pushed rebase can make GitHub's path filter observe descriptors from
the new base. The resulting run must not revalidate or republish a branch whose
matching Forge PR already exists (§FS-forge-publication-readiness).
"""

import importlib.util
import os
import sys
import unittest
from typing import Any
from unittest.mock import patch

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)
PUBLICATION_ID = "forge-9307-20260827212457173272-9358ee807703"
BRANCH = f"ai/kimeta/fix-javac-demo-{PUBLICATION_ID}"
HEAD_SHA = "a" * 40


def _load_publisher() -> Any:
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location(
        "forge_pr_publisher_existing_pr", PUBLISHER_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _pull_request(**overrides: Any) -> dict[str, Any]:
    pull_request: dict[str, Any] = {
        "number": 9563,
        "html_url": "https://github.com/oracle/graalvm-reachability-metadata/pull/9563",
        "body": f"Generated publication\n\nForge-Publication-ID: {PUBLICATION_ID}\n",
        "state": "open",
        "merged_at": None,
        "user": {"login": "graalvmbot"},
    }
    pull_request.update(overrides)
    return pull_request


class ExistingForgePublicationTests(unittest.TestCase):

    def test_legacy_short_timestamp_publication_identity_is_supported(self) -> None:
        publication_id: str = "forge-8380-20260820101530-0123456789ab"
        branch: str = f"ai/kimeta/code-coverage-demo-{publication_id}"
        pull_request = _pull_request(
            body=f"Forge-Publication-ID: {publication_id}\n",
        )

        with patch.object(publisher, "gh_json", return_value=[pull_request]):
            resolved: dict[str, Any] | None = publisher.find_existing_publication(branch)

        self.assertIs(resolved, pull_request)

    def test_matching_open_publication_is_resolved(self) -> None:
        pull_request = _pull_request()

        with patch.object(publisher, "gh_json", return_value=[pull_request]):
            resolved: dict[str, Any] | None = publisher.find_existing_publication(BRANCH)

        self.assertIs(resolved, pull_request)

    def test_matching_merged_publication_is_resolved(self) -> None:
        pull_request = _pull_request(state="closed", merged_at="2026-08-28T15:00:00Z")

        with patch.object(publisher, "gh_json", return_value=[pull_request]):
            resolved: dict[str, Any] | None = publisher.find_existing_publication(BRANCH)

        self.assertIs(resolved, pull_request)

    def test_matching_closed_unmerged_publication_fails(self) -> None:
        pull_request = _pull_request(state="closed")

        with patch.object(publisher, "gh_json", return_value=[pull_request]):
            with self.assertRaisesRegex(RuntimeError, "closed without merge"):
                publisher.find_existing_publication(BRANCH)

    def test_ambiguous_matching_publications_fail(self) -> None:
        with patch.object(
                publisher,
                "gh_json",
                return_value=[_pull_request(), _pull_request(number=9564)],
        ):
            with self.assertRaisesRegex(RuntimeError, "Ambiguous"):
                publisher.find_existing_publication(BRANCH)

    def test_non_forge_pull_request_does_not_bypass_validation(self) -> None:
        pull_request = _pull_request(user={"login": "someone-else"})

        with patch.object(publisher, "gh_json", return_value=[pull_request]):
            with self.assertRaisesRegex(RuntimeError, "different publication identity"):
                publisher.find_existing_publication(BRANCH)

    def test_branch_without_publication_identity_does_not_query_github(self) -> None:
        with patch.object(publisher, "gh_json") as gh_json_mock:
            resolved: dict[str, Any] | None = publisher.find_existing_publication(
                "ai/kimeta/ordinary-branch",
            )

        self.assertIsNone(resolved)
        gh_json_mock.assert_not_called()

    def test_existing_pr_noops_before_validation_after_force_rebase(self) -> None:
        pull_request = _pull_request()
        for command in ("validate", "publish"):
            with self.subTest(command=command):
                argv: list[str] = [
                    "publisher.py",
                    command,
                    "--sha",
                    HEAD_SHA,
                    "--branch",
                    BRANCH,
                    "--actor",
                    "kimeta",
                    "--repository",
                    publisher.REPOSITORY,
                ]
                with (
                        patch.object(sys, "argv", argv),
                        patch.object(
                            publisher,
                            "find_existing_publication",
                            return_value=pull_request,
                        ),
                        patch.object(publisher, "validate_publication") as validate_mock,
                        patch.object(
                            publisher,
                            "write_existing_publication_evidence",
                        ) as evidence_mock,
                ):
                    result: int = publisher.main()

                self.assertEqual(result, 0)
                validate_mock.assert_not_called()
                evidence_mock.assert_called_once_with(BRANCH, HEAD_SHA, pull_request)

    def test_first_publication_still_runs_strict_validation(self) -> None:
        validated = publisher.ValidatedPublication({}, "descriptor.json", HEAD_SHA)
        argv: list[str] = [
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
        with (
                patch.object(sys, "argv", argv),
                patch.object(publisher, "find_existing_publication", return_value=None),
                patch.object(
                    publisher,
                    "validate_publication",
                    return_value=validated,
                ) as validate_mock,
                patch.object(publisher, "render_publication", return_value=("title", "body")),
                patch.object(publisher, "write_evidence") as evidence_mock,
        ):
            result: int = publisher.main()

        self.assertEqual(result, 0)
        validate_mock.assert_called_once_with(
            head_sha=HEAD_SHA,
            branch=BRANCH,
            actor="kimeta",
            repository=publisher.REPOSITORY,
        )
        evidence_mock.assert_called_once_with("title", "body", validated, None)


if __name__ == "__main__":
    unittest.main()
