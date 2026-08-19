# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import unittest
from unittest.mock import patch

from git_scripts import publish_not_for_native_image, branch_publication
from utility_scripts.local_ci_verification import LocalCIVerificationResult


class PublishNotForNativeImageTests(unittest.TestCase):
    def test_push_marker_branch_runs_local_ci_before_push(self) -> None:
        result = LocalCIVerificationResult(status="success", base_commit="FETCH_HEAD")
        events: list[str] = []

        def fake_subprocess_run(command: list[str], check: bool = False, cwd: str | None = None, **kwargs):
            del check, cwd, kwargs
            if command[:2] == ["git", "ls-files"]:
                return subprocess.CompletedProcess(command, 0, stdout="")
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

        def fake_run_local_ci_verification(**kwargs):
            events.append("local-ci")
            self.assertEqual(kwargs["metrics_repo_path"], "/metrics")
            self.assertEqual(kwargs["base_commit"], "FETCH_HEAD")
            return result

        with patch.object(
                    publish_not_for_native_image,
                    "get_not_for_native_image_marker",
                    return_value={"reason": "native-image does not apply"},
                ), \
                patch.object(branch_publication, "find_remote_for_github_repo", return_value="origin"), \
                patch.object(branch_publication, "get_authenticated_login", return_value="octocat"), \
                patch.object(
                    branch_publication,
                    "write_publication_descriptor",
                    return_value="/repo/stats/org.example/demo/1.0.0/forge-publication.json",
                ), \
                patch.object(branch_publication, "stage_and_commit_common"), \
                patch.object(branch_publication, "delete_remote_branch_if_exists"), \
                patch.object(publish_not_for_native_image, "stage_and_commit"), \
                patch.object(branch_publication, "fetch_pr_base_ref", return_value="FETCH_HEAD"), \
                patch.object(
                    branch_publication,
                    "run_local_ci_verification",
                    side_effect=fake_run_local_ci_verification,
                ), \
                patch.object(branch_publication, "run_git_transport", side_effect=fake_run_git_transport), \
                patch.object(branch_publication.subprocess, "run", side_effect=fake_subprocess_run):
            branch, local_ci_verification = publish_not_for_native_image.push_marker_branch(
                "org.example:demo:1.0.0",
                "/repo",
                "/metrics",
                issue_number=1234,
            )

        self.assertTrue(
            branch.startswith("ai/octocat/not-for-native-image-org.example-demo-"),
            branch,
        )
        self.assertIs(local_ci_verification, result)
        self.assertEqual(events, ["rebase", "local-ci", "push"])

