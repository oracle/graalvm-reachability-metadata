# Copyright and related rights waived via CC0

"""Runtime selection and one-shot execution for Forge agent roles.

The role boundary implements §FS-forge-agent-runtime-selection. Backends are
selected at runtime while workflows continue to depend only on ``Agent``.
"""

from __future__ import annotations

from dataclasses import dataclass
import os

from ai_workflows.agents.agent import Agent
from utility_scripts.task_logs import build_task_log_path


SUPPORTED_AGENT_BACKENDS = ("claude-code", "pi", "codex", "opencode")
DEFAULT_ANALYSIS_AGENT = "codex"
DEFAULT_ANALYSIS_MODEL = "gpt-5.6-luna"
DEFAULT_MODEL_BY_BACKEND = {
    "claude-code": "sonnet",
    "codex": "gpt-5.6-luna",
}
GITHUB_CREDENTIAL_ENV_VARS = (
    "GH_TOKEN",
    "GITHUB_TOKEN",
    "GH_ENTERPRISE_TOKEN",
    "GITHUB_ENTERPRISE_TOKEN",
)


@dataclass(frozen=True)
class AgentSelection:
    """One effective role selection."""

    backend: str
    model: str
    family: str | None = None
    thinking_level: str | None = None
    agent: str | None = None


@dataclass(frozen=True)
class AgentRunResult:
    """Result of a one-shot backend-neutral agent invocation."""

    return_code: int
    log_path: str
    timed_out: bool
    response: str = ""


def agent_process_environment(
        environment: dict[str, str] | None = None,
) -> dict[str, str]:
    """Return an agent environment without GitHub credential channels."""
    source = os.environ if environment is None else environment
    sanitized = dict(source)
    for variable in GITHUB_CREDENTIAL_ENV_VARS:
        sanitized.pop(variable, None)
    sanitized["GH_CONFIG_DIR"] = "/nonexistent/forge-agent-no-github-config"
    sanitized["GH_PROMPT_DISABLED"] = "1"
    return sanitized


def normalize_backend_name(value: str) -> str:
    """Return a canonical backend key or raise a concise configuration error."""
    normalized = value.strip().lower().replace("_", "-")
    aliases = {
        "claude": "claude-code",
        "open-code": "opencode",
    }
    normalized = aliases.get(normalized, normalized)
    if normalized not in SUPPORTED_AGENT_BACKENDS:
        choices = ", ".join(SUPPORTED_AGENT_BACKENDS)
        raise ValueError(f"Unknown agent backend '{value}'. Supported backends: {choices}")
    return normalized


def default_model_for_backend(backend: str, fallback: str = "gpt-5.6-terra") -> str:
    """Return the backend-aware model default used for an explicit role selection."""
    return DEFAULT_MODEL_BY_BACKEND.get(normalize_backend_name(backend), fallback)


def analysis_agent_selection(environment: dict[str, str] | None = None) -> AgentSelection:
    """Resolve the analysis role from the worker environment."""
    env = os.environ if environment is None else environment
    family = (
        env.get("FORGE_ANALYSIS_FAMILY")
        or env.get("FORGE_ANALYSIS_AGENT_FAMILY")
        or (
            env.get("FORGE_ANALYSIS_AGENT")
            if env.get("FORGE_ANALYSIS_AGENT") in SUPPORTED_AGENT_BACKENDS
            else None
        )
        or env.get("FORGE_AGENT_FAMILY")
        or DEFAULT_ANALYSIS_AGENT
    )
    backend = normalize_backend_name(family)
    agent = env.get("FORGE_ANALYSIS_AGENT") or backend
    model = env.get("FORGE_ANALYSIS_MODEL") or default_model_for_backend(backend)
    thinking_level = env.get("FORGE_ANALYSIS_THINKING_LEVEL") or (
        "high" if backend == "codex" and model == "gpt-5.6-luna" else None
    )
    return AgentSelection(
        backend=backend,
        model=model,
        family=backend,
        thinking_level=thinking_level,
        agent=agent,
    )


def apply_test_agent_overrides(
        strategy: dict,
        environment: dict[str, str] | None = None,
) -> dict:
    """Return a copied strategy with the configured test-role overrides."""
    env = os.environ if environment is None else environment
    effective = dict(strategy)
    configured_family = normalize_backend_name(str(strategy.get("agent") or "pi"))
    family = (
        env.get("FORGE_TEST_FAMILY")
        or env.get("FORGE_TEST_AGENT_FAMILY")
        or env.get("FORGE_AGENT_FAMILY")
        or (
            env.get("FORGE_TEST_AGENT")
            if env.get("FORGE_TEST_AGENT") in SUPPORTED_AGENT_BACKENDS
            else configured_family
        )
    )
    backend = normalize_backend_name(family)
    agent = env.get("FORGE_TEST_AGENT") or strategy.get("agent-command") or backend
    model = env.get("FORGE_TEST_MODEL")
    if (
            env.get("FORGE_TEST_AGENT")
            or env.get("FORGE_TEST_FAMILY")
            or env.get("FORGE_TEST_AGENT_FAMILY")
            or env.get("FORGE_AGENT_FAMILY")
    ):
        effective["agent"] = backend
        effective["agent-family"] = backend
        effective["agent-command"] = agent
        if not model and backend in DEFAULT_MODEL_BY_BACKEND:
            effective["model"] = default_model_for_backend(backend)
    if model:
        effective["model"] = model
    return effective


def create_agent(
        selection: AgentSelection,
        working_dir: str,
        **kwargs,
) -> Agent:
    """Instantiate a selected registered backend."""
    agent_class = Agent.get_class(selection.backend)
    return agent_class(
        model_name=selection.model,
        working_dir=working_dir,
        agent_name=selection.agent or selection.backend,
        agent_family=selection.family,
        thinking_level=selection.thinking_level,
        **kwargs,
    )


def run_agent_task(
        selection: AgentSelection,
        working_dir: str,
        prompt: str,
        task_type: str,
        library: str,
        timeout: int,
        persistent_instructions: str | None = None,
        environment: dict[str, str] | None = None,
) -> AgentRunResult:
    """Run one offline agent turn and persist a backend-neutral task log."""
    log_path = build_task_log_path(task_type, library, f"{selection.backend}.log")
    agent = create_agent(
        selection,
        working_dir,
        timeout=timeout,
        task_type=task_type,
        library=library,
        persistent_instructions=persistent_instructions,
        environment=environment,
    )
    try:
        response = agent.send_prompt(prompt)
    except (OSError, RuntimeError) as exc:
        timed_out = "timed out" in str(exc).lower()
        with open(log_path, "a", encoding="utf-8") as log_file:
            log_file.write(f"Agent failure: {exc}\n")
        return AgentRunResult(1, log_path, timed_out)

    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(response)
        if response and not response.endswith("\n"):
            log_file.write("\n")
    return AgentRunResult(0, log_path, False, response)
