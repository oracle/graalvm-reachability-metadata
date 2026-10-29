# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Validate every host capability the invoked Forge mode or enabled queue requires."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from dataclasses import dataclass
from typing import Mapping, Sequence

FORGE_PYTHON_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if FORGE_PYTHON_ROOT not in sys.path:
    sys.path.insert(0, FORGE_PYTHON_ROOT)

from ai_workflows.agents.agent_runtime import (  # noqa: E402
    SUPPORTED_AGENT_BACKENDS,
    get_analysis_agent,
    get_setup_agent,
    default_agent_for_backend,
)
from utility_scripts.host_access_checks import (  # noqa: E402
    AccessChecks,
    NETWORK_HOSTS_GITHUB,
    PROJECT_NUMBER,
    REPOSITORY_NAME,
    REPOSITORY_OWNER,
)
from utility_scripts.host_graalvm_checks import (  # noqa: E402
    DEFAULT_GRAALVM_VERSION_CHECK,
    GRAALVM_SCHEMA_PATH,
    GRAALVM_VERSION_CHECK_ENV_VAR,
    GRAALVM_VERSION_CHECK_MODES,
    GraalVMEnvironmentChecks,
    GraalVMVersions,
    ISSUE_GRAALVM_ENV_VARS,
    resolve_graalvm_version_check,
)
from utility_scripts.host_probes import ProbeResult, is_github_transient, probe_tcp_host  # noqa: E402
from utility_scripts.host_toolchain_checks import ToolchainChecks  # noqa: E402


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
        """Return whether this run builds and tests libraries locally."""
        return self.issue_work or self.coverage_work


#: The standalone coverage launcher needs build and publication capabilities,
#: but only one unpinned GraalVM lane (§FS-forge-host-requirements).
COVERAGE_REQUIREMENTS = QueueRequirements(
    issue_work=False,
    review_work=False,
    github_work=True,
    coverage_work=True,
)
#: `EX_TEMPFAIL`: every failed check is transient, so a launcher may wait and check again
#: (§FS-forge-host-requirements.1).
TRANSIENT_FAILURE_EXIT_STATUS = 75


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
    # A failure that clears on its own rather than a host fault (§FS-forge-host-requirements.1).
    transient: bool = False

    @property
    def status(self) -> str:
        """Return the display status for this result."""
        if not self.required:
            return "SKIP"
        if self.passed is None:
            return "INFO"
        if self.passed:
            return "PASS"
        if self.advisory:
            return "WARN"
        return "RETRY" if self.transient else "FAIL"

    @property
    def blocks_work(self) -> bool:
        """Return whether this result must stop the worker before any work starts."""
        return self.required and self.passed is False and not self.advisory


@dataclass(frozen=True)
class AgentRequirement:
    """One executable, family, and model required by an enabled runtime role."""

    family: str
    agent: str
    model: str


class HostRequirements(GraalVMEnvironmentChecks, ToolchainChecks, AccessChecks):
    """Collect and print Forge host requirements without invoking a model.

    §FS-forge-host-requirements
    """

    def __init__(
            self,
            forge_dir: str,
            python_bin: str,
            environment: Mapping[str, str] | None = None,
            requirements: QueueRequirements | None = None,
            graalvm_version_check: str | None = None,
            repo_dir: str | None = None,
            analysis_agent: str | None = None,
            analysis_family: str | None = None,
            analysis_model: str | None = None,
            analysis_provider: str | None = None,
            setup_agent: str | None = None,
            setup_family: str | None = None,
            setup_model: str | None = None,
            setup_provider: str | None = None,
            review_agent: str | None = None,
            review_model: str | None = None,
    ) -> None:
        self.forge_dir = os.path.abspath(forge_dir)
        self.forge_repo_dir = os.path.dirname(self.forge_dir)
        # The repository a run operates on can be a different checkout than the one that
        # contains Forge, so each set of paths is checked for what it actually owns.
        self.repo_dir = os.path.abspath(repo_dir) if repo_dir else self.forge_repo_dir
        self.python_bin = python_bin
        self.environment = dict(os.environ if environment is None else environment)
        # Command-line values are layered onto the environment so the probe
        # resolves each role exactly the way the run will
        # (§FS-forge-agent-runtime-selection) instead of restating the rules.
        selected_env = dict(self.environment)
        for variable, value in (
                ("FORGE_ANALYSIS_AGENT", analysis_agent),
                ("FORGE_ANALYSIS_FAMILY", analysis_family),
                ("FORGE_ANALYSIS_MODEL", analysis_model),
                ("FORGE_ANALYSIS_PROVIDER", analysis_provider),
                ("FORGE_SETUP_AGENT", setup_agent),
                ("FORGE_SETUP_FAMILY", setup_family),
                ("FORGE_SETUP_MODEL", setup_model),
                ("FORGE_SETUP_PROVIDER", setup_provider),
        ):
            if value:
                selected_env[variable] = value
        analysis_selection = get_analysis_agent(selected_env)
        self.analysis_family = analysis_selection.backend
        self.analysis_agent = analysis_selection.agent
        self.analysis_model = analysis_selection.model
        self.analysis_provider = analysis_selection.provider
        setup_selection = get_setup_agent(selected_env)
        self.setup_family = setup_selection.backend
        self.setup_agent = setup_selection.agent
        self.setup_model = setup_selection.model
        self.setup_provider = setup_selection.provider
        # A run has no test role: the worker, checked as analysis, writes the tests. The review
        # is always a Claude Code target and exists only where a launcher names its model.
        # §FS-forge-agent-runtime-selection
        self.review_requirement: AgentRequirement | None = (
            AgentRequirement(
                "claude-code", review_agent or default_agent_for_backend("claude-code"), review_model
            )
            if review_model
            else None
        )
        self.requirements = (
            resolve_queue_requirements(self.environment)
            if requirements is None
            else requirements
        )
        self.graalvm_version_check = resolve_graalvm_version_check(graalvm_version_check, self.environment)
        self.graalvm_versions = GraalVMVersions(None, None, None, None)
        self.results: list[CheckResult] = []
        # One reachability probe per host and gate, shared by the checks that depend on it.
        self.host_probes: dict[str, ProbeResult] = {}

    @property
    def review_role(self) -> AgentRequirement | None:
        """Return the review agent when this run reviews pull requests, else None."""
        return self.review_requirement if self.requirements.review_work else None

    @property
    def version_check_advisory(self) -> bool:
        """Return whether GraalVM version mismatches only warn instead of stopping work."""
        return self.graalvm_version_check == "warn"

    @property
    def separate_repository(self) -> bool:
        """Return whether this run operates on a checkout other than the one holding Forge."""
        return os.path.realpath(self.repo_dir) != os.path.realpath(self.forge_repo_dir)

    def _probe_host(self, host: str) -> ProbeResult:
        """Probe `host:443` once per gate over the route Forge's tools take."""
        if host not in self.host_probes:
            self.host_probes[host] = probe_tcp_host(host, 443, environment=self.environment)
        return self.host_probes[host]

    def _endpoint_down(self, *hosts: str) -> bool:
        """Return whether any of these hosts cannot be reached right now.

        A check that fails while the endpoint it needs is down reports the outage,
        not the host, so its failure is transient (§FS-forge-host-requirements.1).
        """
        return any(self._probe_host(host).transient for host in hosts)

    def _github_failure_transient(self, result: subprocess.CompletedProcess[str]) -> bool:
        """Return whether a failed `gh` or GitHub git call is transient rather than a host fault.

        A rate limit, a GitHub server or network error, or GitHub being unreachable
        clears on its own (§FS-forge-host-requirements.1).
        """
        return result.returncode != 0 and (
            is_github_transient(result) or self._endpoint_down(*NETWORK_HOSTS_GITHUB)
        )

    def run(self, verbose: bool = False) -> bool:
        """Run selected checks, printing details when requested or work is blocked."""
        if self.requirements.issue_work and self.graalvm_version_check != "off":
            self.graalvm_versions = self._resolve_graalvm_versions()
        self._check_tools()
        self._check_environment()
        self._check_write_permissions()
        self._check_network()
        self._check_github()
        self._check_selected_agents()
        self._check_docker()
        failures: list[CheckResult] = [result for result in self.results if result.blocks_work]
        if verbose or any(not result.transient for result in failures):
            self._print_manifest()
            self._print_results()
        elif failures:
            # A launcher reruns the gate while it waits, so only the failures are printed.
            self._print_failures(failures)
        elif any(result.status == "WARN" for result in self.results):
            self._print_warnings()
        return not failures

    def exit_status(self) -> int:
        """Return 0 when work may start, 75 when every failure is transient, else 1.

        §FS-forge-host-requirements.1
        """
        failures: list[CheckResult] = [result for result in self.results if result.blocks_work]
        if not failures:
            return 0
        return TRANSIENT_FAILURE_EXIT_STATUS if all(result.transient for result in failures) else 1

    def _print_manifest(self) -> None:
        issue_text = "enabled" if self.requirements.issue_work else "disabled"
        coverage_text = "enabled" if self.requirements.coverage_work else "disabled"
        review_text = "enabled" if self.requirements.review_work else "disabled"
        print("[forge-host] Deterministic host requirements")
        print(
            f"[forge-host] Modes: issue work={issue_text}, coverage work={coverage_text}, "
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
                github_operations.extend((
                    "assign/label/comment issues",
                    f"push generated branches to {REPOSITORY_OWNER}/{REPOSITORY_NAME}",
                ))
            if self.requirements.review_work:
                github_operations.extend(("submit reviews", "merge eligible PRs"))
            print(f"  - GitHub operations: {', '.join(github_operations)}")
        else:
            print("  - GitHub: no live GitHub access is required by this run")
        print("  - Network: outbound access to every host and port listed in the checks below")
        proxy_url = self.environment.get("https_proxy") or self.environment.get("HTTPS_PROXY")
        if proxy_url:
            print(f"  - Network route: HTTPS reachability is checked through the configured proxy {proxy_url}")
        print(
            f"  - Analysis agent: {self.analysis_agent} model={self.analysis_model}, "
            "offline repository tools"
        )
        if self.requirements.issue_work:
            print(
                f"  - Setup agent: {self.setup_agent} model={self.setup_model}, "
                "network access for URL discovery"
            )
        if self.review_role is not None:
            print(
                f"  - Review agent: {self.review_role.agent} model={self.review_role.model}, "
                "network access for the GraalVM history"
            )
        if self.requirements.build_work:
            print("  - Docker: access to the Docker daemon and configured image registries")
        print("[forge-host] Required environment:")
        if self.requirements.issue_work:
            for variable in ISSUE_GRAALVM_ENV_VARS:
                print(f"  - {variable}={self._required_graalvm_description(variable)}")
            print(f"  - Every GraalVM must load native-image-agent and contain {GRAALVM_SCHEMA_PATH}")
            print("  - Forge pins JAVA_HOME and every Gradle Java selector to GRAALVM_HOME")
        elif self.requirements.coverage_work:
            print(
                "  - GRAALVM_HOME=<GraalVM JDK 25 or newer with Native Image>; "
                "unset takes the value of JAVA_HOME"
            )
            print(
                f"  - The selected GraalVM must load native-image-agent and contain {GRAALVM_SCHEMA_PATH}"
            )
        elif self.requirements.review_work:
            print("  - JAVA_HOME=<JDK 25 with executable bin/java> (GRAALVM_HOME is not required for review-only work)")
        print(f"  - FORGE_ANALYSIS_AGENT={self.analysis_agent}")
        print(f"  - FORGE_ANALYSIS_MODEL={self.analysis_model}")
        if self.requirements.issue_work:
            print(f"  - FORGE_SETUP_AGENT={self.setup_agent}")
            print(f"  - FORGE_SETUP_MODEL={self.setup_model}")
            print(f"  - GraalVM version match: {self._version_check_description()}")

    def _add(
            self,
            category: str,
            name: str,
            required: bool,
            passed: bool | None,
            detail: str,
            remediation: str = "",
            advisory: bool = False,
            transient: bool = False,
    ) -> None:
        self.results.append(
            CheckResult(category, name, required, passed, detail, remediation, advisory, transient)
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
            self._print_failure_summary(failures)
            return
        if warnings:
            print(f"[forge-host] PASS with {len(warnings)} warning(s); work may start.")
        else:
            print("[forge-host] PASS: all required host checks succeeded; work may start.")

    def _print_failures(self, failures: list[CheckResult]) -> None:
        """Print only the failed checks and their fixes."""
        for result in failures:
            print(f"  [{result.status}] {result.category}: {result.name} — {result.detail}")
            if result.remediation:
                print(f"         Fix: {result.remediation}")
        self._print_failure_summary(failures)

    @staticmethod
    def _print_failure_summary(failures: list[CheckResult]) -> None:
        sys.stdout.flush()
        if all(result.transient for result in failures):
            print(f"RETRY: {len(failures)} required check(s) failed transiently. No work was started.")
        else:
            print(f"ERROR: {len(failures)} required check(s) failed. No work was started.")

    def _print_warnings(self) -> None:
        """Print only non-blocking warnings when the successful report is compact."""
        warnings = [result for result in self.results if result.status == "WARN"]
        print("[forge-host] Host requirement warnings:")
        for result in warnings:
            print(f"  [WARN] {result.category}: {result.name} — {result.detail}")
        print(f"[forge-host] PASS with {len(warnings)} warning(s); work may start.")


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
            "Which capabilities to require. `queues` derives them from the effective queue limits; "
            "`coverage` selects the standalone code-coverage launcher requirements."
        ),
    )
    parser.add_argument("--python-bin", default=sys.executable, help="Python interpreter selected by do-work.")
    parser.add_argument("--analysis-agent", default=None)
    parser.add_argument("--analysis-family", choices=SUPPORTED_AGENT_BACKENDS, default=None)
    parser.add_argument("--analysis-model", default=None)
    parser.add_argument("--analysis-provider", default=None)
    parser.add_argument("--setup-agent", default=None)
    parser.add_argument("--setup-family", choices=SUPPORTED_AGENT_BACKENDS, default=None)
    parser.add_argument("--setup-model", default=None)
    parser.add_argument("--setup-provider", default=None)
    parser.add_argument("--review-agent", default=None, help="Claude Code executable of the PR review.")
    parser.add_argument("--review-model", default=None, help="Model of the PR review; selects the role.")
    parser.add_argument("-v", "--verbose", action="store_true", help="Print the complete host report.")
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
            requirements=COVERAGE_REQUIREMENTS if args.mode == "coverage" else None,
            graalvm_version_check=resolve_graalvm_version_check(args.graalvm_version_check),
            repo_dir=args.reachability_metadata_path,
            analysis_agent=args.analysis_agent,
            analysis_family=args.analysis_family,
            analysis_model=args.analysis_model,
            analysis_provider=args.analysis_provider,
            setup_agent=args.setup_agent,
            setup_family=args.setup_family,
            setup_model=args.setup_model,
            setup_provider=args.setup_provider,
            review_agent=args.review_agent,
            review_model=args.review_model,
        )
    except ValueError as exc:
        print(f"ERROR: Forge host requirement configuration is invalid: {exc}", file=sys.stderr)
        print(
            "Fix: set every FORGE_*_LIMIT value to a non-negative integer and "
            f"{GRAALVM_VERSION_CHECK_ENV_VAR} to {', '.join(GRAALVM_VERSION_CHECK_MODES)}.",
            file=sys.stderr,
        )
        return 1
    host_requirements.run(verbose=args.verbose)
    return host_requirements.exit_status()


if __name__ == "__main__":
    sys.exit(main())
