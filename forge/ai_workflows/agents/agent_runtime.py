# Copyright and related rights waived via CC0

"""Runtime selection and one-shot execution for Forge agent roles.

The role boundary implements §FS-forge-agent-runtime-selection. Backends are
selected at runtime while workflows continue to depend only on ``Agent``.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, replace
import os

from ai_workflows.agents.agent import Agent
from utility_scripts.task_logs import build_task_log_path


SUPPORTED_AGENT_BACKENDS = ("claude-code", "pi", "codex", "opencode")
#: Fallback for any worker-configured role that names nothing of its own.
DEFAULT_AGENT = "codex"
#: Backends that route a model through a named provider. Codex and Claude Code
#: reach their own provider through the CLI's own login, so a provider is
#: meaningless to them and is never passed.
PROVIDER_AWARE_BACKENDS = ("pi", "opencode")
DEFAULT_AGENT_PROVIDER = "openai-codex"
DEFAULT_MODEL_BY_BACKEND = {
    "claude-code": "sonnet",
    "codex": "gpt-5.6-luna",
}
DEFAULT_AGENT_BY_BACKEND = {
    "claude-code": "claude",
    "pi": "pi",
    "codex": "codex",
    "opencode": "opencode",
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
    #: Only set for PROVIDER_AWARE_BACKENDS; None everywhere else.
    provider: str | None = None


@dataclass(frozen=True)
class AgentRunResult:
    """Result of a one-shot backend-neutral agent invocation."""

    return_code: int
    log_path: str
    timed_out: bool
    response: str = ""
    #: Usage of the turn, so a role invocation is accounted like a generation
    #: turn rather than dropping off the cost record (§FS-forge-run-metrics).
    input_tokens: int = 0
    output_tokens: int = 0
    cached_input_tokens: int | None = None
    session_log_path: str | None = None


def agent_process_environment(
        environment: dict[str, str] | None = None,
) -> dict[str, str]:
    """Build the environment for one agent-role invocation.

    Published pull-request review explicitly opts into the trusted GitHub
    session; every other role loses GitHub credential channels.
    §FS-forge-agent-runtime-selection
    """
    source = os.environ if environment is None else environment
    sanitized = dict(source)
    allow_github_access: bool = (
        sanitized.pop("_FORGE_AGENT_ALLOW_GITHUB_ACCESS", "") == "1"
    )
    if not allow_github_access:
        for variable in GITHUB_CREDENTIAL_ENV_VARS:
            sanitized.pop(variable, None)
        sanitized["GH_CONFIG_DIR"] = "/nonexistent/forge-agent-no-github-config"

    sanitized["GH_PROMPT_DISABLED"] = "1"
    sanitized["GH_PAGER"] = ""
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


def default_agent_for_backend(backend: str) -> str:
    """Return the default executable for a registered adapter family."""
    return DEFAULT_AGENT_BY_BACKEND[normalize_backend_name(backend)]


def resolve_provider(backend: str, configured: str | None) -> str | None:
    """Return the provider a backend should use, or None if it takes none.

    Pi and OpenCode address a model through a provider, so one is always
    resolved for them and defaults to `openai-codex`. The other backends
    authenticate their own provider, so a configured value is dropped rather
    than passed to a CLI that has no such flag.
    """
    if normalize_backend_name(backend) not in PROVIDER_AWARE_BACKENDS:
        return None
    return configured or DEFAULT_AGENT_PROVIDER


def _role_selection(env: dict[str, str], prefix: str) -> AgentSelection:
    """Resolve one worker-configured role from its `FORGE_<PREFIX>_*` variables.

    Roles do not read each other. What a role names is its own; what it leaves
    unset comes from the shared defaults, never from another role's setting
    (§FS-forge-agent-runtime-selection).
    """

    def configured(name: str) -> str | None:
        return env.get(f"FORGE_{prefix}_{name}") or None

    named_agent = configured("AGENT")
    family = (
        configured("FAMILY")
        or configured("AGENT_FAMILY")
        or (named_agent if named_agent in SUPPORTED_AGENT_BACKENDS else None)
        or env.get("FORGE_AGENT_FAMILY")
        or DEFAULT_AGENT
    )
    backend = normalize_backend_name(family)
    model = configured("MODEL") or default_model_for_backend(backend)
    return AgentSelection(
        backend=backend,
        model=model,
        family=backend,
        thinking_level=configured("THINKING_LEVEL") or (
            "high" if backend == "codex" and model == "gpt-5.6-luna" else None
        ),
        agent=named_agent or default_agent_for_backend(backend),
        provider=resolve_provider(
            backend,
            configured("PROVIDER") or env.get("FORGE_AGENT_PROVIDER"),
        ),
    )


def get_analysis_agent(environment: dict[str, str] | None = None) -> AgentSelection:
    """Resolve the analysis role from the worker environment."""
    return _role_selection(os.environ if environment is None else environment, "ANALYSIS")


def get_setup_agent(environment: dict[str, str] | None = None) -> AgentSelection:
    """Resolve the setup role from the worker environment.

    `FORGE_SETUP_*` configures it, and the shared defaults fill the rest.
    Retuning analysis never moves setup and the reverse is equally true, so a
    repair budget is never charged to a URL lookup by accident.
    """
    return _role_selection(os.environ if environment is None else environment, "SETUP")


def create_agent(
        selection: AgentSelection,
        working_dir: str,
        **kwargs,
) -> Agent:
    """Instantiate a selected registered backend."""
    agent_class = Agent.get_class(selection.backend)
    if selection.provider:
        kwargs.setdefault("provider", selection.provider)
    return agent_class(
        model_name=selection.model,
        working_dir=working_dir,
        agent_name=selection.agent or selection.backend,
        agent_family=selection.family,
        thinking_level=selection.thinking_level,
        **kwargs,
    )


def analysis_agent_run(
        working_dir: str,
        context: str,
        task_type: str,
        library: str,
        timeout: int,
        instructions: str | None = None,
        environment: dict[str, str] | None = None,
        model: str | None = None,
        thinking_level: str | None = None,
) -> AgentRunResult:
    """Run one analysis-role turn on the context the caller assembled.

    The single entry point for every step that hands a failure to the analysis
    agent (§FS-forge-agent-runtime-selection). The caller owns the context,
    because only the caller knows what failed; role resolution and agent
    construction happen here so no call site selects a backend for itself.
    """
    return _role_run(
        get_analysis_agent, "ANALYSIS",
        working_dir, context, task_type, library, timeout,
        instructions, environment, model, thinking_level,
    )


def setup_agent_run(
        working_dir: str,
        context: str,
        task_type: str,
        library: str,
        timeout: int,
        instructions: str | None = None,
        environment: dict[str, str] | None = None,
        model: str | None = None,
        thinking_level: str | None = None,
) -> AgentRunResult:
    """Run one setup-role turn on the context the caller assembled.

    The counterpart to `analysis_agent_run` for a step that prepares a library
    before generation starts (§FS-forge-agent-runtime-selection). Steps that
    hand their prompt to Gradle instead of to an adapter read the same role
    through `get_setup_agent`.
    """
    return _role_run(
        get_setup_agent, "SETUP",
        working_dir, context, task_type, library, timeout,
        instructions, environment, model, thinking_level,
    )


def _role_run(
        select: "Callable[[dict[str, str] | None], AgentSelection]",
        prefix: str,
        working_dir: str,
        context: str,
        task_type: str,
        library: str,
        timeout: int,
        instructions: str | None,
        environment: dict[str, str] | None,
        model: str | None,
        thinking_level: str | None,
) -> AgentRunResult:
    """Run one turn of a worker-configured role.

    `model` and `thinking_level` are caller preferences, not commands: an
    explicit setting for the role still wins, so an operator override is never
    silently replaced by a step's own default.
    """
    env = os.environ if environment is None else environment
    selection = select(environment)
    if model and not env.get(f"FORGE_{prefix}_MODEL"):
        selection = replace(selection, model=model)
    if thinking_level and not env.get(f"FORGE_{prefix}_THINKING_LEVEL"):
        selection = replace(selection, thinking_level=thinking_level)
    return run_agent_task(
        selection=selection,
        working_dir=working_dir,
        prompt=context,
        task_type=task_type,
        library=library,
        timeout=timeout,
        persistent_instructions=instructions,
        environment=environment,
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
    """Run one agent turn and persist a backend-neutral task log."""
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
        return AgentRunResult(1, log_path, timed_out, **_agent_usage(agent))

    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(response)
        if response and not response.endswith("\n"):
            log_file.write("\n")
    return AgentRunResult(0, log_path, False, response, **_agent_usage(agent))


def _agent_usage(agent: Agent) -> dict:
    """Read the turn's usage counters off the agent that produced it."""
    return {
        "input_tokens": int(getattr(agent, "total_tokens_sent", 0) or 0),
        "output_tokens": int(getattr(agent, "total_tokens_received", 0) or 0),
        "cached_input_tokens": getattr(agent, "cached_input_tokens_used", None),
        "session_log_path": getattr(agent, "_session_log_path", None),
    }
