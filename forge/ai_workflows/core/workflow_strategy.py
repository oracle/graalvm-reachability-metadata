# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
from abc import ABC, abstractmethod
import re
import subprocess
import sys
from typing import Callable

from ai_workflows.agents.agent import AgentFailureError
from ai_workflows.agents.agent_runtime import analysis_agent_run
from ai_workflows.core.metadata_fix import run_metadata_fix
from ai_workflows.core.post_generation_fix import (
    DEFAULT_MAX_TEST_OUTPUT_CHARS,
    DEFAULT_POST_GENERATION_TIMEOUT_SECONDS,
    POST_GENERATION_STAGE_METADATA_FIX_FAILED,
    run_post_generation_fix,
)
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.continuation_marker import (
    PHASE_FINALIZATION,
    PHASE_PUBLICATION,
    load_continuation_marker,
    save_phase_update,
)
from utility_scripts.workflow_setup import build_graalvm_environment
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.gradle_test_runner import run_gradle_test_command
from utility_scripts.logged_command import LoggedCommandResult, run_logged_command
from utility_scripts.run_location import (
    PHASE_EXPLORE as RUN_PHASE_EXPLORE,
    PHASE_FINALIZATION as RUN_PHASE_FINALIZATION,
    STEP_AGENT_FIX,
    STEP_FINALIZE_RUN,
    STEP_GENERATE_TESTS,
    RunLocation,
    current_run_location,
    log_step_progress,
    record_step_failure,
    run_step,
)
from utility_scripts.metadata_index import (
    coordinate_parts,
    find_index_entry_for_version,
    resolve_metadata_version,
    resolve_test_version,
)
from utility_scripts.native_test_verification import (
    DEFAULT_MAX_ITERATIONS,
    NativeTestVerificationResult,
    STATUS_FAILED as NATIVE_TEST_GATE_FAILED,
    global_output_dir,
    verify_native_test_passes,
)
from utility_scripts.issue_requested_metadata import (
    NO_REPORTER_METADATA_CONTEXT,
    has_issue_requested_metadata_context as has_reporter_metadata_context,
)
from utility_scripts.task_logs import display_log_path
from utility_scripts.library_preparation_preflight import NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.stage_logger import log_detail, log_stage
from utility_scripts.strategy_loader import load_persistent_instructions, load_prompt_template

ISSUE_REQUESTED_METADATA_PROMPT_KEY = "issue-requested-metadata"
ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY = "issue-requested-metadata-fill"
#: The phase runs on the analysis role with its own prompts, so a strategy that
#: never declared them still gets the reporter's request attempted.
#: §forge/FS-forge-agent-runtime-selection
DEFAULT_ISSUE_REQUESTED_METADATA_PROMPTS = {
    ISSUE_REQUESTED_METADATA_PROMPT_KEY:
        "prompt_templates/issue_requested_metadata/issue-requested-metadata.md",
    ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY:
        "prompt_templates/issue_requested_metadata/issue-requested-metadata-fill.md",
}
ISSUE_REQUESTED_METADATA_TASK_TYPE = "issue-requested-metadata"
ISSUE_REQUESTED_METADATA_TIMEOUT_SECONDS = 1800
ISSUE_REQUESTED_METADATA_TEST_ITERATIONS = 3
ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS = 12000

RUN_STATUS_SUCCESS = "success"
RUN_STATUS_FAILURE = "failure"
SUCCESS_WITH_INTERVENTION_STATUS = "success_with_intervention"
RUN_STATUS_CHUNK_READY = "chunk_ready"


def _trim_output_tail(text: str, limit: int) -> str:
    """Return the tail of command output constrained to `limit` characters."""
    if len(text) <= limit:
        return text
    return "[... truncated ...]\n" + text[-limit:]


def strategy_skips_initial_fix_phase(strategy_obj: dict) -> bool:
    """Return True when a strategy enters dynamic-access work without a fix phase."""
    workflow_name = strategy_obj.get("workflow")
    if workflow_name in {"basic_iterative", "dynamic_access_iterative", "bulk_dynamic_access"}:
        return True
    return workflow_name == "increase_dynamic_access_coverage" and not strategy_obj.get("primary-workflow")


class WorkflowStrategy(ABC):
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

    def _run_test_with_retry(self, library: str) -> str:
        """Run Gradle tests for a library and classify post-generation failures."""
        self.post_generation_intervention = None
        test_cmd = f"./gradlew test -Pcoordinates={library}"
        repo_path = getattr(self, "reachability_repo_path", os.getcwd())
        final_status = RUN_STATUS_SUCCESS

        # Lanes are the visible units; their commands remain verbose narration.
        # §FS-forge-run-output-legibility.1 §FS-forge-run-output-legibility.5
        def run_lane(
                lane_number: int,
                lane_name: str,
                stage_name: str,
                command_runner: Callable[[], str],
                reproduction_command: str,
                command_env: dict[str, str] | None,
        ) -> str:
            lane_target = f"native-test lane {lane_number}/3 for {library}: {lane_name}"
            log_step_progress(
                RUN_PHASE_FINALIZATION,
                STEP_FINALIZE_RUN,
                f"Running {lane_target}",
                indent_level=1,
            )
            log_detail("post-generation-test", f"Running {stage_name} for {library}")
            test_output = command_runner()
            if self._get_first_failed_task(test_output) is None:
                log_step_progress(
                    RUN_PHASE_FINALIZATION,
                    STEP_FINALIZE_RUN,
                    f"Passed {lane_target}",
                    indent_level=1,
                )
                log_detail("post-generation-test", f"{stage_name} passed for {library}")
                return RUN_STATUS_SUCCESS

            def record_lane_failure() -> str:
                """Locate this lane's unrepaired failure before returning it."""
                record_step_failure()
                return RUN_STATUS_FAILURE

            # Repairing a failed post-generation lane is the finalization phase's
            # agent fix. §FS-forge-run-location-reporting.2
            with run_step(RUN_PHASE_FINALIZATION, STEP_AGENT_FIX, operand=f"{library} {stage_name}"):
                log_step_progress(
                    RUN_PHASE_FINALIZATION,
                    STEP_AGENT_FIX,
                    f"Running agent fix for {lane_target}",
                )
                log_detail("metadata-fix", f"Running metadata fix workflow for {library} after {stage_name} failure")
                codex_env = gradle_command_environment(repo_path, command_env)
                codex_rc, codex_log_path, codex_timed_out = run_metadata_fix(
                    repo_path,
                    library,
                    reproduction_command=reproduction_command,
                    graalvm_home=codex_env.get("GRAALVM_HOME"),
                    base_env=command_env,
                )
                recovery_test_output = test_output
                if not codex_timed_out and codex_rc == 0:
                    recovery_test_output = command_runner()
                    if self._get_first_failed_task(recovery_test_output) is None:
                        log_step_progress(
                            RUN_PHASE_FINALIZATION,
                            STEP_AGENT_FIX,
                            f"Agent fix passed {lane_target}",
                        )
                        log_detail(
                            "post-generation-test",
                            f"{stage_name} passed for {library} after metadata fix",
                        )
                        return RUN_STATUS_SUCCESS

                log_step_progress(
                    RUN_PHASE_FINALIZATION,
                    STEP_AGENT_FIX,
                    f"Retrying agent fix for {lane_target}",
                )
                log_detail(
                    "post-generation-fix",
                    f"Running post generation fix for {library} after {stage_name} failure",
                )
                pi_rc, intervention_path, pi_timed_out = run_post_generation_fix(
                    reachability_metadata_path=repo_path,
                    coordinates=library,
                    analysis_log_path=codex_log_path,
                    test_output=recovery_test_output,
                    timeout_seconds=self._parameter_int(
                        "post-generation-timeout-seconds",
                        DEFAULT_POST_GENERATION_TIMEOUT_SECONDS,
                    ),
                    max_test_output_chars=self._parameter_int(
                        "post-generation-test-output-chars",
                        DEFAULT_MAX_TEST_OUTPUT_CHARS,
                    ),
                )
                if pi_timed_out or pi_rc != 0:
                    reason = "timed out" if pi_timed_out else f"failed with exit code {pi_rc}"
                    log_step_progress(
                        RUN_PHASE_FINALIZATION,
                        STEP_AGENT_FIX,
                        f"Agent fix for {lane_target} {reason} "
                        f"(log: {display_log_path(intervention_path)})",
                    )
                    return record_lane_failure()

                rerun_output = command_runner()
                if self._get_first_failed_task(rerun_output) is not None:
                    log_step_progress(
                        RUN_PHASE_FINALIZATION,
                        STEP_AGENT_FIX,
                        f"Agent fix did not pass {lane_target}",
                    )
                    return record_lane_failure()
                log_step_progress(
                    RUN_PHASE_FINALIZATION,
                    STEP_AGENT_FIX,
                    f"Agent fix passed {lane_target}",
                )

            with open(intervention_path, "r", encoding="utf-8") as intervention_file:
                intervention_markdown = intervention_file.read().strip()

            if self.post_generation_intervention is None:
                self.post_generation_intervention = {
                    "stage": POST_GENERATION_STAGE_METADATA_FIX_FAILED,
                    # The record lives with the run's logs, not in the published
                    # tree (§FS-forge-run-status).
                    "intervention_file": display_log_path(intervention_path),
                    "analysis_markdown": intervention_markdown,
                }
            return SUCCESS_WITH_INTERVENTION_STATUS

        regular_status = run_lane(
            1,
            "latest GraalVM, current defaults",
            "current-defaults latest GRAALVM test",
            lambda: self._run_command_with_env(test_cmd),
            test_cmd,
            None,
        )
        if regular_status == RUN_STATUS_FAILURE:
            return RUN_STATUS_FAILURE
        if regular_status == SUCCESS_WITH_INTERVENTION_STATUS:
            final_status = SUCCESS_WITH_INTERVENTION_STATUS

        future_defaults_env = dict(os.environ)
        future_defaults_env["GVM_TCK_NATIVE_IMAGE_MODE"] = "future-defaults-all"
        future_defaults_status = run_lane(
            2,
            "latest GraalVM, future defaults",
            "future-defaults latest GRAALVM test",
            lambda: self._run_command_with_env(test_cmd, future_defaults_env),
            f"GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all {test_cmd}",
            future_defaults_env,
        )
        if future_defaults_status == RUN_STATUS_FAILURE:
            return RUN_STATUS_FAILURE
        if future_defaults_status == SUCCESS_WITH_INTERVENTION_STATUS:
            final_status = SUCCESS_WITH_INTERVENTION_STATUS

        # Generation/finalization tier (§FS-local-ci-equivalent-verification.1):
        # current-defaults coverage on the GraalVM 25 toolchain runs here, in the
        # generation lanes with the same metadata/Pi fixers, because the
        # pre-publication gate (§FS-local-ci-equivalent-verification.2) no longer
        # reproduces the native test matrix.
        # GRAALVM_HOME_25_0 is preflighted by workflow_setup.resolve_graalvm_java_home().
        current_defaults_25_env = build_graalvm_environment(os.environ["GRAALVM_HOME_25_0"])
        current_defaults_25_env.pop("GVM_TCK_NATIVE_IMAGE_MODE", None)
        current_defaults_25_status = run_lane(
            3,
            "GraalVM 25, current defaults",
            "current-defaults GraalVM 25 test",
            lambda: self._run_command_with_env(test_cmd, current_defaults_25_env),
            f'GRAALVM_HOME="$GRAALVM_HOME_25_0" JAVA_HOME="$GRAALVM_HOME_25_0" {test_cmd}',
            current_defaults_25_env,
        )
        if current_defaults_25_status == RUN_STATUS_FAILURE:
            return RUN_STATUS_FAILURE
        if current_defaults_25_status == SUCCESS_WITH_INTERVENTION_STATUS:
            final_status = SUCCESS_WITH_INTERVENTION_STATUS

        return final_status

    def _finalization_libraries(self) -> list[str]:
        """Return requested and resolved metadata coordinates that must stay valid."""
        libraries = [self.library]
        metadata_version = str(
            self.context.get("metadata_version")
            or resolve_metadata_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        metadata_library = f"{self.group}:{self.artifact}:{metadata_version}"
        if metadata_library not in libraries:
            libraries.append(metadata_library)
        return libraries

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

    @staticmethod
    def _extract_missing_allowed_packages(check_metadata_output: str) -> set[str]:
        """Extract package names from TypeReached entries for index.json allowed-packages."""
        packages: set[str] = set()
        pattern = re.compile(r"^TypeReached:\s+([A-Za-z0-9_$.]+)\s*$")
        for line in check_metadata_output.splitlines():
            match = pattern.match(line.strip())
            if match is None:
                continue
            class_name = match.group(1)
            if "." not in class_name:
                continue
            packages.add(class_name.rsplit(".", 1)[0])
        return packages

    def _resolve_index_entry_for_current_version(self, index_entries: list[dict]) -> dict | None:
        """Return the metadata index entry that should receive allowed-package updates."""
        resolved_entry = find_index_entry_for_version(
            self.reachability_repo_path,
            self.group,
            self.artifact,
            self.version,
        )
        if resolved_entry is not None:
            return resolved_entry

        matching_version_entries = [
            entry for entry in index_entries if str(entry.get("metadata-version") or "") == self.version
        ]
        if matching_version_entries:
            latest_matching_entries = [entry for entry in matching_version_entries if entry.get("latest")]
            if latest_matching_entries:
                return latest_matching_entries[0]
            return matching_version_entries[0]

        latest_entries = [entry for entry in index_entries if entry.get("latest")]
        if len(latest_entries) == 1:
            return latest_entries[0]
        if len(index_entries) == 1:
            return index_entries[0]
        return None

    def _append_allowed_packages_to_metadata_index(self, packages: set[str]) -> bool:
        """Append missing allowed packages to the library metadata index.json entry."""
        index_path = os.path.join(
            self.reachability_repo_path,
            "metadata",
            self.group,
            self.artifact,
            "index.json",
        )
        index_path_display = os.path.relpath(index_path, self.reachability_repo_path)
        try:
            with open(index_path, "r", encoding="utf-8") as index_file:
                index_entries = json.load(index_file)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"ERROR: Failed to load metadata index {index_path_display}: {exc}", file=sys.stderr)
            return False

        if not isinstance(index_entries, list):
            print(f"ERROR: Metadata index {index_path_display} does not contain a JSON array.", file=sys.stderr)
            return False

        index_entry = self._resolve_index_entry_for_current_version(index_entries)
        if index_entry is None:
            print(
                f"ERROR: Could not resolve metadata index entry for {self.library} in {index_path_display}.",
                file=sys.stderr,
            )
            return False

        allowed_packages = index_entry.get("allowed-packages")
        if not isinstance(allowed_packages, list):
            allowed_packages = []
            index_entry["allowed-packages"] = allowed_packages

        added_packages = [package for package in sorted(packages) if package not in allowed_packages]
        if not added_packages:
            return True

        allowed_packages.extend(added_packages)
        with open(index_path, "w", encoding="utf-8") as index_file:
            json.dump(index_entries, index_file, indent=2)
            index_file.write("\n")

        log_stage("allowed-packages", f"Updated {index_path_display}: {', '.join(added_packages)}")
        return True

    def _run_check_metadata_files_with_allowed_packages_fix(self, library: str) -> bool:
        """Run checkMetadataFiles and update missing allowed-packages when the task reports them."""
        log_stage("check-metadata-files", f"Running checkMetadataFiles for {library}")
        seen_packages: set[str] = set()
        for attempt in range(1, 4):
            log_stage("check-metadata-files", f"Running checkMetadataFiles attempt {attempt}/3 for {library}")
            result = self._run_gradle_command_with_output([
                "./gradlew",
                "checkMetadataFiles",
                f"-Pcoordinates={library}",
            ])
            if result.returncode == 0:
                log_stage("check-metadata-files", f"checkMetadataFiles passed for {library}")
                return True

            log_stage("check-metadata-files", f"checkMetadataFiles failed for {library}; resolving missing allowed-packages")
            missing_packages = self._extract_missing_allowed_packages(result.stdout)
            new_packages = missing_packages - seen_packages
            if not new_packages:
                log_stage("check-metadata-files", "No new TypeReached packages found in checkMetadataFiles output")
                return False
            log_stage("allowed-packages", f"Adding allowed-packages for {library}: {', '.join(sorted(new_packages))}")
            if not self._append_allowed_packages_to_metadata_index(new_packages):
                return False
            seen_packages.update(new_packages)

        print(f"ERROR: checkMetadataFiles still fails after updating allowed-packages for {library}.", file=sys.stderr)
        return False

    def finalize_run(self, base_commit: str | None, workflow_status: str = RUN_STATUS_SUCCESS) -> str:
        """Finalize a PR-eligible run and merge the finalization status.

        The single driver-facing finalization path (§AR-forge-driver-finalization):
        a chunk-ready run stays chunk-ready when finalization succeeds, otherwise
        the finalization status becomes the run status.
        """
        save_phase_update(
            self.continuation_marker_path,
            lambda marker: marker.mark_phase_running(PHASE_FINALIZATION),
        )
        # Finalization is one step of the run, and a status-code failure inside
        # it still names its location. §FS-forge-run-location-reporting.2
        with run_step(RUN_PHASE_FINALIZATION, STEP_FINALIZE_RUN, operand=self.library):
            finalize_status, _ = self._finalize_successful_iteration(base_commit=base_commit)
            if finalize_status == SUCCESS_WITH_INTERVENTION_STATUS:
                outcome = f"Finalization completed with agent intervention for {self.library}"
            elif finalize_status == RUN_STATUS_SUCCESS:
                outcome = f"Finalization completed for {self.library}"
            else:
                outcome = f"Finalization failed for {self.library}"
            log_step_progress(
                RUN_PHASE_FINALIZATION,
                STEP_FINALIZE_RUN,
                outcome,
            )
        finalize_succeeded = finalize_status in {RUN_STATUS_SUCCESS, SUCCESS_WITH_INTERVENTION_STATUS}
        if not finalize_succeeded:
            record_step_failure(operand=self.library)
        if finalize_succeeded:
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: (
                    marker.mark_phase_completed(PHASE_FINALIZATION),
                    marker.mark_phase_pending(PHASE_PUBLICATION),
                ),
            )
        else:
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_pending(PHASE_FINALIZATION),
            )
        if finalize_succeeded and workflow_status == RUN_STATUS_CHUNK_READY:
            return RUN_STATUS_CHUNK_READY
        return finalize_status

    def _finalize_successful_iteration(self, base_commit: str | None = None) -> tuple[str, str | None]:
        """Run the terminal native gate, follow-up tasks, and commit the iteration.

        §AR-forge-workflow-pipeline §FS-native-test-verification-gate
        """
        test_version = str(
            self.context.get("test_version")
            or resolve_test_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        if not self.verify_native_test_gate(global_output_dir(
            self.reachability_repo_path,
            self.group,
            self.artifact,
            test_version,
        )):
            return RUN_STATUS_FAILURE, None
        final_status = RUN_STATUS_SUCCESS
        finalization_libraries = self._finalization_libraries()
        for library in finalization_libraries:
            test_retry_status = self._run_test_with_retry(library)
            if test_retry_status == RUN_STATUS_FAILURE:
                return test_retry_status, None
            if test_retry_status == SUCCESS_WITH_INTERVENTION_STATUS:
                final_status = SUCCESS_WITH_INTERVENTION_STATUS
        for library in finalization_libraries:
            group, artifact, library_version = coordinate_parts(library)
            if library_version is None:
                return RUN_STATUS_FAILURE, None
            log_step_progress(
                RUN_PHASE_FINALIZATION,
                STEP_FINALIZE_RUN,
                f"Running final repository checks for {library}",
                indent_level=1,
            )
            if not run_library_finalization(
                repo_path=self.reachability_repo_path,
                library=library,
                group=group,
                artifact=artifact,
                library_version=library_version,
                base_commit=base_commit,
            ):
                log_step_progress(
                    RUN_PHASE_FINALIZATION,
                    STEP_FINALIZE_RUN,
                    f"Final repository checks failed for {library}",
                    indent_level=1,
                )
                return RUN_STATUS_FAILURE, None
            log_step_progress(
                RUN_PHASE_FINALIZATION,
                STEP_FINALIZE_RUN,
                f"Final repository checks passed for {library}",
                indent_level=1,
            )
        log_detail("commit-iteration", f"Running commit iteration for {self.library}")
        if not self._commit_library_iteration():
            return RUN_STATUS_FAILURE, None
        checkpoint_commit_hash = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        return final_status, checkpoint_commit_hash

    def _run_split_test_only_metadata(self, library: str) -> bool:
        """Split test-only metadata before stats generation or committing."""
        return self._run_gradle_command([
            "./gradlew",
            "splitTestOnlyMetadata",
            f"-Pcoordinates={library}",
        ])

    def _commit_library_iteration(self) -> bool:
        """Stage and commit generated library files for an iteration."""
        test_version = str(
            self.context.get("test_version")
            or resolve_test_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        stage_paths = [
            os.path.join(
                self.reachability_repo_path,
                "tests",
                "src",
                self.group,
                self.artifact,
                test_version,
            ),
            # Routing can update a dependency owner's metadata and stats, so both trees are
            # staged as one finalization result (§AR-forge-driver-finalization).
            os.path.join(self.reachability_repo_path, "metadata"),
            os.path.join(self.reachability_repo_path, "stats"),
        ]
        existing_paths = [path for path in stage_paths if os.path.exists(path)]
        add_result = subprocess.run(
            ["git", "add", "-A", *existing_paths],
            cwd=self.reachability_repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if add_result.returncode != 0:
            print("ERROR: Failed to stage generated library files for commit.", file=sys.stderr)
            print(add_result.stdout)
            return False

        if not self._has_staged_library_changes(existing_paths):
            return True

        commit_result = subprocess.run(
            ["git", "commit", "-m", f"Update generated library support for {self.library}"],
            cwd=self.reachability_repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if commit_result.returncode != 0:
            print("ERROR: Failed to commit generated library iteration.", file=sys.stderr)
            print(commit_result.stdout)
            return False
        return True

    def _has_staged_library_changes(self, paths: list[str]) -> bool:
        """Check whether there are staged changes in the given paths."""
        diff_result = subprocess.run(
            ["git", "diff", "--cached", "--quiet", "--", *paths],
            cwd=self.reachability_repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if diff_result.returncode == 1:
            return True
        if diff_result.returncode == 0:
            return False
        print("ERROR: Failed to inspect staged generated library files.", file=sys.stderr)
        print(diff_result.stdout)
        return True

    def has_issue_requested_metadata_context(self) -> bool:
        """Return whether this run carries reporter-requested metadata to cover."""
        return has_reporter_metadata_context(self.context.get("issue_requested_metadata_context"))

    def run_issue_requested_metadata_phase(self) -> tuple[bool, int]:
        """Cover reporter-requested metadata, independent of dynamic access.

        Generate-then-fill: the analysis agent writes tests, the engine traces
        them with `generateMetadata`, and the analysis agent hand-writes only the
        requested entries tracing missed (§root/FS-test-contract.2.7). The phase
        succeeds only when the whole suite passes, so "reached `nativeTest`" is
        never mistaken for a satisfied request.
        """
        if not self.has_issue_requested_metadata_context():
            return True, 0

        checkpoint = self._current_head_commit()
        iterations = 0
        with run_step(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, operand="reporter-requested metadata"):
            self._print_issue_requested_metadata_message("agent: writing tests for the reporter-requested metadata")
            write_prompt = self._render_issue_requested_metadata_prompt(ISSUE_REQUESTED_METADATA_PROMPT_KEY)
            iterations += 1
            if not self._run_issue_requested_metadata_turn(write_prompt):
                return self._fail_issue_requested_metadata_phase(
                    checkpoint, iterations, "analysis_agent_turn_failed",
                )
            # The engine owns every Gradle command; the agent only edits.
            # §forge/AR-forge-strategy-agent-boundary
            last_generate_output: str = ""
            last_generate_failed_task: str | None = None
            generation_succeeded: bool = False
            for generate_iteration in range(ISSUE_REQUESTED_METADATA_TEST_ITERATIONS):
                self._print_issue_requested_metadata_message(
                    "generate {current}/{maximum}: running ./gradlew generateMetadata "
                    "-Pcoordinates={library}".format(
                        current=generate_iteration + 1,
                        maximum=ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
                        library=self.library,
                    )
                )
                last_generate_output = self._run_command_with_env(
                    f"./gradlew generateMetadata -Pcoordinates={self.library}"
                )
                last_generate_failed_task = self._get_first_failed_task(last_generate_output)
                generation_succeeded = (
                    last_generate_failed_task is None
                    and "BUILD SUCCESSFUL" in last_generate_output
                )
                self._print_issue_requested_metadata_message(
                    "generateMetadata: complete (succeeded: {succeeded}, failed task: {task})".format(
                        succeeded=generation_succeeded,
                        task=last_generate_failed_task or "none",
                    )
                )
                if generation_succeeded:
                    break
                if generate_iteration + 1 == ISSUE_REQUESTED_METADATA_TEST_ITERATIONS:
                    break

                self._print_issue_requested_metadata_message(
                    "agent: sending generation failure back for test repair"
                )
                iterations += 1
                if not self._run_issue_requested_metadata_turn(
                        "{prompt}\n\nWhen `./gradlew generateMetadata "
                        "-Pcoordinates={library}` is run this is the error:\n"
                        "{error_output}".format(
                            prompt=write_prompt,
                            library=self.library,
                            error_output=_trim_output_tail(
                                last_generate_output,
                                ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS,
                            ),
                        )
                ):
                    return self._fail_issue_requested_metadata_phase(
                        checkpoint, iterations, "analysis_agent_turn_failed",
                    )

            if not generation_succeeded:
                return self._fail_issue_requested_metadata_phase(
                    checkpoint,
                    iterations,
                    "generate_metadata_never_succeeded",
                    failed_task=last_generate_failed_task or "unknown",
                )

            self._print_issue_requested_metadata_message(
                "agent: filling requested entries tracing did not produce"
            )
            fill_prompt = self._render_issue_requested_metadata_prompt(
                ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY,
                issue_requested_metadata_dir=self._issue_requested_metadata_dir(),
                generate_metadata_output=_trim_output_tail(
                    last_generate_output,
                    ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS,
                ),
            )
            iterations += 1
            if not self._run_issue_requested_metadata_turn(fill_prompt):
                return self._fail_issue_requested_metadata_phase(
                    checkpoint, iterations, "analysis_agent_turn_failed",
                )

        last_test_output: str = ""
        last_failed_task: str | None = None
        for test_iteration in range(ISSUE_REQUESTED_METADATA_TEST_ITERATIONS):
            self._print_issue_requested_metadata_message(
                "test {current}/{maximum}: running ./gradlew test -Pcoordinates={library}".format(
                    current=test_iteration + 1,
                    maximum=ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
                    library=self.library,
                )
            )
            last_test_output = self._run_command_with_env(f"./gradlew test -Pcoordinates={self.library}")
            last_failed_task = self._get_first_failed_task(last_test_output)
            self._print_issue_requested_metadata_message(
                "test: complete (failed task: {failed_task})".format(
                    failed_task=last_failed_task or "none"
                )
            )
            # The request is satisfied only when the suite it is exercised by
            # passes end to end, `nativeTest` included.
            # §forge/FS-local-ci-equivalent-verification
            if last_failed_task is None and "BUILD SUCCESSFUL" in last_test_output:
                self._commit_issue_requested_metadata(
                    f"Issue-requested metadata coverage for {self.library}"
                )
                return True, iterations
            if test_iteration + 1 == ISSUE_REQUESTED_METADATA_TEST_ITERATIONS:
                break

            # Generation already succeeded for the current tests. Retry only the
            # permitted metadata fill so no test change can bypass regeneration.
            self._print_issue_requested_metadata_message(
                "agent: sending test failure back for metadata repair"
            )
            iterations += 1
            if not self._run_issue_requested_metadata_turn(
                    "{prompt}\n\nWhen `./gradlew test -Pcoordinates={library}` "
                    "is run this is the error:\n{error_output}".format(
                        prompt=fill_prompt,
                        library=self.library,
                        error_output=_trim_output_tail(
                            last_test_output,
                            ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS,
                        ),
                    )
            ):
                return self._fail_issue_requested_metadata_phase(
                    checkpoint, iterations, "analysis_agent_turn_failed",
                )

        return self._fail_issue_requested_metadata_phase(
            checkpoint,
            iterations,
            "test_failures_prevented_a_passing_suite",
            failed_task=last_failed_task or "unknown",
        )

    def _run_issue_requested_metadata_turn(self, prompt: str) -> bool:
        """Run one reporter-requested metadata turn on the analysis role.

        The phase reads reporter evidence and repairs from it, so it sits on the
        analysis role rather than on the strategy's own agent field
        (§forge/FS-forge-agent-runtime-selection). The prompt is self-contained:
        strategy persistent instructions forbid the hand-written entry this phase
        is allowed to make, so they are deliberately not passed.
        """
        result = analysis_agent_run(
            working_dir=self.reachability_repo_path,
            context=prompt,
            task_type=ISSUE_REQUESTED_METADATA_TASK_TYPE,
            library=self.library,
            timeout=ISSUE_REQUESTED_METADATA_TIMEOUT_SECONDS,
        )
        if result.return_code != 0:
            self._print_issue_requested_metadata_message(
                "agent: failed ({message}); see {log}".format(
                    message=result.failure_message or "non-zero exit",
                    log=display_log_path(result.log_path),
                )
            )
            return False
        self._print_issue_requested_metadata_message("agent: complete")
        return True

    def _render_issue_requested_metadata_prompt(self, key: str, **extra_context) -> str:
        """Render a phase prompt, falling back to the phase's own template."""
        template_path = self.prompts.get(key) or DEFAULT_ISSUE_REQUESTED_METADATA_PROMPTS[key]
        prompt_context = dict(self.context)
        prompt_context["library"] = self.library
        prompt_context.update(extra_context)
        return load_prompt_template(template_path, **prompt_context)

    def _issue_requested_metadata_dir(self) -> str:
        """Return the repository-relative metadata directory the phase may edit."""
        metadata_version = str(
            self.context.get("metadata_version")
            or resolve_metadata_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        return os.path.join("metadata", self.group, self.artifact, metadata_version)

    def _commit_issue_requested_metadata(self, message: str) -> None:
        """Commit the tests and the metadata the phase produced."""
        test_version = str(
            self.context.get("test_version")
            or resolve_test_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        paths = [
            os.path.join("tests", "src", self.group, self.artifact, test_version),
            self._issue_requested_metadata_dir(),
        ]
        subprocess.run(
            ["git", "add", "-A", "--", *paths],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        )
        # `git diff --cached --quiet -- <paths>` exits non-zero iff they are staged.
        if subprocess.run(
                ["git", "diff", "--cached", "--quiet", "--", *paths],
                cwd=self.reachability_repo_path,
                capture_output=True, check=False,
        ).returncode != 0:
            subprocess.run(
                ["git", "commit", "-m", message, "--", *paths],
                cwd=self.reachability_repo_path,
                capture_output=True, check=False,
            )

    def _fail_issue_requested_metadata_phase(
            self,
            checkpoint: str,
            iterations: int,
            issue: str,
            **details,
    ) -> tuple[bool, int]:
        """Revert the phase to its checkpoint and locate the failure."""
        self._print_issue_requested_metadata_message(
            f"result: {issue}, reverting to checkpoint"
        )
        for key, value in details.items():
            log_detail("issue-requested-metadata", f"{key}={value}", indent_level=1)
        subprocess.run(
            ["git", "reset", "--hard", checkpoint],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        )
        # The first recorded location wins, so an earlier gate keeps its step.
        # §forge/FS-forge-run-location-reporting.3
        record_step_failure(
            location=RunLocation(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, "reporter-requested metadata"),
        )
        return False, iterations

    def _current_head_commit(self) -> str:
        """Return the current `HEAD` SHA of the reachability worktree."""
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=self.reachability_repo_path,
            text=True,
        ).strip()

    @staticmethod
    def _print_issue_requested_metadata_message(message: str) -> None:
        log_detail("issue-requested-metadata", message)

    @abstractmethod
    def run(self, agent, **kwargs):
        """Execute the strategy-specific workflow using the given agent."""
        raise NotImplementedError
