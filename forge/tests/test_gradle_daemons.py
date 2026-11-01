# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import contextlib
import io
import os
import tempfile
import unittest
from dataclasses import dataclass
from unittest.mock import patch

from utility_scripts.gradle_daemons import (
    GRADLE_STOP_TIMEOUT_SECONDS,
    is_stale_daemon_failure,
    run_with_stale_daemon_retry,
    stop_gradle_daemons,
)
from utility_scripts.gradle_environment import gradle_user_home_for_repo
from utility_scripts.logged_command import LoggedCommandResult

STALE_DAEMON_OUTPUT = """
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring root project 'graalvm-reachability-metadata'.
> Could not create task ':checkMetadataFiles'.
   > Could not create task of type 'MetadataFilesCheckerTask'.
      > Could not generate a decorated class for type MetadataFilesCheckerTask.
         > com/networknt/schema/JsonSchema
"""


GATE = "./gradlew checkMetadataFiles -Pcoordinates=org.example:demo:1.0.0"


def _result(command: list[str], returncode: int, stdout: str = "") -> LoggedCommandResult:
    return LoggedCommandResult(command, returncode, stdout, "/tmp/gate.log", False, 0.5)


@dataclass
class _GateRun:
    """One Gradle gate run, as the retry reads it."""

    command: str
    ok: bool
    output: str


class GradleDaemonRecyclingTests(unittest.TestCase):
    """Daemon pool recycling.

    §FS-forge-run-requirements.4 §FS-local-ci-equivalent-verification.1
    """

    def test_stop_runs_the_wrapper_stop_against_the_forge_gradle_home_only(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {"GRADLE_USER_HOME": os.path.expanduser("~/.gradle")}, clear=True), \
                patch("utility_scripts.gradle_daemons.run_logged_command") as run_command:
            run_command.return_value = _result(["./gradlew", "--stop", "--quiet"], 0)

            self.assertTrue(stop_gradle_daemons(repo_path, "issue-1412", "new workspace"))

            forge_gradle_home = gradle_user_home_for_repo(repo_path)
        run_command.assert_called_once()
        command_args, command_kwargs = run_command.call_args
        self.assertEqual(command_args[0], ["./gradlew", "--stop", "--quiet"])
        self.assertEqual(command_kwargs["cwd"], repo_path)
        self.assertEqual(command_kwargs["subject"], "issue-1412")
        self.assertEqual(command_kwargs["timeout_seconds"], GRADLE_STOP_TIMEOUT_SECONDS)
        # `--stop` only reaches the daemon registry of `GRADLE_USER_HOME`; scoping it
        # to the Forge home leaves the operator's `~/.gradle` daemons alone.
        self.assertEqual(command_kwargs["env"]["GRADLE_USER_HOME"], forge_gradle_home)
        self.assertNotEqual(forge_gradle_home, os.path.expanduser("~/.gradle"))

    def test_failed_stop_is_reported_and_returns_false(self) -> None:
        terminal = io.StringIO()
        with tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {}, clear=True), \
                patch("utility_scripts.gradle_daemons.run_logged_command") as run_command, \
                contextlib.redirect_stdout(terminal):
            run_command.return_value = _result(["./gradlew", "--stop", "--quiet"], 1, "no wrapper")

            self.assertFalse(stop_gradle_daemons(repo_path, None, "new workspace"))

        self.assertIn("Could not stop the Gradle daemons", terminal.getvalue())
        self.assertIn("new workspace", terminal.getvalue())

    def test_stale_daemon_signature_detection(self) -> None:
        self.assertTrue(is_stale_daemon_failure(STALE_DAEMON_OUTPUT))
        self.assertTrue(is_stale_daemon_failure("java.lang.NoClassDefFoundError: com/networknt/schema/JsonSchema"))
        self.assertFalse(is_stale_daemon_failure("TypeReached: org.example.Foo\nBUILD FAILED"))
        self.assertFalse(is_stale_daemon_failure(""))

    def test_stale_daemon_failure_stops_daemons_and_reruns_exactly_once(self) -> None:
        attempts = [_GateRun(GATE, False, STALE_DAEMON_OUTPUT), _GateRun(GATE, True, "BUILD SUCCESSFUL")]
        events: list[str] = []

        def run() -> _GateRun:
            events.append("run")
            return attempts[len([event for event in events if event == "run"]) - 1]

        terminal = io.StringIO()
        with patch(
                "utility_scripts.gradle_daemons.stop_gradle_daemons",
                side_effect=lambda *_args: events.append("stop") or True,
        ) as stop_daemons, contextlib.redirect_stdout(terminal):
            result = run_with_stale_daemon_retry("/repo", "org.example:demo:1.0.0", run)

        self.assertIs(result, attempts[1])
        self.assertEqual(events, ["run", "stop", "run"])
        stop_daemons.assert_called_once_with("/repo", "org.example:demo:1.0.0", "stale daemon failure")
        self.assertIn("stale Gradle daemon", terminal.getvalue())

    def test_second_stale_daemon_failure_is_returned_as_the_real_failure(self) -> None:
        runs: list[_GateRun] = []

        def run() -> _GateRun:
            runs.append(_GateRun(GATE, False, STALE_DAEMON_OUTPUT))
            return runs[-1]

        with patch("utility_scripts.gradle_daemons.stop_gradle_daemons", return_value=True) as stop_daemons, \
                contextlib.redirect_stdout(io.StringIO()):
            result = run_with_stale_daemon_retry("/repo", None, run)

        self.assertEqual(len(runs), 2)
        self.assertIs(result, runs[1])
        stop_daemons.assert_called_once()

    def test_other_failures_and_successes_do_not_touch_the_daemons(self) -> None:
        with patch("utility_scripts.gradle_daemons.stop_gradle_daemons") as stop_daemons:
            failed = run_with_stale_daemon_retry("/repo", None, lambda: _GateRun(GATE, False, "TypeReached: a.B"))
            passed = run_with_stale_daemon_retry("/repo", None, lambda: _GateRun(GATE, True, STALE_DAEMON_OUTPUT))

        self.assertFalse(failed.ok)
        self.assertTrue(passed.ok)
        stop_daemons.assert_not_called()

if __name__ == "__main__":
    unittest.main()
