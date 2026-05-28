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

from ai_workflows.fix_metadata_codex import run_codex_metadata_fix
from ai_workflows.fix_post_generation_pi import (
    DEFAULT_MAX_TEST_OUTPUT_CHARS,
    DEFAULT_PI_TIMEOUT_SECONDS,
    POST_GENERATION_STAGE_METADATA_FIX_FAILED,
    run_pi_post_generation_fix,
)
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.gradle_test_runner import run_gradle_test_command
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.metadata_index import (
    coordinate_parts,
    find_index_entry_for_version,
    resolve_metadata_version,
    resolve_test_version,
)
from utility_scripts.native_test_verification import (
    STATUS_FAILED as NATIVE_TEST_GATE_FAILED,
    STATUS_PASSED as NATIVE_TEST_GATE_PASSED,
    STATUS_PASSED_WITH_INTERVENTION as NATIVE_TEST_GATE_PASSED_WITH_INTERVENTION,
    NativeTestVerificationResult,
    verify_native_test_passes,
)
from utility_scripts.issue_requested_metadata import NO_REPORTER_METADATA_CONTEXT
from utility_scripts.repo_path_resolver import require_complete_reachability_repo
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import load_persistent_instructions, load_prompt_template
from utility_scripts.task_logs import sanitize_log_segment

RUN_STATUS_SUCCESS = "success"
RUN_STATUS_FAILURE = "failure"
SUCCESS_WITH_INTERVENTION_STATUS = "success_with_intervention"
RUN_STATUS_CHUNK_READY = "chunk_ready"
POST_GENERATION_STAGE_NATIVE_TEST_GATE = "native-test-finalization-gate"

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
        self.context.setdefault("resolved_edit_scope_context", "")
        self.model_name = self.strategy_obj.get("model")
        if not isinstance(self.model_name, str) or not self.model_name:
            raise ValueError("Strategy is missing required field: model")
        self.prompts = self.strategy_obj.get("prompts", {})
        self.parameters = self.strategy_obj.get("parameters", {})
        self.persistent_instructions = load_persistent_instructions(self.strategy_obj, **self.context)
        self.post_generation_intervention: dict | None = None
        self.native_gate_finalizations: list[dict] = []
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
        test_cmd = f"./gradlew test -Pcoordinates={library}"
        final_status = RUN_STATUS_SUCCESS

        def run_lane(
                stage_name: str,
                command_runner: Callable[[], str],
                reproduction_command: str,
                command_env: dict[str, str] | None,
                mode_key: str,
        ) -> str:
            log_stage("post-generation-test", f"Running {stage_name} for {library}")
            test_output = command_runner()
            failed_task = self._get_first_failed_task(test_output)
            if failed_task is None:
                log_stage("post-generation-test", f"{stage_name} passed for {library}")
                return RUN_STATUS_SUCCESS

            if failed_task == "nativeTest":
                return self._run_finalization_native_test_gate(
                    library=library,
                    mode_key=mode_key,
                    reproduction_command=reproduction_command,
                    command_env=command_env,
                )

            return self._run_post_generation_repair_lane(
                library=library,
                stage_name=stage_name,
                command_runner=command_runner,
                reproduction_command=reproduction_command,
                command_env=command_env,
                test_output=test_output,
            )

        regular_status = run_lane(
            "current-defaults latest GRAALVM test",
            lambda: self._run_command_with_env(test_cmd),
            test_cmd,
            None,
            "current-defaults",
        )
        if regular_status == RUN_STATUS_FAILURE:
            return RUN_STATUS_FAILURE
        if regular_status == SUCCESS_WITH_INTERVENTION_STATUS:
            final_status = SUCCESS_WITH_INTERVENTION_STATUS

        future_defaults_env = dict(os.environ)
        future_defaults_env["GVM_TCK_NATIVE_IMAGE_MODE"] = "future-defaults-all"
        future_defaults_status = run_lane(
            "future-defaults latest GRAALVM test",
            lambda: self._run_command_with_env(test_cmd, future_defaults_env),
            f"GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all {test_cmd}",
            future_defaults_env,
            "future-defaults-all",
        )
        if future_defaults_status == RUN_STATUS_FAILURE:
            return RUN_STATUS_FAILURE
        if future_defaults_status == SUCCESS_WITH_INTERVENTION_STATUS:
            final_status = SUCCESS_WITH_INTERVENTION_STATUS

        # Full CI-matrix GraalVM coverage, including GRAALVM_HOME_25_0, runs in
        # local CI verification after generation.
        return final_status

    def _run_post_generation_repair_lane(
            self,
            library: str,
            stage_name: str,
            command_runner: Callable[[], str],
            reproduction_command: str,
            command_env: dict[str, str] | None,
            test_output: str,
    ) -> str:
        """Run the existing Codex-then-Pi recovery lane for non-native failures."""
        repo_path = getattr(self, "reachability_repo_path", os.getcwd())

        log_stage("metadata-fix", f"Running metadata fix workflow for {library} after {stage_name} failure")
        codex_env = gradle_command_environment(repo_path, command_env)
        codex_rc, codex_log_path, codex_timed_out = run_codex_metadata_fix(
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
                log_stage("post-generation-test", f"{stage_name} passed for {library} after metadata fix")
                return RUN_STATUS_SUCCESS

        log_stage("post-generation-fix", f"Running pi post generation fix for {library} after {stage_name} failure")
        pi_rc, intervention_path, pi_timed_out = run_pi_post_generation_fix(
            reachability_metadata_path=repo_path,
            coordinates=library,
            codex_log_path=codex_log_path,
            test_output=recovery_test_output,
            model_name=self.model_name,
            timeout_seconds=self._parameter_int("post-generation-timeout-seconds", DEFAULT_PI_TIMEOUT_SECONDS),
            max_test_output_chars=self._parameter_int(
                "post-generation-test-output-chars",
                DEFAULT_MAX_TEST_OUTPUT_CHARS,
            ),
        )
        if pi_timed_out or pi_rc != 0:
            return RUN_STATUS_FAILURE

        rerun_output = command_runner()
        if self._get_first_failed_task(rerun_output) is not None:
            return RUN_STATUS_FAILURE

        with open(intervention_path, "r", encoding="utf-8") as intervention_file:
            intervention_markdown = intervention_file.read().strip()

        if self.post_generation_intervention is None:
            self.post_generation_intervention = {
                "stage": POST_GENERATION_STAGE_METADATA_FIX_FAILED,
                "intervention_file": os.path.relpath(intervention_path, repo_path),
                "analysis_markdown": intervention_markdown,
            }
        return SUCCESS_WITH_INTERVENTION_STATUS

    def _run_finalization_native_test_gate(
            self,
            library: str,
            mode_key: str,
            reproduction_command: str,
            command_env: dict[str, str] | None,
    ) -> str:
        """Route post-finalization nativeTest failures through the native gate."""
        output_dir = self._finalization_native_test_output_dir(library, mode_key)
        if output_dir is None:
            return RUN_STATUS_FAILURE

        log_stage(
            "native-test-verify",
            f"Running finalization native-test gate for {library} mode={mode_key} output_dir={output_dir}",
        )
        result = verify_native_test_passes(
            reachability_repo_path=self.reachability_repo_path,
            coordinate=library,
            output_dir=output_dir,
            max_iterations=self._parameter_int("max-native-test-verification-iterations", 100),
            base_env=command_env,
        )
        self._record_native_gate_finalization(library, mode_key, result)
        if result.status == NATIVE_TEST_GATE_PASSED:
            log_stage("native-test-verify", f"finalization native-test gate passed for {library} mode={mode_key}")
            return RUN_STATUS_SUCCESS
        if result.status == NATIVE_TEST_GATE_PASSED_WITH_INTERVENTION:
            log_stage(
                "native-test-verify",
                f"finalization native-test gate passed with intervention for {library} mode={mode_key}",
            )
            self._record_native_gate_intervention(library, mode_key, reproduction_command, result)
            return SUCCESS_WITH_INTERVENTION_STATUS
        if result.status == NATIVE_TEST_GATE_FAILED:
            log_stage("native-test-verify", f"finalization native-test gate failed for {library} mode={mode_key}")
            return RUN_STATUS_FAILURE

        print(f"ERROR: Unknown native-test gate status: {result.status}", file=sys.stderr)
        return RUN_STATUS_FAILURE

    def _record_native_gate_finalization(
            self,
            library: str,
            mode_key: str,
            result: NativeTestVerificationResult,
    ) -> None:
        """Record structured native-gate finalization evidence for run metrics."""
        repo_path = self.reachability_repo_path
        staged_agent_dir = os.path.join(result.output_dir, "agent")
        staged_trace_dir = os.path.join(result.output_dir, "trace")
        codex_log_path = self._native_gate_codex_log_path(result)
        record = {
            "coordinate": library,
            "graalvm_mode": mode_key,
            "gate_status": result.status,
            "iterations_used": result.iterations_used,
            "output_dir": self._display_path(result.output_dir, repo_path),
            "staged_agent_metadata_dir": self._display_path(staged_agent_dir, repo_path),
            "staged_trace_metadata_dir": (
                self._display_path(staged_trace_dir, repo_path)
                if os.path.exists(staged_trace_dir)
                else None
            ),
            "accepted_trace_run_dirs": [
                self._display_path(path, repo_path) or path
                for path in result.accepted_run_dirs
            ],
            "last_native_or_trace_log_path": self._display_path(result.last_native_test_log_path, repo_path),
            "codex_intervention_log_path": self._display_path(codex_log_path, repo_path),
            "pi_invoked": False,
        }
        self.native_gate_finalizations.append(record)

    def _finalization_native_test_output_dir(self, library: str, mode_key: str) -> str | None:
        """Return the stable finalization native-gate output directory."""
        try:
            group, artifact, library_version = coordinate_parts(library)
        except SystemExit:
            return None
        if library_version is None:
            print(f"ERROR: Finalization native-test gate requires versioned coordinates: {library}", file=sys.stderr)
            return None

        test_version = str(resolve_test_version(self.reachability_repo_path, group, artifact, library_version))
        return os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            group,
            artifact,
            test_version,
            "build",
            "natively-collected",
            "finalization",
            sanitize_log_segment(mode_key),
            sanitize_log_segment(library),
        )

    def _record_native_gate_intervention(
            self,
            library: str,
            mode_key: str,
            reproduction_command: str,
            result: NativeTestVerificationResult,
    ) -> None:
        """Record native-gate Codex convergence in the existing intervention shape."""
        repo_path = self.reachability_repo_path
        codex_log_path = self._native_gate_codex_log_path(result)
        codex_log_display = self._display_path(codex_log_path, repo_path) or "unknown"
        analysis_markdown = self._native_gate_intervention_markdown(
            library=library,
            mode_key=mode_key,
            reproduction_command=reproduction_command,
            result=result,
            codex_log_path=codex_log_path,
        )

        if self.post_generation_intervention is None:
            self.post_generation_intervention = {
                "stage": POST_GENERATION_STAGE_NATIVE_TEST_GATE,
                "intervention_file": codex_log_display,
                "analysis_markdown": analysis_markdown,
            }
            return

        existing_analysis = str(self.post_generation_intervention.get("analysis_markdown", "")).strip()
        combined_analysis = "\n\n".join(part for part in [existing_analysis, analysis_markdown] if part)
        self.post_generation_intervention["analysis_markdown"] = combined_analysis

    @staticmethod
    def _native_gate_codex_log_path(result: NativeTestVerificationResult) -> str | None:
        for record in result.intervention_records:
            if record.kind == "codex":
                return record.log_path
        return None

    def _native_gate_intervention_markdown(
            self,
            library: str,
            mode_key: str,
            reproduction_command: str,
            result: NativeTestVerificationResult,
            codex_log_path: str | None,
    ) -> str:
        repo_path = self.reachability_repo_path
        staged_agent_dir = os.path.join(result.output_dir, "agent")
        staged_trace_dir = os.path.join(result.output_dir, "trace")
        accepted_trace_dirs = [
            self._display_path(path, repo_path) or path
            for path in result.accepted_run_dirs
        ]
        accepted_trace_dirs_markdown = (
            "\n".join(f"  - `{path}`" for path in accepted_trace_dirs)
            if accepted_trace_dirs
            else "  - `(none)`"
        )
        if os.path.exists(staged_trace_dir):
            staged_trace_value = self._display_path(staged_trace_dir, repo_path)
        else:
            staged_trace_value = "(none)"
        return "\n".join([
            "Native test verification gate converged during dynamic-access finalization.",
            "",
            f"- Coordinate: `{library}`",
            f"- Mode: `{mode_key}`",
            f"- Reproduction command: `{reproduction_command}`",
            f"- Gate output directory: `{self._display_path(result.output_dir, repo_path)}`",
            f"- Staged agent metadata directory: `{self._display_path(staged_agent_dir, repo_path)}`",
            f"- Staged trace metadata directory: `{staged_trace_value}`",
            "- Accepted trace run directories:",
            accepted_trace_dirs_markdown,
            f"- Last native log: `{self._display_path(result.last_native_test_log_path, repo_path) or '(none)'}`",
            f"- Native gate Codex log: `{self._display_path(codex_log_path, repo_path) or '(none)'}`",
        ])

    @staticmethod
    def _display_path(path: str | None, base_path: str) -> str | None:
        if path is None:
            return None
        if os.path.isabs(path):
            absolute_path = os.path.abspath(path)
            absolute_base_path = os.path.abspath(base_path)
            try:
                if os.path.commonpath([absolute_path, absolute_base_path]) == absolute_base_path:
                    return os.path.relpath(absolute_path, absolute_base_path)
            except ValueError:
                pass
            return absolute_path
        return path

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

    def _run_gradle_command_with_output(self, command: list[str]) -> subprocess.CompletedProcess[str]:
        """Run a Gradle command in the reachability repo and capture combined output."""
        require_complete_reachability_repo(self.reachability_repo_path)
        return subprocess.run(
            command,
            cwd=self.reachability_repo_path,
            env=gradle_command_environment(self.reachability_repo_path),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )

    def _run_gradle_command(self, command: list[str]) -> bool:
        """Run a Gradle command in the reachability repo, returning True on success."""
        result = self._run_gradle_command_with_output(command)
        if result.returncode != 0:
            print(result.stdout)
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
                print(result.stdout)
                return False
            log_stage("allowed-packages", f"Adding allowed-packages for {library}: {', '.join(sorted(new_packages))}")
            if not self._append_allowed_packages_to_metadata_index(new_packages):
                print(result.stdout)
                return False
            seen_packages.update(new_packages)

        print(f"ERROR: checkMetadataFiles still fails after updating allowed-packages for {library}.", file=sys.stderr)
        return False

    def _finalize_successful_iteration(self, base_commit: str | None = None) -> tuple[str, str | None]:
        """Generate metadata, run follow-up Gradle tasks, and commit the iteration."""
        log_stage("generate-metadata", f"Running generateMetadata for {self.library}")
        self._run_command(f"./gradlew generateMetadata -Pcoordinates={self.library} --agentAllowedPackages=fromJar")
        final_status = RUN_STATUS_SUCCESS
        self.post_generation_intervention = None
        self.native_gate_finalizations = []
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
            if not run_library_finalization(
                repo_path=self.reachability_repo_path,
                library=library,
                group=group,
                artifact=artifact,
                library_version=library_version,
                model_name=self.model_name,
                base_commit=base_commit,
            ):
                return RUN_STATUS_FAILURE, None
        log_stage("commit-iteration", f"Running commit iteration for {self.library}")
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
        metadata_version = str(
            self.context.get("metadata_version")
            or resolve_metadata_version(self.reachability_repo_path, self.group, self.artifact, self.version)
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
            os.path.join(
                self.reachability_repo_path,
                "metadata",
                self.group,
                self.artifact,
                "index.json",
            ),
            os.path.join(
                self.reachability_repo_path,
                "metadata",
                self.group,
                self.artifact,
                metadata_version,
            ),
            stats_artifact_dir(self.reachability_repo_path, self.group, self.artifact),
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

    @abstractmethod
    def run(self, agent, **kwargs):
        """Execute the strategy-specific workflow using the given agent."""
        raise NotImplementedError
