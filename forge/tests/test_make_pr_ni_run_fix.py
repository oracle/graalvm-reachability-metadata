# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import unittest
from unittest.mock import patch

from git_scripts import make_pr_ni_run_fix


class SevereMetadataDropGuardrailTests(unittest.TestCase):
    def test_is_severe_metadata_drop_requires_existing_baseline_and_below_threshold(self) -> None:
        self.assertTrue(make_pr_ni_run_fix.is_severe_metadata_drop(34, 0))
        self.assertTrue(make_pr_ni_run_fix.is_severe_metadata_drop(100, 24))
        self.assertFalse(make_pr_ni_run_fix.is_severe_metadata_drop(100, 25))
        self.assertFalse(make_pr_ni_run_fix.is_severe_metadata_drop(100, 26))
        self.assertFalse(make_pr_ni_run_fix.is_severe_metadata_drop(0, 0))

    def test_create_pull_request_routes_severe_metadata_drop_to_human_intervention(self) -> None:
        gh_calls: list[tuple[str, ...]] = []

        def fake_gh(*args: str, check: bool = True):
            del check
            gh_calls.append(args)
            return subprocess.CompletedProcess(["gh", *args], 1 if args[:2] == ("pr", "view") else 0)

        with patch.object(make_pr_ni_run_fix, "get_origin_owner", return_value="me"), \
                patch.object(make_pr_ni_run_fix, "gh", side_effect=fake_gh), \
                patch.object(make_pr_ni_run_fix, "find_issue_common", return_value=5181), \
                patch.object(make_pr_ni_run_fix, "count_metadata_entries", side_effect=[0, 34]), \
                patch.object(make_pr_ni_run_fix, "count_test_only_metadata_entries", return_value=0), \
                patch.object(make_pr_ni_run_fix, "collect_version_coverage_metrics", return_value=(0.0, None)), \
                patch.object(make_pr_ni_run_fix, "format_stats_diff", return_value=""), \
                patch.object(make_pr_ni_run_fix, "format_forge_revision_section", return_value="Forge revision\n"):
            make_pr_ni_run_fix.create_pull_request(
                branch="ai/test/fix-native-image-run-org.example-demo-2.0.0",
                old_coordinates="org.example:demo:1.0.0",
                new_coordinates="org.example:demo:2.0.0",
                group="org.example",
                artifact="demo",
                repo_path="/repo",
            )

        create_call = gh_calls[1]
        body = create_call[create_call.index("--body") + 1]
        labels = [
            create_call[index + 1]
            for index, value in enumerate(create_call[:-1])
            if value == "--label"
        ]

        self.assertIn("Severe Metadata Drop", body)
        self.assertIn("Previous metadata entries (`org.example:demo:1.0.0`): 34", body)
        self.assertIn("New metadata entries (`org.example:demo:2.0.0`): 0", body)
        self.assertIn("Retained metadata entries: 0.00%", body)
        self.assertIn("fixes-native-image-run-fail", labels)
        self.assertIn("human-intervention", labels)


if __name__ == "__main__":
    unittest.main()
