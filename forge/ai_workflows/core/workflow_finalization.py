# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Finalization of a PR-eligible workflow strategy run.

The single driver-facing finalization path (§AR-forge-driver-finalization):
the terminal native gate, the three post-generation native-test lanes with
their metadata/Pi fixers, allowed-packages repair, and the iteration commit.
"""

import json
import os
import re
import subprocess
import sys
from typing import Callable

from ai_workflows.core.metadata_fix import run_metadata_fix
from ai_workflows.core.post_generation_fix import (
    DEFAULT_MAX_TEST_OUTPUT_CHARS,
    DEFAULT_POST_GENERATION_TIMEOUT_SECONDS,
    POST_GENERATION_STAGE_METADATA_FIX_FAILED,
    run_post_generation_fix,
)
from ai_workflows.core.run_status import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.continuation_marker import (
    PHASE_FINALIZATION,
    PHASE_PUBLICATION,
    save_phase_update,
)
from utility_scripts.workflow_setup import build_graalvm_environment
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.run_location import (
    PHASE_FINALIZATION as RUN_PHASE_FINALIZATION,
    STEP_AGENT_FIX,
    STEP_FINALIZE_RUN,
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
from utility_scripts.native_trace_metadata import global_output_dir
from utility_scripts.task_logs import display_log_path
from utility_scripts.stage_logger import log_detail, log_stage


class WorkflowFinalization:
    """Mixin owning post-generation testing, repair, and the iteration commit."""

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
