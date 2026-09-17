# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the gate's Gradle command execution and log parsing.

Exercises pure-string and pure-IO helpers (exit-code recovery, log tails)
plus the Gradle runners via ``subprocess.run`` mocks. The Gradle /
native-image side is intentionally not exercised here.
"""

from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_test_verification as ntv  # noqa: E402
from utility_scripts import native_trace_execution as nte  # noqa: E402


class ParseBinaryExitCodeTests(unittest.TestCase):
    """The legacy log-scrape fallback when the sentinel file is unavailable."""

    def _write(self, content: str) -> str:
        fd, path = tempfile.mkstemp(suffix=".log")
        os.close(fd)
        Path(path).write_text(content, encoding="utf-8")
        self.addCleanup(os.unlink, path)
        return path

    def test_recovers_exit_code_from_gradle_message(self) -> None:
        log = self._write(
            "> Task :runNativeTraceImage\n"
            "Process 'command '/path/to/binary'' finished with non-zero exit value 172\n"
        )
        self.assertEqual(nte._parse_binary_exit_code(log), 172)

    def test_returns_last_match_when_multiple(self) -> None:
        log = self._write(
            "earlier subprocess: exit value 1\n"
            "later subprocess:   exit value 7\n"
        )
        self.assertEqual(nte._parse_binary_exit_code(log), 7)

    def test_returns_none_when_no_match(self) -> None:
        log = self._write("BUILD SUCCESSFUL in 12s\n")
        self.assertIsNone(nte._parse_binary_exit_code(log))

    def test_returns_none_for_unreadable_path(self) -> None:
        self.assertIsNone(nte._parse_binary_exit_code("/nonexistent/log.txt"))


class ReadExitFileTests(unittest.TestCase):
    """Sentinel-file reader (the primary exit-code recovery path)."""

    def _write(self, content: str) -> str:
        fd, path = tempfile.mkstemp(suffix=".exit")
        os.close(fd)
        Path(path).write_text(content, encoding="utf-8")
        self.addCleanup(os.unlink, path)
        return path

    def test_reads_integer(self) -> None:
        path = self._write("172\n")
        self.assertEqual(nte._read_exit_file(path), 172)

    def test_strips_whitespace(self) -> None:
        path = self._write("  0  \n")
        self.assertEqual(nte._read_exit_file(path), 0)

    def test_returns_none_for_empty(self) -> None:
        path = self._write("")
        self.assertIsNone(nte._read_exit_file(path))

    def test_returns_none_for_missing(self) -> None:
        self.assertIsNone(nte._read_exit_file("/nonexistent/exit"))

    def test_returns_none_for_garbage(self) -> None:
        path = self._write("not-a-number")
        self.assertIsNone(nte._read_exit_file(path))


class FailureLogTailTests(unittest.TestCase):
    """Native failure diagnostics print the tail of the Gradle log."""

    def test_extracts_last_20_lines(self) -> None:
        fd, path = tempfile.mkstemp(suffix=".log")
        os.close(fd)
        self.addCleanup(os.unlink, path)
        Path(path).write_text(
            "\n".join(f"line-{index}" for index in range(350)),
            encoding="utf-8",
        )

        excerpt = nte.extract_failure_log_tail(path)

        self.assertNotIn("line-329", excerpt)
        self.assertIn("line-330", excerpt)
        self.assertIn("line-349", excerpt)


class GradlePropertyThreadingTests(unittest.TestCase):
    """Caller-supplied properties ride on every command and reproduction string.

    A caller that widens the test source set must see the same widening in the
    gate's own commands and in the reproduction command the analysis agent gets,
    or the repair reproduces a different build (§FS-native-test-verification-gate.2).
    """

    _PROPERTY = ("-PincludeCodeCoverageSuite=true",)

    def test_command_builders_carry_the_properties(self) -> None:
        test_command = ntv._coordinate_test_command(
            "g:a:1.0", ["/tmp/agent"], gradle_properties=self._PROPERTY
        )
        self.assertIn("-PincludeCodeCoverageSuite=true", test_command)
        trace_command = nte.run_native_trace_image_command(
            coordinate="g:a:1.0",
            run_dir="/tmp/run",
            condition_packages=["g"],
            metadata_config_dirs=[],
            gradle_properties=self._PROPERTY,
        )
        self.assertIn("-PincludeCodeCoverageSuite=true", trace_command)

    def test_runners_place_the_properties_on_the_gradle_command(self) -> None:
        recorded: list[list[str]] = []

        def _record(cmd, cwd, env, stdout, stderr, check, timeout=None):
            recorded.append(list(cmd))
            return Mock(returncode=0)

        with tempfile.TemporaryDirectory() as scratch:
            log_path = os.path.join(scratch, "log.txt")
            with patch.object(nte.subprocess, "run", side_effect=_record):
                nte.run_generate_metadata(
                    reachability_repo_path=scratch,
                    coordinate="g:a:1.0",
                    output_dir=os.path.join(scratch, "agent"),
                    log_path=log_path,
                    env={},
                    gradle_properties=self._PROPERTY,
                )
                nte.run_coordinate_test(
                    reachability_repo_path=scratch,
                    coordinate="g:a:1.0",
                    metadata_config_dirs=[],
                    log_path=log_path,
                    timeout_seconds=60,
                    env={},
                    gradle_properties=self._PROPERTY,
                )
                nte.run_native_trace_image(
                    reachability_repo_path=scratch,
                    coordinate="g:a:1.0",
                    run_dir=scratch,
                    condition_packages=["g"],
                    metadata_config_dirs=[],
                    log_path=log_path,
                    timeout_seconds=60,
                    env={},
                    gradle_properties=self._PROPERTY,
                )
        self.assertEqual(len(recorded), 3)
        for command in recorded:
            self.assertIn("-PincludeCodeCoverageSuite=true", command)


if __name__ == "__main__":
    unittest.main()
