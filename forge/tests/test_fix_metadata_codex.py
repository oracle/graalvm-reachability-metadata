# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from ai_workflows.core.fix_metadata_codex import run_codex_metadata_fix  # noqa: E402
from utility_scripts.host_requirements import GRAALVM_SCHEMA_PATH  # noqa: E402


class FixMetadataCodexTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repo = tempfile.mkdtemp(prefix="repo-")
        self.addCleanup(shutil.rmtree, self.repo, ignore_errors=True)
        _make_complete_reachability_repo(self.repo)
        self.graalvm_home = tempfile.mkdtemp(prefix="graalvm-")
        self.addCleanup(shutil.rmtree, self.graalvm_home, ignore_errors=True)
        _make_forge_usable_graalvm(self.graalvm_home)

    def test_pins_codex_environment_and_instructions_to_resolved_graalvm(self) -> None:
        calls: list[tuple[list[str], dict]] = []

        def fake_run(cmd, **kwargs):  # type: ignore[no-untyped-def]
            calls.append((list(cmd), kwargs))
            if cmd[:2] == ["git", "rev-parse"]:
                return subprocess.CompletedProcess(cmd, 0, stdout=self.repo + "\n")
            if cmd[0].endswith("native-image"):
                return subprocess.CompletedProcess(
                    cmd,
                    0,
                    stdout=(
                        "native-image 25.0.2 2026-01-20\n"
                        "GraalVM Runtime Environment Oracle GraalVM 25.1.0-dev+10.1 "
                        "(build 25.0.2+10-LTS-jvmci-25.1-b17)\n"
                    ),
                )
            return subprocess.CompletedProcess(cmd, 0)

        with patch.dict(os.environ, {"GRAALVM_HOME": self.graalvm_home, "JAVA_HOME": "/other-jdk"}, clear=True), \
                patch("ai_workflows.core.fix_metadata_codex.subprocess.run", side_effect=fake_run):
            rc, _log_path, timed_out = run_codex_metadata_fix(self.repo, "g:a:1.0")

        self.assertEqual(rc, 0)
        self.assertFalse(timed_out)
        codex_cmd, codex_kwargs = calls[-1]
        self.assertEqual(codex_cmd[:2], ["codex", "exec"])
        self.assertEqual(codex_kwargs["env"]["GRAALVM_HOME"], self.graalvm_home)
        self.assertEqual(codex_kwargs["env"]["JAVA_HOME"], self.graalvm_home)
        developer_instructions = _developer_instructions_from(codex_cmd)
        self.assertIn(self.graalvm_home, developer_instructions)
        self.assertIn("jvmci-25.1-b17", developer_instructions)
        self.assertIn("GRAALVM_HOME=" + self.graalvm_home, codex_cmd[-1])
        self.assertIn("jvmci-25.1-b17", codex_cmd[-1])

    def test_explicit_graalvm_home_overrides_base_environment_for_codex(self) -> None:
        inherited_graalvm = tempfile.mkdtemp(prefix="inherited-graalvm-")
        self.addCleanup(shutil.rmtree, inherited_graalvm, ignore_errors=True)
        _make_forge_usable_graalvm(inherited_graalvm)
        calls: list[tuple[list[str], dict]] = []

        def fake_run(cmd, **kwargs):  # type: ignore[no-untyped-def]
            calls.append((list(cmd), kwargs))
            if cmd[:2] == ["git", "rev-parse"]:
                return subprocess.CompletedProcess(cmd, 0, stdout=self.repo + "\n")
            if cmd[0].endswith("native-image"):
                return subprocess.CompletedProcess(cmd, 0, stdout="native-image pinned\n")
            return subprocess.CompletedProcess(cmd, 0)

        with patch("ai_workflows.core.fix_metadata_codex.subprocess.run", side_effect=fake_run):
            rc, _log_path, timed_out = run_codex_metadata_fix(
                self.repo,
                "g:a:1.0",
                graalvm_home=self.graalvm_home,
                base_env={"GRAALVM_HOME": inherited_graalvm, "JAVA_HOME": inherited_graalvm},
            )

        self.assertEqual(rc, 0)
        self.assertFalse(timed_out)
        codex_env = calls[-1][1]["env"]
        self.assertEqual(codex_env["GRAALVM_HOME"], self.graalvm_home)
        self.assertEqual(codex_env["JAVA_HOME"], self.graalvm_home)


def _developer_instructions_from(cmd: list[str]) -> str:
    for index, part in enumerate(cmd):
        if part == "-c" and cmd[index + 1].startswith("developer_instructions="):
            return json.loads(cmd[index + 1].split("=", 1)[1])
    raise AssertionError(f"developer_instructions not found in command: {cmd}")


def _make_forge_usable_graalvm(path: str) -> None:
    """Create a GraalVM home that satisfies `check_graalvm_installation`."""
    os.makedirs(os.path.join(path, "bin"), exist_ok=True)
    for executable in ("java", "native-image"):
        executable_path = Path(path, "bin", executable)
        executable_path.write_text("#!/usr/bin/env sh\n", encoding="utf-8")
        executable_path.chmod(0o755)
    schema_path = Path(path, GRAALVM_SCHEMA_PATH)
    schema_path.parent.mkdir(parents=True, exist_ok=True)
    schema_path.write_text("{}\n", encoding="utf-8")


def _make_complete_reachability_repo(path: str) -> None:
    subprocess.run(["git", "init", "-b", "master"], cwd=path, check=True, stdout=subprocess.PIPE)
    for directory in ("forge", "metadata", "tests", os.path.join("gradle", "wrapper")):
        os.makedirs(os.path.join(path, directory), exist_ok=True)
    Path(path, "gradlew").write_text("#!/usr/bin/env sh\n", encoding="utf-8")
    Path(path, "settings.gradle").write_text("rootProject.name = 'test'\n", encoding="utf-8")
    Path(path, "build.gradle").write_text("plugins { id 'java' }\n", encoding="utf-8")
    Path(path, "gradle", "wrapper", "gradle-wrapper.jar").write_text("wrapper jar\n", encoding="utf-8")
    Path(path, "gradle", "wrapper", "gradle-wrapper.properties").write_text(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-bin.zip\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
