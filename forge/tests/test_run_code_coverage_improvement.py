# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


FORGE_ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = FORGE_ROOT / "run-code-coverage-improvement.sh"


def _write_executable(path: Path, source: str) -> None:
    path.write_text(source, encoding="utf-8")
    path.chmod(0o755)


class RunCodeCoverageImprovementTests(unittest.TestCase):
    def _run_launcher(
            self,
            issue_number: str,
            *arguments: str,
    ) -> tuple[subprocess.CompletedProcess[str], Path, Path]:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        temp_path = Path(temp_dir.name)
        bin_path = temp_path / "bin"
        bin_path.mkdir()
        gh_arguments_path = temp_path / "gh-arguments.txt"
        rhei_invocation_path = temp_path / "rhei-invocation.txt"

        _write_executable(
            bin_path / "gh",
            """#!/usr/bin/env bash
printf '%s\\n' "$@" > "$FAKE_GH_ARGUMENTS"
printf '%s\\n' "$FAKE_ISSUE_NUMBER"
""",
        )
        _write_executable(
            bin_path / "rhei",
            """#!/usr/bin/env bash
pwd > "$FAKE_RHEI_INVOCATION"
printf '%s\\n' "$@" >> "$FAKE_RHEI_INVOCATION"
""",
        )

        environment = os.environ.copy()
        environment.update({
            "FAKE_GH_ARGUMENTS": str(gh_arguments_path),
            "FAKE_ISSUE_NUMBER": issue_number,
            "FAKE_RHEI_INVOCATION": str(rhei_invocation_path),
            "PATH": f"{bin_path}{os.pathsep}{environment['PATH']}",
        })
        result = subprocess.run(
            [str(LAUNCHER), *arguments],
            cwd=temp_path,
            env=environment,
            check=False,
            capture_output=True,
            text=True,
        )
        return result, gh_arguments_path, rhei_invocation_path

    def test_selects_issue_and_executes_with_dashboard(self) -> None:
        result, gh_arguments_path, rhei_invocation_path = self._run_launcher("9460")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Selected code-coverage-improvement issue #9460.", result.stdout)
        gh_arguments = gh_arguments_path.read_text(encoding="utf-8").splitlines()
        self.assertIn("code-coverage-improvement", gh_arguments)
        self.assertIn("no:assignee -is:blocked", gh_arguments)
        self.assertIn("number,labels,projectItems", gh_arguments)
        invocation = rhei_invocation_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(str(FORGE_ROOT), invocation[0])
        self.assertEqual([
            "instantiate",
            "code-coverage-improvement",
            "issue_number=9460",
            "--output",
            "code-coverage-9460",
            "--execute",
            "--",
            "--dashboard",
        ], invocation[1:])

    def test_forwards_rhei_run_options_after_dashboard(self) -> None:
        result, _, rhei_invocation_path = self._run_launcher(
            "9459",
            "--",
            "--parallel",
            "2",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        invocation = rhei_invocation_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(["--dashboard", "--parallel", "2"], invocation[-3:])

    def test_fails_before_rhei_when_no_issue_is_eligible(self) -> None:
        result, _, rhei_invocation_path = self._run_launcher("null")

        self.assertEqual(1, result.returncode)
        self.assertIn("No eligible code-coverage-improvement issue", result.stderr)
        self.assertFalse(rhei_invocation_path.exists())


if __name__ == "__main__":
    unittest.main()
