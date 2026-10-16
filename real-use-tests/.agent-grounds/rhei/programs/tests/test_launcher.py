# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the launcher's worker-agent check.

§FS-real-use-test-generation.1 allows Pi and Claude Code; §AR-real-use-test-launcher
maps the target onto Forge's host gate. A fake host gate records its arguments
and fails, so no test reaches the claim.
"""
from __future__ import annotations

import os
import subprocess
import tempfile
import unittest

LAUNCHER: str = os.path.abspath(os.path.join(
    os.path.dirname(__file__), "..", "..", "..", "..", "run-real-use-tests.sh"))


class LauncherTest(unittest.TestCase):

    def _launch(self, *arguments: str) -> tuple[subprocess.CompletedProcess[str], list[str] | None]:
        temp: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        bin_dir: str = os.path.join(temp.name, "bin")
        os.mkdir(bin_dir)
        host_arguments: str = os.path.join(temp.name, "host-arguments.txt")
        fakes: dict[str, str] = {
            "gh": "#!/usr/bin/env bash\nprintf 'gh version 2.39.0 (fake)\\n'\n",
            "python3": f"#!/usr/bin/env bash\nprintf '%s\\n' \"$@\" > {host_arguments}\nexit 1\n",
            "rhei": "#!/usr/bin/env bash\nexit 0\n",
        }
        for name, source in fakes.items():
            path: str = os.path.join(bin_dir, name)
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(source)
            os.chmod(path, 0o755)
        environment: dict[str, str] = dict(os.environ)
        environment.update({
            "PATH": f"{bin_dir}{os.pathsep}{environment['PATH']}",
            "JAVA_HOME": temp.name,
            "GRAALVM_HOME": temp.name,
        })
        result: subprocess.CompletedProcess[str] = subprocess.run(
            [LAUNCHER, *arguments], env=environment, capture_output=True, text=True, check=False)
        recorded: list[str] | None = None
        if os.path.exists(host_arguments):
            with open(host_arguments, encoding="utf-8") as handle:
                recorded = handle.read().splitlines()
        return result, recorded

    def _agent_arguments(self, recorded: list[str] | None) -> list[str]:
        self.assertIsNotNone(recorded)
        start: int = recorded.index("--analysis-agent")
        return recorded[start:]

    def test_default_target_checks_pi_with_its_provider(self) -> None:
        result, recorded = self._launch()

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual(
            ["--analysis-agent", "pi", "--analysis-family", "pi", "--analysis-model", "gpt-5.6-luna",
             "--analysis-provider", "openai-codex"],
            self._agent_arguments(recorded))

    def test_claude_code_target_checks_the_claude_executable_without_a_provider(self) -> None:
        result, recorded = self._launch("--worker-agent", "claude-code[high]:opus")

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual(
            ["--analysis-agent", "claude", "--analysis-family", "claude-code", "--analysis-model", "opus"],
            self._agent_arguments(recorded))

    def test_other_agents_are_rejected_before_the_host_gate(self) -> None:
        result, recorded = self._launch("--worker-agent", "codex[high]:gpt-5.6-luna")

        self.assertEqual(2, result.returncode)
        self.assertIn("the agent must be pi or claude-code", result.stderr)
        self.assertIsNone(recorded)


if __name__ == "__main__":
    unittest.main()
