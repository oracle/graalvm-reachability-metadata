# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from utility_scripts.foreign_metadata_owner_issue import (
    ForeignMetadataOwnerFailure,
    ensure_foreign_metadata_owner_issue,
    failure_report_path,
    load_foreign_metadata_owner_failure,
)


class ForeignMetadataOwnerIssueTests(unittest.TestCase):
    def test_loads_only_two_field_ignored_report(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            report_path = failure_report_path(repo_path, "org.example:source:2.0.0")
            os.makedirs(os.path.dirname(report_path))
            with open(report_path, "w", encoding="utf-8") as report_file:
                json.dump(
                    {
                        "reason": "owner-version-unsupported",
                        "coordinate": "org.owner:dependency:1.0.0",
                    },
                    report_file,
                )

            failure = load_foreign_metadata_owner_failure(repo_path, "org.example:source:2.0.0")

        self.assertEqual(
            failure,
            ForeignMetadataOwnerFailure("owner-version-unsupported", "org.owner:dependency:1.0.0"),
        )
        self.assertIn(os.path.join("build", "reports", "route-foreign-metadata"), report_path)

    @patch("utility_scripts.foreign_metadata_owner_issue.resolve_github_repo_slug", return_value="oracle/repo")
    @patch("utility_scripts.foreign_metadata_owner_issue.gh_json", return_value=[])
    @patch("utility_scripts.foreign_metadata_owner_issue.gh")
    def test_creates_new_library_issue_once(self, gh: Mock, _gh_json: Mock, _repo: Mock) -> None:
        gh.return_value = subprocess.CompletedProcess([], 0, stdout="https://github.com/oracle/repo/issues/42\n")
        failure = ForeignMetadataOwnerFailure(
            "owner-library-unsupported",
            "org.owner:dependency:1.0.0",
        )

        issue_url = ensure_foreign_metadata_owner_issue(
            "/repo",
            "org.example:source:2.0.0",
            failure,
        )

        self.assertEqual(issue_url, "https://github.com/oracle/repo/issues/42")
        arguments = gh.call_args.args
        self.assertIn("library-new-request", arguments)
        self.assertIn("Support for org.owner:dependency:1.0.0", arguments)

    @patch("utility_scripts.foreign_metadata_owner_issue.resolve_github_repo_slug", return_value="oracle/repo")
    @patch("utility_scripts.foreign_metadata_owner_issue.gh")
    @patch("utility_scripts.foreign_metadata_owner_issue.gh_json")
    def test_reuses_matching_open_issue(self, gh_json: Mock, gh: Mock, _repo: Mock) -> None:
        gh_json.return_value = [{
            "number": 7,
            "url": "https://github.com/oracle/repo/issues/7",
            "body": "<!-- forge-foreign-metadata-owner:owner-version-unsupported:org.owner:dependency:1.0.0 -->",
        }]
        failure = ForeignMetadataOwnerFailure(
            "owner-version-unsupported",
            "org.owner:dependency:1.0.0",
        )

        issue_url = ensure_foreign_metadata_owner_issue(
            "/repo",
            "org.example:source:2.0.0",
            failure,
        )

        self.assertEqual(issue_url, "https://github.com/oracle/repo/issues/7")
        gh.assert_not_called()


if __name__ == "__main__":
    unittest.main()
