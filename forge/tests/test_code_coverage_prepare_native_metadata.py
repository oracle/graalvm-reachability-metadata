# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from contextlib import redirect_stderr
import io
import json
import os
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts import code_coverage_prepare_native_metadata as prepare_module
from utility_scripts.native_test_verification import (
    InterventionRecord,
    NativeTestVerificationResult,
    STATUS_FAILED,
    STATUS_PASSED,
    STATUS_PASSED_WITH_INTERVENTION,
)


def _gate_result(status: str, iterations: int = 3, interventions: int = 0) -> NativeTestVerificationResult:
    return NativeTestVerificationResult(
        status=status,
        output_dir="/tmp/gate",
        iterations_used=iterations,
        last_native_test_log_path="/tmp/gate/logs/last.log",
        intervention_records=[
            InterventionRecord(stage=f"cycle-{index}-analysis-agent", kind="codex", log_path="/tmp/fix.log")
            for index in range(interventions)
        ],
        failure_detail="gate failed" if status == STATUS_FAILED else None,
    )


class PrepareNativeMetadataTests(unittest.TestCase):
    """The helper is a thin program around the shared native trace gate."""

    def setUp(self) -> None:
        self.workdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.workdir.cleanup)
        self.repo = os.path.join(self.workdir.name, "repo")
        self.suite = os.path.join(self.repo, "tests", "src", "g", "a", "1.0", "code-coverage-improvement")
        os.makedirs(os.path.join(self.suite, "src", "test", "java"))
        self.output = os.path.join(self.workdir.name, "prepare")

    def _prepare(self, gate: NativeTestVerificationResult) -> tuple[dict, dict]:
        with patch.object(prepare_module, "verify_native_test_passes", return_value=gate) as mock:
            report = prepare_module.prepare_native_metadata(
                repo_path=self.repo,
                coordinate="g:a:1.0",
                coverage_suite=self.suite,
                output_dir=self.output,
                max_cycles=40,
                skip_gradle=False,
            )
        return report, mock.call_args.kwargs

    def test_gate_pass_reports_success_and_writes_artifacts(self) -> None:
        report, kwargs = self._prepare(_gate_result(STATUS_PASSED))
        self.assertTrue(report["nativeTestPassed"])
        self.assertFalse(report["needsHumanIntervention"])
        self.assertEqual(report["gateStatus"], STATUS_PASSED)
        for name in ("native-metadata-prepare.json", "native-metadata-prepare.md"):
            self.assertTrue(os.path.isfile(os.path.join(self.output, name)))
        written = json.load(open(os.path.join(self.output, "native-metadata-prepare.json")))
        self.assertEqual(written["gateStatus"], STATUS_PASSED)

    def test_gate_receives_the_coverage_suite_property(self) -> None:
        """The suite must ride on every gate command (§FS-native-test-verification-gate.2)."""
        _report, kwargs = self._prepare(_gate_result(STATUS_PASSED))
        self.assertEqual(kwargs["gradle_properties"], (prepare_module.COVERAGE_SUITE_PROPERTY,))
        self.assertEqual(kwargs["coordinate"], "g:a:1.0")
        self.assertEqual(kwargs["max_iterations"], 40)

    def test_terminal_repair_still_counts_as_success(self) -> None:
        report, _ = self._prepare(_gate_result(STATUS_PASSED_WITH_INTERVENTION, interventions=1))
        self.assertTrue(report["nativeTestPassed"])
        self.assertEqual(report["agentInterventions"], 1)
        self.assertFalse(report["needsHumanIntervention"])

    def test_gate_failure_routes_to_human(self) -> None:
        report, _ = self._prepare(_gate_result(STATUS_FAILED, interventions=1))
        self.assertFalse(report["nativeTestPassed"])
        self.assertTrue(report["needsHumanIntervention"])
        self.assertEqual(report["failureReason"], "gate failed")

    def test_skip_gradle_writes_a_noop_report_without_the_gate(self) -> None:
        with patch.object(prepare_module, "verify_native_test_passes") as mock:
            report = prepare_module.prepare_native_metadata(
                repo_path=self.repo,
                coordinate="g:a:1.0",
                coverage_suite=self.suite,
                output_dir=self.output,
                max_cycles=40,
                skip_gradle=True,
            )
        mock.assert_not_called()
        self.assertIsNone(report["gateStatus"])
        self.assertTrue(os.path.isfile(os.path.join(self.output, "native-metadata-prepare.json")))

    def test_rejects_missing_or_malformed_coverage_suite(self) -> None:
        for suite in ("", "   ", os.path.join(self.workdir.name, "absent")):
            with self.assertRaises(prepare_module.NativeMetadataPreparationError):
                prepare_module.prepare_native_metadata(
                    repo_path=self.repo,
                    coordinate="g:a:1.0",
                    coverage_suite=suite,
                    output_dir=self.output,
                    max_cycles=40,
                    skip_gradle=True,
                )
        no_java = os.path.join(self.workdir.name, "no-java-suite")
        os.makedirs(no_java)
        with self.assertRaises(prepare_module.NativeMetadataPreparationError):
            prepare_module.prepare_native_metadata(
                repo_path=self.repo,
                coordinate="g:a:1.0",
                coverage_suite=no_java,
                output_dir=self.output,
                max_cycles=40,
                skip_gradle=True,
            )

    def test_rejects_invalid_coordinate_and_cycle_budget(self) -> None:
        for coordinate in ("", "g:a", "g:a:1 .0", "g::1.0"):
            with self.assertRaises(prepare_module.NativeMetadataPreparationError):
                prepare_module.prepare_native_metadata(
                    repo_path=self.repo,
                    coordinate=coordinate,
                    coverage_suite=self.suite,
                    output_dir=self.output,
                    max_cycles=40,
                    skip_gradle=True,
                )
        for cycles in (0, -1, True, "3"):
            with self.assertRaises(prepare_module.NativeMetadataPreparationError):
                prepare_module.prepare_native_metadata(
                    repo_path=self.repo,
                    coordinate="g:a:1.0",
                    coverage_suite=self.suite,
                    output_dir=self.output,
                    max_cycles=cycles,
                    skip_gradle=True,
                )

    def test_cli_exit_codes_track_the_gate(self) -> None:
        argv = [
            "prog",
            "--repo-path", self.repo,
            "--coordinate", "g:a:1.0",
            "--coverage-suite", self.suite,
            "--output-dir", self.output,
        ]
        with patch.object(prepare_module, "verify_native_test_passes", return_value=_gate_result(STATUS_FAILED)):
            with patch("sys.argv", argv), redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as caught:
                    prepare_module.main()
        self.assertEqual(caught.exception.code, 3)
        with patch.object(prepare_module, "verify_native_test_passes", return_value=_gate_result(STATUS_PASSED)):
            with patch("sys.argv", argv):
                prepare_module.main()

    def test_cli_reports_invalid_suite_as_clear_error(self) -> None:
        argv = [
            "prog",
            "--repo-path", self.repo,
            "--coordinate", "g:a:1.0",
            "--coverage-suite", os.path.join(self.workdir.name, "absent"),
            "--output-dir", self.output,
        ]
        stderr = io.StringIO()
        with patch("sys.argv", argv), redirect_stderr(stderr):
            with self.assertRaises(SystemExit) as caught:
                prepare_module.main()
        self.assertEqual(caught.exception.code, 2)
        self.assertIn("Coverage suite directory does not exist", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
