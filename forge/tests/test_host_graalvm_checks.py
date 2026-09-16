# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator
from unittest.mock import Mock, patch

from utility_scripts.host_graalvm_checks import (
    GRAALVM_SCHEMA_PATH,
    check_graalvm_installation,
    graalvm_ea_version_matches,
    java_version_major,
    parse_ea_release_version,
    parse_graalvm_runtime_version,
    parse_native_image_version,
    resolve_graalvm_version_check,
)
from utility_scripts.host_requirements import COVERAGE_REQUIREMENTS, HostRequirements


@contextmanager
def _graalvm_home(native_image: bool = True, schema: bool = True) -> Iterator[str]:
    """Create a GraalVM home whose Native Image and schema presence can be controlled."""
    with tempfile.TemporaryDirectory() as temp_dir:
        executables = ["java", "native-image"] if native_image else ["java"]
        bin_dir = Path(temp_dir) / "bin"
        bin_dir.mkdir()
        for executable in executables:
            (bin_dir / executable).touch(mode=0o755)
        if schema:
            schema_path = Path(temp_dir) / GRAALVM_SCHEMA_PATH
            schema_path.parent.mkdir(parents=True)
            schema_path.touch()
        yield temp_dir


class HostGraalVMChecksTests(unittest.TestCase):
    def test_java_version_major_supports_current_and_legacy_version_lines(self) -> None:
        self.assertEqual(25, java_version_major('openjdk version "25.0.2" 2026-01-20'))
        self.assertEqual(8, java_version_major('java version "1.8.0_402"'))
        self.assertIsNone(java_version_major("java version unavailable"))

    def test_native_image_and_ea_version_parsing(self) -> None:
        native_output = (
            "native-image 25.0.4 2026-07-21\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 "
            "(build 25.0.4+7-jvmci-25.2-b20)\n"
        )

        self.assertEqual("25.0.4", parse_native_image_version(native_output))
        self.assertEqual("25.2.4+7.1", parse_graalvm_runtime_version(native_output))
        self.assertEqual(
            ("25.3", "25.0.4.1", 2),
            parse_ea_release_version("25i3-25.0.4.1-ea.02"),
        )
        self.assertTrue(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.02",
            "25.3.4.1-dev+0.1",
            "25.0.4.1+0-LTS-jvmci-25.3-b21",
        ))
        self.assertFalse(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.02",
            "25.3.4.1-dev+0.0",
            "25.0.4.1+0-LTS-jvmci-25.3-b20",
        ))
        self.assertTrue(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.03",
            "25.3.4.1-dev+0.1",
            "25.0.4.1+0-LTS-jvmci-25.3-b21",
        ))

    def test_pinned_graalvm_25_version_is_recorded_in_repository(self) -> None:
        forge_dir = Path(__file__).resolve().parents[1]
        with (forge_dir / "graalvm-versions.json").open(encoding="utf-8") as version_file:
            version = json.load(version_file)["GRAALVM_HOME_25_0"]

        self.assertRegex(version, r"^25\.0\.\d+$")

    @patch("utility_scripts.host_graalvm_checks.run_command")
    def test_graalvm_check_requires_repository_schema(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n",
            "",
        )
        with _graalvm_home(schema=False) as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home},
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.2.4")

        self.assertFalse(host_requirements.results[-1].passed)
        self.assertIn(GRAALVM_SCHEMA_PATH, host_requirements.results[-1].detail)
        self.assertIn("is missing", host_requirements.results[-1].detail)

    @patch("utility_scripts.host_graalvm_checks.run_command")
    def test_graalvm_check_requires_loadable_native_image_agent(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["java", "-agentlib:native-image-agent", "-version"],
            1,
            "",
            "Could not find agent library native-image-agent",
        )
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home},
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.2.4")

        self.assertEqual(1, len(host_requirements.results))
        self.assertFalse(host_requirements.results[0].passed)
        self.assertIn("cannot load native-image-agent", host_requirements.results[0].detail)

    @patch("utility_scripts.host_graalvm_checks.run_command")
    def test_strict_version_check_stops_work_on_a_mismatched_graalvm(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home},
                graalvm_version_check="strict",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.3.0")

        installation, version = host_requirements.results
        self.assertEqual("PASS", installation.status)
        self.assertEqual("FAIL", version.status)
        self.assertTrue(version.blocks_work)

    @patch("utility_scripts.host_graalvm_checks.run_command")
    def test_warn_version_check_keeps_native_image_and_schema_mandatory(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as patched_graalvm, _graalvm_home(native_image=False) as broken_graalvm:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": patched_graalvm, "GRAALVM_HOME_25_0": broken_graalvm},
                graalvm_version_check="warn",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.3.0")
            host_requirements._check_graalvm_home("GRAALVM_HOME_25_0", True, "25.0.4")

        mismatched_version = host_requirements.results[1]
        self.assertEqual("WARN", mismatched_version.status)
        self.assertFalse(mismatched_version.blocks_work)
        self.assertTrue(host_requirements.results[2].blocks_work)

    def test_coverage_lane_uses_graalvm_home_at_jdk_25_or_newer(self) -> None:
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home},
                requirements=COVERAGE_REQUIREMENTS,
            )
            with patch("utility_scripts.host_graalvm_checks.run_command") as command:
                command.return_value = subprocess.CompletedProcess(
                    ["java", "-version"], 0, "", 'openjdk version "26.0.1" 2026-10-20\n'
                )
                host_requirements._check_environment()

        selected, = host_requirements.results
        self.assertEqual("GRAALVM_HOME", selected.name)
        self.assertEqual("PASS", selected.status)

    def test_coverage_lane_falls_back_from_graalvm_home_to_java_home(self) -> None:
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"JAVA_HOME": graalvm_home},
                requirements=COVERAGE_REQUIREMENTS,
            )
            with patch("utility_scripts.host_graalvm_checks.run_command") as command:
                command.return_value = subprocess.CompletedProcess(
                    ["java", "-version"], 0, "", 'openjdk version "26.0.1" 2026-10-20\n'
                )
                host_requirements._check_environment()

        selected, = host_requirements.results
        self.assertEqual("PASS", selected.status)
        self.assertIn("from JAVA_HOME", selected.detail)
        self.assertEqual(graalvm_home, host_requirements.environment["GRAALVM_HOME"])

    def test_coverage_lane_prefers_graalvm_home_over_java_home(self) -> None:
        with _graalvm_home() as graalvm_home, _graalvm_home() as java_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home, "JAVA_HOME": java_home},
                requirements=COVERAGE_REQUIREMENTS,
            )
            with patch("utility_scripts.host_graalvm_checks.run_command") as command:
                command.return_value = subprocess.CompletedProcess(
                    ["java", "-version"], 0, "", 'openjdk version "26.0.1" 2026-10-20\n'
                )
                host_requirements._check_environment()

        selected, = host_requirements.results
        self.assertIn(graalvm_home, selected.detail)
        self.assertNotIn("from JAVA_HOME", selected.detail)

    def test_coverage_lane_rejects_graalvm_older_than_25(self) -> None:
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"JAVA_HOME": graalvm_home},
                requirements=COVERAGE_REQUIREMENTS,
            )
            with patch("utility_scripts.host_graalvm_checks.run_command") as command:
                command.return_value = subprocess.CompletedProcess(
                    ["java", "-version"], 0, "", 'openjdk version "21.0.5" 2024-10-15\n'
                )
                host_requirements._check_environment()

        selected, = host_requirements.results
        self.assertTrue(selected.blocks_work)
        self.assertIn("JDK 25 or newer", selected.remediation)

    def test_coverage_lane_requires_a_graalvm_environment_variable(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            {},
            requirements=COVERAGE_REQUIREMENTS,
        )

        host_requirements._check_environment()

        selected, = host_requirements.results
        self.assertTrue(selected.blocks_work)
        self.assertIn("neither GRAALVM_HOME nor JAVA_HOME is set", selected.detail)

    def test_coverage_lane_requires_native_image(self) -> None:
        with _graalvm_home(native_image=False) as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home},
                requirements=COVERAGE_REQUIREMENTS,
            )
            host_requirements._check_environment()

        selected, = host_requirements.results
        self.assertTrue(selected.blocks_work)
        self.assertIn("bin/native-image is missing", selected.detail)

    def test_disabled_version_check_skips_version_resolution_and_comparison(self) -> None:
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                {"GRAALVM_HOME": graalvm_home, "FORGE_WORK_LIMIT": "1"},
                graalvm_version_check="off",
            )

            with patch("utility_scripts.host_graalvm_checks.run_command") as command:
                command.return_value = subprocess.CompletedProcess(["java", "-version"], 0, "", "")
                host_requirements._check_graalvm_home("GRAALVM_HOME", True, None)

        self.assertEqual(1, command.call_count)
        self.assertIn("-agentlib:native-image-agent", command.call_args.args[0][1])
        installation, version = host_requirements.results
        self.assertEqual("PASS", installation.status)
        self.assertEqual("SKIP", version.status)
        self.assertIn("version match disabled", version.detail)

    def test_version_check_mode_is_read_from_the_environment_and_validated(self) -> None:
        self.assertEqual("warn", resolve_graalvm_version_check(None, {"FORGE_GRAALVM_VERSION_CHECK": "warn"}))
        self.assertEqual("off", resolve_graalvm_version_check("off", {"FORGE_GRAALVM_VERSION_CHECK": "strict"}))
        self.assertEqual("strict", resolve_graalvm_version_check(None, {}))
        with self.assertRaises(ValueError):
            resolve_graalvm_version_check("lenient", {})

    def test_graalvm_installation_problems_are_named_once(self) -> None:
        with _graalvm_home(native_image=False, schema=False) as graalvm_home:
            problems = check_graalvm_installation(graalvm_home)

        self.assertEqual(
            [
                os.path.join("bin", "native-image") + " is missing or not executable",
                f"{GRAALVM_SCHEMA_PATH} is missing",
            ],
            problems,
        )


if __name__ == "__main__":
    unittest.main()
