# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Validate every host capability the invoked Forge mode or enabled queue requires."""

from __future__ import annotations

import argparse
import os
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
    resolve_provider,
    default_agent_for_backend,
    default_model_for_backend,
    normalize_backend_name,
)
from utility_scripts.host_access_checks import (  # noqa: E402
    AccessChecks,
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
from utility_scripts.host_toolchain_checks import ToolchainChecks  # noqa: E402
from utility_scripts.strategy_loader import (  # noqa: E402
    apply_test_agent_alias,
    load_predefined_strategies,
)


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
class AgentRequirement:
    """One executable, family, and model required by an enabled runtime role."""

    family: str
    agent: str
    model: str
    strategy_name: str | None = None
    provider: str | None = None


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
            test_strategy_name: str | None = None,
            test_strategy_names: Sequence[str] | None = None,
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
        requested_strategy_names = list(test_strategy_names or [])
        if test_strategy_name and test_strategy_name not in requested_strategy_names:
            requested_strategy_names.append(test_strategy_name)
        if not requested_strategy_names and self.environment.get("FORGE_STRATEGY_NAME"):
            requested_strategy_names.append(str(self.environment["FORGE_STRATEGY_NAME"]))
        configured_test_strategies = [
            (strategy_name, self._load_test_strategy(strategy_name))
            for strategy_name in requested_strategy_names
        ] or [(None, {})]
        self.test_requirements = self._resolve_test_requirements(
            configured_test_strategies,
        )
        primary_test_requirement = self.test_requirements[0]
        self.test_family = primary_test_requirement.family
        self.test_agent = primary_test_requirement.agent
        self.test_model = primary_test_requirement.model
        self.requirements = (
            resolve_queue_requirements(self.environment)
            if requirements is None
            else requirements
        )
        self.graalvm_version_check = resolve_graalvm_version_check(graalvm_version_check, self.environment)
        self.graalvm_versions = GraalVMVersions(None, None, None)
        self.results: list[CheckResult] = []

    def _load_test_strategy(self, strategy_name: str | None) -> dict:
        """Load the selected strategy, which owns the test role it declares."""
        if not strategy_name:
            return {}
        for strategy in load_predefined_strategies():
            if strategy.get("name") == strategy_name:
                return apply_test_agent_alias(strategy, self.environment)
        raise ValueError(f"Unknown Forge test strategy '{strategy_name}'")

    def _resolve_test_requirements(
            self,
            configured_strategies: Sequence[tuple[str | None, dict]],
    ) -> list[AgentRequirement]:
        """Resolve and deduplicate the test roles the enabled strategies declare.

        The bundle is the only source: nothing outside it retargets the test role
        (§FS-forge-agent-runtime-selection), so the probe checks the backend that
        the named strategy will actually run.
        """
        requirements: list[AgentRequirement] = []
        seen: set[tuple[str, str, str, str | None]] = set()
        for strategy_name, strategy in configured_strategies:
            family = normalize_backend_name(
                strategy.get("agent-family") or strategy.get("agent") or "pi"
            )
            agent = strategy.get("agent-command") or default_agent_for_backend(family)
            model = strategy.get("model") or default_model_for_backend(family)
            provider = resolve_provider(family, strategy.get("provider"))
            key = (family, str(agent), str(model), provider)
            if key in seen:
                continue
            seen.add(key)
            requirements.append(
                AgentRequirement(
                    family, str(agent), str(model),
                    strategy_name=strategy_name, provider=provider,
                )
            )
        return requirements

    @property
    def version_check_advisory(self) -> bool:
        """Return whether GraalVM version mismatches only warn instead of stopping work."""
        return self.graalvm_version_check == "warn"

    @property
    def separate_repository(self) -> bool:
        """Return whether this run operates on a checkout other than the one holding Forge."""
        return os.path.realpath(self.repo_dir) != os.path.realpath(self.forge_repo_dir)

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
        passed = not any(result.blocks_work for result in self.results)
        if verbose or not passed:
            self._print_manifest()
            self._print_results()
        elif any(result.status == "WARN" for result in self.results):
            self._print_warnings()
        return passed

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
        if self.requirements.issue_work:
            for test_requirement in self.test_requirements:
                strategy_suffix = (
                    f" strategy={test_requirement.strategy_name}"
                    if test_requirement.strategy_name
                    else ""
                )
                print(
                    f"  - Test agent: {test_requirement.agent} "
                    f"model={test_requirement.model}{strategy_suffix}, offline repository tools"
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
    ) -> None:
        self.results.append(CheckResult(category, name, required, passed, detail, remediation, advisory))

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

    def _print_warnings(self) -> None:
        """Print only non-blocking warnings when the successful report is compact."""
        warnings = [result for result in self.results if result.status == "WARN"]
        print("[forge-host] Host requirement warnings:")
        for result in warnings:
            print(f"  [WARN] {result.category}: {result.name} — {result.detail}")
        print(f"[forge-host] PASS with {len(warnings)} warning(s); work may start.")


def verify_host_requirements(
        forge_dir: str,
        python_bin: str | None = None,
        requirements: QueueRequirements | None = None,
        graalvm_version_check: str | None = None,
        environment: Mapping[str, str] | None = None,
        repo_dir: str | None = None,
        test_strategy_name: str | None = None,
        test_strategy_names: Sequence[str] | None = None,
        verbose: bool = False,
) -> bool:
    """Run every host requirement selected by this run and report whether work may start.

    `repo_dir` is the reachability-metadata checkout the run operates on; it defaults to the
    checkout that contains `forge_dir` (§FS-forge-host-requirements).
    """
    host_requirements = HostRequirements(
        forge_dir,
        python_bin or sys.executable,
        environment,
        requirements,
        graalvm_version_check,
        repo_dir,
        test_strategy_name=test_strategy_name,
        test_strategy_names=test_strategy_names,
    )
    return host_requirements.run(verbose=verbose)


def ensure_host_requirements(
        forge_dir: str,
        python_bin: str | None = None,
        requirements: QueueRequirements | None = None,
        graalvm_version_check: str | None = None,
        environment: Mapping[str, str] | None = None,
        repo_dir: str | None = None,
        test_strategy_name: str | None = None,
        test_strategy_names: Sequence[str] | None = None,
        verbose: bool = False,
) -> None:
    """Stop the process with a non-zero exit when a required host capability is missing.

    §FS-forge-host-requirements
    """
    passed = verify_host_requirements(
        forge_dir,
        python_bin,
        requirements,
        graalvm_version_check,
        environment,
        repo_dir,
        test_strategy_name,
        test_strategy_names,
        verbose,
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
    parser.add_argument("--test-strategy", action="append", default=None)
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
            test_strategy_names=args.test_strategy,
        )
    except ValueError as exc:
        print(f"ERROR: Forge host requirement configuration is invalid: {exc}", file=sys.stderr)
        print(
            "Fix: set every FORGE_*_LIMIT value to a non-negative integer and "
            f"{GRAALVM_VERSION_CHECK_ENV_VAR} to {', '.join(GRAALVM_VERSION_CHECK_MODES)}.",
            file=sys.stderr,
        )
        return 1
    return 0 if host_requirements.run(verbose=args.verbose) else 1


if __name__ == "__main__":
    sys.exit(main())
