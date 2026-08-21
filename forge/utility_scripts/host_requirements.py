# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Validate every host capability the invoked Forge mode or enabled queue requires."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import tomllib
import urllib.parse
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence


REPOSITORY_OWNER = "oracle"
REPOSITORY_NAME = "graalvm-reachability-metadata"
PROJECT_NUMBER = 30
PI_PROVIDER = "openai-codex"
REQUIRED_GRYPE_VERSION = "0.104.0"
DEFAULT_REVIEW_MODEL = "gpt-5.6-terra"
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
WRITE_REPOSITORY_PERMISSIONS = {"WRITE", "MAINTAIN", "ADMIN"}
ISSUE_LIMIT_ENV_VARS = (
    "FORGE_JAVAC_WORK_LIMIT",
    "FORGE_JAVA_RUN_WORK_LIMIT",
    "FORGE_NI_RUN_WORK_LIMIT",
    "FORGE_LIBRARY_UPDATE_WORK_LIMIT",
    "FORGE_WORK_LIMIT",
)
REVIEW_LIMIT_ENV_VARS = (
    "FORGE_LIBRARY_REVIEW_LIMIT",
    "FORGE_JAVAC_REVIEW_LIMIT",
    "FORGE_JAVA_RUN_REVIEW_LIMIT",
    "FORGE_NI_RUN_REVIEW_LIMIT",
    "FORGE_LIBRARY_UPDATE_REVIEW_LIMIT",
    "FORGE_BULK_UPDATE_REVIEW_LIMIT",
)
NETWORK_HOSTS_GITHUB = ("github.com", "api.github.com")
NETWORK_HOSTS_WORK = ("chatgpt.com", "services.gradle.org")
NETWORK_HOSTS_ISSUE_WORK = (
    "repo.maven.apache.org",
    "plugins.gradle.org",
    "registry-1.docker.io",
    "auth.docker.io",
)


@dataclass(frozen=True)
class QueueRequirements:
    """Capabilities selected by the invoked Forge mode or the effective queue limits."""

    issue_work: bool
    review_work: bool
    github_work: bool = True
    coverage_work: bool = False

    @property
    def any_work(self) -> bool:
        """Return whether this run starts issue, coverage, or review work."""
        return self.issue_work or self.coverage_work or self.review_work

    @property
    def build_work(self) -> bool:
        """Return whether this run builds and tests libraries locally.

        Issue work and coverage work select the same build capabilities; they differ
        only in how GraalVM is resolved (§FS-forge-host-requirements).
        """
        return self.issue_work or self.coverage_work


#: Capabilities the code-coverage-improvement workflow needs: the build toolchain and a
#: GitHub account that can publish its pull request, but no issue-queue GraalVM lanes
#: (§FS-forge-host-requirements, §WF-code-coverage-improvement).
COVERAGE_REQUIREMENTS = QueueRequirements(
    issue_work=False,
    review_work=False,
    github_work=True,
    coverage_work=True,
)


@dataclass(frozen=True)
class CheckResult:
    """One deterministic host requirement result."""

    category: str
    name: str
    required: bool
    passed: bool | None
    detail: str
    remediation: str = ""
    advisory: bool = False

    @property
    def status(self) -> str:
        """Return the display status for this result."""
        if not self.required:
            return "SKIP"
        if self.passed is None:
            return "INFO"
        if self.passed:
            return "PASS"
        return "WARN" if self.advisory else "FAIL"

    @property
    def blocks_work(self) -> bool:
        """Return whether this result must stop the worker before any work starts."""
        return self.required and self.passed is False and not self.advisory


@dataclass(frozen=True)
class GraalVMVersions:
    """Required versions for the three Forge GraalVM lanes."""

    latest_ga: str | None
    pinned_25: str | None
    latest_ea: str | None


class HostRequirements:
    """Collect and print Forge host requirements without invoking a model.

    §FS-forge-host-requirements
    """

    def __init__(
            self,
            forge_dir: str,
            python_bin: str,
            review_model: str,
            environment: Mapping[str, str] | None = None,
            requirements: QueueRequirements | None = None,
            graalvm_version_check: str | None = None,
            repo_dir: str | None = None,
    ) -> None:
        self.forge_dir = os.path.abspath(forge_dir)
        self.forge_repo_dir = os.path.dirname(self.forge_dir)
        # The repository a run operates on can be a different checkout than the one that
        # contains Forge, so each set of paths is checked for what it actually owns.
        self.repo_dir = os.path.abspath(repo_dir) if repo_dir else self.forge_repo_dir
        self.python_bin = python_bin
        self.review_model = review_model
        self.environment = dict(os.environ if environment is None else environment)
        self.requirements = (
            resolve_queue_requirements(self.environment)
            if requirements is None
            else requirements
        )
        self.graalvm_version_check = resolve_graalvm_version_check(graalvm_version_check, self.environment)
        self.graalvm_versions = GraalVMVersions(None, None, None)
        self.results: list[CheckResult] = []

    @property
    def version_check_advisory(self) -> bool:
        """Return whether GraalVM version mismatches only warn instead of stopping work."""
        return self.graalvm_version_check == "warn"

    @property
    def separate_repository(self) -> bool:
        """Return whether this run operates on a checkout other than the one holding Forge."""
        return os.path.realpath(self.repo_dir) != os.path.realpath(self.forge_repo_dir)

    def run(self) -> bool:
        """Run all selected checks, print the report, and return whether they passed."""
        if self.requirements.issue_work and self.graalvm_version_check != "off":
            self.graalvm_versions = self._resolve_graalvm_versions()
        self._print_manifest()
        self._check_tools()
        self._check_environment()
        self._check_write_permissions()
        self._check_network()
        self._check_github()
        self._check_pi()
        self._check_codex()
        self._check_docker()
        self._print_results()
        return not any(result.blocks_work for result in self.results)

    def _print_manifest(self) -> None:
        issue_text = "enabled" if self.requirements.issue_work else "disabled"
        review_text = "enabled" if self.requirements.review_work else "disabled"
        coverage_text = "enabled" if self.requirements.coverage_work else "disabled"
        print("[forge-host] Deterministic host requirements")
        print(
            f"[forge-host] Queues: issue work={issue_text}, coverage work={coverage_text}, "
            f"PR review={review_text}"
        )
        print(f"[forge-host] Forge checkout: {self.forge_dir}")
        print(f"[forge-host] Selected repository: {self.repo_dir}")
        print("[forge-host] Required host permissions:")
        write_targets = [self.forge_dir, os.path.join(self.forge_repo_dir, ".git")]
        if self.separate_repository:
            write_targets.append(os.path.join(self.repo_dir, ".git"))
        write_targets.append(os.path.join(self.forge_dir, "local_repositories"))
        print(f"  - Filesystem write: {', '.join(write_targets)}, temporary Gradle state")
        if self.requirements.github_work:
            print("  - GitHub repository: Contents=write, Issues=write, Pull requests=write")
            print(f"  - GitHub project: Projects=write for oracle project {PROJECT_NUMBER}")
            github_operations: list[str] = []
            if self.requirements.build_work:
                github_operations.extend(("assign/label/comment issues", "push generated branches"))
            if self.requirements.review_work:
                github_operations.extend(("submit reviews", "merge eligible PRs"))
            print(f"  - GitHub operations: {', '.join(github_operations)}")
        else:
            print("  - GitHub: no live GitHub access is required by this run")
        print("  - Network: outbound access to every host and port listed in the checks below")
        proxy_url = self.environment.get("https_proxy") or self.environment.get("HTTPS_PROXY")
        if proxy_url:
            print(f"  - Network route: HTTPS reachability is checked through the configured proxy {proxy_url}")
        print("  - Pi reviews: authenticated provider plus unattended tool approval (`pi --approve`)")
        if self.requirements.build_work:
            print("  - Codex recovery: approval=never, sandbox=danger-full-access, worktree write, provider network")
            print("  - Docker: access to the Docker daemon and configured image registries")
        print("[forge-host] Required environment:")
        if self.requirements.issue_work:
            for variable in ISSUE_GRAALVM_ENV_VARS:
                print(f"  - {variable}={self._required_graalvm_description(variable)}")
            print(f"  - Every GraalVM must contain {GRAALVM_SCHEMA_PATH}")
            print("  - JAVA_HOME is aligned to GRAALVM_HOME by Forge; explicit matching value is recommended")
        elif self.requirements.coverage_work:
            print("  - GRAALVM_HOME=<GraalVM JDK 25 or newer with Native Image>; unset takes the value of JAVA_HOME")
            print(f"  - The selected GraalVM must contain {GRAALVM_SCHEMA_PATH}")
        elif self.requirements.review_work:
            print("  - JAVA_HOME=<JDK 25 with executable bin/java> (GRAALVM_HOME is not required for review-only work)")
        print(f"  - FORGE_REVIEW_MODEL={self.review_model}")
        if self.requirements.issue_work:
            print(f"  - GraalVM version match: {self._version_check_description()}")

    def _required_graalvm_description(self, variable: str) -> str:
        """Describe the distribution one GraalVM lane must point to."""
        if self.graalvm_version_check == "off":
            return "any GraalVM with Native Image and the reachability-metadata schema"
        if variable == "GRAALVM_HOME_25_0":
            return f"GraalVM {self.graalvm_versions.pinned_25 or '<invalid repo pin>'}"
        if variable == "GRAALVM_HOME_LATEST_EA":
            return f"Oracle GraalVM {self.graalvm_versions.latest_ea or '<unresolved latest EA>'}"
        return f"GraalVM {self.graalvm_versions.latest_ga or '<unresolved latest GA>'}"

    def _version_check_description(self) -> str:
        """Describe the effect of the selected GraalVM version-check mode."""
        if self.graalvm_version_check == "off":
            return "off — version match is not evaluated; Native Image and schema stay mandatory"
        if self.graalvm_version_check == "warn":
            return "warn — version mismatches report WARN and do not stop work"
        return "strict — a version mismatch stops the worker"

    def _add(
            self,
            category: str,
            name: str,
            required: bool,
            passed: bool | None,
            detail: str,
            remediation: str = "",
            advisory: bool = False,
    ) -> None:
        self.results.append(CheckResult(category, name, required, passed, detail, remediation, advisory))

    def _check_tools(self) -> None:
        any_work = self.requirements.any_work
        tool_specs: tuple[tuple[str, str, bool, tuple[str, ...]], ...] = (
            ("Python", self.python_bin, True, ("--version",)),
            ("git", "git", True, ("--version",)),
            ("GitHub CLI", "gh", self.requirements.github_work, ("--version",)),
            ("Pi", "pi", any_work, ("--version",)),
            ("Codex", "codex", self.requirements.build_work, ("--version",)),
            ("Docker CLI", "docker", self.requirements.build_work, ("--version",)),
        )
        for name, command, required, version_args in tool_specs:
            self._check_tool(name, command, required, version_args)

        self._check_grype()
        self._check_gradle_wrapper(any_work)

    def _check_grype(self) -> None:
        required = self.requirements.issue_work
        resolved = resolve_executable("grype")
        if not required:
            self._add("tool", "grype", False, None, resolved or "not required by this run")
            return
        if resolved is None:
            self._add(
                "tool",
                "grype",
                True,
                False,
                f"current=missing; required={REQUIRED_GRYPE_VERSION}",
                "Install grype 0.104.0 and ensure `grype` is on PATH.",
            )
            return
        result = run_command([resolved, "version"], self.environment)
        installed = parse_grype_version(f"{result.stdout}\n{result.stderr}")
        self._add(
            "tool",
            "grype",
            True,
            result.returncode == 0 and installed == REQUIRED_GRYPE_VERSION,
            f"path={resolved}; current={installed or '<unresolved>'}; required={REQUIRED_GRYPE_VERSION}",
            "Install grype 0.104.0 and ensure that version is first on PATH.",
        )

    def _check_gradle_wrapper(self, required: bool) -> None:
        gradlew = os.path.join(self.repo_dir, "gradlew")
        gradle_ok = os.path.isfile(gradlew) and os.access(gradlew, os.X_OK)
        if not required or not gradle_ok:
            self._add(
                "tool",
                "Gradle wrapper",
                required,
                gradle_ok if required else None,
                gradlew,
                f"Restore the executable repository wrapper and run `chmod +x {gradlew}`.",
            )
            return
        gradle_environment = dict(self.environment)
        gradle_environment["GRADLE_USER_HOME"] = resolve_gradle_state_root(self.environment)
        result = run_command(
            [gradlew, "--version", "--no-daemon"],
            gradle_environment,
            timeout=120,
        )
        installed_version = parse_gradle_version(f"{result.stdout}\n{result.stderr}")
        self._add(
            "tool",
            "Gradle wrapper",
            True,
            result.returncode == 0,
            f"path={gradlew}; version={installed_version or '<unresolved>'}",
            "Allow wrapper execution, Gradle-state writes, and services.gradle.org access; then run "
            f"`{gradlew} --version --no-daemon` successfully.",
        )

    def _check_tool(
            self,
            name: str,
            command: str,
            required: bool,
            version_args: Sequence[str],
    ) -> None:
        resolved = resolve_executable(command)
        if not required:
            self._add("tool", name, False, None, resolved or f"{command} is not required by this run")
            return
        if resolved is None:
            self._add(
                "tool",
                name,
                True,
                False,
                f"`{command}` was not found on PATH",
                f"Install {name} and ensure `{command}` is on PATH.",
            )
            return
        result = run_command([resolved, *version_args], self.environment)
        version = first_output_line(result) or f"exit={result.returncode}"
        self._add(
            "tool",
            name,
            True,
            result.returncode == 0,
            f"{resolved} ({version})",
            f"Repair the `{command}` installation; `{command} {' '.join(version_args)}` must exit 0.",
        )

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
        """Require one Forge-usable GraalVM of JDK 25 or newer for coverage work.

        `GRAALVM_HOME` names the distribution, and when it is unset it takes the value
        of `JAVA_HOME`, so a host that exports only `JAVA_HOME` resolves the same lane
        under the same name instead of a second one. Coverage work builds native images,
        so Native Image and the reachability-metadata schema stay mandatory; only the
        pinned GA/EA lane matching is dropped (§FS-forge-host-requirements).
        """
        home = self.environment.get("GRAALVM_HOME")
        source = ""
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
                f"Native Image and {GRAALVM_SCHEMA_PATH}.",
            )
            return
        # The fallback is an assignment, not a second lane: every later check and the
        # `java -version` probe below must see the same distribution the report names.
        self.environment["GRAALVM_HOME"] = home
        problems = check_graalvm_installation(home)
        if problems:
            self._add(
                "environment",
                "GRAALVM_HOME",
                True,
                False,
                f"GRAALVM_HOME={home}{source}: {'; '.join(problems)}",
                f"Point `GRAALVM_HOME` to a GraalVM that provides Native Image and {GRAALVM_SCHEMA_PATH}.",
            )
            return
        version = run_command([os.path.join(home, "bin", "java"), "-version"], self.environment)
        version_line = first_output_line(version) or "java version unavailable"
        major = java_version_major(version_line) if version.returncode == 0 else None
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
                f"Install {self._required_graalvm_description(variable)} with Native Image and "
                f"{GRAALVM_SCHEMA_PATH}, then export `{variable}=/absolute/path/to/that/distribution`.",
            )
            return
        problems = check_graalvm_installation(value)
        if problems:
            self._add(
                "environment",
                variable,
                True,
                False,
                f"path={value}; {'; '.join(problems)}",
                f"Install {self._required_graalvm_description(variable)} with Native Image and "
                f"{GRAALVM_SCHEMA_PATH}, then point `{variable}` to it.",
            )
            return
        self._add(
            "environment",
            variable,
            True,
            True,
            f"path={value}; native-image and {GRAALVM_SCHEMA_PATH} are present",
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

    def _check_write_permissions(self) -> None:
        any_work = self.requirements.any_work
        paths: list[tuple[str, str, bool, str]] = [
            ("Forge checkout", self.forge_dir, True, ""),
            ("Forge git metadata", os.path.join(self.forge_repo_dir, ".git"), True, ""),
            (
                "Selected repository git metadata",
                os.path.join(self.repo_dir, ".git"),
                self.separate_repository,
                f"{self.repo_dir} is the checkout that contains Forge; covered by the Forge git metadata check",
            ),
            ("Forge local repositories", os.path.join(self.forge_dir, "local_repositories"), any_work, ""),
            ("Gradle state", resolve_gradle_state_root(self.environment), any_work, ""),
            ("Pi state", resolve_pi_state_root(self.environment), any_work, ""),
            ("Codex state", resolve_codex_state_root(self.environment), self.requirements.build_work, ""),
        ]
        for name, path, required, skip_detail in paths:
            if not required:
                self._add("filesystem", name, False, None, skip_detail or f"{path} is not required by this run")
                continue
            passed, detail = probe_write_access(path)
            self._add(
                "filesystem",
                name,
                True,
                passed,
                detail,
                f"Grant the current user write access to `{path}` and run Forge outside a read-only sandbox.",
            )

    def _check_network(self) -> None:
        self._check_git_remote_access()
        hosts: list[tuple[str, bool]] = [
            (host, self.requirements.github_work) for host in NETWORK_HOSTS_GITHUB
        ]
        hosts.extend((host, self.requirements.any_work) for host in NETWORK_HOSTS_WORK)
        hosts.extend((host, self.requirements.build_work) for host in NETWORK_HOSTS_ISSUE_WORK)
        for host, required in hosts:
            if not required:
                self._add("network", host, False, None, "not required by this run")
                continue
            passed, detail = probe_tcp_host(host, 443, environment=self.environment)
            proxy = resolve_https_proxy(self.environment, host)
            self._add(
                "network",
                host,
                True,
                passed,
                detail,
                (
                    f"Allow the configured proxy {proxy[0]}:{proxy[1]} to reach `{host}:443`, "
                    "or unset the proxy for it."
                    if proxy
                    else f"Allow DNS and outbound TCP 443 access to `{host}` in the host firewall or sandbox policy."
                ),
            )
        if self.requirements.issue_work:
            self._add(
                "network",
                "library-specific sources and Docker registries",
                True,
                None,
                "Targets are discovered from each selected library and cannot be enumerated before queue selection",
                "Allow outbound HTTPS to source-code/documentation URLs and registries declared by selected libraries.",
            )

    def _check_git_remote_access(self) -> None:
        self._check_git_remote("Forge git self-update", self.forge_dir, True, "")
        self._check_git_remote(
            "Selected repository git remote",
            self.repo_dir,
            self.separate_repository,
            f"{self.repo_dir} is the checkout that contains Forge; covered by the Forge self-update check",
        )

    def _check_git_remote(self, name: str, checkout: str, required: bool, skip_detail: str) -> None:
        """Check that one checkout reaches the monitored branch through its own origin remote."""
        if not required:
            self._add("network", name, False, None, skip_detail)
            return
        monitored_branch = self.environment.get("FORGE_MONITORED_BRANCH", "origin/master")
        branch = monitored_branch.removeprefix("origin/")
        remote_result = run_command(
            ["git", "-C", checkout, "remote", "get-url", "origin"],
            self.environment,
        )
        remote_url = first_output_line(remote_result) or "origin"
        result = run_command(
            [
                "git", "-C", checkout,
                "ls-remote", "--exit-code", "origin", f"refs/heads/{branch}",
            ],
            self.environment,
        )
        self._add(
            "network",
            name,
            True,
            result.returncode == 0,
            f"checkout={checkout}, remote={remote_url}, branch={branch}, "
            f"status={'reachable' if result.returncode == 0 else 'unreachable'}",
            f"Grant git transport access to `{remote_url}` from `{checkout}` and ensure branch "
            f"`{branch}` exists on origin.",
        )

    def _check_github(self) -> None:
        required = self.requirements.github_work
        if not required or resolve_executable("gh") is None:
            self._add(
                "github",
                "authentication and permissions",
                required,
                None if not required else False,
                "GitHub checks require the gh CLI",
                "Install `gh`, put it on PATH, and run `gh auth login -h github.com`.",
            )
            return

        auth = run_command(["gh", "auth", "status", "--active", "--hostname", "github.com"], self.environment)
        self._add(
            "github",
            "gh authentication",
            True,
            auth.returncode == 0,
            first_output_line(auth) or "gh auth status returned no output",
            "Run `gh auth login -h github.com` with the account used by Forge.",
        )
        if auth.returncode != 0:
            self._add(
                "github",
                "repository and project permissions",
                True,
                False,
                "permission query skipped because gh authentication failed",
                "Authenticate first, then grant Contents=write, Issues=write, Pull requests=write, and Projects=write.",
            )
            return

        query = """
query($owner: String!, $name: String!, $project: Int!) {
  viewer {
    login
    repository(name: $name) { nameWithOwner viewerPermission }
  }
  repository(owner: $owner, name: $name) { nameWithOwner viewerPermission }
  organization(login: $owner) {
    projectV2(number: $project) { id title viewerCanUpdate }
  }
}
""".strip()
        permission_result = run_command(
            [
                "gh", "api", "graphql",
                "-f", f"query={query}",
                "-F", f"owner={REPOSITORY_OWNER}",
                "-F", f"name={REPOSITORY_NAME}",
                "-F", f"project={PROJECT_NUMBER}",
            ],
            self.environment,
        )
        if permission_result.returncode != 0:
            self._add(
                "github",
                "repository and project permissions",
                True,
                False,
                first_output_line(permission_result) or "GitHub GraphQL permission query failed",
                "Allow api.github.com and grant repository plus organization-project access to the active gh account.",
            )
            return
        self._record_github_permissions(permission_result.stdout)

    def _record_github_permissions(self, output: str) -> None:
        try:
            payload = json.loads(output)
            data = payload["data"]
            viewer = data["viewer"]
            target_repo = data["repository"]
            project = data["organization"]["projectV2"]
        except (json.JSONDecodeError, KeyError, TypeError):
            self._add(
                "github",
                "repository and project permissions",
                True,
                False,
                "GitHub permission response did not contain viewer, repository, and project data",
                "Verify the active gh account can read the repository and oracle project 30.",
            )
            return

        login = str(viewer.get("login") or "unknown")
        target_permission = str((target_repo or {}).get("viewerPermission") or "NONE")
        self._add(
            "github",
            "oracle repository mutations",
            True,
            target_permission in WRITE_REPOSITORY_PERMISSIONS,
            f"account={login}, repository={REPOSITORY_OWNER}/{REPOSITORY_NAME}, permission={target_permission}",
            "Grant the active account repository WRITE, MAINTAIN, or ADMIN permission "
            "(Issues and Pull requests write).",
        )

        project_can_update = bool((project or {}).get("viewerCanUpdate"))
        self._add(
            "github",
            f"oracle project {PROJECT_NUMBER} updates",
            True,
            project_can_update,
            (
                f"account={login}, project={(project or {}).get('title') or '<unavailable>'}, "
                f"viewerCanUpdate={project_can_update}"
            ),
            f"Grant the active account write access to oracle GitHub project {PROJECT_NUMBER}.",
        )

        fork = viewer.get("repository")
        fork_permission = str((fork or {}).get("viewerPermission") or "NONE")
        self._add(
            "github",
            "generated-branch push target",
            self.requirements.build_work,
            fork_permission in WRITE_REPOSITORY_PERMISSIONS if self.requirements.build_work else None,
            (
                f"repository={(fork or {}).get('nameWithOwner') or f'{login}/{REPOSITORY_NAME}'}, "
                f"permission={fork_permission}"
            ),
            f"Create `{login}/{REPOSITORY_NAME}` and grant the active account write access to push generated branches.",
        )

    def _check_pi(self) -> None:
        required = self.requirements.any_work
        if not required:
            self._add("agent", "Pi authentication", False, None, "not required by this run")
        else:
            ready, detail = check_pi_authentication(self.review_model, self.environment)
            self._add(
                "agent",
                "Pi authentication",
                True,
                ready,
                detail,
                f"Install `pi`, then run `pi` and `/login {PI_PROVIDER}`; `pi auth check --provider "
                f"{PI_PROVIDER} --model {self.review_model} --json` must report status=ready.",
            )
        self._add(
            "agent",
            "Pi review command approval",
            self.requirements.review_work,
            pi_approve_supported(self.environment) if self.requirements.review_work else None,
            "required command option: `pi --approve`",
            "Upgrade Pi to a version whose `pi --help` lists `--approve`.",
        )

    def _check_codex(self) -> None:
        if not self.requirements.build_work:
            self._add("agent", "Codex recovery", False, None, "not required by this run")
            return
        if resolve_executable("codex") is None:
            self._add("agent", "Codex recovery", True, False, "Codex is unavailable", "Install and authenticate Codex.")
            return

        login = run_command(["codex", "login", "status"], self.environment)
        self._add(
            "agent",
            "Codex authentication",
            True,
            login.returncode == 0,
            first_output_line(login) or "codex login status returned no output",
            "Run `codex login` before starting Forge.",
        )

        doctor = run_command(["codex", "doctor", "--json"], self.environment, timeout=30)
        provider_ok, doctor_detail = codex_doctor_provider_status(doctor.stdout)
        self._add(
            "agent",
            "Codex provider network",
            True,
            provider_ok,
            doctor_detail,
            "Allow Codex provider HTTPS/WebSocket access; run `codex doctor --json` for the complete diagnosis.",
        )

        compatible, policy_detail = codex_unattended_policy_status(resolve_codex_state_root(self.environment))
        self._add(
            "agent",
            "Codex unattended command permissions",
            True,
            compatible,
            policy_detail,
            "Forge Codex recovery requires approval policy `never` and sandbox `danger-full-access`; "
            "change the managed Codex requirements or replace the remaining Codex recovery lane.",
        )

    def _check_docker(self) -> None:
        if not self.requirements.build_work:
            self._add("docker", "daemon access", False, None, "not required by this run")
            return
        if resolve_executable("docker") is None:
            self._add("docker", "daemon access", True, False, "Docker CLI is unavailable", "Install Docker.")
            return
        result = run_command(["docker", "info"], self.environment)
        detail = "docker info succeeded" if result.returncode == 0 else (
            first_output_line(result) or f"docker info exited {result.returncode}"
        )
        self._add(
            "docker",
            "daemon access",
            True,
            result.returncode == 0,
            detail,
            "Start Docker and grant the current user access to the Docker daemon socket without sudo.",
        )

    def _print_results(self) -> None:
        print("[forge-host] Check results:")
        for result in self.results:
            print(f"  [{result.status}] {result.category}: {result.name} — {result.detail}")
            if result.required and result.passed is False and result.remediation:
                print(f"         Fix: {result.remediation}")
        failures = [result for result in self.results if result.blocks_work]
        warnings = [result for result in self.results if result.status == "WARN"]
        if failures:
            sys.stdout.flush()
            print(f"ERROR: {len(failures)} required check(s) failed. No work was started.")
            return
        if warnings:
            print(f"[forge-host] PASS with {len(warnings)} warning(s); work may start.")
        else:
            print("[forge-host] PASS: all required host checks succeeded; work may start.")


def check_graalvm_installation(graalvm_home: str) -> list[str]:
    """Return the reasons a GraalVM home cannot run Forge work; empty when it can.

    This is the single definition of a Forge-usable GraalVM distribution: it must
    run Java and Native Image and carry the reachability-metadata schema that the
    repository validates generated metadata against (§FS-forge-host-requirements).
    """
    problems: list[str] = []
    for executable in ("java", "native-image"):
        path = os.path.join(graalvm_home, "bin", executable)
        if not os.path.isfile(path) or not os.access(path, os.X_OK):
            problems.append(f"{os.path.join('bin', executable)} is missing or not executable")
    if not os.path.isfile(os.path.join(graalvm_home, GRAALVM_SCHEMA_PATH)):
        problems.append(f"{GRAALVM_SCHEMA_PATH} is missing")
    return problems


def require_graalvm_home_env(variable: str, environment: Mapping[str, str] | None = None) -> str:
    """Require one GraalVM home variable to point at a Forge-usable distribution.

    §FS-forge-host-requirements
    """
    values = os.environ if environment is None else environment
    graalvm_home = values.get(variable)
    if not graalvm_home:
        print(f"ERROR: Required environment variable '{variable}' is not set.", file=sys.stderr)
        print(
            f"Fix: export `{variable}=/absolute/path/to/a/graalvm` that provides Native Image and "
            f"{GRAALVM_SCHEMA_PATH}.",
            file=sys.stderr,
        )
        sys.exit(1)
    problems = check_graalvm_installation(graalvm_home)
    if problems:
        print(
            f"ERROR: Environment variable '{variable}' points to '{graalvm_home}', "
            f"which cannot run Forge work: {'; '.join(problems)}.",
            file=sys.stderr,
        )
        print(
            f"Fix: point `{variable}` at a GraalVM distribution that provides Native Image and "
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


def verify_host_requirements(
        forge_dir: str,
        python_bin: str | None = None,
        review_model: str = DEFAULT_REVIEW_MODEL,
        requirements: QueueRequirements | None = None,
        graalvm_version_check: str | None = None,
        environment: Mapping[str, str] | None = None,
        repo_dir: str | None = None,
) -> bool:
    """Run every host requirement selected by this run and report whether work may start.

    `repo_dir` is the reachability-metadata checkout the run operates on; it defaults to the
    checkout that contains `forge_dir` (§FS-forge-host-requirements).
    """
    host_requirements = HostRequirements(
        forge_dir,
        python_bin or sys.executable,
        review_model,
        environment,
        requirements,
        graalvm_version_check,
        repo_dir,
    )
    return host_requirements.run()


def ensure_host_requirements(
        forge_dir: str,
        python_bin: str | None = None,
        review_model: str = DEFAULT_REVIEW_MODEL,
        requirements: QueueRequirements | None = None,
        graalvm_version_check: str | None = None,
        environment: Mapping[str, str] | None = None,
        repo_dir: str | None = None,
) -> None:
    """Stop the process with a non-zero exit when a required host capability is missing.

    §FS-forge-host-requirements
    """
    passed = verify_host_requirements(
        forge_dir,
        python_bin,
        review_model,
        requirements,
        graalvm_version_check,
        environment,
        repo_dir,
    )
    if not passed:
        sys.exit(1)


def resolve_queue_requirements(environment: Mapping[str, str]) -> QueueRequirements:
    """Resolve whether issue and review capabilities are needed from effective limits."""
    issue_limits = [read_nonnegative_limit(environment, name, 1) for name in ISSUE_LIMIT_ENV_VARS]
    issue_work = any(limit > 0 for limit in issue_limits)
    base_review_limit = read_nonnegative_limit(environment, "FORGE_REVIEW_LIMIT", 1)
    if environment.get("FORGE_REVIEW_LABEL"):
        review_work = base_review_limit > 0
    else:
        review_limits = [
            read_nonnegative_limit(environment, name, base_review_limit)
            for name in REVIEW_LIMIT_ENV_VARS
        ]
        review_work = any(limit > 0 for limit in review_limits)
    return QueueRequirements(issue_work=issue_work, review_work=review_work)


def read_nonnegative_limit(environment: Mapping[str, str], name: str, default: int) -> int:
    """Read a non-negative queue limit, raising a clear configuration error."""
    raw_value = environment.get(name)
    if raw_value is None:
        return default
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise ValueError(f"{name} must be a non-negative integer, got {raw_value!r}") from exc
    if value < 0:
        raise ValueError(f"{name} must be a non-negative integer, got {raw_value!r}")
    return value


def resolve_executable(command: str) -> str | None:
    """Resolve an executable command or absolute path."""
    if os.path.sep in command:
        return command if os.path.isfile(command) and os.access(command, os.X_OK) else None
    return shutil.which(command)


def run_command(
        command: Sequence[str],
        environment: Mapping[str, str],
        timeout: int = 20,
) -> subprocess.CompletedProcess[str]:
    """Run one deterministic probe command without a shell or interactive prompt."""
    command_environment = dict(environment)
    command_environment["GH_PROMPT_DISABLED"] = "1"
    command_environment["GH_PAGER"] = ""
    try:
        return subprocess.run(
            list(command),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
            env=command_environment,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return subprocess.CompletedProcess(list(command), 1, "", str(exc))


def first_output_line(result: subprocess.CompletedProcess[str]) -> str:
    """Return one safe diagnostic line from a completed command."""
    output = "\n".join(part.strip() for part in (result.stdout, result.stderr) if part.strip())
    return output.splitlines()[0] if output else ""


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


def parse_grype_version(output: str) -> str | None:
    """Extract the release from `grype version`."""
    match = re.search(r"^Version:\s*v?(\S+)", output, flags=re.MULTILINE | re.IGNORECASE)
    return match.group(1) if match else None


def parse_gradle_version(output: str) -> str | None:
    """Extract the release from Gradle wrapper version output."""
    match = re.search(r"^Gradle\s+(\S+)", output, flags=re.MULTILINE)
    return match.group(1) if match else None


def check_pi_authentication(
        review_model: str,
        environment: Mapping[str, str] | None = None,
) -> tuple[bool, str]:
    """Check Pi authentication for the Forge provider and model without invoking a model.

    §FS-forge-host-requirements
    """
    values = os.environ if environment is None else environment
    if resolve_executable("pi") is None:
        return False, f"`pi` was not found on PATH; provider={PI_PROVIDER}, model={review_model}"
    result = run_command(
        [
            "pi", "auth", "check",
            "--provider", PI_PROVIDER,
            "--model", review_model,
            "--json",
        ],
        values,
    )
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError:
        payload = {}
    if not isinstance(payload, dict):
        payload = {}
    ready = (
        result.returncode == 0
        and payload.get("status") == "ready"
        and payload.get("provider") == PI_PROVIDER
    )
    detail = (
        f"provider={payload.get('provider') or PI_PROVIDER}, model={review_model}, "
        f"status={payload.get('status') or 'invalid'}"
    )
    failure = "" if ready else first_output_line(result)
    return ready, f"{detail}; {failure}" if failure else detail


def pi_approve_supported(environment: Mapping[str, str]) -> bool:
    """Return whether the installed Pi CLI supports unattended tool approval."""
    result = run_command(["pi", "--help"], environment)
    return result.returncode == 0 and re.search(r"(?:^|\s)--approve(?:\s|,|$)", result.stdout) is not None


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
    ea_revision = build - 1
    graal_matches = re.match(
        rf"^{re.escape(graal_version)}\..*-dev\+\d+\.0*{ea_revision}(?:\D|$)",
        graalvm_version,
    ) is not None
    runtime_matches = (
        java_runtime_version.startswith(f"{jdk_version}+")
        and f"-jvmci-{graal_version}-" in java_runtime_version
    )
    return graal_matches and runtime_matches


def nearest_existing_directory(path: str) -> str:
    """Return the nearest existing directory at or above path."""
    candidate = Path(path).expanduser().absolute()
    while not candidate.exists():
        if candidate.parent == candidate:
            break
        candidate = candidate.parent
    if candidate.is_file():
        candidate = candidate.parent
    return str(candidate)


def probe_write_access(path: str) -> tuple[bool, str]:
    """Prove write access with a temporary file that is removed immediately."""
    expanded = os.path.abspath(os.path.expanduser(path))
    probe_dir = nearest_existing_directory(expanded)
    try:
        with tempfile.NamedTemporaryFile(prefix=".forge-host-", dir=probe_dir, delete=True):
            pass
    except OSError as exc:
        return False, f"target={expanded}, probe={probe_dir}, error={exc}"
    return True, f"target={expanded}, write probe passed in {probe_dir}"


def host_bypasses_proxy(environment: Mapping[str, str], host: str) -> bool:
    """Return whether `no_proxy` sends this host straight out instead of through a proxy."""
    no_proxy = environment.get("no_proxy") or environment.get("NO_PROXY") or ""
    target = host.lower()
    for entry in (part.strip().lower().lstrip(".") for part in no_proxy.split(",")):
        if entry == "*":
            return True
        if entry and (target == entry or target.endswith(f".{entry}")):
            return True
    return False


def resolve_https_proxy(environment: Mapping[str, str], host: str) -> tuple[str, int] | None:
    """Return the proxy that must carry HTTPS traffic to a host, or None for a direct route."""
    proxy_url = environment.get("https_proxy") or environment.get("HTTPS_PROXY")
    if not proxy_url or host_bypasses_proxy(environment, host):
        return None
    parsed = urllib.parse.urlparse(proxy_url if "://" in proxy_url else f"http://{proxy_url}")
    if not parsed.hostname:
        return None
    return parsed.hostname, parsed.port or 80


def probe_tcp_host(
        host: str,
        port: int,
        timeout: float = 5.0,
        environment: Mapping[str, str] | None = None,
) -> tuple[bool, str]:
    """Test outbound access the way Forge's tools use it: directly, or through the configured proxy."""
    values = os.environ if environment is None else environment
    proxy = resolve_https_proxy(values, host)
    if proxy is None:
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return True, f"tcp://{host}:{port} reachable"
        except OSError as exc:
            return False, f"tcp://{host}:{port} unreachable: {exc}"
    return probe_proxied_host(host, port, proxy, timeout)


def probe_proxied_host(
        host: str,
        port: int,
        proxy: tuple[str, int],
        timeout: float = 5.0,
) -> tuple[bool, str]:
    """Ask the configured proxy to open a tunnel, proving both hops without transferring data."""
    proxy_host, proxy_port = proxy
    route = f"tcp://{host}:{port} via proxy {proxy_host}:{proxy_port}"
    request = f"CONNECT {host}:{port} HTTP/1.1\r\nHost: {host}:{port}\r\n\r\n".encode()
    try:
        with socket.create_connection((proxy_host, proxy_port), timeout=timeout) as connection:
            connection.sendall(request)
            status_line = connection.recv(512).decode("latin-1", "replace").splitlines()
    except OSError as exc:
        return False, f"{route} unreachable: {exc}"
    status = status_line[0].strip() if status_line else ""
    fields = status.split()
    if len(fields) > 1 and fields[1] == "200":
        return True, f"{route} reachable"
    return False, f"{route} refused: {status or 'proxy closed the connection'}"


def resolve_gradle_state_root(environment: Mapping[str, str]) -> str:
    """Return the operator-configured or default Forge Gradle state root."""
    override = environment.get("FORGE_GRADLE_USER_HOME")
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(tempfile.gettempdir(), "metadata-forge-gradle")


def resolve_pi_state_root(environment: Mapping[str, str]) -> str:
    """Return Pi's writable agent state directory."""
    override = environment.get("PI_CODING_AGENT_DIR")
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(os.path.expanduser(environment.get("HOME", "~")), ".pi", "agent")


def resolve_codex_state_root(environment: Mapping[str, str]) -> str:
    """Return Codex's writable state directory."""
    override = environment.get("CODEX_HOME")
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(os.path.expanduser(environment.get("HOME", "~")), ".codex")


def codex_doctor_provider_status(output: str) -> tuple[bool, str]:
    """Extract the provider reachability result from `codex doctor --json`."""
    try:
        payload = json.loads(output)
        check = payload["checks"]["network.provider_reachability"]
        status = str(check["status"])
        summary = str(check["summary"])
    except (json.JSONDecodeError, KeyError, TypeError):
        return False, "codex doctor did not return provider reachability data"
    return status == "ok", f"status={status}, {summary}"


def codex_unattended_policy_status(codex_home: str) -> tuple[bool, str]:
    """Detect cached enterprise requirements incompatible with Forge Codex exec."""
    bundle_path = os.path.join(codex_home, "cloud-config-bundle-cache.json")
    if not os.path.isfile(bundle_path):
        return True, "no cached enterprise requirements conflict was found"
    try:
        with open(bundle_path, "rb") as bundle_file:
            bundle = json.load(bundle_file)
        requirement_entries = bundle.get("requirements_toml", {}).get("enterprise_managed", [])
    except (OSError, json.JSONDecodeError, AttributeError):
        return False, f"could not read managed Codex requirements from {bundle_path}"

    conflicts: list[str] = []
    policies: list[str] = []
    for entry in requirement_entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("contents"), str):
            continue
        name = str(entry.get("name") or entry.get("id") or "enterprise policy")
        try:
            requirements = tomllib.loads(entry["contents"])
        except tomllib.TOMLDecodeError:
            conflicts.append(f"{name}: requirements TOML is invalid")
            continue
        approvals = requirements.get("allowed_approval_policies")
        sandboxes = requirements.get("allowed_sandbox_modes")
        policies.append(name)
        if isinstance(approvals, list) and "never" not in approvals:
            conflicts.append(f"{name}: approval policy `never` is disallowed ({approvals})")
        if isinstance(sandboxes, list) and "danger-full-access" not in sandboxes:
            conflicts.append(f"{name}: sandbox `danger-full-access` is disallowed ({sandboxes})")

    if conflicts:
        return False, "; ".join(conflicts)
    if policies:
        return True, f"managed policies compatible with unattended recovery: {', '.join(policies)}"
    return True, "no cached enterprise requirements conflict was found"


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    """Parse host-requirements CLI arguments."""
    parser = argparse.ArgumentParser(description="Validate Forge host requirements before work starts.")
    parser.add_argument("--forge-dir", required=True, help="Absolute or relative path to the Forge directory.")
    parser.add_argument(
        "--reachability-metadata-path",
        default=None,
        help=(
            "Reachability-metadata checkout this run operates on. Defaults to the checkout that "
            "contains --forge-dir."
        ),
    )
    parser.add_argument(
        "--mode",
        choices=("queues", "coverage"),
        default="queues",
        help=(
            "Which capabilities to require. `queues` (the default) derives them from the effective "
            "queue limits; `coverage` selects the code-coverage-improvement lane, which needs the "
            "build toolchain but takes GraalVM from GRAALVM_HOME, or from JAVA_HOME when that is "
            "unset, at JDK 25 or newer."
        ),
    )
    parser.add_argument("--python-bin", default=sys.executable, help="Python interpreter selected by do-work.")
    parser.add_argument("--review-model", default=DEFAULT_REVIEW_MODEL, help="Pi model used for PR reviews.")
    parser.add_argument(
        "--graalvm-version-check",
        choices=GRAALVM_VERSION_CHECK_MODES,
        default=None,
        help=(
            "How to treat a GraalVM version mismatch: `strict` stops the worker, `warn` reports it, "
            "`off` skips the version match. Native Image and the reachability-metadata schema stay "
            f"mandatory in every mode. Defaults to {GRAALVM_VERSION_CHECK_ENV_VAR}, "
            f"then {DEFAULT_GRAALVM_VERSION_CHECK}."
        ),
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    """Run the host-requirements CLI."""
    args = parse_args(argv)
    try:
        host_requirements = HostRequirements(
            args.forge_dir,
            args.python_bin,
            args.review_model,
            requirements=COVERAGE_REQUIREMENTS if args.mode == "coverage" else None,
            graalvm_version_check=resolve_graalvm_version_check(args.graalvm_version_check),
            repo_dir=args.reachability_metadata_path,
        )
    except ValueError as exc:
        print(f"ERROR: Forge host requirement configuration is invalid: {exc}", file=sys.stderr)
        print(
            "Fix: set every FORGE_*_LIMIT value to a non-negative integer and "
            f"{GRAALVM_VERSION_CHECK_ENV_VAR} to {', '.join(GRAALVM_VERSION_CHECK_MODES)}.",
            file=sys.stderr,
        )
        return 1
    return 0 if host_requirements.run() else 1


if __name__ == "__main__":
    sys.exit(main())
