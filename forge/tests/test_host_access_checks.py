# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from utility_scripts.host_probes import (
    probe_http_200,
    probe_proxied_host,
    resolve_https_proxy,
    run_command,
)
from utility_scripts.host_requirements import (
    COVERAGE_REQUIREMENTS,
    HostRequirements,
    QueueRequirements,
)
from utility_scripts.native_image_artifact import ARTIFACT_REPOSITORY_URLS


class HostAccessChecksTests(unittest.TestCase):
    def test_custom_agent_alias_uses_family_for_state_and_provider_checks(self) -> None:
        environment = {"HOME": "/operator"}
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            environment=environment,
            requirements=QueueRequirements(
                issue_work=False,
                review_work=True,
                github_work=False,
            ),
            analysis_agent="cdx",
            analysis_family="codex",
            analysis_model="gpt-5.6-terra",
        )
        with patch(
                "utility_scripts.host_access_checks.probe_write_access",
                return_value=(True, "writable"),
        ), patch.object(
            host_requirements,
            "_check_git_remote_access",
        ), patch(
            "utility_scripts.host_access_checks.probe_tcp_host",
            return_value=(True, "reachable"),
        ) as probe_host:
            host_requirements._check_write_permissions()
            host_requirements._check_network()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertIn("codex state", result_by_name)
        self.assertIn("/operator/.codex", result_by_name["codex state"].remediation)
        probed_hosts = [call.args[0] for call in probe_host.call_args_list]
        self.assertIn("chatgpt.com", probed_hosts)
        self.assertNotIn("opencode.ai", probed_hosts)

    def test_github_permission_results_name_each_required_mutation_boundary(self) -> None:
        environment = {
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "1",
        }
        host_requirements = HostRequirements("/repo/forge", "python3", environment)
        response = json.dumps({
            "data": {
                "viewer": {
                    "login": "automation-user",
                },
                "repository": {
                    "nameWithOwner": "oracle/graalvm-reachability-metadata",
                    "viewerPermission": "MAINTAIN",
                },
                "organization": {
                    "projectV2": {
                        "title": "Reachability Metadata",
                        "viewerCanUpdate": True,
                    },
                },
            },
        })

        host_requirements._record_github_permissions(response)

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertTrue(result_by_name["oracle repository mutations"].passed)
        self.assertTrue(result_by_name["oracle project 30 updates"].passed)
        push_target = result_by_name["generated-branch push target"]
        self.assertTrue(push_target.passed)
        self.assertIn("oracle/graalvm-reachability-metadata", push_target.detail)

    @patch("utility_scripts.host_access_checks.uuid.uuid4", return_value=Mock(hex="probe-id"))
    @patch("utility_scripts.host_access_checks.find_remote_for_github_repo", return_value="upstream")
    @patch("utility_scripts.host_access_checks.run_command")
    def test_build_work_requires_dry_run_push_to_publication_remote(
            self,
            command: Mock,
            find_remote: Mock,
            _uuid: Mock,
    ) -> None:
        command.side_effect = [
            subprocess.CompletedProcess(
                ["git", "remote", "get-url", "--push", "upstream"],
                0,
                "https://github.com/oracle/graalvm-reachability-metadata.git\n",
                "",
            ),
            subprocess.CompletedProcess(
                ["git", "push", "--dry-run"],
                128,
                "",
                "fatal: Authentication failed",
            ),
        ]
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=COVERAGE_REQUIREMENTS,
        )

        host_requirements._check_generated_branch_push_access()

        result = host_requirements.results[-1]
        self.assertTrue(result.required)
        self.assertFalse(result.passed)
        self.assertIn("remote=upstream", result.detail)
        self.assertIn("dry-run push=failed", result.detail)
        self.assertIn("gh auth setup-git --hostname github.com", result.remediation)
        find_remote.assert_called_once_with(
            "oracle/graalvm-reachability-metadata",
            cwd="/repo",
        )
        push_call = command.call_args_list[-1]
        self.assertEqual(
            [
                "git", "-C", "/repo",
                "push", "--dry-run", "--no-verify", "--porcelain",
                "upstream", "HEAD:refs/heads/ai/forge-host-check-probe-id",
            ],
            push_call.args[0],
        )
        self.assertEqual(60, push_call.kwargs["timeout"])

    @patch("utility_scripts.host_access_checks.uuid.uuid4", return_value=Mock(hex="probe-id"))
    @patch("utility_scripts.host_access_checks.find_remote_for_github_repo", return_value="origin")
    @patch("utility_scripts.host_access_checks.run_command")
    def test_dry_run_push_probe_supports_ssh(
            self,
            command: Mock,
            _find_remote: Mock,
            _uuid: Mock,
    ) -> None:
        command.side_effect = [
            subprocess.CompletedProcess(
                ["git", "remote", "get-url", "--push", "origin"],
                0,
                "git@github.com:oracle/graalvm-reachability-metadata.git\n",
                "",
            ),
            subprocess.CompletedProcess(
                ["git", "push", "--dry-run"],
                0,
                "To github.com:oracle/graalvm-reachability-metadata.git\n",
                "",
            ),
        ]
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=COVERAGE_REQUIREMENTS,
        )

        host_requirements._check_generated_branch_push_access()

        result = host_requirements.results[-1]
        self.assertTrue(result.passed)
        self.assertIn("protocol=ssh", result.detail)
        self.assertIn("dry-run push=passed", result.detail)

    @patch(
        "utility_scripts.host_access_checks.find_remote_for_github_repo",
        side_effect=RuntimeError("missing"),
    )
    @patch("utility_scripts.host_access_checks.run_command")
    def test_push_probe_requires_remote_for_target_repository(
            self,
            command: Mock,
            _find_remote: Mock,
    ) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=COVERAGE_REQUIREMENTS,
        )

        host_requirements._check_generated_branch_push_access()

        result = host_requirements.results[-1]
        self.assertFalse(result.passed)
        self.assertIn("remote=unavailable", result.detail)
        command.assert_not_called()

    @patch("utility_scripts.host_probes.subprocess.run")
    def test_probe_commands_disable_git_terminal_prompts(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(["git"], 1, "", "failed")

        run_command(["git", "push", "--dry-run"], {})

        environment = command.call_args.kwargs["env"]
        self.assertEqual("0", environment["GIT_TERMINAL_PROMPT"])

    def test_runs_without_live_github_skip_github_permissions_and_api_hosts(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False, github_work=False),
        )

        with patch("utility_scripts.host_access_checks.probe_tcp_host", return_value=(True, "reachable")), \
                patch("utility_scripts.host_access_checks.probe_http_200", return_value=(True, "HTTP 200")), \
                patch.object(host_requirements, "_check_git_remote_access"):
            host_requirements._check_github()
            host_requirements._check_network()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["authentication and permissions"].status)
        self.assertEqual("SKIP", result_by_name["github.com"].status)
        self.assertEqual("SKIP", result_by_name["api.github.com"].status)
        self.assertEqual("PASS", result_by_name["chatgpt.com"].status)

    def test_issue_work_requires_http_200_from_every_artifact_repository(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False, github_work=False),
        )

        with patch.object(host_requirements, "_check_git_remote_access"), \
                patch("utility_scripts.host_access_checks.probe_tcp_host", return_value=(True, "reachable")), \
                patch(
                    "utility_scripts.host_access_checks.probe_http_200",
                    side_effect=[(True, "HTTP 200"), (False, "HTTP 503")],
                ) as probe_repository:
            host_requirements._check_network()

        self.assertEqual(
            list(ARTIFACT_REPOSITORY_URLS),
            [call.args[0] for call in probe_repository.call_args_list],
        )
        result_by_name = {result.name: result for result in host_requirements.results}
        central = result_by_name[f"artifact repository {ARTIFACT_REPOSITORY_URLS[0]}"]
        confluent = result_by_name[f"artifact repository {ARTIFACT_REPOSITORY_URLS[1]}"]
        self.assertEqual("PASS", central.status)
        self.assertEqual("FAIL", confluent.status)
        self.assertTrue(confluent.blocks_work)

    def test_http_repository_probe_requires_exactly_200(self) -> None:
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.getcode.return_value = 204

        with patch("utility_scripts.host_probes.urllib.request.urlopen", return_value=response) as open_url:
            passed, detail = probe_http_200("https://repo.example/maven")

        self.assertFalse(passed)
        self.assertIn("HTTP 204", detail)
        request = open_url.call_args.args[0]
        self.assertEqual("HEAD", request.get_method())
        self.assertEqual("https://repo.example/maven/", request.full_url)

    def test_selected_repository_paths_are_checked_instead_of_the_forge_parent_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as target_repo:
            gradlew = Path(target_repo) / "gradlew"
            gradlew.touch(mode=0o755)
            host_requirements = HostRequirements(
                "/parent/forge",
                "python3",
                {},
                requirements=QueueRequirements(issue_work=True, review_work=False),
                repo_dir=target_repo,
            )

            command = Mock(return_value=subprocess.CompletedProcess(["gradlew"], 0, "Gradle 8.14\n", ""))
            with patch("utility_scripts.host_toolchain_checks.run_command", command), \
                    patch("utility_scripts.host_access_checks.run_command", command):
                host_requirements._check_gradle_wrapper(True)
                host_requirements._check_write_permissions()
                host_requirements._check_git_remote_access()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertIn(str(gradlew), result_by_name["Gradle wrapper"].detail)
        self.assertIn("/parent/.git", result_by_name["Forge git metadata"].detail)
        self.assertIn(
            os.path.join(target_repo, ".git"),
            result_by_name["Selected repository git metadata"].detail,
        )
        self.assertTrue(result_by_name["Selected repository git metadata"].required)
        self.assertIn(f"checkout={target_repo}", result_by_name["Selected repository git remote"].detail)
        self.assertIn("checkout=/parent/forge", result_by_name["Forge git self-update"].detail)

    def test_selected_repository_checks_collapse_when_forge_owns_the_checkout(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=QueueRequirements(issue_work=False, review_work=True),
        )

        with patch("utility_scripts.host_access_checks.run_command") as command:
            command.return_value = subprocess.CompletedProcess(["git"], 0, "origin\n", "")
            host_requirements._check_write_permissions()
            host_requirements._check_git_remote_access()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["Selected repository git metadata"].status)
        self.assertEqual("SKIP", result_by_name["Selected repository git remote"].status)
        self.assertIn("checkout that contains Forge", result_by_name["Selected repository git remote"].detail)

    def test_network_probes_follow_the_configured_proxy(self) -> None:
        proxied = {"https_proxy": "http://10.0.0.1:80", "no_proxy": "localhost,.internal.example"}

        self.assertEqual(("10.0.0.1", 80), resolve_https_proxy(proxied, "github.com"))
        self.assertIsNone(resolve_https_proxy(proxied, "build.internal.example"))
        self.assertIsNone(resolve_https_proxy({}, "github.com"))
        self.assertEqual(("proxy.example", 80), resolve_https_proxy({"HTTPS_PROXY": "proxy.example"}, "github.com"))

    def test_proxy_tunnel_result_reports_both_hops(self) -> None:
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        connection.recv.return_value = b"HTTP/1.1 200 Connection established\r\n\r\n"

        with patch("utility_scripts.host_probes.socket.create_connection", return_value=connection):
            passed, detail = probe_proxied_host("github.com", 443, ("10.0.0.1", 80))

        self.assertTrue(passed)
        self.assertIn("via proxy 10.0.0.1:80", detail)
        connection.sendall.assert_called_once_with(
            b"CONNECT github.com:443 HTTP/1.1\r\nHost: github.com:443\r\n\r\n"
        )

    def test_proxy_refusal_is_reported_as_unreachable(self) -> None:
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        connection.recv.return_value = b"HTTP/1.1 403 Forbidden\r\n\r\n"

        with patch("utility_scripts.host_probes.socket.create_connection", return_value=connection):
            passed, detail = probe_proxied_host("registry-1.docker.io", 443, ("10.0.0.1", 80))

        self.assertFalse(passed)
        self.assertIn("403 Forbidden", detail)


if __name__ == "__main__":
    unittest.main()
