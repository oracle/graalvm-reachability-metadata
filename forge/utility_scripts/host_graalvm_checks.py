# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""GraalVM toolchain requirements: usable-installation probes, version parsing and matching, and the environment lane checks."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from typing import Mapping

from utility_scripts.host_probes import first_output_line, run_command

GRAALVM_VERSION_CONFIG = "graalvm-versions.json"
GRAALVM_GA_RELEASE_ENDPOINT = "repos/graalvm/graalvm-ce-builds/releases/latest"
GRAALVM_EA_RELEASE_ENDPOINT = (
    "repos/graalvm/oracle-graalvm-ea-builds/contents/versions/latest-ea.json"
)
GRAALVM_SCHEMA_PATH = "lib/svm/schemas/reachability-metadata-schema.json"
ISSUE_GRAALVM_ENV_VARS = ("GRAALVM_HOME", "GRAALVM_HOME_25_0", "GRAALVM_HOME_LATEST_EA")
GRAALVM_VERSION_CHECK_MODES = ("strict", "warn", "off")
DEFAULT_GRAALVM_VERSION_CHECK = "strict"
GRAALVM_VERSION_CHECK_ENV_VAR = "FORGE_GRAALVM_VERSION_CHECK"
# Published EA labels can be reissued from the same reported runtime revision.
# §FS-forge-host-requirements
GRAALVM_EA_RUNTIME_REVISIONS = {
    "25i3-25.0.4.1-ea.03": 1,
}


@dataclass(frozen=True)
class GraalVMVersions:
    """Required versions for the three Forge GraalVM lanes."""

    latest_ga: str | None
    pinned_25: str | None
    latest_ea: str | None


def check_graalvm_installation(
        graalvm_home: str,
        environment: Mapping[str, str] | None = None,
) -> list[str]:
    """Return the reasons a GraalVM home cannot run Forge work; empty when it can.

    This is the single definition of a Forge-usable GraalVM distribution: it must
    run Java and Native Image, load the native-image agent, and carry the
    reachability-metadata schema that the repository validates generated metadata
    against (§FS-forge-host-requirements).
    """
    problems: list[str] = []
    for executable in ("java", "native-image"):
        path = os.path.join(graalvm_home, "bin", executable)
        if not os.path.isfile(path) or not os.access(path, os.X_OK):
            problems.append(f"{os.path.join('bin', executable)} is missing or not executable")
    if not os.path.isfile(os.path.join(graalvm_home, GRAALVM_SCHEMA_PATH)):
        problems.append(f"{GRAALVM_SCHEMA_PATH} is missing")
    if not problems:
        agent_problem = probe_native_image_agent(graalvm_home, environment)
        if agent_problem:
            problems.append(agent_problem)
    return problems


def probe_native_image_agent(
        graalvm_home: str,
        environment: Mapping[str, str] | None = None,
) -> str | None:
    """Return why `bin/java` cannot load the Native Image agent, or `None` on success."""
    with tempfile.TemporaryDirectory(prefix="forge-native-image-agent-") as output_dir:
        result = run_command(
            [
                os.path.join(graalvm_home, "bin", "java"),
                f"-agentlib:native-image-agent=config-output-dir={output_dir}",
                "-version",
            ],
            os.environ if environment is None else environment,
        )
    if result.returncode == 0:
        return None
    detail = first_output_line(result) or f"exit={result.returncode}"
    return f"bin/java cannot load native-image-agent ({detail})"


def require_graalvm_home_env(variable: str, environment: Mapping[str, str] | None = None) -> str:
    """Require one GraalVM home variable to point at a Forge-usable distribution.

    §FS-forge-host-requirements
    """
    values = os.environ if environment is None else environment
    graalvm_home = values.get(variable)
    if not graalvm_home:
        print(f"ERROR: Required environment variable '{variable}' is not set.", file=sys.stderr)
        print(
            f"Fix: export `{variable}=/absolute/path/to/a/graalvm` that provides Native Image, its agent, and "
            f"{GRAALVM_SCHEMA_PATH}.",
            file=sys.stderr,
        )
        sys.exit(1)
    problems = check_graalvm_installation(graalvm_home, values)
    if problems:
        print(
            f"ERROR: Environment variable '{variable}' points to '{graalvm_home}', "
            f"which cannot run Forge work: {'; '.join(problems)}.",
            file=sys.stderr,
        )
        print(
            f"Fix: point `{variable}` at a GraalVM distribution that provides Native Image, its agent, and "
            f"{GRAALVM_SCHEMA_PATH}.",
            file=sys.stderr,
        )
        sys.exit(1)
    return graalvm_home


def require_issue_graalvm_homes(environment: Mapping[str, str] | None = None) -> None:
    """Require every GraalVM lane used by Forge issue work.

    §FS-forge-host-requirements
    """
    for variable in ISSUE_GRAALVM_ENV_VARS:
        require_graalvm_home_env(variable, environment)


def resolve_graalvm_version_check(
        requested: str | None = None,
        environment: Mapping[str, str] | None = None,
) -> str:
    """Resolve the GraalVM version-check mode from an explicit value or the environment."""
    values = os.environ if environment is None else environment
    mode = (requested or values.get(GRAALVM_VERSION_CHECK_ENV_VAR) or DEFAULT_GRAALVM_VERSION_CHECK)
    mode = mode.strip().lower()
    if mode not in GRAALVM_VERSION_CHECK_MODES:
        raise ValueError(
            f"{GRAALVM_VERSION_CHECK_ENV_VAR} must be one of "
            f"{', '.join(GRAALVM_VERSION_CHECK_MODES)}, got {mode!r}"
        )
    return mode


def java_version_major(version_line: str) -> int | None:
    """Extract the Java major version from a standard `java -version` first line."""
    for token in version_line.replace('"', " ").split():
        normalized = token.removeprefix("1.")
        major_text = normalized.split(".", maxsplit=1)[0].split("-", maxsplit=1)[0]
        if major_text.isdigit():
            return int(major_text)
    return None


def parse_native_image_version(output: str) -> str | None:
    """Extract the JDK release from `native-image --version`."""
    match = re.search(r"^native-image\s+(\S+)", output, flags=re.MULTILINE)
    return match.group(1) if match else None


def parse_graalvm_runtime_version(output: str) -> str | None:
    """Extract the GraalVM product release from `native-image --version`."""
    match = re.search(
        r"^GraalVM Runtime Environment .*?GraalVM(?: CE)?\s+(\S+)",
        output,
        flags=re.MULTILINE,
    )
    return match.group(1) if match else None


def graalvm_ga_version_matches(expected: str | None, installed: str | None) -> bool:
    """Return whether a GraalVM product version matches the latest GA tag."""
    if expected is None or installed is None:
        return False
    return installed == expected or installed.startswith(f"{expected}+")


def parse_java_runtime_version(output: str) -> str | None:
    """Extract `java.runtime.version` from Java settings output."""
    match = re.search(r"^\s*java\.runtime\.version\s*=\s*(\S+)", output, flags=re.MULTILINE)
    return match.group(1) if match else None


def parse_ea_release_version(release_version: str) -> tuple[str, str, int] | None:
    """Return the Graal, JDK, and build versions from an Oracle EA release ID."""
    match = re.fullmatch(
        r"(\d+)i(\d+)-(\d+(?:\.\d+)+)-ea\.(\d+)",
        release_version,
    )
    if match is None:
        return None
    graal_version = f"{match.group(1)}.{match.group(2)}"
    return graal_version, match.group(3), int(match.group(4))


def graalvm_ea_version_matches(
        expected_release: str,
        graalvm_version: str | None,
        java_runtime_version: str | None,
) -> bool:
    """Return whether local GraalVM and JDK versions match an Oracle EA release."""
    expected = parse_ea_release_version(expected_release)
    if expected is None or graalvm_version is None or java_runtime_version is None:
        return False
    graal_version, jdk_version, build = expected
    ea_revision = GRAALVM_EA_RUNTIME_REVISIONS.get(expected_release, build - 1)
    graal_matches = re.match(
        rf"^{re.escape(graal_version)}\..*-dev\+\d+\.0*{ea_revision}(?:\D|$)",
        graalvm_version,
    ) is not None
    runtime_matches = (
        java_runtime_version.startswith(f"{jdk_version}+")
        and f"-jvmci-{graal_version}-" in java_runtime_version
    )
    return graal_matches and runtime_matches


class GraalVMEnvironmentChecks:
    """Environment-lane checks for the GraalVM distributions a run requires.

    Mixed into ``HostRequirements``; relies on its ``_add`` recorder, resolved
    queue requirements, and version-check mode (§FS-forge-host-requirements).
    """

    def _required_graalvm_description(self, variable: str) -> str:
        """Describe the distribution one GraalVM lane must point to."""
        if self.graalvm_version_check == "off":
            return "any GraalVM with Native Image, its agent, and the reachability-metadata schema"
        if variable == "GRAALVM_HOME_25_0":
            return f"GraalVM {self.graalvm_versions.pinned_25 or '<invalid repo pin>'}"
        if variable == "GRAALVM_HOME_LATEST_EA":
            return f"Oracle GraalVM {self.graalvm_versions.latest_ea or '<unresolved latest EA>'}"
        return f"GraalVM {self.graalvm_versions.latest_ga or '<unresolved latest GA>'}"

    def _version_check_description(self) -> str:
        """Describe the effect of the selected GraalVM version-check mode."""
        if self.graalvm_version_check == "off":
            return "off — version match is not evaluated; Native Image, its agent, and schema stay mandatory"
        if self.graalvm_version_check == "warn":
            return "warn — version mismatches report WARN and do not stop work"
        return "strict — a version mismatch stops the worker"

    def _check_environment(self) -> None:
        if self.requirements.coverage_work and not self.requirements.issue_work:
            self._check_coverage_graalvm_home()
            return
        self._check_graalvm_home(
            "GRAALVM_HOME",
            self.requirements.issue_work,
            self.graalvm_versions.latest_ga,
        )
        self._check_graalvm_home(
            "GRAALVM_HOME_25_0",
            self.requirements.issue_work,
            self.graalvm_versions.pinned_25,
        )
        self._check_graalvm_home(
            "GRAALVM_HOME_LATEST_EA",
            self.requirements.issue_work,
            self.graalvm_versions.latest_ea,
            early_access=True,
        )
        if self.requirements.issue_work:
            java_home = self.environment.get("JAVA_HOME")
            graalvm_home = self.environment.get("GRAALVM_HOME")
            detail = (
                f"JAVA_HOME={java_home}"
                if java_home
                else f"unset; Forge will align it to GRAALVM_HOME={graalvm_home or '<unset>'}"
            )
            self._add("environment", "JAVA_HOME alignment", True, True, detail)
        elif self.requirements.review_work:
            self._check_review_java_home()

    def _check_coverage_graalvm_home(self) -> None:
        """Require one Forge-usable GraalVM of JDK 25 or newer for coverage work."""
        home: str | None = self.environment.get("GRAALVM_HOME")
        source: str = ""
        if not home:
            home = self.environment.get("JAVA_HOME")
            source = " (from JAVA_HOME)"
        if not home:
            self._add(
                "environment",
                "GRAALVM_HOME",
                True,
                False,
                "neither GRAALVM_HOME nor JAVA_HOME is set",
                "Export `GRAALVM_HOME=/absolute/path/to/a/graalvm` of JDK 25 or newer that provides "
                f"Native Image, native-image-agent, and {GRAALVM_SCHEMA_PATH}.",
            )
            return
        self.environment["GRAALVM_HOME"] = home
        problems: list[str] = check_graalvm_installation(home, self.environment)
        if problems:
            self._add(
                "environment",
                "GRAALVM_HOME",
                True,
                False,
                f"GRAALVM_HOME={home}{source}: {'; '.join(problems)}",
                "Point `GRAALVM_HOME` to a GraalVM that provides Native Image, native-image-agent, "
                f"and {GRAALVM_SCHEMA_PATH}.",
            )
            return
        version: subprocess.CompletedProcess[str] = run_command(
            [os.path.join(home, "bin", "java"), "-version"],
            self.environment,
        )
        version_line: str = first_output_line(version) or "java version unavailable"
        major: int | None = java_version_major(version_line) if version.returncode == 0 else None
        self._add(
            "environment",
            "GRAALVM_HOME",
            True,
            major is not None and major >= 25,
            f"GRAALVM_HOME={home}{source} ({version_line})",
            "Point `GRAALVM_HOME` to a GraalVM of JDK 25 or newer; coverage work requires 25+.",
        )

    def _check_review_java_home(self) -> None:
        java_home = self.environment.get("JAVA_HOME")
        if not java_home:
            self._add(
                "environment",
                "JAVA_HOME",
                True,
                False,
                "JAVA_HOME is unset",
                "Export `JAVA_HOME=/absolute/path/to/jdk-25` before starting review-only Forge.",
            )
            return
        java = os.path.join(java_home, "bin", "java")
        if not os.path.isfile(java) or not os.access(java, os.X_OK):
            self._add(
                "environment",
                "JAVA_HOME",
                True,
                False,
                f"JAVA_HOME={java_home}; `{java}` is not executable",
                "Point `JAVA_HOME` to a JDK 25 distribution containing executable bin/java.",
            )
            return
        version = run_command([java, "-version"], self.environment)
        version_line = first_output_line(version) or "java version unavailable"
        is_jdk_25 = version.returncode == 0 and java_version_major(version_line) == 25
        self._add(
            "environment",
            "JAVA_HOME",
            True,
            is_jdk_25,
            f"JAVA_HOME={java_home} ({version_line})",
            "Point `JAVA_HOME` to JDK 25; review Gradle validation must match the repository prerequisite.",
        )

    def _resolve_graalvm_versions(self) -> GraalVMVersions:
        pinned_25 = self._read_pinned_graalvm_25_version()
        latest_ga = self._read_latest_graalvm_ga_version()
        latest_ea = self._read_latest_graalvm_ea_version()
        return GraalVMVersions(latest_ga, pinned_25, latest_ea)

    def _read_pinned_graalvm_25_version(self) -> str | None:
        config_path = os.path.join(self.forge_dir, GRAALVM_VERSION_CONFIG)
        try:
            with open(config_path, encoding="utf-8") as config_file:
                payload = json.load(config_file)
            version = payload.get("GRAALVM_HOME_25_0")
        except (OSError, json.JSONDecodeError, AttributeError):
            version = None
        valid = isinstance(version, str) and re.fullmatch(r"25\.0\.\d+", version) is not None
        self._add(
            "version",
            "pinned GraalVM 25.0.x",
            True,
            valid,
            f"required={version or '<invalid>'}; source={config_path}",
            f"Set `GRAALVM_HOME_25_0` to a valid 25.0.x version in `{config_path}`.",
            advisory=self.version_check_advisory,
        )
        return version if valid else None

    def _read_latest_graalvm_ga_version(self) -> str | None:
        result = run_command(["gh", "api", GRAALVM_GA_RELEASE_ENDPOINT], self.environment)
        try:
            tag = json.loads(result.stdout)["tag_name"]
            match = re.fullmatch(r"(?:graal-|jdk-)(\d+(?:\.\d+)+)", str(tag))
            version = match.group(1) if match else None
        except (json.JSONDecodeError, KeyError, TypeError):
            version = None
        passed = result.returncode == 0 and version is not None
        self._add(
            "version",
            "latest GraalVM GA",
            True,
            passed,
            f"required={version or '<unresolved>'}; source=graalvm/graalvm-ce-builds latest release",
            "Allow `gh api repos/graalvm/graalvm-ce-builds/releases/latest` and retry.",
            advisory=self.version_check_advisory,
        )
        return version if passed else None

    def _read_latest_graalvm_ea_version(self) -> str | None:
        result = run_command(
            [
                "gh", "api", GRAALVM_EA_RELEASE_ENDPOINT,
                "-H", "Accept: application/vnd.github.raw+json",
            ],
            self.environment,
        )
        try:
            version = json.loads(result.stdout)["version"]
        except (json.JSONDecodeError, KeyError, TypeError):
            version = None
        valid = isinstance(version, str) and parse_ea_release_version(version) is not None
        passed = result.returncode == 0 and valid
        self._add(
            "version",
            "latest Oracle GraalVM EA",
            True,
            passed,
            (
                f"required={version or '<unresolved>'}; "
                "source=graalvm/oracle-graalvm-ea-builds latest-ea.json"
            ),
            "Allow the latest-ea.json `gh api` query and retry.",
            advisory=self.version_check_advisory,
        )
        return version if passed else None

    def _check_graalvm_home(
            self,
            variable: str,
            required: bool,
            expected_version: str | None,
            early_access: bool = False,
    ) -> None:
        """Check one GraalVM lane: installation is mandatory, the version match follows the mode."""
        value = self.environment.get(variable)
        if not required:
            self._add("environment", variable, False, None, value or "not required by this run")
            return
        if not value:
            self._add(
                "environment",
                variable,
                True,
                False,
                f"current=<unset>; required={self._required_graalvm_description(variable)}",
                f"Install {self._required_graalvm_description(variable)} with Native Image, its agent, and "
                f"{GRAALVM_SCHEMA_PATH}, then export `{variable}=/absolute/path/to/that/distribution`.",
            )
            return
        problems = check_graalvm_installation(value, self.environment)
        if problems:
            self._add(
                "environment",
                variable,
                True,
                False,
                f"path={value}; {'; '.join(problems)}",
                f"Install {self._required_graalvm_description(variable)} with Native Image, its agent, and "
                f"{GRAALVM_SCHEMA_PATH}, then point `{variable}` to it.",
            )
            return
        self._add(
            "environment",
            variable,
            True,
            True,
            f"path={value}; native-image, native-image-agent, and {GRAALVM_SCHEMA_PATH} are usable",
        )
        self._check_graalvm_version(variable, value, expected_version, early_access)

    def _check_graalvm_version(
            self,
            variable: str,
            value: str,
            expected_version: str | None,
            early_access: bool,
    ) -> None:
        """Compare one installed GraalVM lane against the version required for it."""
        if self.graalvm_version_check == "off":
            self._add(
                "version",
                f"{variable} version",
                False,
                None,
                f"path={value}; version match disabled by `--graalvm-version-check off`",
            )
            return
        native_version_result = run_command(
            [os.path.join(value, "bin", "native-image"), "--version"],
            self.environment,
        )
        native_version_output = f"{native_version_result.stdout}\n{native_version_result.stderr}"
        installed_graalvm_version = parse_graalvm_runtime_version(native_version_output)
        if variable == "GRAALVM_HOME_25_0":
            installed_display = parse_native_image_version(native_version_output) or "<unresolved>"
            version_matches = expected_version is not None and installed_display == expected_version
        elif early_access:
            java_result = run_command(
                [os.path.join(value, "bin", "java"), "-XshowSettings:properties", "-version"],
                self.environment,
            )
            java_runtime = parse_java_runtime_version(f"{java_result.stdout}\n{java_result.stderr}")
            version_matches = expected_version is not None and graalvm_ea_version_matches(
                expected_version,
                installed_graalvm_version,
                java_runtime,
            )
            installed_display = (
                f"graalvm={installed_graalvm_version or '<unresolved>'}, "
                f"java.runtime.version={java_runtime or '<unresolved>'}"
            )
        else:
            version_matches = graalvm_ga_version_matches(expected_version, installed_graalvm_version)
            installed_display = installed_graalvm_version or "<unresolved>"
        self._add(
            "version",
            f"{variable} version",
            True,
            native_version_result.returncode == 0 and version_matches,
            f"path={value}; installed={installed_display}; required={expected_version or '<unresolved>'}",
            self._version_remediation(variable),
            advisory=self.version_check_advisory,
        )

    def _version_remediation(self, variable: str) -> str:
        """Return the one actionable instruction for a mismatched GraalVM lane."""
        required = self._required_graalvm_description(variable)
        if self.version_check_advisory:
            return (
                f"Point `{variable}` at {required} before trusting results from this host; "
                "`--graalvm-version-check warn` allowed the mismatch."
            )
        return (
            f"Point `{variable}` at {required}, or rerun with `--graalvm-version-check warn` "
            "to work with a locally built GraalVM."
        )
