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
EXISTING_COORDINATE = "org.apache.kafka:kafka-streams:3.6.0"


def _write_executable(path: Path, source: str) -> None:
    path.write_text(source, encoding="utf-8")
    path.chmod(0o755)


class RunCodeCoverageImprovementTests(unittest.TestCase):
    def _run_launcher(
            self,
            issue_number: str,
            *arguments: str,
            gh_version: str = "2.24.0",
            host_exit: int = 0,
            issue_title: str = f"Improve coverage for {EXISTING_COORDINATE}",
    ) -> tuple[
        subprocess.CompletedProcess[str],
        Path,
        Path,
        Path,
        Path,
    ]:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        temp_path = Path(temp_dir.name)
        bin_path = temp_path / "bin"
        bin_path.mkdir()
        gh_arguments_path = temp_path / "gh-arguments.txt"
        rhei_invocation_path = temp_path / "rhei-invocation.txt"
        host_arguments_path = temp_path / "host-arguments.txt"
        events_path = temp_path / "events.txt"

        _write_executable(
            bin_path / "gh",
            """#!/usr/bin/env bash
if [[ "${1:-}" == "version" || "${1:-}" == "--version" ]]; then
    printf 'gh version %s (fake)\n' "$FAKE_GH_VERSION"
    exit 0
fi
printf '%s\n' "$@" >> "$FAKE_GH_ARGUMENTS"
if [[ "${1:-} ${2:-}" == "issue list" ]]; then
    printf 'gh-issue-list\n' >> "$FAKE_EVENTS"
    printf '%s\n' "$FAKE_ISSUE_NUMBER"
    exit 0
fi
if [[ "${1:-} ${2:-}" == "issue view" ]]; then
    printf 'gh-issue-view\n' >> "$FAKE_EVENTS"
    printf '%s\n' "$FAKE_ISSUE_TITLE"
    exit 0
fi
exit 2
""".replace("\\$", "$"),
        )
        _write_executable(
            bin_path / "python3",
            """#!/usr/bin/env bash
printf '%s\n' "$@" > "$FAKE_HOST_ARGUMENTS"
printf 'host-requirements\n' >> "$FAKE_EVENTS"
exit "$FAKE_HOST_EXIT"
""",
        )
        _write_executable(
            bin_path / "rhei",
            """#!/usr/bin/env bash
printf 'rhei\n' >> "$FAKE_EVENTS"
pwd > "$FAKE_RHEI_INVOCATION"
printf '%s\n' "$@" >> "$FAKE_RHEI_INVOCATION"
""",
        )

        environment = os.environ.copy()
        environment.update({
            "FAKE_EVENTS": str(events_path),
            "FAKE_GH_ARGUMENTS": str(gh_arguments_path),
            "FAKE_GH_VERSION": gh_version,
            "FAKE_HOST_ARGUMENTS": str(host_arguments_path),
            "FAKE_HOST_EXIT": str(host_exit),
            "FAKE_ISSUE_NUMBER": issue_number,
            "FAKE_ISSUE_TITLE": issue_title,
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
        return (
            result,
            gh_arguments_path,
            rhei_invocation_path,
            host_arguments_path,
            events_path,
        )

    def test_validates_issue_before_rhei_and_passes_coordinate_to_workspace(self) -> None:
        result, gh_path, rhei_path, host_path, events_path = self._run_launcher("9460")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Validating code-coverage host requirements.", result.stdout)
        self.assertIn(
            f"Selected code-coverage-improvement issue #9460 for {EXISTING_COORDINATE}.",
            result.stdout,
        )

        host_arguments = host_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            str(FORGE_ROOT / "utility_scripts" / "host_requirements.py"),
            host_arguments[0],
        )
        self.assertIn("--mode", host_arguments)
        self.assertIn("coverage", host_arguments)
        self.assertIn("gpt-5.6-luna", host_arguments)

        gh_arguments = gh_path.read_text(encoding="utf-8").splitlines()
        self.assertIn("list", gh_arguments)
        self.assertIn("view", gh_arguments)
        self.assertIn("number,labels,projectItems", gh_arguments)
        self.assertIn("title", gh_arguments)

        invocation = rhei_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(str(FORGE_ROOT), invocation[0])
        self.assertEqual([
            "instantiate",
            "code-coverage-improvement",
            "issue_number=9460",
            f"coordinate={EXISTING_COORDINATE}",
            "--output",
            "code-coverage-9460",
            "--execute",
            "--",
            "--dashboard",
        ], invocation[1:])
        self.assertEqual(
            ["host-requirements", "gh-issue-list", "gh-issue-view", "rhei"],
            events_path.read_text(encoding="utf-8").splitlines(),
        )

    def test_rejects_missing_exact_version_project_before_rhei(self) -> None:
        missing_coordinate = "org.apache.kafka:kafka-streams:3.6.2"
        result, _, rhei_path, _, events_path = self._run_launcher(
            "9460",
            issue_title=f"Improve coverage for {missing_coordinate}",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(f"{missing_coordinate}, from the title", result.stderr)
        self.assertIn(
            "Expected directory: tests/src/org.apache.kafka/kafka-streams/3.6.2",
            result.stderr,
        )
        self.assertIn("3.6.0", result.stderr)
        self.assertFalse(rhei_path.exists())
        self.assertEqual(
            ["host-requirements", "gh-issue-list", "gh-issue-view"],
            events_path.read_text(encoding="utf-8").splitlines(),
        )

    def test_rejects_ambiguous_title_before_rhei(self) -> None:
        result, _, rhei_path, _, _ = self._run_launcher(
            "9460",
            issue_title=(
                "Compare org.apache.kafka:kafka-streams:3.6.0 with "
                "org.apache.kafka:kafka-streams:3.5.1"
            ),
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("names 2 coordinates; expected exactly one", result.stderr)
        self.assertFalse(rhei_path.exists())

    def test_rejects_gh_before_project_items_support(self) -> None:
        result, gh_path, rhei_path, host_path, events_path = self._run_launcher(
            "9460",
            gh_version="2.23.0",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("gh 2.23.0 is too old", result.stderr)
        self.assertIn("gh 2.24.0 or newer", result.stderr)
        self.assertFalse(gh_path.exists())
        self.assertFalse(host_path.exists())
        self.assertFalse(rhei_path.exists())
        self.assertFalse(events_path.exists())

    def test_host_failure_stops_before_issue_selection(self) -> None:
        result, gh_path, rhei_path, _, events_path = self._run_launcher(
            "9460",
            host_exit=1,
        )

        self.assertEqual(1, result.returncode)
        self.assertFalse(gh_path.exists())
        self.assertFalse(rhei_path.exists())
        self.assertEqual(
            ["host-requirements"],
            events_path.read_text(encoding="utf-8").splitlines(),
        )

    def test_forwards_rhei_run_options_after_dashboard(self) -> None:
        result, _, rhei_path, _, _ = self._run_launcher(
            "9459",
            "--",
            "--parallel",
            "2",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        invocation = rhei_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(["--dashboard", "--parallel", "2"], invocation[-3:])

    def test_fails_before_rhei_when_no_issue_is_eligible(self) -> None:
        result, _, rhei_path, _, events_path = self._run_launcher("null")

        self.assertEqual(1, result.returncode)
        self.assertIn("No eligible code-coverage-improvement issue", result.stderr)
        self.assertFalse(rhei_path.exists())
        self.assertEqual(
            ["host-requirements", "gh-issue-list"],
            events_path.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
