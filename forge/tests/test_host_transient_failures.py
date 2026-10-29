# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests that the host gate tells a transient failure from a host fault.

§FS-forge-host-requirements.1: a GitHub rate limit or an unreachable endpoint waits, a host fault
stops the worker.
"""

import io
import json
import os
import socket
import ssl
import subprocess
import threading
import unittest
import urllib.error
import urllib.request
from collections import Counter
from contextlib import redirect_stdout
from typing import Sequence
from unittest.mock import Mock, patch

from utility_scripts.host_probes import GITHUB_RATE_LIMIT_FIX, ProbeResult, probe_http_200, run_command
from utility_scripts.host_requirements import (
    TRANSIENT_FAILURE_EXIT_STATUS,
    CheckResult,
    HostRequirements,
    QueueRequirements,
    main,
)

REACHABLE: ProbeResult = ProbeResult(True, "reachable")
UNREACHABLE: ProbeResult = ProbeResult(
    False, "tcp://github.com:443 unreachable: [Errno -3] Temporary failure in name resolution", True
)
AUTHENTICATED = subprocess.CompletedProcess(["gh", "auth", "status"], 0, "github.com\n  Logged in\n", "")
# What the permission query printed when four workers sharing one account tripped GitHub's
# short-lived GraphQL limit, with thousands of points of the hourly budget left (#9630).
OBSERVED_RATE_LIMIT = subprocess.CompletedProcess(
    ["gh", "api", "graphql"],
    1,
    '{"errors":[{"type":"RATE_LIMIT","code":"graphql_rate_limit",'
    '"message":"API rate limit already exceeded for user ID 352820."}]}',
    "gh: API rate limit already exceeded for user ID 352820.\n",
)
PERMISSIONS = "repository and project permissions"
LOGIN = "gh authentication"
# What `gh auth status` prints for a token it could not confirm, whatever the reason.
LOGIN_UNCONFIRMED = subprocess.CompletedProcess(
    ["gh", "auth", "status"], 1,
    "github.com\n  X Failed to log in to github.com account forge-bot (keyring)\n"
    "  - The token in keyring is invalid.\n", "",
)


def forge_launcher_host() -> HostRequirements:
    """A gate in the mode `run-forge.sh` runs it: issue work with PR review."""
    return HostRequirements(
        "/repo/forge",
        "python3",
        {},
        requirements=QueueRequirements(issue_work=True, review_work=True),
        review_model="opus",
    )


def failed(stdout: str = "", stderr: str = "") -> subprocess.CompletedProcess:
    return subprocess.CompletedProcess(["gh"], 1, stdout, stderr)


def check_github(host: HostRequirements, outputs: Sequence[subprocess.CompletedProcess],
                 probe: Mock) -> dict[str, CheckResult]:
    """Run the GitHub checks against scripted `gh` outputs; return the results by name."""
    with patch("utility_scripts.host_access_checks.resolve_executable", return_value="/usr/bin/gh"), \
            patch("utility_scripts.host_access_checks.run_command", side_effect=list(outputs)), \
            patch("utility_scripts.host_requirements.probe_tcp_host", probe):
        host._check_github()
    return {result.name: result for result in host.results}


class RateLimitTests(unittest.TestCase):

    def test_a_rate_limited_permission_query_is_a_rate_limit_not_a_missing_permission(self) -> None:
        outputs: dict[str, subprocess.CompletedProcess] = {
            "short-lived GraphQL limit (#9630)": OBSERVED_RATE_LIMIT,
            "hourly GraphQL budget": failed(
                '{"errors":[{"type":"RATE_LIMITED","message":"API rate limit exceeded for user ID 1."}]}'),
            "limit typed without the usual message": failed(
                '{"errors":[{"type":"RATE_LIMIT","code":"graphql_rate_limit","message":"Slow down."}]}'),
            "secondary limit, HTTP 403": failed(stderr="gh: You have exceeded a secondary rate limit. (HTTP 403)"),
            "primary limit, HTTP 429": failed(stderr="gh: API rate limit exceeded for user ID 1. (HTTP 429)"),
        }
        for case, output in outputs.items():
            with self.subTest(case):
                host: HostRequirements = forge_launcher_host()
                probe: Mock = Mock(return_value=REACHABLE)

                permissions = check_github(host, [AUTHENTICATED, output], probe)[PERMISSIONS]

                self.assertEqual("RETRY", permissions.status)
                self.assertTrue(permissions.detail.startswith("GitHub rate limit: "), permissions.detail)
                self.assertEqual(GITHUB_RATE_LIMIT_FIX, permissions.remediation)
                self.assertEqual(TRANSIENT_FAILURE_EXIT_STATUS, host.exit_status())
                # The limit alone decides; GitHub need not be probed.
                probe.assert_not_called()

    def test_a_rate_limited_release_lookup_does_not_fail_the_graalvm_lane(self) -> None:
        host: HostRequirements = forge_launcher_host()
        native_image = subprocess.CompletedProcess(["native-image"], 0, "native-image 25.0.4\n", "")
        with patch("utility_scripts.host_graalvm_checks.run_command",
                   side_effect=[OBSERVED_RATE_LIMIT, native_image]):
            latest_ga: str | None = host._read_latest_graalvm_ga_version()
            host._check_graalvm_version("GRAALVM_HOME", "/graalvm", latest_ga, False)

        lookup, lane = host.results
        self.assertEqual(("RETRY", "RETRY"), (lookup.status, lane.status))
        self.assertIn("GitHub rate limit: ", lookup.detail)
        self.assertEqual(GITHUB_RATE_LIMIT_FIX, lookup.remediation)
        self.assertEqual(TRANSIENT_FAILURE_EXIT_STATUS, host.exit_status())


class NetworkFailureTests(unittest.TestCase):

    def test_an_outage_fails_every_network_and_github_check_transiently(self) -> None:
        # The DNS outage of #9630: no host resolves, so gh cannot log in and git cannot fetch.
        def offline(command: list[str], _environment: dict, timeout: int = 20) -> subprocess.CompletedProcess:
            if command[:2] == ["gh", "auth"]:
                return failed("github.com\n  X Failed to log in to github.com account forge-bot (keyring)")
            if "get-url" in command:
                return subprocess.CompletedProcess(command, 0, "git@github.com:oracle/repo.git\n", "")
            return subprocess.CompletedProcess(command, 128, "", "ssh: Could not resolve hostname github.com")

        host: HostRequirements = forge_launcher_host()
        probe: Mock = Mock(return_value=UNREACHABLE)
        with patch("utility_scripts.host_access_checks.run_command", side_effect=offline), \
                patch("utility_scripts.host_access_checks.resolve_executable", return_value="/usr/bin/gh"), \
                patch("utility_scripts.host_access_checks.find_remote_for_github_repo", return_value="origin"), \
                patch("utility_scripts.host_requirements.probe_tcp_host", probe), \
                patch("utility_scripts.host_access_checks.probe_http_200",
                      return_value=ProbeResult(False, "HEAD failed: <urlopen error [Errno -3]>", True)):
            host._check_network()
            host._check_github()

        failures: dict[str, str] = {result.name: result.status for result in host.results if result.blocks_work}
        for name in ("github.com", "api.github.com", "chatgpt.com", "Forge git self-update",
                     "generated-branch push access", "gh authentication", PERMISSIONS):
            self.assertEqual("RETRY", failures.pop(name), name)
        self.assertEqual({"RETRY"}, set(failures.values()))
        self.assertEqual(TRANSIENT_FAILURE_EXIT_STATUS, host.exit_status())
        # Each host is probed once, however many checks depend on it.
        self.assertEqual({1}, set(Counter(call.args[0] for call in probe.call_args_list).values()))

    def test_an_endpoint_that_answers_without_service_is_transient_and_one_that_denies_is_not(self) -> None:
        for error, transient in (
                (urllib.error.HTTPError("https://repo.example/", 503, "Service Unavailable", {}, None), True),
                (urllib.error.HTTPError("https://repo.example/", 429, "Too Many Requests", {}, None), True),
                (urllib.error.URLError("[Errno -3] Temporary failure in name resolution"), True),
                (urllib.error.HTTPError("https://repo.example/", 404, "Not Found", {}, None), False),
        ):
            with self.subTest(str(error)):
                with patch("utility_scripts.host_probes.urllib.request.urlopen", side_effect=error):
                    probe: ProbeResult = probe_http_200("https://repo.example")

                self.assertFalse(probe.passed)
                self.assertEqual(transient, probe.transient)

    def test_a_proxy_that_refuses_the_tunnel_answered_and_is_judged_by_its_status(self) -> None:
        for status, transient in ((403, False), (407, False), (502, True), (503, True)):
            with self.subTest(status):
                refused = urllib.error.URLError(OSError(f"Tunnel connection failed: {status} Refused"))
                with patch("utility_scripts.host_probes.urllib.request.urlopen", side_effect=refused):
                    probe: ProbeResult = probe_http_200("https://repo.example")

                self.assertFalse(probe.passed)
                self.assertEqual(transient, probe.transient)

    def test_a_proxy_that_answers_403_to_the_tunnel_is_a_host_fault(self) -> None:
        with socket.create_server(("127.0.0.1", 0)) as proxy:
            def refuse() -> None:
                connection, _ = proxy.accept()
                with connection:
                    connection.recv(4096)
                    connection.sendall(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n")

            thread: threading.Thread = threading.Thread(target=refuse, daemon=True)
            thread.start()
            proxy_url: str = f"http://127.0.0.1:{proxy.getsockname()[1]}"
            with patch.dict(os.environ, {"https_proxy": proxy_url, "HTTPS_PROXY": proxy_url,
                                         "no_proxy": "", "NO_PROXY": ""}), \
                    patch.object(urllib.request, "_opener", None):
                probe: ProbeResult = probe_http_200("https://repo.example.invalid", timeout=5)
            thread.join(5)

        self.assertIn("Tunnel connection failed: 403", probe.detail)
        self.assertFalse(probe.transient)

    def test_an_untrusted_certificate_is_a_host_fault(self) -> None:
        unknown_ca = ssl.SSLError(1, "[SSL: TLSV1_ALERT_UNKNOWN_CA] tlsv1 alert unknown ca")
        unknown_ca.reason = "TLSV1_ALERT_UNKNOWN_CA"
        for error, transient in (
                (ssl.SSLCertVerificationError(1, "[SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed"), False),
                (unknown_ca, False),
                (ssl.SSLEOFError(8, "[SSL: UNEXPECTED_EOF_WHILE_READING] EOF occurred"), True),
        ):
            with self.subTest(str(error)):
                with patch("utility_scripts.host_probes.urllib.request.urlopen",
                           side_effect=urllib.error.URLError(error)):
                    probe: ProbeResult = probe_http_200("https://repo.example")

                self.assertEqual(transient, probe.transient)

    def test_a_github_call_that_times_out_is_transient(self) -> None:
        # A slow but reachable GitHub: the probe connects, the call does not answer in time.
        with patch("utility_scripts.host_probes.subprocess.run",
                   side_effect=subprocess.TimeoutExpired(["gh", "api", "graphql"], 20)):
            timed_out: subprocess.CompletedProcess = run_command(["gh", "api", "graphql"], {})
        login_timeout = failed("github.com\n  X Timeout trying to log in to github.com account forge-bot (keyring)")
        cases: dict[str, tuple[list[subprocess.CompletedProcess], str]] = {
            "permission query": ([AUTHENTICATED, timed_out], PERMISSIONS),
            "gh login": ([login_timeout], LOGIN),
        }
        for case, (outputs, name) in cases.items():
            with self.subTest(case):
                host: HostRequirements = forge_launcher_host()

                results: dict[str, CheckResult] = check_github(host, outputs, Mock(return_value=REACHABLE))

                self.assertEqual("RETRY", results[name].status)
                self.assertEqual(TRANSIENT_FAILURE_EXIT_STATUS, host.exit_status())

        host = forge_launcher_host()
        remote = subprocess.CompletedProcess(["git"], 0, "git@github.com:oracle/repo.git\n", "")
        with patch("utility_scripts.host_access_checks.run_command", side_effect=[remote, timed_out]), \
                patch("utility_scripts.host_requirements.probe_tcp_host", return_value=REACHABLE):
            host._check_git_remote("Forge git self-update", "/repo", True, "")
        self.assertEqual("RETRY", host.results[0].status)

    def test_an_agent_login_fails_transiently_only_while_its_provider_is_down(self) -> None:
        coverage: QueueRequirements = QueueRequirements(issue_work=False, review_work=False, coverage_work=True)
        for provider, status in ((UNREACHABLE, "RETRY"), (REACHABLE, "FAIL")):
            with self.subTest(status):
                host: HostRequirements = HostRequirements("/repo/forge", "python3", {}, requirements=coverage)
                with patch("utility_scripts.host_toolchain_checks.resolve_executable", return_value="/usr/bin/pi"), \
                        patch("utility_scripts.host_requirements.probe_tcp_host", return_value=provider), \
                        patch.object(host, "_selected_agent_authentication", return_value=(False, "refresh failed")):
                    host._check_selected_agents()

                login, = (result for result in host.results if result.required)
                self.assertEqual(status, login.status)


class HostFaultTests(unittest.TestCase):

    def test_a_permission_github_denies_still_stops_the_worker(self) -> None:
        denied = failed(
            json.dumps({"data": {"organization": {"projectV2": None}}, "errors": [{
                "type": "NOT_FOUND", "message": "Could not resolve to a ProjectV2 with the number 30."}]}),
            "gh: Could not resolve to a ProjectV2 with the number 30.",
        )
        read_only = subprocess.CompletedProcess(["gh"], 0, json.dumps({"data": {
            "viewer": {"login": "forge-bot"},
            "repository": {"viewerPermission": "READ"},
            "organization": {"projectV2": {"title": "Reachability Metadata", "viewerCanUpdate": False}},
        }}), "")
        cases: dict[str, tuple[list[subprocess.CompletedProcess], str]] = {
            "query denied": ([AUTHENTICATED, denied], PERMISSIONS),
            "read-only account": ([AUTHENTICATED, read_only], "oracle repository mutations"),
            "token GitHub rejects": ([LOGIN_UNCONFIRMED, rate_limit_answer(401)], LOGIN),
        }
        for case, (outputs, name) in cases.items():
            with self.subTest(case):
                host: HostRequirements = forge_launcher_host()

                result = check_github(host, outputs, Mock(return_value=REACHABLE))[name]

                self.assertEqual("FAIL", result.status)
                self.assertNotEqual(GITHUB_RATE_LIMIT_FIX, result.remediation)
                self.assertEqual(1, host.exit_status())

    def test_a_login_gh_cannot_confirm_waits_unless_github_rejects_the_token(self) -> None:
        # gh reports a rate limit or a slow answer as an invalid token; the uncounted rate_limit
        # endpoint tells them apart.
        for status, verdict in ((200, "RETRY"), (429, "RETRY"), (503, "RETRY"), (401, "FAIL")):
            with self.subTest(status):
                host: HostRequirements = forge_launcher_host()
                run: Mock = Mock(side_effect=[LOGIN_UNCONFIRMED, rate_limit_answer(status)])
                with patch("utility_scripts.host_access_checks.resolve_executable", return_value="/usr/bin/gh"), \
                        patch("utility_scripts.host_access_checks.run_command", run), \
                        patch("utility_scripts.host_requirements.probe_tcp_host", return_value=REACHABLE):
                    host._check_github()

                results: dict[str, CheckResult] = {result.name: result for result in host.results}
                self.assertEqual(["gh", "api", "--include", "rate_limit"], run.call_args_list[1].args[0])
                self.assertEqual((verdict, verdict), (results[LOGIN].status, results[PERMISSIONS].status))
                self.assertIn(f"gh api rate_limit answered HTTP {status}", results[LOGIN].detail)
                self.assertEqual(1 if verdict == "FAIL" else TRANSIENT_FAILURE_EXIT_STATUS, host.exit_status())

    def test_a_host_fault_beside_a_transient_failure_stops_with_the_full_report(self) -> None:
        host, output = run_gate(
            ("github", PERMISSIONS, "GitHub rate limit: exceeded", GITHUB_RATE_LIMIT_FIX, True),
            ("docker", "daemon access", "Docker CLI is unavailable", "Install Docker.", False),
        )

        self.assertEqual(1, host.exit_status())
        self.assertIn("[forge-host] Deterministic host requirements", output)
        self.assertIn("[RETRY] github: repository and project permissions", output)
        self.assertIn("[FAIL] docker: daemon access", output)
        self.assertIn("ERROR: 2 required check(s) failed. No work was started.", output)


class GateVerdictTests(unittest.TestCase):

    def test_a_gate_whose_every_failure_is_transient_exits_75_and_prints_only_the_failures(self) -> None:
        host, output = run_gate(("github", PERMISSIONS, "GitHub rate limit: exceeded", GITHUB_RATE_LIMIT_FIX, True))

        self.assertEqual(TRANSIENT_FAILURE_EXIT_STATUS, host.exit_status())
        self.assertEqual(
            "  [RETRY] github: repository and project permissions — GitHub rate limit: exceeded\n"
            f"         Fix: {GITHUB_RATE_LIMIT_FIX}\n"
            "RETRY: 1 required check(s) failed transiently. No work was started.\n",
            output,
        )

    def test_the_cli_exits_with_the_gate_status(self) -> None:
        def transient_failure(host: HostRequirements, verbose: bool = False) -> bool:
            host._add("network", "github.com", True, False, "unreachable", "Allow it.", transient=True)
            return False

        with patch.object(HostRequirements, "run", autospec=True, side_effect=transient_failure):
            self.assertEqual(TRANSIENT_FAILURE_EXIT_STATUS, main(["--forge-dir", "/repo/forge"]))


def rate_limit_answer(status: int) -> subprocess.CompletedProcess:
    """What `gh api --include rate_limit` prints when GitHub answers with `status`."""
    headers: str = f"HTTP/2.0 {status} Status\nContent-Type: application/json\n\n"
    if status == 200:
        return subprocess.CompletedProcess(["gh"], 0, headers + '{"resources": {}}', "")
    return subprocess.CompletedProcess(["gh"], 1, headers + '{"message": "No"}', f"gh: No (HTTP {status})\n")


def run_gate(*failures: tuple[str, str, str, str, bool]) -> tuple[HostRequirements, str]:
    """Run a gate whose only results are these failures (category, name, detail, fix, transient).

    Returns the gate and what it printed.
    """
    host: HostRequirements = HostRequirements(
        "/repo/forge", "python3", {},
        requirements=QueueRequirements(issue_work=False, review_work=False, github_work=False),
    )

    def report() -> None:
        for category, name, detail, fix, transient in failures:
            host._add(category, name, True, False, detail, fix, transient=transient)

    stdout: io.StringIO = io.StringIO()
    with patch.object(host, "_check_tools"), \
            patch.object(host, "_check_environment"), \
            patch.object(host, "_check_write_permissions"), \
            patch.object(host, "_check_network"), \
            patch.object(host, "_check_github"), \
            patch.object(host, "_check_selected_agents"), \
            patch.object(host, "_check_docker", side_effect=report), \
            redirect_stdout(stdout):
        host.run()
    return host, stdout.getvalue()


if __name__ == "__main__":
    unittest.main()
