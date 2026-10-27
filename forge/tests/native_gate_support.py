# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared harness for native-test verification gate tests.

Provides the fake ``subprocess.run`` script, the minimal reachability repo
builder, and the common gate-test fixture used by the gate routing and
metadata aggregation test files.
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
if str(_FORGE_ROOT) not in sys.path:
    sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts.run_location import reset_run_location  # noqa: E402

#: The single module whose ``subprocess.run`` executes every gate command.
GATE_SUBPROCESS_RUN = "utility_scripts.native_trace_execution.subprocess.run"


def _command_property(command: str, property_name: str) -> str:
    prefix = f"{property_name}="
    for part in command.split():
        if part.startswith(prefix):
            return part.split("=", 1)[1]
    raise AssertionError(f"{property_name} missing from command: {command}")


def _rmtree(path: str) -> None:
    import shutil
    shutil.rmtree(path, ignore_errors=True)


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


def _write_user_code_filter(
        repo: str,
        coordinate: str,
        rules: list[dict[str, str]],
) -> None:
    group, artifact, version = coordinate.split(":", 2)
    filter_path = Path(
        repo,
        "tests",
        "src",
        group,
        artifact,
        version,
        "user-code-filter.json",
    )
    filter_path.parent.mkdir(parents=True, exist_ok=True)
    filter_path.write_text(json.dumps({"rules": rules}), encoding="utf-8")


class GateHarness(unittest.TestCase):
    """Common fixture for tests that drive ``verify_native_test_passes``.

    Patches subprocess.run so no real Gradle is invoked. Uses a sentinel
    file to feed the binary's exit code back to the gate.
    """

    def setUp(self) -> None:
        verbose = patch.dict(os.environ, {"FORGE_VERBOSE": "1"})
        verbose.start()
        self.addCleanup(verbose.stop)
        reset_run_location()
        self.addCleanup(reset_run_location)
        self.repo = tempfile.mkdtemp(prefix="repo-")
        self.addCleanup(_rmtree, self.repo)
        _make_complete_reachability_repo(self.repo)
        self.repo_validation = patch(
            "utility_scripts.native_test_verification.require_complete_reachability_repo",
            return_value=self.repo,
        )
        self.repo_validation.start()
        self.addCleanup(self.repo_validation.stop)
        self.output_dir = os.path.join(
            tempfile.mkdtemp(prefix="output-"),
            "natively-collected",
        )
        self.addCleanup(_rmtree, os.path.dirname(self.output_dir))

    def _fake_run_factory(
            self,
            scripted_exits: list[int],
            metadata_exit_codes: set[int] | None = None,
            log_text: str = "BUILD SUCCESSFUL\n",
            repeated_metadata: bool = False,
            generate_metadata_rc: int = 0,
            test_rc: int = 1,
            test_failed_task: str | None = "nativeTest",
            finalized_test_rc: int = 0,
            finalized_test_failed_task: str | None = None,
    ):
        """Build a subprocess.run replacement that consumes ``scripted_exits``.

        The gate runs generateMetadata and test before any trace fallback.
        When the invocation contains ``runNativeTraceImage``, the script
        writes the next scripted exit code to the sentinel file referenced by
        the ``-PtraceBinaryExitFile=`` argument so the gate's reader returns
        that value. By default, 172 runs also write synthetic trace metadata
        so tests that exercise the correction loop represent real progress.
        """
        calls: list[list[str]] = []
        remaining = list(scripted_exits)
        metadata_exit_codes = {172} if metadata_exit_codes is None else metadata_exit_codes

        def _fake(cmd, **kwargs):  # type: ignore[no-untyped-def]
            calls.append(list(cmd))
            stdout = kwargs.get("stdout")
            # Write a synthetic Gradle log if the caller asked us to.
            if hasattr(stdout, "write"):
                stdout.write(log_text)
            if "generateMetadata" in cmd:
                output_dir = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("--metadataOutputDir=")),
                    None,
                )
                if generate_metadata_rc == 0 and output_dir:
                    Path(output_dir).mkdir(parents=True, exist_ok=True)
                    Path(output_dir, "reachability-metadata.json").write_text(
                        json.dumps({"reflection": [{"type": "com.example.AgentGenerated"}]}),
                        encoding="utf-8",
                    )
                return subprocess.CompletedProcess(cmd, generate_metadata_rc)
            if "test" in cmd:
                uses_staged_metadata = any(
                    arg.startswith("-PmetadataConfigDirs=")
                    for arg in cmd
                )
                rc = test_rc if uses_staged_metadata else finalized_test_rc
                failed_task = test_failed_task if uses_staged_metadata else finalized_test_failed_task
                if hasattr(stdout, "write") and failed_task is not None:
                    stdout.write(f"> Task :{failed_task} FAILED\n")
                return subprocess.CompletedProcess(cmd, rc)
            if "runNativeTraceImage" in cmd:
                exit_file = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PtraceBinaryExitFile=")),
                    None,
                )
                run_dir = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PtraceMetadataPath=")),
                    None,
                )
                rc = remaining.pop(0)
                if exit_file:
                    Path(exit_file).parent.mkdir(parents=True, exist_ok=True)
                    Path(exit_file).write_text(str(rc), encoding="utf-8")
                if run_dir and rc in metadata_exit_codes:
                    Path(run_dir).mkdir(parents=True, exist_ok=True)
                    generated_type = "com.example.Generated"
                    if not repeated_metadata:
                        generated_type = f"{generated_type}{len(calls)}"
                    Path(run_dir, "reachability-metadata.json").write_text(
                        json.dumps({"reflection": [{"type": generated_type}]}),
                        encoding="utf-8",
                    )
                # Gradle-side exit is always 0 (Exec uses ignoreExitValue).
                return subprocess.CompletedProcess(cmd, 0)
            if "mergeNativeTraceMetadata" in cmd:
                input_dirs = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PinputDirs=")),
                    "",
                ).split(",")
                output_dir = next(
                    (a.split("=", 1)[1] for a in cmd if a.startswith("-PoutputDir=")),
                    None,
                )
                if output_dir:
                    merged_reflection = []
                    for input_dir in input_dirs:
                        metadata_path = Path(input_dir) / "reachability-metadata.json"
                        if metadata_path.is_file():
                            try:
                                payload = json.loads(metadata_path.read_text(encoding="utf-8"))
                            except json.JSONDecodeError:
                                return subprocess.CompletedProcess(cmd, 1)
                            merged_reflection.extend(payload.get("reflection", []))
                    Path(output_dir).mkdir(parents=True, exist_ok=True)
                    Path(output_dir, "reachability-metadata.json").write_text(
                        json.dumps({"reflection": merged_reflection}),
                        encoding="utf-8",
                    )
                return subprocess.CompletedProcess(cmd, 0)
            # mergeNativeTraceMetadata or anything else — succeeds.
            return subprocess.CompletedProcess(cmd, 0)

        return _fake, calls
