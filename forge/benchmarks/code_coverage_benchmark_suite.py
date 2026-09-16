# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The fixed benchmark suite model and deterministic matrix expansion.

Checked-in suite loading with semantic validation, and the filtered
library/configuration/thinking/strategy cross-product
(§FS-code-coverage-benchmarking.1 §AR-code-coverage-benchmarking).
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from ai_workflows.agents.agent_runtime import PROVIDER_AWARE_BACKENDS
from benchmarks.code_coverage_benchmark_common import (
    FORGE_ROOT,
    BenchmarkError,
    _read_json,
    _validate,
)

SUITE_PATH = FORGE_ROOT / "benchmarks" / "code_coverage_suite.json"
SUITE_SCHEMA_PATH = (
    FORGE_ROOT / "schemas" / "code_coverage_benchmark_suite_schema.json"
)
KNOWN_STRATEGIES = ("guided", "naive")


@dataclass(frozen=True)
class Library:
    """One fixed benchmark subject."""

    index: int
    coordinate: str
    covered_methods: int
    all_methods: int
    coverage_percent: float


@dataclass(frozen=True)
class AgentConfiguration:
    """One configured agent/model pair."""

    agent: str
    configured_model: str
    provider: str
    target_model: str

    def target(self, thinking: str) -> str:
        """Render the Rhei target selector for this configuration.

        Rhei hands everything after the first colon to the agent CLI's model
        flag, so only a provider-aware backend may carry a `<provider>/`
        prefix. Claude Code and Codex reach their provider through their own
        login and reject a prefixed model outright.
        """
        model: str = (
            f"{self.provider}/{self.target_model}"
            if self.agent in PROVIDER_AWARE_BACKENDS
            else self.target_model
        )
        return f"{self.agent}[{thinking}]:{model}"

    def analysis_role_environment(self, thinking: str) -> dict[str, str]:
        """Render the `FORGE_ANALYSIS_*` variables that pin the repair agent.

        A cell runs on one configuration, repairs included, so the analysis
        role resolves to the cell's own agent rather than to whatever the
        launching machine happens to export
        (§FS-code-coverage-benchmarking.2). The executable is left unset so
        the backend's registered default applies, and the provider is sent
        only where it means something (§FS-forge-agent-runtime-selection).
        """
        environment: dict[str, str] = {
            "FORGE_ANALYSIS_FAMILY": self.agent,
            "FORGE_ANALYSIS_MODEL": self.target_model,
            "FORGE_ANALYSIS_THINKING_LEVEL": thinking,
        }
        if self.agent in PROVIDER_AWARE_BACKENDS:
            environment["FORGE_ANALYSIS_PROVIDER"] = self.provider
        return environment


@dataclass(frozen=True)
class MatrixCell:
    """One library/configuration/thinking/strategy execution."""

    library: Library
    configuration: AgentConfiguration
    thinking: str
    # Strategy arm: the guided workflow or the naive baseline
    # (§AR-code-coverage-benchmarking.2, §AR-code-coverage-benchmarking.3).
    strategy: str = "guided"


@dataclass(frozen=True)
class Suite:
    """Validated checked-in benchmark configuration."""

    commit: str
    issue_number: int
    workspace_name: str
    libraries: tuple[Library, ...]
    configurations: tuple[AgentConfiguration, ...]
    thinking_levels: tuple[str, ...]


def load_suite(path: Path = SUITE_PATH) -> Suite:
    """Load and semantically validate the fixed benchmark suite."""
    raw: dict[str, Any] = _read_json(path)
    _validate(raw, SUITE_SCHEMA_PATH)
    libraries = tuple(
        Library(
            index=int(item["index"]),
            coordinate=str(item["coordinate"]),
            covered_methods=int(item["coveredMethods"]),
            all_methods=int(item["allMethods"]),
            coverage_percent=float(item["coveragePercent"]),
        )
        for item in raw["libraries"]
    )
    configurations = tuple(
        AgentConfiguration(
            agent=str(item["agent"]),
            configured_model=str(item["configuredModel"]),
            provider=str(item["provider"]),
            target_model=str(item["targetModel"]),
        )
        for item in raw["configurations"]
    )
    indexes = [library.index for library in libraries]
    if indexes != list(range(1, len(libraries) + 1)):
        raise BenchmarkError("Library indexes must be consecutive and ordered.")
    if len({library.coordinate for library in libraries}) != len(libraries):
        raise BenchmarkError("Benchmark library coordinates must be unique.")
    config_keys = {
        (configuration.agent, configuration.configured_model)
        for configuration in configurations
    }
    if len(config_keys) != len(configurations):
        raise BenchmarkError("Benchmark agent/model configurations must be unique.")
    for library in libraries:
        expected = round(100.0 * library.covered_methods / library.all_methods, 2)
        if expected != library.coverage_percent:
            raise BenchmarkError(
                f"Checked-in coverage for {library.coordinate} is inconsistent."
            )
    return Suite(
        commit=str(raw["benchmarkSuiteCommit"]),
        issue_number=int(raw["syntheticIssueNumber"]),
        workspace_name=str(raw["workspaceName"]),
        libraries=libraries,
        configurations=configurations,
        thinking_levels=tuple(str(value) for value in raw["thinkingLevels"]),
    )


def _reject_duplicates(values: list[Any] | None, label: str) -> None:
    if values is not None and len(values) != len(set(values)):
        raise BenchmarkError(f"Duplicate {label} selection.")


def expand_matrix(
        suite: Suite,
        library_indexes: list[int] | None = None,
        agents: list[str] | None = None,
        models: list[str] | None = None,
        thinking_levels: list[str] | None = None,
        strategies: list[str] | None = None,
) -> list[MatrixCell]:
    """Return the filtered cross-product after validating all selections.

    The default matrix stays the guided arm; naive baseline cells are selected
    explicitly (§AR-code-coverage-benchmarking.2).
    """
    _reject_duplicates(library_indexes, "library index")
    _reject_duplicates(agents, "agent")
    _reject_duplicates(models, "model")
    _reject_duplicates(thinking_levels, "thinking")
    _reject_duplicates(strategies, "strategy")

    known_indexes = {library.index for library in suite.libraries}
    known_agents = {configuration.agent for configuration in suite.configurations}
    known_models = {
        configuration.configured_model
        for configuration in suite.configurations
    }
    known_thinking = set(suite.thinking_levels)
    for selected, known, label in (
        (library_indexes, known_indexes, "library index"),
        (agents, known_agents, "agent"),
        (models, known_models, "model"),
        (thinking_levels, known_thinking, "thinking"),
        (strategies, set(KNOWN_STRATEGIES), "strategy"),
    ):
        unknown = set(selected or []) - known
        if unknown:
            values = ", ".join(str(value) for value in sorted(unknown))
            raise BenchmarkError(f"Unknown {label} selection: {values}.")

    selected_libraries = [
        library
        for library in suite.libraries
        if library_indexes is None or library.index in library_indexes
    ]
    selected_configurations = [
        configuration
        for configuration in suite.configurations
        if (agents is None or configuration.agent in agents)
        and (
            models is None
            or configuration.configured_model in models
        )
    ]
    if agents is not None and models is not None:
        for agent in agents:
            if not any(
                    configuration.agent == agent
                    for configuration in selected_configurations
            ):
                raise BenchmarkError(
                    f"Selected models are incompatible with agent '{agent}'."
                )
        for model in models:
            if not any(
                    configuration.configured_model == model
                    for configuration in selected_configurations
            ):
                raise BenchmarkError(
                    f"Model '{model}' is incompatible with the selected agents."
                )
    selected_thinking = [
        thinking
        for thinking in suite.thinking_levels
        if thinking_levels is None or thinking in thinking_levels
    ]
    selected_strategies = list(strategies) if strategies is not None else ["guided"]
    return [
        MatrixCell(library, configuration, thinking, strategy)
        for library in selected_libraries
        for configuration in selected_configurations
        for thinking in selected_thinking
        for strategy in selected_strategies
    ]


def print_matrix(cells: list[MatrixCell]) -> None:
    """Print every selected cell before any repository mutation."""
    print(f"Selected {len(cells)} code coverage benchmark execution(s):")
    for ordinal, cell in enumerate(cells, start=1):
        print(
            f"  {ordinal:>2}. library={cell.library.index} "
            f"{cell.library.coordinate} agent={cell.configuration.agent} "
            f"model={cell.configuration.configured_model} "
            f"thinking={cell.thinking} strategy={cell.strategy}"
        )
