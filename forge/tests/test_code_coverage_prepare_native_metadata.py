# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from contextlib import redirect_stderr
import io
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts import code_coverage_prepare_native_metadata as prepare_module


class _Gradle:
    """Stateful fake for run_gradle_test_command driven by scripted nativeTest results."""

    def __init__(
            self,
            native_results: list[bool],
            generate_succeeded: bool = True,
    ) -> None:
        self.native_results = list(native_results)
        self.generate_succeeded = generate_succeeded
        self.commands: list[str] = []

    def __call__(self, command: str, working_dir: str, library: str | None = None) -> str:
        self.commands.append(command)
        if "generateMetadata" in command:
            return (
                "BUILD SUCCESSFUL in 1s"
                if self.generate_succeeded
                else "BUILD FAILED\nmetadata generation failed"
            )
        if "nativeTest" in command:
            succeeded = self.native_results.pop(0) if self.native_results else False
            return "BUILD SUCCESSFUL in 9s" if succeeded else "BUILD FAILED\nmissing metadata"
        return "BUILD SUCCESSFUL"


class PrepareNativeMetadataTests(unittest.TestCase):
    def _run(
            self,
            native_results: list[bool],
            max_fix_passes: int = 2,
            generate_succeeded: bool = True,
    ) -> tuple[dict, _Gradle, list[tuple], bool, str]:
        gradle = _Gradle(native_results, generate_succeeded)
        fix_calls: list[tuple] = []

        def fake_fix(
                repo: str,
                coordinate: str,
                reproduction_command: str | None = None,
                **kwargs: object,
        ) -> tuple[int, str, bool]:
            fix_calls.append((repo, coordinate, reproduction_command))
            return (0, "/tmp/codex.log", False)

        with tempfile.TemporaryDirectory() as tmp, \
                patch.object(prepare_module, "run_gradle_test_command", gradle), \
                patch.object(prepare_module, "run_codex_metadata_fix", fake_fix):
            relative_suite: str = os.path.join(
                "tests", "g", "a", "1.0", "code-coverage",
            )
            suite_path: str = os.path.join(tmp, relative_suite)
            os.makedirs(os.path.join(suite_path, "src", "test", "java"))
            report: dict = prepare_module.prepare_native_metadata(
                repo_path=tmp, coordinate="g:a:1.0", coverage_suite=relative_suite,
                output_dir=os.path.join(tmp, "out"),
                max_fix_passes=max_fix_passes, skip_gradle=False,
            )
            artifact: bool = os.path.isfile(
                os.path.join(tmp, "out", "native-metadata-prepare.json")
            )
        return report, gradle, fix_calls, artifact, suite_path

    def test_passes_without_fix(self) -> None:
        report, gradle, fix_calls, artifact, suite_path = self._run(native_results=[True])
        self.assertTrue(report["metadataGenerated"])
        self.assertTrue(report["nativeTestPassed"])
        self.assertEqual(report["fixPasses"], 0)
        self.assertFalse(report["needsHumanIntervention"])
        self.assertEqual(report["coverageSuite"], suite_path)
        self.assertEqual(fix_calls, [])
        self.assertTrue(artifact)
        self.assertEqual(len(gradle.commands), 2)
        for command in gradle.commands:
            self.assertIn(f"-PcodeCoverageSuitePath={suite_path}", command)

    def test_codex_fix_then_pass(self) -> None:
        report, _gradle, fix_calls, _artifact, suite_path = self._run(
            native_results=[False, True],
        )
        self.assertEqual(report["fixPasses"], 1)
        self.assertTrue(report["nativeTestPassed"])
        self.assertFalse(report["needsHumanIntervention"])
        self.assertEqual(len(fix_calls), 1)
        # The Codex fix is given the nativeTest reproduction command.
        self.assertIn("nativeTest", fix_calls[0][2])
        self.assertIn(f"-PcodeCoverageSuitePath={suite_path}", fix_calls[0][2])

    def test_exhausts_budget_routes_to_human(self) -> None:
        report, _gradle, fix_calls, _artifact, _suite_path = self._run(
            native_results=[False, False, False],
            max_fix_passes=2,
        )
        self.assertEqual(report["fixPasses"], 2)
        self.assertFalse(report["nativeTestPassed"])
        self.assertTrue(report["needsHumanIntervention"])
        self.assertEqual(len(fix_calls), 2)

    def test_generation_failure_stops_before_native_validation(self) -> None:
        report, gradle, fix_calls, artifact, _suite_path = self._run(
            native_results=[True],
            generate_succeeded=False,
        )

        self.assertFalse(report["metadataGenerated"])
        self.assertFalse(report["nativeTestPassed"])
        self.assertTrue(report["needsHumanIntervention"])
        self.assertEqual(report["fixPasses"], 0)
        self.assertIn("stale metadata", report["failureReason"])
        self.assertEqual(len(gradle.commands), 1)
        self.assertIn("generateMetadata", gradle.commands[0])
        self.assertEqual(fix_calls, [])
        self.assertTrue(artifact)

    def test_rejects_missing_or_malformed_coverage_suite(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            missing = os.path.join(tmp, "missing-suite")
            with self.assertRaisesRegex(
                    prepare_module.NativeMetadataPreparationError,
                    "does not exist",
            ):
                prepare_module.prepare_native_metadata(
                    repo_path=tmp,
                    coordinate="g:a:1.0",
                    coverage_suite=missing,
                    output_dir=os.path.join(tmp, "out"),
                    max_fix_passes=0,
                    skip_gradle=True,
                )

            incomplete = os.path.join(tmp, "incomplete-suite")
            os.makedirs(incomplete)
            with self.assertRaisesRegex(
                    prepare_module.NativeMetadataPreparationError,
                    "src/test/java",
            ):
                prepare_module.prepare_native_metadata(
                    repo_path=tmp,
                    coordinate="g:a:1.0",
                    coverage_suite=incomplete,
                    output_dir=os.path.join(tmp, "out"),
                    max_fix_passes=0,
                    skip_gradle=True,
                )

    def test_rejects_invalid_coordinate_and_fix_budget(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            suite_path = os.path.join(tmp, "suite")
            os.makedirs(os.path.join(suite_path, "src", "test", "java"))
            with self.assertRaisesRegex(
                    prepare_module.NativeMetadataPreparationError,
                    "group:artifact:version",
            ):
                prepare_module.prepare_native_metadata(
                    repo_path=tmp,
                    coordinate="g:a",
                    coverage_suite=suite_path,
                    output_dir=os.path.join(tmp, "out"),
                    max_fix_passes=0,
                    skip_gradle=True,
                )
            with self.assertRaisesRegex(
                    prepare_module.NativeMetadataPreparationError,
                    "non-negative integer",
            ):
                prepare_module.prepare_native_metadata(
                    repo_path=tmp,
                    coordinate="g:a:1.0",
                    coverage_suite=suite_path,
                    output_dir=os.path.join(tmp, "out"),
                    max_fix_passes=-1,
                    skip_gradle=True,
                )

    def test_cli_reports_invalid_suite_as_clear_error(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            stderr = io.StringIO()
            argv = [
                "code_coverage_prepare_native_metadata.py",
                "--repo-path", tmp,
                "--coordinate", "g:a:1.0",
                "--coverage-suite", "missing-suite",
                "--output-dir", os.path.join(tmp, "out"),
                "--skip-gradle",
            ]
            with patch.object(sys, "argv", argv), redirect_stderr(stderr):
                with self.assertRaisesRegex(SystemExit, "2"):
                    prepare_module.main()
            self.assertTrue(stderr.getvalue().startswith("ERROR: "))


if __name__ == "__main__":
    unittest.main()
