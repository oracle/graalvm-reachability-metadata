# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the native-test verification gate and trace driver.

Exercises pure-string and pure-IO helpers (exit-code recovery, convergence
hashing) plus the gate's outer-loop routing via ``subprocess.run`` mocks.
The Gradle / native-image side is intentionally not exercised here.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_metadata_exploration as nme  # noqa: E402
from utility_scripts import native_test_verification as ntv  # noqa: E402


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
        self.assertEqual(ntv._parse_binary_exit_code(log), 172)

    def test_returns_last_match_when_multiple(self) -> None:
        log = self._write(
            "earlier subprocess: exit value 1\n"
            "later subprocess:   exit value 7\n"
        )
        self.assertEqual(ntv._parse_binary_exit_code(log), 7)

    def test_returns_none_when_no_match(self) -> None:
        log = self._write("BUILD SUCCESSFUL in 12s\n")
        self.assertIsNone(ntv._parse_binary_exit_code(log))

    def test_returns_none_for_unreadable_path(self) -> None:
        self.assertIsNone(ntv._parse_binary_exit_code("/nonexistent/log.txt"))


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
        self.assertEqual(ntv._read_exit_file(path), 172)

    def test_strips_whitespace(self) -> None:
        path = self._write("  0  \n")
        self.assertEqual(ntv._read_exit_file(path), 0)

    def test_returns_none_for_empty(self) -> None:
        path = self._write("")
        self.assertIsNone(ntv._read_exit_file(path))

    def test_returns_none_for_missing(self) -> None:
        self.assertIsNone(ntv._read_exit_file("/nonexistent/exit"))

    def test_returns_none_for_garbage(self) -> None:
        path = self._write("not-a-number")
        self.assertIsNone(ntv._read_exit_file(path))


class ClassKeyTests(unittest.TestCase):

    def test_replaces_dollar_signs(self) -> None:
        self.assertEqual(
            ntv.class_key_from_class_name("com.foo.Bar$Inner"),
            "com.foo.Bar_Inner",
        )

    def test_keeps_dots_dashes_underscores(self) -> None:
        self.assertEqual(
            ntv.class_key_from_class_name("com.foo.Bar-Baz_qux"),
            "com.foo.Bar-Baz_qux",
        )


class CollectEntriesTests(unittest.TestCase):
    """Convergence semantics for the trace driver."""

    def _make_run(self, contents: dict[str, object]) -> str:
        run_dir = tempfile.mkdtemp(prefix="trace-run-")
        self.addCleanup(_rmtree, run_dir)
        for name, payload in contents.items():
            target = Path(run_dir) / name
            target.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(payload, (dict, list)):
                target.write_text(json.dumps(payload), encoding="utf-8")
            else:
                target.write_bytes(payload)  # type: ignore[arg-type]
        return run_dir

    def test_identical_runs_produce_identical_sets(self) -> None:
        a = self._make_run({"reachability-metadata.json": {"reflection": [{"type": "Foo"}]}})
        b = self._make_run({"reachability-metadata.json": {"reflection": [{"type": "Foo"}]}})
        self.assertEqual(nme._collect_entries(a), nme._collect_entries(b))

    def test_added_entry_changes_set(self) -> None:
        a = self._make_run({"m.json": {"reflection": [{"type": "Foo"}]}})
        b = self._make_run({"m.json": {"reflection": [{"type": "Foo"}, {"type": "Bar"}]}})
        added = nme._collect_entries(b) - nme._collect_entries(a)
        self.assertTrue(added, "adding a reflection entry must yield delta")

    def test_non_json_files_use_stable_hash(self) -> None:
        # Same opaque bytes in two separate runs must produce the same entry
        # string — guarding against hash() salting.
        a = self._make_run({"opaque.bin": b"\x00\x01\x02"})
        b = self._make_run({"opaque.bin": b"\x00\x01\x02"})
        self.assertEqual(nme._collect_entries(a), nme._collect_entries(b))

    def test_different_opaque_bytes_diverge(self) -> None:
        a = self._make_run({"opaque.bin": b"AAA"})
        b = self._make_run({"opaque.bin": b"BBB"})
        self.assertNotEqual(nme._collect_entries(a), nme._collect_entries(b))


class GateRoutingTests(unittest.TestCase):
    """End-to-end routing: 0 → PASSED, 172 → continue, other → codex.

    Patches subprocess.run so no real Gradle is invoked. Uses a sentinel
    file to feed the binary's exit code back to the gate.
    """

    def setUp(self) -> None:
        self.repo = tempfile.mkdtemp(prefix="repo-")
        self.addCleanup(_rmtree, self.repo)
        self.output_dir = os.path.join(
            tempfile.mkdtemp(prefix="output-"),
            "natively-collected",
        )
        self.addCleanup(_rmtree, os.path.dirname(self.output_dir))

    def _fake_run_factory(self, scripted_exits: list[int]):
        """Build a subprocess.run replacement that consumes ``scripted_exits``.

        Each call corresponds to one ``./gradlew`` invocation. When the
        invocation contains ``runNativeTraceImage``, the script writes the
        next scripted exit code to the sentinel file referenced by the
        ``-PtraceBinaryExitFile=`` argument so the gate's reader returns
        that value.
        """
        calls: list[list[str]] = []
        remaining = list(scripted_exits)

        def _fake(cmd, **kwargs):  # type: ignore[no-untyped-def]
            calls.append(list(cmd))
            stdout = kwargs.get("stdout")
            # Write a synthetic Gradle log if the caller asked us to.
            if hasattr(stdout, "write"):
                stdout.write("BUILD SUCCESSFUL\n")
            if "runNativeTraceImage" in cmd:
                exit_file = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PtraceBinaryExitFile=")),
                    None,
                )
                rc = remaining.pop(0)
                if exit_file:
                    Path(exit_file).parent.mkdir(parents=True, exist_ok=True)
                    Path(exit_file).write_text(str(rc), encoding="utf-8")
                # Gradle-side exit is always 0 (Exec uses ignoreExitValue).
                return subprocess.CompletedProcess(cmd, 0)
            # mergeNativeTraceMetadata or anything else — succeeds.
            return subprocess.CompletedProcess(cmd, 0)

        return _fake, calls

    def test_passes_immediately_when_binary_exits_zero(self) -> None:
        fake, calls = self._fake_run_factory([0])
        with patch("utility_scripts.native_test_verification.subprocess.run", side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        self.assertEqual(result.iterations_used, 1)
        self.assertEqual(result.last_native_test_exit_code, 0)
        # First call is runNativeTraceImage; second is mergeNativeTraceMetadata.
        self.assertTrue(any("runNativeTraceImage" in c for c in calls))

    def test_continues_on_172_until_pass(self) -> None:
        fake, _calls = self._fake_run_factory([172, 172, 0])
        with patch("utility_scripts.native_test_verification.subprocess.run", side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        self.assertEqual(result.iterations_used, 3)
        self.assertEqual(len(result.accepted_run_dirs), 2)

    def test_failed_when_budget_exhausted_with_only_172(self) -> None:
        fake, _calls = self._fake_run_factory([172, 172])
        with patch("utility_scripts.native_test_verification.subprocess.run", side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=2,
            )
        self.assertEqual(result.status, ntv.STATUS_FAILED)
        self.assertEqual(result.iterations_used, 2)
        self.assertEqual(result.last_native_test_exit_code, ntv.MISSING_METADATA_EXIT_CODE)
        self.assertEqual(result.intervention_records, [])

    def test_routes_to_codex_on_non_172_failure(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        with patch(
                "utility_scripts.native_test_verification.subprocess.run",
                side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_codex_metadata_fix",
            return_value=(0, "/tmp/codex.log", False),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        codex_mock.assert_called_once()
        self.assertEqual(len(result.intervention_records), 1)
        self.assertEqual(result.intervention_records[0].kind, "codex")

    def test_failed_when_codex_does_not_converge(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        with patch(
                "utility_scripts.native_test_verification.subprocess.run",
                side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_codex_metadata_fix",
            return_value=(2, "/tmp/codex.log", False),
        ):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_FAILED)


def _rmtree(path: str) -> None:
    import shutil
    shutil.rmtree(path, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
