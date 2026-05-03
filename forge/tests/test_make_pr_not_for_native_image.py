# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import subprocess
import unittest
from unittest.mock import patch

from git_scripts import make_pr_not_for_native_image
from utility_scripts.local_ci_verification import LocalCIVerificationResult


class MakePrNotForNativeImageTests(unittest.TestCase):
    def test_push_marker_branch_runs_local_ci_before_push(self) -> None:
        result = LocalCIVerificationResult(status="success", base_commit="FETCH_HEAD")
        events: list[str] = []

        def fake_subprocess_run(command: list[str], check: bool = False, cwd: str | None = None):
            del check, cwd
            if command[:2] == ["git", "rebase"]:
                events.append("rebase")
            if command[:2] == ["git", "push"]:
                events.append("push")
            return subprocess.CompletedProcess(command, 0)

        def fake_run_local_ci_verification(**kwargs):
            events.append("local-ci")
            self.assertEqual(kwargs["metrics_repo_path"], "/metrics")
            self.assertEqual(kwargs["base_commit"], "FETCH_HEAD")
            return result

        with patch.object(make_pr_not_for_native_image, "build_ai_branch_name", return_value="not-native-branch"), \
                patch.object(make_pr_not_for_native_image, "delete_remote_branch_if_exists"), \
                patch.object(make_pr_not_for_native_image, "stage_and_commit"), \
                patch.object(make_pr_not_for_native_image, "fetch_pr_base", return_value="FETCH_HEAD"), \
                patch.object(
                    make_pr_not_for_native_image,
                    "run_local_ci_verification",
                    side_effect=fake_run_local_ci_verification,
                ), \
                patch.object(make_pr_not_for_native_image.subprocess, "run", side_effect=fake_subprocess_run):
            branch, local_ci_verification = make_pr_not_for_native_image.push_marker_branch(
                "org.example:demo:1.0.0",
                "/repo",
                "/metrics",
            )

        self.assertEqual(branch, "not-native-branch")
        self.assertIs(local_ci_verification, result)
        self.assertEqual(events, ["rebase", "local-ci", "push"])

    def test_create_pull_request_includes_local_ci_section(self) -> None:
        local_ci_verification = LocalCIVerificationResult(
            status="success",
            base_commit="FETCH_HEAD",
            repo_fix_paths=["build.gradle"],
            human_intervention_required=True,
        )
        gh_calls: list[tuple[str, ...]] = []

        def fake_gh(*args: str, check: bool = True):
            del check
            gh_calls.append(args)
            return subprocess.CompletedProcess(["gh", *args], 1 if args[:2] == ("pr", "view") else 0)

        with patch.object(make_pr_not_for_native_image.shutil, "which", return_value="/usr/bin/gh"), \
                patch.object(
                    make_pr_not_for_native_image,
                    "get_not_for_native_image_marker",
                    return_value={"reason": "native-image does not apply"},
                ), \
                patch.object(make_pr_not_for_native_image, "get_origin_owner", return_value="me"), \
                patch.object(make_pr_not_for_native_image, "find_issue_for_coordinates", return_value=1234), \
                patch.object(make_pr_not_for_native_image, "format_forge_revision_section", return_value="Forge revision"), \
                patch.object(make_pr_not_for_native_image, "gh", side_effect=fake_gh):
            make_pr_not_for_native_image.create_pull_request(
                "not-native-branch",
                "org.example:demo:1.0.0",
                "/repo",
                local_ci_verification,
            )

        create_call = gh_calls[1]
        body = create_call[create_call.index("--body") + 1]
        self.assertIn("Local CI Verification", body)
        self.assertIn("Repository-level fix paths", body)
        self.assertIn("--label", create_call)
        self.assertIn("human-intervention", create_call)


if __name__ == "__main__":
    unittest.main()
