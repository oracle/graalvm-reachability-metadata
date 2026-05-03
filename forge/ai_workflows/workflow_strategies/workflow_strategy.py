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
from utility_scripts.gradle_test_runner import run_gradle_test_command
from utility_scripts.library_stats import stats_artifact_dir
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import load_persistent_instructions, load_prompt_template

RUN_STATUS_SUCCESS = "success"
RUN_STATUS_FAILURE = "failure"
SUCCESS_WITH_INTERVENTION_STATUS = "success_with_intervention"
RUN_STATUS_CHUNK_READY = "chunk_ready"

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
        self.model_name = self.strategy_obj.get("model")
        if not isinstance(self.model_name, str) or not self.model_name:
            raise ValueError("Strategy is missing required field: model")
        self.prompts = self.strategy_obj.get("prompts", {})
        self.parameters = self.strategy_obj.get("parameters", {})
        self.persistent_instructions = load_persistent_instructions(self.strategy_obj, **self.context)
        self.post_generation_intervention: dict | None = None
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
        result = subprocess.run(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        return result.stdout

    def _run_command_with_env(self, cmd: str, env: dict[str, str] | None = None) -> str:
        """Execute a shell command with optional environment overrides."""
        if cmd.startswith("./gradlew test "):
            return run_gradle_test_command(
                cmd,
                getattr(self, "reachability_repo_path", os.getcwd()),
                library=getattr(self, "library", None),
                env=env,
            )
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=getattr(self, "reachability_repo_path", None),
            env=env,
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

        def run_lane(
                stage_name: str,
                command_runner: Callable[[], str],
                reproduction_command: str,
        ) -> str:
            log_stage("post-generation-test", f"Running {stage_name} for {library}")
            test_output = command_runner()
            if self._get_first_failed_task(test_output) is None:
                log_stage("post-generation-test", f"{stage_name} passed for {library}")
                return RUN_STATUS_SUCCESS

            log_stage("metadata-fix", f"Running metadata fix workflow for {library} after {stage_name} failure")
            codex_rc, codex_log_path, codex_timed_out = run_codex_metadata_fix(
                repo_path,
                library,
                reproduction_command=reproduction_command,
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

        regular_status = run_lane(
            "current-defaults latest GRAALVM test",
            lambda: self._run_command_with_env(test_cmd),
            test_cmd,
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
        )
        if future_defaults_status == RUN_STATUS_FAILURE:
            return RUN_STATUS_FAILURE
        if future_defaults_status == SUCCESS_WITH_INTERVENTION_STATUS:
            final_status = SUCCESS_WITH_INTERVENTION_STATUS

        return final_status

    def _run_gradle_command_with_output(self, command: list[str]) -> subprocess.CompletedProcess[str]:
        """Run a Gradle command in the reachability repo and capture combined output."""
        return subprocess.run(
            command,
            cwd=self.reachability_repo_path,
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

    def _finalize_successful_iteration(self) -> tuple[str, str | None]:
        """Generate metadata, run follow-up Gradle tasks, and commit the iteration."""
        log_stage("generate-metadata", f"Running generateMetadata for {self.library}")
        self._run_command(f"./gradlew generateMetadata -Pcoordinates={self.library} --agentAllowedPackages=fromJar")
        test_retry_status = self._run_test_with_retry(self.library)
        if test_retry_status == RUN_STATUS_FAILURE:
            return test_retry_status, None
        if not run_library_finalization(
            repo_path=self.reachability_repo_path,
            library=self.library,
            group=self.group,
            artifact=self.artifact,
            library_version=self.version,
            model_name=self.model_name,
        ):
            return RUN_STATUS_FAILURE, None
        log_stage("commit-iteration", f"Running commit iteration for {self.library}")
        if not self._commit_library_iteration():
            return RUN_STATUS_FAILURE, None
        checkpoint_commit_hash = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        return test_retry_status, checkpoint_commit_hash

    def _run_split_test_only_metadata(self, library: str) -> bool:
        """Split test-only metadata before stats generation or committing."""
        return self._run_gradle_command([
            "./gradlew",
            "splitTestOnlyMetadata",
            f"-Pcoordinates={library}",
        ])

    def _commit_library_iteration(self) -> bool:
        """Stage and commit generated library files for an iteration."""
        stage_paths = [
            os.path.join(
                self.reachability_repo_path,
                "tests",
                "src",
                self.group,
                self.artifact,
                self.version,
            ),
            os.path.join(
                self.reachability_repo_path,
                "metadata",
                self.group,
                self.artifact,
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
