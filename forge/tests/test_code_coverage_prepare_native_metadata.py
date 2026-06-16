# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts import code_coverage_prepare_native_metadata as prepare_module


class _Gradle:
    """Stateful fake for run_gradle_test_command driven by scripted nativeTest results."""

    def __init__(self, native_results: list[bool]) -> None:
        self.native_results = list(native_results)
        self.commands: list[str] = []

    def __call__(self, command: str, working_dir: str, library: str | None = None) -> str:
        self.commands.append(command)
        if "generateMetadata" in command:
            return "BUILD SUCCESSFUL in 1s"
        if "nativeTest" in command:
            succeeded = self.native_results.pop(0) if self.native_results else False
            return "BUILD SUCCESSFUL in 9s" if succeeded else "BUILD FAILED\nmissing metadata"
        return "BUILD SUCCESSFUL"


class PrepareNativeMetadataTests(unittest.TestCase):
    def _run(self, native_results: list[bool], max_fix_passes: int = 2):
        gradle = _Gradle(native_results)
        fix_calls: list[tuple] = []

        def fake_fix(repo, coordinate, reproduction_command=None, **kwargs):
            fix_calls.append((repo, coordinate, reproduction_command))
            return (0, "/tmp/codex.log", False)

        with tempfile.TemporaryDirectory() as tmp, \
                patch.object(prepare_module, "run_gradle_test_command", gradle), \
                patch.object(prepare_module, "run_codex_metadata_fix", fake_fix):
            report = prepare_module.prepare_native_metadata(
                repo_path=tmp, coordinate="g:a:1.0", output_dir=os.path.join(tmp, "out"),
                max_fix_passes=max_fix_passes, skip_gradle=False,
            )
            artifact = os.path.isfile(os.path.join(tmp, "out", "native-metadata-prepare.json"))
        return report, gradle, fix_calls, artifact

    def test_passes_without_fix(self) -> None:
        report, _gradle, fix_calls, artifact = self._run(native_results=[True])
        self.assertTrue(report["metadataGenerated"])
        self.assertTrue(report["nativeTestPassed"])
        self.assertEqual(report["fixPasses"], 0)
        self.assertFalse(report["needsHumanIntervention"])
        self.assertEqual(fix_calls, [])
        self.assertTrue(artifact)

    def test_codex_fix_then_pass(self) -> None:
        report, _gradle, fix_calls, _artifact = self._run(native_results=[False, True])
        self.assertEqual(report["fixPasses"], 1)
        self.assertTrue(report["nativeTestPassed"])
        self.assertFalse(report["needsHumanIntervention"])
        self.assertEqual(len(fix_calls), 1)
        # The Codex fix is given the nativeTest reproduction command.
        self.assertIn("nativeTest", fix_calls[0][2])

    def test_exhausts_budget_routes_to_human(self) -> None:
        report, _gradle, fix_calls, _artifact = self._run(native_results=[False, False, False], max_fix_passes=2)
        self.assertEqual(report["fixPasses"], 2)
        self.assertFalse(report["nativeTestPassed"])
        self.assertTrue(report["needsHumanIntervention"])
        self.assertEqual(len(fix_calls), 2)


if __name__ == "__main__":
    unittest.main()
