# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run-state support for the dynamic-access iterative strategy.

Continuation persistence, native-test gate batching, chunk/exhaust-report
bookkeeping, and checkpoint commits behind §AR-dynamic-access-iterative,
kept out of the per-class loop flow.
"""

import os
import subprocess

from ai_workflows.core.workflow_strategy import RUN_STATUS_CHUNK_READY
from utility_scripts.continuation_marker import PHASE_EXPLORE, save_phase_update
from utility_scripts.metadata_index import resolve_metadata_version
from utility_scripts.native_test_verification import per_class_output_dir
from utility_scripts.run_location import (
    PHASE_EXPLORE as RUN_PHASE_EXPLORE,
    STEP_GENERATE_TESTS,
    STEP_NATIVE_TRACE_GATE,
    RunLocation,
    log_step_progress,
    record_step_failure,
    run_step,
)


class DynamicAccessRunSupport:
    """Mixin owning continuation, gate batching, and chunk persistence."""

    def _mark_continuation_explore_completed(
            self,
            iteration: int,
            exhausted_classes: set[str],
            chunk_processed_class_count: int | None = None,
    ) -> None:
        """Persist successful dynamic-access phase completion."""
        save_phase_update(
            self.continuation_marker_path,
            lambda marker: (
                marker.record_exhausted_classes(sorted(exhausted_classes)),
                marker.record_chunk_progress(self.chunk_class_count, chunk_processed_class_count),
                marker.mark_phase_completed(PHASE_EXPLORE, iteration=iteration),
            ),
        )

    def _locate_explore_failure(self, class_name: str | None = None) -> None:
        """Locate an explore failure that returned a status instead of raising.

        The first recorded location wins, so a gate failure keeps its own step.
        §FS-forge-run-location-reporting.3
        """
        record_step_failure(
            location=RunLocation(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, class_name or self.library),
        )

    def _has_successful_class_coverage(self, successful_classes: int) -> bool:
        """Return whether iterative or preceding bulk work gained coverage.

        Bulk passes its covered-call gain directly and records completed classes
        in the shared exhaust report before iterative exploration finishes the
        remainder. §FS-forge-chunked-dynamic-access
        """
        if successful_classes > 0 or self.preceding_dynamic_access_covered_call_gain > 0:
            return True
        return (
            self.dynamic_access_exhaust_report is not None
            and bool(self.dynamic_access_exhaust_report.completed_classes)
        )

    def _mark_continuation_explore_pending(
            self,
            iteration: int,
            exhausted_classes: set[str],
            chunk_processed_class_count: int | None = None,
    ) -> None:
        """Persist an incomplete dynamic-access phase."""
        save_phase_update(
            self.continuation_marker_path,
            lambda marker: (
                marker.record_exhausted_classes(sorted(exhausted_classes)),
                marker.record_chunk_progress(self.chunk_class_count, chunk_processed_class_count),
                marker.mark_phase_pending(PHASE_EXPLORE, iteration=iteration),
            ),
        )

    def _continuation_exhausted_classes(self) -> set[str]:
        """Return EXPLORE classes that continuation must not retry."""
        if self.continuation_marker is None:
            return set()
        explore_phase: dict[str, object] = self.continuation_marker.phases.get(PHASE_EXPLORE, {})
        exhausted_classes: object = explore_phase.get("exhaustedClasses", [])
        if not isinstance(exhausted_classes, list):
            return set()
        return {
            class_name
            for class_name in exhausted_classes
            if isinstance(class_name, str) and class_name
        }

    def _finish_chunk_boundary(
            self,
            flush_native_test_batch,
            class_name: str,
            prompt_iterations: int,
            exhausted_classes: set[str],
            terminal_classes_this_part: set[str],
    ) -> tuple[bool, int]:
        """Flush the gate batch and persist a chunk-ready phase completion.

        Shared terminal handling for a reached chunk boundary
        (§AR-dynamic-access-exhaust-report): a failed flush leaves the phase
        pending, otherwise the phase completes as chunk-ready.
        """
        if not flush_native_test_batch("chunked-dynamic-access-boundary"):
            self._mark_continuation_explore_pending(
                prompt_iterations,
                exhausted_classes,
                len(terminal_classes_this_part),
            )
            self._locate_explore_failure(class_name)
            return False, prompt_iterations
        self._last_phase_status = RUN_STATUS_CHUNK_READY
        log_step_progress(
            RUN_PHASE_EXPLORE,
            STEP_GENERATE_TESTS,
            f"Exploration chunk ready after {len(terminal_classes_this_part)} classes",
        )
        self._print_dynamic_access_message(
            "Chunked dynamic-access boundary reached after {count} terminal class(es).".format(
                count=len(terminal_classes_this_part),
            )
        )
        self._save_dynamic_access_exhaust_report()
        self._mark_continuation_explore_completed(
            prompt_iterations,
            exhausted_classes,
            len(terminal_classes_this_part),
        )
        return True, prompt_iterations

    def _queue_native_test_verification_step(
            self,
            pending_classes: list[str],
            class_name: str,
    ) -> bool:
        """Queue one committed class step for the next native-test verification batch.

        Implements the dynamic-access caller half of
        §AR-native-test-verification-callers: the gate runs after a configured
        batch of coverage-gain commits, and any remaining batch is flushed
        before returning.
        """
        pending_classes.append(class_name)
        self._print_dynamic_access_detail(
            "native-test gate: queued class={class_name} pending={pending}/{batch_size}".format(
                class_name=class_name,
                pending=len(pending_classes),
                batch_size=self.native_test_verification_batch_size,
            ),
            indent_level=2,
        )
        return True

    def _native_test_verification_batch_ready(self, pending_classes: list[str]) -> bool:
        return len(pending_classes) >= self.native_test_verification_batch_size

    def _run_pending_native_test_verification_gate(
            self,
            pending_classes: list[str],
            reason: str,
    ) -> tuple[bool, str | None]:
        """Run and clear the pending native-test gate batch when any classes are queued.

        A `FAILED` result from the gate (§FS-native-test-verification-gate)
        aborts the workflow: partial coverage with broken native tests is not a
        PR-eligible state.
        """
        if not pending_classes:
            return True, None
        gate_label = self._native_test_verification_batch_label(pending_classes)
        self._print_dynamic_access_detail(
            "native-test gate: flushing {count} queued class step(s), reason={reason}".format(
                count=len(pending_classes),
                reason=reason,
            ),
            indent_level=2,
        )
        if not self._run_native_test_verification_gate(gate_label):
            return False, gate_label
        pending_classes.clear()
        return True, gate_label

    @staticmethod
    def _native_test_verification_batch_label(class_names: list[str]) -> str:
        if len(class_names) == 1:
            return class_names[0]
        return "{last_class}__native_batch_{count}".format(
            last_class=class_names[-1],
            count=len(class_names),
        )

    def _run_native_test_verification_gate(self, class_name: str) -> bool:
        """Run the per-class native-test verification gate; return True if PASSED.

        The gate (§FS-native-test-verification-gate) is the dynamic-access
        success criterion for committed class progress; it writes durable
        metadata only after verification passes.
        """
        with run_step(RUN_PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE, operand=class_name):
            output_dir = per_class_output_dir(
                self.reachability_repo_path, self.group, self.artifact, self.test_version, class_name,
            )
            if not self.verify_native_test_gate(output_dir, label=class_name):
                record_step_failure()
                return False
            self._commit_test_sources(f"Native-test gate fixes for {class_name}")
            self._latest_class_checkpoint = self._commit_library_metadata(
                f"Native metadata for {class_name}"
            )
            return True

    def _refresh_report_after_gate(self, class_name: str):
        """Regenerate the dynamic-access report after the gate to reflect new coverage.

        Gate-supplied metadata can change which call sites remain uncovered for
        the next class prompt — the post-gate report refresh required by the
        per-class loop (§AR-dynamic-access-iterative).
        """
        refreshed = self._generate_dynamic_access_report(indent_level=2)
        if refreshed is None:
            self._print_dynamic_access_detail(
                f"native-test gate: post-gate report refresh failed for {class_name}",
                indent_level=2,
            )
        return refreshed

    def _save_dynamic_access_exhaust_report(self) -> None:
        """Persist the coordinate-derived exhaust report, when chunked mode is active."""
        if self.dynamic_access_exhaust_report is None or self.dynamic_access_exhaust_report_path is None:
            return
        self.dynamic_access_exhaust_report.save(self.dynamic_access_exhaust_report_path)

    def _commit_dynamic_access_exhaust_report(self, message: str) -> None:
        """Commit the exhaust report so class checkpoints preserve chunk state."""
        if self.dynamic_access_exhaust_report_path is None:
            return
        self._save_dynamic_access_exhaust_report()
        if not os.path.isdir(self.reachability_repo_path):
            return
        self._run_exhaust_report_git_command(["git", "add", "-A", self.dynamic_access_exhaust_report_path])
        diff_rc = subprocess.run(
            ["git", "diff", "--cached", "--quiet", "--", self.dynamic_access_exhaust_report_path],
            cwd=self.reachability_repo_path,
            capture_output=True,
            check=False,
        ).returncode
        if diff_rc == 0:
            return
        if diff_rc > 1:
            raise RuntimeError(
                "Failed to inspect staged dynamic-access exhaust report changes "
                f"(exit code {diff_rc})."
            )
        self._run_exhaust_report_git_command(
            ["git", "commit", "-m", message, "--", self.dynamic_access_exhaust_report_path]
        )
        # The exhaust-report commit records terminal class state above the last
        # test/metadata commit, so advance the whole-phase recovery checkpoint
        # past it too; otherwise a later reset drops the completed-class marker
        # while keeping its tests, per §AR-dynamic-access-fallback-and-failure.
        self._latest_class_checkpoint = subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=self.reachability_repo_path,
            text=True,
        ).strip()

    def _run_exhaust_report_git_command(self, command: list[str]) -> None:
        result = subprocess.run(
            command,
            cwd=self.reachability_repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if result.returncode == 0:
            return
        raise RuntimeError(
            "Dynamic-access exhaust report Git command failed with exit code "
            f"{result.returncode}: {' '.join(command)}\n{result.stdout}"
        )

    def _mark_chunk_class_completed(self, class_name: str) -> None:
        """Persist a completed class boundary for chunk continuation."""
        if self.dynamic_access_exhaust_report is None:
            return
        self.dynamic_access_exhaust_report.mark_completed(class_name)
        self._commit_dynamic_access_exhaust_report(f"Record completed dynamic-access class {class_name}")

    def _mark_chunk_class_skipped(self, class_name: str) -> None:
        """Persist a skipped class boundary for chunk continuation."""
        if self.dynamic_access_exhaust_report is None:
            return
        self.dynamic_access_exhaust_report.mark_skipped(class_name)
        self._commit_dynamic_access_exhaust_report(f"Record skipped dynamic-access class {class_name}")

    def _mark_chunk_class_exhausted(self, class_name: str) -> None:
        """Persist an exhausted class boundary for chunk continuation."""
        if self.dynamic_access_exhaust_report is None:
            return
        self.dynamic_access_exhaust_report.mark_exhausted(class_name)
        self._commit_dynamic_access_exhaust_report(f"Record exhausted dynamic-access class {class_name}")

    def _mark_chunk_class_failed(self, class_name: str) -> None:
        """Persist a failed class boundary for chunk diagnostics."""
        if self.dynamic_access_exhaust_report is None:
            return
        self.dynamic_access_exhaust_report.mark_failed(class_name)
        self.dynamic_access_exhaust_report.mark_exhausted(class_name)
        self._commit_dynamic_access_exhaust_report(f"Record failed dynamic-access class {class_name}")

    def _should_stop_for_chunked_dynamic_access(
            self,
            current_report,
            exhausted_classes: set[str],
            terminal_classes_this_part: set[str],
    ) -> bool:
        """Return True when the current chunk reached its configured boundary.

        A class is never split across chunks (§AR-dynamic-access-exhaust-report):
        the strategy stops only after enough classes have reached a terminal
        state, whether explicitly selected or incidentally completed.
        """
        if self.dynamic_access_exhaust_report is None:
            return False
        if current_report.next_uncovered_class(exhausted_classes) is None:
            return False
        return self.chunk_class_count > 0 and len(terminal_classes_this_part) >= self.chunk_class_count

    def _library_test_change_signature(self) -> str:
        """Capture tracked and untracked changes under the generated library test tree."""
        test_dir = os.path.join("tests", "src", self.group, self.artifact, self.test_version)
        tracked_diff = subprocess.run(
            ["git", "diff", "--no-ext-diff", "HEAD", "--", test_dir],
            cwd=self.reachability_repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        untracked_files = subprocess.run(
            ["git", "ls-files", "--others", "--exclude-standard", "--", test_dir],
            cwd=self.reachability_repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        return "\n".join([tracked_diff.stdout, "--untracked--", untracked_files.stdout])

    def _commit_test_sources(self, message: str) -> str:
        """Stage and commit test sources so the next class has a clean checkpoint."""
        tests_dir = os.path.join(
            self.reachability_repo_path, "tests", "src",
            self.group, self.artifact, self.test_version,
        )
        subprocess.run(
            ["git", "add", "-A", tests_dir],
            cwd=self.reachability_repo_path, check=False,
        )
        subprocess.run(
            ["git", "diff", "--cached", "--quiet"],
            cwd=self.reachability_repo_path,
        ).returncode != 0 and subprocess.run(
            ["git", "commit", "-m", message],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        )
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=self.reachability_repo_path,
            text=True,
        ).strip()

    def _commit_library_metadata(self, message: str) -> str:
        """Stage and commit durable library metadata created by the class gate.

        Returns the resulting `HEAD` SHA so a passing gate can advance the
        whole-phase recovery checkpoint past the verified test and metadata
        commits, per §AR-dynamic-access-fallback-and-failure.
        """
        metadata_version = resolve_metadata_version(
            self.reachability_repo_path,
            self.group,
            self.artifact,
            self.version,
        )
        metadata_dir = os.path.join(
            self.reachability_repo_path, "metadata", self.group, self.artifact, metadata_version,
        )
        subprocess.run(
            ["git", "add", "-A", metadata_dir],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        )
        # `git diff --cached --quiet -- <path>` exits non-zero iff <path> has staged changes.
        # Restricting to <metadata_dir> ensures unrelated staged files don't trigger a commit.
        diff_rc = subprocess.run(
            ["git", "diff", "--cached", "--quiet", "--", metadata_dir],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        ).returncode
        if diff_rc != 0:
            subprocess.run(
                ["git", "commit", "-m", message, "--", metadata_dir],
                cwd=self.reachability_repo_path,
                capture_output=True, check=False,
            )
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=self.reachability_repo_path,
            text=True,
        ).strip()
