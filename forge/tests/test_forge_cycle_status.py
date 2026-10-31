# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the cycle's exit status, and the loop path each status takes.

§AR-forge-launcher.2 gives "nothing claimed" a status no crash produces, so the
loop idles on an empty queue only, and §AR-forge-launcher.1 backs a failed
cycle off, a crash and a transient GitHub halt included.
"""
from __future__ import annotations

import contextlib
import io
import os
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import forge_metadata
from dispatcher import issue_fetch
from tests.test_forge_launcher import FORGE_DIR

RATE_LIMITED: str = "HTTP 403: You have exceeded a secondary rate limit."


def write_executable(path: str, source: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(source)
    os.chmod(path, 0o755)


class CycleStatusTest(unittest.TestCase):

    def test_nothing_claimed_is_no_status_a_crash_exits_with(self) -> None:
        self.assertNotIn(forge_metadata.NOTHING_CLAIMED, (forge_metadata.RAN, 1, forge_metadata.GITHUB_OUTAGE))

    def test_a_crash_in_the_fetch_keeps_its_traceback(self) -> None:
        with patch.object(forge_metadata, "fetch_candidates", side_effect=ValueError("a fetch bug")), \
                self.assertRaisesRegex(ValueError, "a fetch bug"):
            forge_metadata.main(["--label", "library-new-request"])

    def test_a_rate_limited_fetch_is_a_transient_halt(self) -> None:
        refused = subprocess.CompletedProcess([], 1, stdout="", stderr=RATE_LIMITED)
        with patch.object(issue_fetch.subprocess, "run", return_value=refused), \
                contextlib.redirect_stderr(io.StringIO()) as logged:
            code: int = forge_metadata.main(["--label", "library-new-request"])
        self.assertEqual(forge_metadata.GITHUB_OUTAGE, code)
        self.assertEqual(75, code)
        self.assertIn("secondary rate limit", logged.getvalue())
        self.assertNotIn("Traceback", logged.getvalue())

    def test_a_refusal_that_is_no_outage_stays_a_crash(self) -> None:
        refused = subprocess.CompletedProcess([], 1, stdout="", stderr="HTTP 404: Not Found")
        with patch.object(issue_fetch.subprocess, "run", return_value=refused), \
                self.assertRaisesRegex(RuntimeError, "cannot list issues"):
            forge_metadata.main(["--label", "library-new-request"])


class LoopPathTest(unittest.TestCase):
    """`run-forge.sh` over the real cycle, with the host gate passed and `gh` and `sleep` faked."""

    def loop(self, issue_list: str) -> tuple[subprocess.CompletedProcess[str], list[str], list[str]]:
        """One loop iteration whose `gh issue list` runs `issue_list`: the launcher, its sleeps and its calls."""
        with tempfile.TemporaryDirectory() as root:
            forge: str = os.path.join(root, "forge")
            bin_dir: str = os.path.join(root, "bin")
            slept: str = os.path.join(root, "slept")
            calls: str = os.path.join(root, "calls")
            os.makedirs(forge)
            shutil.copy(os.path.join(FORGE_DIR, "run-forge.sh"), forge)
            write_executable(os.path.join(forge, "utility_scripts", "rhei_launcher_host.sh"),
                             "require_rhei_launcher_host() { return 0; }\n")
            # The real cycle, with its runs directory in the temporary forge; a review is only logged.
            write_executable(os.path.join(forge, "forge_metadata.py"), f"""import os, sys
sys.path.insert(0, {FORGE_DIR!r})
with open({calls!r}, "a", encoding="utf-8") as handle:
    handle.write(("review" if "--review" in sys.argv else "cycle") + "\\n")
if "--review" in sys.argv:
    sys.exit(0)
import forge_metadata
forge_metadata.FORGE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.exit(forge_metadata.main(sys.argv[1:]))
""")
            write_executable(os.path.join(bin_dir, "gh"), f"""#!/usr/bin/env bash
case "$1 $2" in
    "issue list") {issue_list} ;;
    "auth status"|"api user") printf 'forge-bot\\n' ;;
    *) exit 1 ;;
esac
""")
            # The first wait ends the launcher, so the test sees one iteration.
            write_executable(os.path.join(bin_dir, "sleep"),
                             f"#!/usr/bin/env bash\nprintf '%s\\n' \"$1\" >> {slept}\nkill -TERM \"$PPID\"\n")
            completed = subprocess.run(
                [os.path.join(forge, "run-forge.sh"), "--label", "library-new-request"],
                env={**os.environ, "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}"},
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=120,
            )
            with open(slept, encoding="utf-8") as handle:
                waits: list[str] = handle.read().splitlines()
            with open(calls, encoding="utf-8") as handle:
                made: list[str] = handle.read().splitlines()
        return completed, waits, made

    def test_a_crash_in_the_fetch_backs_off_instead_of_idling(self) -> None:
        completed, waits, _ = self.loop("printf 'not json\\n'")
        self.assertIn("Traceback", completed.stderr)
        self.assertIn("ERROR: The cycle failed with status 1;", completed.stderr)
        self.assertNotIn("No issue to claim", completed.stdout)
        self.assertEqual(["60"], waits)

    def test_a_rate_limited_fetch_backs_off_as_a_transient_halt(self) -> None:
        completed, waits, _ = self.loop(f"printf '%s\\n' '{RATE_LIMITED}' >&2; exit 1")
        self.assertIn("ERROR: The cycle failed with status 75;", completed.stderr)
        self.assertNotIn("Traceback", completed.stderr)
        self.assertNotIn("No issue to claim", completed.stdout)
        self.assertEqual(["60"], waits)

    def test_an_empty_queue_still_idles_and_reviews(self) -> None:
        completed, waits, made = self.loop("printf '[]\\n'")
        self.assertIn("No issue to claim; looking again in 600 seconds.", completed.stdout, completed.stderr)
        self.assertNotIn("The cycle failed", completed.stderr)
        self.assertEqual(["600"], waits)
        self.assertIn("review", made[made.index("cycle"):])


if __name__ == "__main__":
    unittest.main()
