# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The per-class dynamic-access exploration loop.

The per-class loop of §AR-dynamic-access-iterative: each selected class
gets bounded prompt/test attempts, coverage-gain commits advance the class
checkpoint, and exhausted classes are not retried in the same phase.
"""

import subprocess

from ai_workflows.agents.agent import send_agent_prompt
from utility_scripts.continuation_marker import PHASE_EXPLORE, PHASE_FIX, save_phase_update
from utility_scripts.dynamic_access_report import compute_class_delta, format_call_sites
from utility_scripts.run_location import (
    PHASE_EXPLORE as RUN_PHASE_EXPLORE,
    STEP_GENERATE_TESTS,
    enter_phase,
    log_step_progress,
    run_step,
)


class DynamicAccessClassLoop:
    """Mixin owning the per-class dynamic-access phase loop."""

    def _run_dynamic_access_phase(self, agent, current_report=None) -> tuple[bool, int]:
        """Drive the per-class dynamic-access loop for one strategy phase.

        This is the per-class loop of §AR-dynamic-access-iterative:
        each selected class gets bounded prompt/test attempts, coverage-gain
        commits advance the class checkpoint, and exhausted classes are not
        retried in the same phase.
        """
        if current_report is None:
            current_report = self._generate_dynamic_access_report()
        if self._should_fallback_to_basic_flow(current_report):
            if self.has_issue_requested_metadata_context():
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: (
                        marker.mark_phase_skipped_if_pending(PHASE_FIX),
                        marker.mark_phase_running(PHASE_EXPLORE),
                    ),
                )
                enter_phase(RUN_PHASE_EXPLORE)
            else:
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: marker.mark_phase_skipped(PHASE_EXPLORE),
                )
            return True, 0
        save_phase_update(
            self.continuation_marker_path,
            lambda marker: (
                marker.mark_phase_skipped_if_pending(PHASE_FIX),
                marker.record_chunk_progress(self.chunk_class_count, 0),
                marker.mark_phase_running(PHASE_EXPLORE),
            ),
        )
        enter_phase(RUN_PHASE_EXPLORE)

        exhausted_classes: set[str] = self._continuation_exhausted_classes()
        if self.dynamic_access_exhaust_report is not None:
            exhausted_classes.update(self.dynamic_access_exhaust_report.processed_classes())
        previous_report = None
        prompt_iterations = 0
        successful_classes = 0
        terminal_classes_this_part: set[str] = set()
        pending_native_test_classes: list[str] = []
        self._save_dynamic_access_exhaust_report()
        initial_test_change_signature = self._library_test_change_signature()
        initial_class_positions = {
            class_coverage.class_name: index
            for index, class_coverage in enumerate(current_report.classes, start=1)
        }
        initial_class_count = len(current_report.classes)
        initial_uncovered_classes = {
            class_coverage.class_name
            for class_coverage in current_report.classes
            if class_coverage.uncovered_calls > 0 and class_coverage.class_name not in exhausted_classes
        }

        def record_indirectly_completed_classes(active_class_name: str | None = None) -> None:
            nonlocal current_report
            if self.dynamic_access_exhaust_report is None:
                return
            for candidate in sorted(initial_uncovered_classes):
                if candidate == active_class_name:
                    continue
                if candidate in terminal_classes_this_part or candidate in exhausted_classes:
                    continue
                candidate_coverage = current_report.get_class(candidate)
                if candidate_coverage is not None and candidate_coverage.uncovered_calls > 0:
                    continue
                self._print_dynamic_access_detail(
                    f"chunked-dynamic-access: indirectly completed class={candidate}",
                    indent_level=2,
                )
                terminal_classes_this_part.add(candidate)
                exhausted_classes.add(candidate)
                self._mark_chunk_class_completed(candidate)

        def flush_native_test_batch(reason: str, active_class_name: str | None = None) -> bool:
            nonlocal current_report
            ok, gate_label = self._run_pending_native_test_verification_gate(
                pending_native_test_classes,
                reason,
            )
            if not ok:
                return False
            if gate_label is not None:
                current_report = self._refresh_report_after_gate(gate_label) or current_report
                record_indirectly_completed_classes(active_class_name)
            return True

        while True:
            active_class = current_report.next_uncovered_class(exhausted_classes)
            if active_class is None:
                if not flush_native_test_batch("end-of-classes"):
                    self._mark_continuation_explore_pending(
                        prompt_iterations,
                        exhausted_classes,
                        len(terminal_classes_this_part),
                    )
                    self._locate_explore_failure()
                    return False, prompt_iterations
                if (
                        successful_classes == 0
                        and self.keep_tests_without_dynamic_access
                        and prompt_iterations > 0
                        and self._library_test_change_signature() != initial_test_change_signature
                ):
                    self._print_dynamic_access_message("Coverage not improved. Keeping generated tests by request.")
                    self._mark_continuation_explore_completed(
                        prompt_iterations,
                        exhausted_classes,
                        len(terminal_classes_this_part),
                    )
                    return True, prompt_iterations
                phase_succeeded: bool = self._has_successful_class_coverage(successful_classes)
                if phase_succeeded:
                    self._mark_continuation_explore_completed(
                        prompt_iterations,
                        exhausted_classes,
                        len(terminal_classes_this_part),
                    )
                else:
                    self._mark_continuation_explore_pending(
                        prompt_iterations,
                        exhausted_classes,
                        len(terminal_classes_this_part),
                    )
                return phase_succeeded, prompt_iterations

            class_name = active_class.class_name
            # Snapshot HEAD before this class so we can roll back on failure
            # without losing progress from previously successful classes.
            class_checkpoint = subprocess.check_output(
                ["git", "rev-parse", "HEAD"], text=True,
            ).strip()
            class_progress = self._resolve_class_progress(
                class_name,
                current_report,
                initial_class_positions,
                initial_class_count,
            )
            progress_text = "unknown"
            if class_progress is not None:
                progress_text = "{current}/{total}".format(
                    current=class_progress[0],
                    total=class_progress[1],
                )
            self._print_dynamic_access_message(
                "Class {progress}: {class_name}".format(
                    progress=progress_text,
                    class_name=class_name,
                )
            )
            log_step_progress(
                RUN_PHASE_EXPLORE,
                STEP_GENERATE_TESTS,
                f"Generating tests for class {progress_text}: {class_name}",
            )
            self._print_dynamic_access_detail(
                "coverage: {covered}/{total}".format(
                    covered=active_class.covered_calls,
                    total=active_class.total_calls,
                )
            )
            class_attempts = 0
            class_failed = False
            class_committed = False
            gate_failed = False
            while class_attempts < self.max_class_iterations:
                if class_attempts > 0:
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Retrying test generation for class {progress_text}: {class_name} "
                        f"(attempt {class_attempts + 1}/{self.max_class_iterations})",
                    )
                self._print_dynamic_access_detail(
                    "attempt {attempt}/{max_attempts}".format(
                        attempt=class_attempts + 1,
                        max_attempts=self.max_class_iterations,
                    )
                )
                delta = compute_class_delta(previous_report, current_report, class_name)
                dynamic_prompt = self._render_prompt(
                    "dynamic-access-iteration",
                    active_class_name=class_name,
                    active_class_source_file=active_class.resolved_source_file or active_class.source_file or "N/A",
                    dynamic_access_progress=self._format_progress(delta, class_attempts),
                    uncovered_dynamic_access_calls=format_call_sites(active_class.uncovered_call_sites),
                )
                with run_step(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, operand=class_name):
                    self._print_dynamic_access_detail("agent: running dynamic-access prompt", indent_level=2)
                    send_agent_prompt(agent, dynamic_prompt, "dynamic_access_iteration()")
                    self._print_dynamic_access_detail("agent: complete", indent_level=2)
                prompt_iterations += 1
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: marker.record_iteration(PHASE_EXPLORE, prompt_iterations),
                )
                class_attempts += 1

                reached_native_test = False
                last_test_output = ""
                last_failed_task = None
                test_attempts: int = self.max_class_test_repairs + 1
                for test_iteration in range(test_attempts):
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Running test {test_iteration + 1}/{test_attempts}",
                        indent_level=1,
                    )
                    self._print_dynamic_access_detail(
                        "test {current}/{maximum}: running ./gradlew test -Pcoordinates={library}".format(
                            current=test_iteration + 1,
                            maximum=test_attempts,
                            library=self.library,
                        ),
                        indent_level=2,
                    )
                    test_output = agent.run_test_command(f"./gradlew test -Pcoordinates={self.library}")
                    failed_task = self._get_first_failed_task(test_output)
                    last_test_output = test_output
                    last_failed_task = failed_task
                    if failed_task == "nativeTest":
                        test_outcome = "reached nativeTest"
                    elif failed_task is None:
                        test_outcome = "passed"
                    else:
                        test_outcome = f"failed at {failed_task}"
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Test {test_iteration + 1}/{test_attempts} {test_outcome}",
                        indent_level=1,
                    )
                    self._print_dynamic_access_detail(
                        "test: complete (failed task: {failed_task})".format(
                            failed_task=failed_task or "none",
                        ),
                        indent_level=2,
                    )
                    if failed_task in {"nativeTest", None}:
                        reached_native_test = True
                        break
                    if test_iteration == test_attempts - 1:
                        break
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Running feedback fix after {failed_task}",
                        indent_level=2,
                    )
                    self._print_dynamic_access_detail(
                        "agent: test failed before nativeTest; sending failure output back to agent",
                        indent_level=2,
                    )
                    send_agent_prompt(
                        agent,
                        "When `./gradlew test -Pcoordinates={library}` is ran this is the error:\n{error_output}".format(
                            library=self.library,
                            error_output=test_output,
                        ),
                        "feedback_fix()",
                    )
                    self._print_dynamic_access_detail("agent: complete", indent_level=2)
                    prompt_iterations += 1

                if not reached_native_test:
                    # Tests failed for this class, roll back to before this class
                    # was attempted so previous successful classes are preserved.
                    self._print_dynamic_access_detail("result: failed, reverting to checkpoint", indent_level=2)
                    self._print_failure_analysis(
                        "gradle_test_failed",
                        issue=self._summarize_gradle_issue(last_test_output),
                        indent_level=2,
                        failed_task=last_failed_task or "unknown",
                        class_name=class_name,
                    )
                    subprocess.run(["git", "reset", "--hard", class_checkpoint], check=False)
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Class {class_name} failed before nativeTest",
                    )
                    class_failed = True
                    break

                previous_report = current_report
                current_report = self._generate_dynamic_access_report(indent_level=2)
                if current_report is None:
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Class {class_name} failed: dynamic-access report unavailable",
                    )
                    self._print_dynamic_access_detail(
                        "result: dynamic-access report unavailable after test run",
                        indent_level=2,
                    )
                    self._print_failure_analysis(
                        "dynamic_access_report_refresh_failed",
                        issue=self._last_dynamic_access_report_issue,
                        indent_level=2,
                        class_name=class_name,
                    )
                    self._mark_continuation_explore_pending(
                        prompt_iterations,
                        exhausted_classes,
                        len(terminal_classes_this_part),
                    )
                    self._locate_explore_failure(class_name)
                    return False, prompt_iterations
                record_indirectly_completed_classes(class_name)

                updated_class = current_report.get_class(class_name)
                if updated_class is None:
                    if current_report.covered_calls > previous_report.covered_calls:
                        self._print_dynamic_access_detail("result: resolved", indent_level=2)
                        self._latest_class_checkpoint = self._commit_test_sources(
                            f"Dynamic-access coverage for {class_name} "
                            f"({current_report.covered_calls}/{current_report.total_calls})"
                        )
                        successful_classes += 1
                        if not self._queue_native_test_verification_step(
                                pending_native_test_classes,
                                class_name,
                        ):
                            gate_failed = True
                            exhausted_classes.add(class_name)
                            break
                        if self._native_test_verification_batch_ready(pending_native_test_classes):
                            if not flush_native_test_batch("batch-size", class_name):
                                gate_failed = True
                                exhausted_classes.add(class_name)
                                break
                        self._mark_chunk_class_completed(class_name)
                    else:
                        self._print_dynamic_access_detail(
                            "result: class disappeared without coverage gain, skipping",
                            indent_level=2,
                        )
                        self._mark_chunk_class_skipped(class_name)
                    exhausted_classes.add(class_name)
                    break
                if updated_class.uncovered_calls == 0:
                    self._print_dynamic_access_detail("result: resolved", indent_level=2)
                    self._latest_class_checkpoint = self._commit_test_sources(
                        f"Dynamic-access coverage for {class_name} "
                        f"({current_report.covered_calls}/{current_report.total_calls})"
                    )
                    successful_classes += 1
                    exhausted_classes.add(class_name)
                    if not self._queue_native_test_verification_step(
                            pending_native_test_classes,
                            class_name,
                    ):
                        gate_failed = True
                        break
                    if self._native_test_verification_batch_ready(pending_native_test_classes):
                        if not flush_native_test_batch("batch-size", class_name):
                            gate_failed = True
                            break
                    self._mark_chunk_class_completed(class_name)
                    break
                if updated_class.covered_calls > active_class.covered_calls:
                    # Coverage improved but class is not fully resolved yet.
                    # Commit and advance checkpoint so a later failed iteration
                    # doesn't wipe this progress.
                    self._print_dynamic_access_detail(
                        "result: partially covered, now {covered}/{total}".format(
                            covered=updated_class.covered_calls,
                            total=updated_class.total_calls,
                        ),
                        indent_level=2,
                    )
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Class {class_name} partially covered: "
                        f"{updated_class.covered_calls}/{updated_class.total_calls}",
                    )
                    class_checkpoint = self._commit_test_sources(
                        f"Partial dynamic-access coverage for {class_name} "
                        f"({updated_class.covered_calls}/{updated_class.total_calls})"
                    )
                    self._latest_class_checkpoint = class_checkpoint
                    class_committed = True
                    if not self._queue_native_test_verification_step(
                            pending_native_test_classes,
                            class_name,
                    ):
                        gate_failed = True
                        break
                    if self._native_test_verification_batch_ready(pending_native_test_classes):
                        if not flush_native_test_batch("batch-size", class_name):
                            gate_failed = True
                            break
                        updated_class = current_report.get_class(class_name) or updated_class
                    self._save_dynamic_access_exhaust_report()
                else:
                    log_step_progress(
                        RUN_PHASE_EXPLORE,
                        STEP_GENERATE_TESTS,
                        f"Class {class_name} gained no coverage; "
                        f"{updated_class.uncovered_calls} calls remain",
                    )
                    self._print_dynamic_access_detail(
                        "result: no new coverage, {remaining} {call_label} still uncovered".format(
                            remaining=updated_class.uncovered_calls,
                            call_label=self._call_label(updated_class.uncovered_calls),
                        ),
                        indent_level=2,
                    )
                active_class = updated_class

            # Clear context between classes to keep the agent window focused.
            agent.clear_context()
            if gate_failed:
                self._print_failure_analysis(
                    "native_test_verification_gate_failed",
                    issue="nativeTest_did_not_pass_within_verification_budget",
                    indent_level=1,
                    class_name=class_name,
                )
                self._mark_continuation_explore_pending(
                    prompt_iterations,
                    exhausted_classes,
                    len(terminal_classes_this_part),
                )
                self._locate_explore_failure(class_name)
                return False, prompt_iterations
            if class_failed:
                self._print_dynamic_access_detail(
                    "final: failed before reaching native test",
                    indent_level=1,
                )
                self._print_failure_analysis(
                    "class_iteration_failed",
                    issue="test_failures_prevented_reaching_nativeTest",
                    indent_level=1,
                    class_name=class_name,
                )
                exhausted_classes.add(class_name)
                self._mark_chunk_class_failed(class_name)
                terminal_classes_this_part.add(class_name)
                self._print_class_completion_progress(
                    class_name,
                    self._completed_class_count(current_report, exhausted_classes),
                    self._class_completion_total(initial_class_count, current_report, exhausted_classes),
                    current_report,
                )
                if self._should_stop_for_chunked_dynamic_access(
                        current_report,
                        exhausted_classes,
                        terminal_classes_this_part,
                ):
                    return self._finish_chunk_boundary(
                        flush_native_test_batch,
                        class_name,
                        prompt_iterations,
                        exhausted_classes,
                        terminal_classes_this_part,
                    )
                continue
            updated_class = current_report.get_class(class_name)
            if updated_class is not None and updated_class.uncovered_calls > 0:
                # Max attempts reached for this class, mark it so we don't retry it
                # and move on to the next uncovered class.
                self._print_dynamic_access_detail(
                    "final: exhausted after {attempts} attempts".format(
                        attempts=self.max_class_iterations,
                    )
                )
                log_step_progress(
                    RUN_PHASE_EXPLORE,
                    STEP_GENERATE_TESTS,
                    f"Class {class_name} exhausted after {self.max_class_iterations} attempts",
                )
                self._print_failure_analysis(
                    "class_iteration_exhausted",
                    issue="coverage_did_not_reach_zero_uncovered_calls",
                    indent_level=1,
                    class_name=class_name,
                    covered_calls=updated_class.covered_calls,
                    total_calls=updated_class.total_calls,
                    uncovered_calls=updated_class.uncovered_calls,
                )
                exhausted_classes.add(class_name)
                if class_committed:
                    successful_classes += 1
                self._mark_chunk_class_exhausted(class_name)
            self._print_class_completion_progress(
                class_name,
                self._completed_class_count(current_report, exhausted_classes),
                self._class_completion_total(initial_class_count, current_report, exhausted_classes),
                current_report,
            )
            terminal_classes_this_part.add(class_name)
            if self._should_stop_for_chunked_dynamic_access(
                    current_report,
                    exhausted_classes,
                    terminal_classes_this_part,
            ):
                return self._finish_chunk_boundary(
                    flush_native_test_batch,
                    class_name,
                    prompt_iterations,
                    exhausted_classes,
                    terminal_classes_this_part,
                )
