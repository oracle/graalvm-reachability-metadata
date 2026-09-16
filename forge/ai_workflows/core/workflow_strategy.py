# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
from abc import ABC, abstractmethod
import re
import subprocess

from ai_workflows.agents.agent import AgentFailureError
from ai_workflows.core.issue_requested_metadata_phase import IssueRequestedMetadataPhase
from ai_workflows.core.run_status import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from ai_workflows.core.workflow_finalization import WorkflowFinalization
from utility_scripts.continuation_marker import load_continuation_marker
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.gradle_test_runner import run_gradle_test_command
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.run_location import current_run_location, log_step_progress
from utility_scripts.native_test_verification import (
    DEFAULT_MAX_ITERATIONS,
    NativeTestVerificationResult,
    STATUS_FAILED as NATIVE_TEST_GATE_FAILED,
    verify_native_test_passes,
)
from utility_scripts.issue_requested_metadata import NO_REPORTER_METADATA_CONTEXT
from utility_scripts.task_logs import display_log_path
from utility_scripts.library_preparation_setup import NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.stage_logger import log_detail
from utility_scripts.strategy_loader import load_persistent_instructions, load_prompt_template


def strategy_skips_initial_fix_phase(strategy_obj: dict) -> bool:
    """Return True when a strategy enters dynamic-access work without a fix phase."""
    workflow_name = strategy_obj.get("workflow")
    if workflow_name in {"basic_iterative", "dynamic_access_iterative", "bulk_dynamic_access"}:
        return True
    return workflow_name == "increase_dynamic_access_coverage" and not strategy_obj.get("primary-workflow")


class WorkflowStrategy(IssueRequestedMetadataPhase, WorkflowFinalization, ABC):
    """Base class for workflow strategy implementations.

    A workflow strategy orchestrates an AI agent through a multi-step process
    (e.g. scaffold a library, generate metadata, run tests, iterate on fixes).
    Each concrete strategy declares its required prompts and parameters and
    implements the ``run`` method.

    Strategies are discovered via a class-level registry. Use the
    ``@WorkflowStrategy.register`` decorator to make a concrete implementation
    available by name::

        @WorkflowStrategy.register("my-strategy")
        class MyStrategy(WorkflowStrategy):
            REQUIRED_PROMPTS = ["initial"]
            REQUIRED_PARAMS = ["max_iterations"]
            ...

    Attributes:
        ITERATION_DIVIDER: Visual separator printed between workflow iterations.
        REQUIRED_PROMPTS: Keys that must be present in the strategy's ``prompts``
            dict. Subclasses override this to declare their prompt dependencies.
        REQUIRED_PARAMS: Keys that must be present in the strategy's ``parameters``
            dict. Subclasses override this to declare their parameter dependencies.
        _registry: Maps strategy key strings to their implementing classes.
        strategy_obj: The raw strategy configuration dict.
        context: Extra keyword arguments (e.g. ``library``, ``version``) that are
            substituted into prompt templates.
        model_name: Optional model override from the strategy config.
        prompts: Mapping of prompt key to template path/string.
        parameters: Mapping of parameter key to value.
    """

    ITERATION_DIVIDER = "=" * 70
    REQUIRED_PROMPTS = []
    REQUIRED_PARAMS = []

    _registry: dict[str, type["WorkflowStrategy"]] = {}

    @classmethod
    def register(cls, strategy_key: str):
        """Class decorator that registers a strategy under the given key."""
        def decorator(subclass):
            if strategy_key in cls._registry:
                raise ValueError(f"Duplicate strategy key: {strategy_key}")
            cls._registry[strategy_key] = subclass
            return subclass
        return decorator

    @classmethod
    def get_class(cls, strategy_name: str) -> type["WorkflowStrategy"]:
        """Look up a strategy class by its registered name."""
        if strategy_name not in cls._registry:
            available = ", ".join(sorted(cls._registry.keys()))
            raise ValueError(f"Unknown workflow strategy '{strategy_name}'. Available: {available}")
        return cls._registry[strategy_name]

    def __init__(self, strategy_obj: dict, **context):
        """Initialize the strategy from a configuration dict and context substitutions."""
        self.strategy_obj = strategy_obj or {}
        self.context = context
        self.context.setdefault(
            "issue_requested_metadata_context",
            NO_REPORTER_METADATA_CONTEXT,
        )
        self.context.setdefault(
            "library_preparation_preflight_context",
            NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT,
        )
        self.context.setdefault("resolved_edit_scope_context", "")
        self.model_name = self.strategy_obj.get("model")
        if not isinstance(self.model_name, str) or not self.model_name:
            raise ValueError("Strategy is missing required field: model")
        self.prompts = self.strategy_obj.get("prompts", {})
        self.parameters = self.strategy_obj.get("parameters", {})
        self.max_native_test_verification_iterations: int = self._parameter_int(
            "max-native-test-verification-iterations",
            DEFAULT_MAX_ITERATIONS,
        )
        self.persistent_instructions = load_persistent_instructions(self.strategy_obj, **self.context)
        self.post_generation_intervention: dict | None = None
        self.continuation_marker_path: str | None = self.context.get("continuation_marker_path")
        self.continuation_marker = load_continuation_marker(self.continuation_marker_path)
        self._validate_required_prompts()
        self._validate_required_params()

    def _validate_required_prompts(self) -> None:
        missing = [key for key in self.REQUIRED_PROMPTS if key not in self.prompts]
        if missing:
            raise ValueError(f"Strategy is missing required prompts: {', '.join(missing)}")

    def _validate_required_params(self) -> None:
        missing = [key for key in self.REQUIRED_PARAMS if key not in self.parameters]
        if missing:
            raise ValueError(f"Strategy is missing required parameters: {', '.join(missing)}")

    def _parameter_int(self, name: str, default: int) -> int:
        """Return a non-negative integer strategy parameter."""
        value = self.parameters.get(name, default)
        if not isinstance(value, int) or value < 0:
            raise ValueError(f"Strategy parameter '{name}' must be a non-negative integer")
        return value

    def verify_native_test_gate(
            self,
            output_dir: str,
            label: str | None = None,
            env: dict[str, str] | None = None,
    ) -> bool:
        """Run the shared native-test gate (§FS-native-test-verification-gate)."""
        label_suffix: str = f" for {label}" if label else ""
        gate_target = label or self.library
        location = current_run_location()
        if location is not None:
            log_step_progress(
                location.phase,
                location.step,
                f"Running native trace gate for {gate_target}",
            )
        log_detail(
            "native-test-verify",
            f"native-test gate: starting{label_suffix} output_dir={output_dir} "
            f"budget={self.max_native_test_verification_iterations}",
        )
        result: NativeTestVerificationResult = verify_native_test_passes(
            reachability_repo_path=self.context["reachability_repo_path"],
            coordinate=self.library,
            output_dir=output_dir,
            max_iterations=self.max_native_test_verification_iterations,
            env=env,
        )
        if result.status == NATIVE_TEST_GATE_FAILED:
            log_path: str = result.last_native_test_log_path or "(none)"
            last_exit: str = (
                str(result.last_native_test_exit_code)
                if result.last_native_test_exit_code is not None
                else "unknown"
            )
            failure_cause = result.failure_detail or (
                f"native test did not pass after {result.iterations_used} cycles; "
                f"last binary exit {last_exit}"
            )
            if location is not None:
                displayed_log = display_log_path(log_path) if log_path != "(none)" else log_path
                log_step_progress(
                    location.phase,
                    location.step,
                    f"Native trace gate failed for {gate_target}: {failure_cause} "
                    f"(log: {displayed_log})",
                )
            log_detail(
                "native-test-verify",
                f"native-test gate FAILED{label_suffix} after {result.iterations_used} cycles "
                f"(last log: {log_path})",
            )
            # Preserve the agent cause and log through the terminal boundary.
            # §FS-forge-run-output-legibility.2
            if result.failure_detail is not None:
                raise AgentFailureError(
                    f"native-trace-gate agent failed with: {result.failure_detail}",
                    result.failure_log_path,
                )
            return False
        status_text = result.status.lower().replace("_", " ")
        if location is not None:
            log_step_progress(
                location.phase,
                location.step,
                f"Native trace gate {status_text} for {gate_target}",
            )
        log_detail(
            "native-test-verify",
            f"native-test gate {result.status}{label_suffix} after {result.iterations_used} cycles",
        )
        return True

    def _load_prompt(self, key: str) -> str:
        """Load and render a prompt template by key, substituting context values."""
        return self._render_prompt(key)

    def _render_prompt(self, key: str, **extra_context) -> str:
        """Load and render a prompt template with merged base and per-call context.

        Unlike _load_prompt (which uses only the static init-time context),
        this accepts extra_context for values only known at iteration time
        (e.g. active_class_name, uncovered call sites).
        """
        prompt_context = dict(self.context)
        prompt_context.update(extra_context)
        return load_prompt_template(self.prompts[key], **prompt_context)

    @staticmethod
    def _run_command(cmd: str) -> str:
        """Execute a shell command and return its combined stdout/stderr."""
        env = None
        if cmd.startswith("./gradlew"):
            repo_path = os.getcwd()
            require_complete_reachability_repo(repo_path)
            env = gradle_command_environment(repo_path)
        result = subprocess.run(cmd, shell=True, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        return result.stdout

    def _run_command_with_env(self, cmd: str, env: dict[str, str] | None = None) -> str:
        """Execute a shell command with optional environment overrides."""
        repo_path = getattr(self, "reachability_repo_path", os.getcwd())
        if cmd.startswith("./gradlew test "):
            return run_gradle_test_command(
                cmd,
                repo_path,
                library=getattr(self, "library", None),
                env=env,
            )
        command_env = gradle_command_environment(repo_path, env) if cmd.startswith("./gradlew") else env
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=repo_path,
            env=command_env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        return result.stdout

    @staticmethod
    def _get_first_failed_task(output: str):
        """Extract the first Gradle task name that failed from build output, or None."""
        pattern = r"> Task :(\S+) FAILED"
        match = re.search(pattern, output)
        return match.group(1) if match else None

    def _run_gradle_command_with_output(self, command: list[str]) -> LoggedCommandResult:
        """Run a Gradle command quietly and retain complete durable output."""
        require_complete_reachability_repo(self.reachability_repo_path)
        action = command[1] if len(command) > 1 else "gradle"
        return run_logged_command(
            command,
            cwd=self.reachability_repo_path,
            task_type="gradle",
            subject=self.library,
            action=action,
            env=gradle_command_environment(self.reachability_repo_path),
            stage="gradle",
        )

    def _run_gradle_command(self, command: list[str]) -> bool:
        """Run a Gradle command in the reachability repo, returning True on success."""
        result = self._run_gradle_command_with_output(command)
        if result.returncode != 0:
            return False
        return True

    @abstractmethod
    def run(self, agent, **kwargs):
        """Execute the strategy-specific workflow using the given agent."""
        raise NotImplementedError
