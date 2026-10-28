# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# What the fetch script may publish (§root/PRCPL-graalvm-workaround-tickets.3).

"""Self-tests for `fetch-graalvm-ticket.py` against a fake `gdev-cli`.

Run with `python3 graalvm-tickets/test_fetch_graalvm_ticket.py`.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any

SCRIPT: Path = Path(__file__).with_name("fetch-graalvm-ticket.py")
# Answers `get-issue` with the projected issue in FAKE_ISSUE, `get` with the URLs in FAKE_LINKS,
# and fails like gdev-cli when FAKE_ERROR is set or the issue would be written to its cache.
FAKE_GDEV_CLI: str = """#!{python}
import json, os, sys
arguments = sys.argv[1:]
cached = arguments[1] == "get-issue" and "--no-cache" not in arguments
if os.environ.get("FAKE_ERROR") or cached:
    error = os.environ.get("FAKE_ERROR", "the full issue would be cached")
    print(json.dumps({{"status": 1, "error": error}}), file=sys.stderr)
    sys.exit(1)
print(os.environ["FAKE_ISSUE"] if arguments[1] == "get-issue" else os.environ["FAKE_LINKS"])
"""
PUBLIC_ISSUE: dict[str, Any] = {
    "summary": "[Native Image] Agent drops\n  `List<String>` & \u00a7GR-1 conditions",
    "status": "Open",
    "resolution": None,
    "fixVersions": ["25.0.1", "graalvm-23.0.0", "25.0.1-internal-patch"],
    "security": None,
}
LINKS: list[str] = [
    "https://github.com/oracle/graal/issues/14799",
    "https://github.com/oracle/graal/issues/14799#issuecomment-1",
    "https://jira.oraclecorp.com/browse/GR-80923",
    "https://github.com/oracle/graal-private/issues/1",
]


class FetchGraalvmTicketTest(unittest.TestCase):
    """Runs the script as `grund fetch` does, with the fake first on PATH."""

    def setUp(self) -> None:
        self.directory: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory()
        fake: Path = Path(self.directory.name) / "gdev-cli"
        fake.write_text(FAKE_GDEV_CLI.format(python=sys.executable))
        fake.chmod(0o755)

    def tearDown(self) -> None:
        self.directory.cleanup()

    def fetch(
            self, issue: dict[str, Any], *arguments: str, **environment: str
    ) -> subprocess.CompletedProcess[str]:
        """Run the script for GR-80923, or `arguments`, against the fake."""
        env: dict[str, str] = {
            **os.environ,
            "PATH": f"{self.directory.name}{os.pathsep}{os.environ['PATH']}",
            "FAKE_ISSUE": json.dumps(issue),
            "FAKE_LINKS": json.dumps(LINKS),
            **environment,
        }
        return subprocess.run(
            [sys.executable, str(SCRIPT), *(arguments or ("GR-80923",))],
            capture_output=True, text=True, env=env, check=False,
        )

    def assert_refused(self, result: subprocess.CompletedProcess[str], message: str) -> None:
        """Exit 1, nothing on stdout, and `message` on stderr."""
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn(message, result.stderr)

    def test_public_ticket_publishes_only_escaped_public_fields(self) -> None:
        result: subprocess.CompletedProcess[str] = self.fetch(PUBLIC_ISSUE)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            "## GR-80923: \\[Native Image\\] Agent drops `List&lt;String&gt;` &amp;"
            " <\u00a7>GR-1 conditions\n"
            "\n"
            "- Status: Open\n"
            "- Resolution: Unresolved\n"
            "- Fix versions: 25.0.1, graalvm-23.0.0\n"
            "- Public links: https://github.com/oracle/graal/issues/14799\n",
            result.stdout,
        )
        self.assertIn("not publishing fix version '25.0.1-internal-patch'", result.stderr)

    def test_security_restricted_ticket_is_refused(self) -> None:
        issue: dict[str, Any] = {**PUBLIC_ISSUE, "security": "Internal"}
        self.assert_refused(self.fetch(issue), "security-restricted")

    def test_summary_with_url_is_refused(self) -> None:
        issue: dict[str, Any] = {**PUBLIC_ISSUE, "summary": "Crash, see https://example.test/x"}
        self.assert_refused(self.fetch(issue), "URL in its summary")

    def test_status_and_resolution_must_be_plain_names(self) -> None:
        self.assert_refused(self.fetch({**PUBLIC_ISSUE, "status": "Open <b>"}), "status")
        self.assert_refused(self.fetch({**PUBLIC_ISSUE, "status": None}), "status")
        self.assert_refused(self.fetch({**PUBLIC_ISSUE, "resolution": "Done: 1.2"}), "resolution")

    def test_gdev_cli_error_is_reported(self) -> None:
        result: subprocess.CompletedProcess[str] = self.fetch(
            PUBLIC_ISSUE, FAKE_ERROR="Issue Does Not Exist"
        )
        self.assert_refused(result, "Issue Does Not Exist")

    def test_missing_gdev_cli_is_reported(self) -> None:
        result: subprocess.CompletedProcess[str] = self.fetch(PUBLIC_ISSUE, PATH=os.defpath)
        self.assert_refused(result, "gdev-cli is not on PATH")

    def test_bad_argument_is_a_usage_error(self) -> None:
        for arguments in (("GR-0",), ("80923",), ("GR-1", "GR-2")):
            result: subprocess.CompletedProcess[str] = self.fetch(PUBLIC_ISSUE, *arguments)
            self.assertEqual(2, result.returncode, arguments)
            self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
