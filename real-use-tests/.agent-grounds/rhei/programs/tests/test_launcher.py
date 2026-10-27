# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the launcher's worker-agent and scenario-pass checks and its review calls.

§FS-real-use-test-generation.1 allows Pi and Claude Code; §AR-real-use-test-launcher
maps the target onto Forge's host gate. A fake host gate records its arguments
and fails, so those tests never reach the claim. The review tests run a copy of
the launcher in a temporary tree with no Forge interpreter, so every program it
starts is the fake `python3` and nothing reaches GitHub
(§FS-real-use-test-generation.4).
"""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest

LAUNCHER: str = os.path.abspath(os.path.join(
    os.path.dirname(__file__), "..", "..", "..", "..", "run-real-use-tests.sh"))
HOST_SCRIPT: str = os.path.abspath(os.path.join(
    os.path.dirname(LAUNCHER), "..", "forge", "utility_scripts", "rhei_launcher_host.sh"))

# The fake interpreter for the review tests: the host gate passes `HOST_PASSES` times,
# `claim.py` prints issue 7 while `CLAIMS` is set or fails, and `review.py` records
# its arguments and fails, so the launcher must go on after it.
FAKE_PROGRAMS: str = r"""#!/usr/bin/env bash
count() { local n=0; [[ -f "$1" ]] && n="$(cat "$1")"; echo $((n + 1)) > "$1"; echo $((n + 1)); }
case "$1" in
    */host_requirements.py) (( $(count "$STATE/host") <= HOST_PASSES )) ;;
    */claim.py) [[ -n "$CLAIMS" ]] && echo 7 ;;
    */review.py) printf '%s\n' "${@:2}" > "$STATE/review-$(count "$STATE/reviews")"; exit 1 ;;
    *) exit 99 ;;
esac
"""


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

    def test_a_scenario_pass_count_keeps_the_agent_check(self) -> None:
        result, recorded = self._launch("--scenario-passes", "20", "--worker-agent", "claude-code[high]:opus")

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual(
            ["--analysis-agent", "claude", "--analysis-family", "claude-code", "--analysis-model", "opus"],
            self._agent_arguments(recorded))

    def test_a_count_that_is_not_positive_is_rejected_before_the_host_gate(self) -> None:
        for count in ("0", "-3", "ten"):
            result, recorded = self._launch("--scenario-passes", count)

            self.assertEqual(2, result.returncode, count)
            self.assertIn("the count must be a positive integer", result.stderr)
            self.assertIsNone(recorded)

    def test_other_agents_are_rejected_before_the_host_gate(self) -> None:
        result, recorded = self._launch("--worker-agent", "codex[high]:gpt-5.6-luna")

        self.assertEqual(2, result.returncode)
        self.assertIn("the agent must be pi or claude-code", result.stderr)
        self.assertIsNone(recorded)

    def _launch_copy(self, host_passes: int, claims: bool) -> tuple[subprocess.CompletedProcess[str], str, str]:
        """Run a copy of the launcher; return the result, its state directory, and its root."""
        temp: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root: str = os.path.join(temp.name, "repo")
        os.makedirs(os.path.join(root, "real-use-tests"))
        os.makedirs(os.path.join(root, "forge", "utility_scripts"))
        shutil.copy(LAUNCHER, os.path.join(root, "real-use-tests", "run-real-use-tests.sh"))
        shutil.copy(HOST_SCRIPT, os.path.join(root, "forge", "utility_scripts", "rhei_launcher_host.sh"))
        state: str = os.path.join(temp.name, "state")
        bin_dir: str = os.path.join(temp.name, "bin")
        os.mkdir(state)
        os.mkdir(bin_dir)
        fakes: dict[str, str] = {
            "gh": "#!/usr/bin/env bash\nprintf 'gh version 2.39.0 (fake)\\n'\n",
            "python3": FAKE_PROGRAMS,
            "rhei": "#!/usr/bin/env bash\nexit 0\n",
            "sleep": "#!/usr/bin/env bash\nexit 7\n",
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
            "STATE": state,
            "HOST_PASSES": str(host_passes),
            "CLAIMS": "yes" if claims else "",
        })
        result: subprocess.CompletedProcess[str] = subprocess.run(
            [os.path.join(root, "real-use-tests", "run-real-use-tests.sh"), "--worker-agent", "claude-code:opus"],
            env=environment, capture_output=True, text=True, check=False)
        return result, state, root

    def _reviews(self, state: str) -> list[list[str]]:
        names: list[str] = sorted(name for name in os.listdir(state) if name.startswith("review-"))
        reviews: list[list[str]] = []
        for name in names:
            with open(os.path.join(state, name), encoding="utf-8") as handle:
                reviews.append(handle.read().splitlines())
        return reviews

    def test_an_empty_queue_reviews_without_branches_and_goes_on_after_a_failed_review(self) -> None:
        result, state, root = self._launch_copy(host_passes=1, claims=False)

        self.assertEqual(7, result.returncode, result.stderr)
        self.assertEqual([["--repo", root, "--worker-agent", "claude-code:opus"]], self._reviews(state))
        self.assertIn("WARNING: The review failed", result.stderr)
        self.assertIn("No issue to claim", result.stdout)

    def test_every_third_run_reviews_without_branches(self) -> None:
        result, state, root = self._launch_copy(host_passes=4, claims=True)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual(4, result.stdout.count("Claimed real-use-test issue #7."))
        self.assertEqual([["--repo", root, "--worker-agent", "claude-code:opus"]], self._reviews(state))


if __name__ == "__main__":
    unittest.main()
