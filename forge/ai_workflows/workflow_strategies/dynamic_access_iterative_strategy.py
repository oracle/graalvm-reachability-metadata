# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess

from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS, WorkflowStrategy
from utility_scripts.dynamic_access_report import compute_class_delta, format_call_sites, load_dynamic_access_coverage_report
from utility_scripts.native_test_verification import (
    STATUS_FAILED as NATIVE_TEST_GATE_FAILED,
    per_class_output_dir,
    verify_native_test_passes,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import load_strategy_by_name


FALLBACK_STRATEGY_NAME = "basic_iterative_pi_gpt-5.4"
DEFAULT_MAX_NATIVE_TEST_VERIFICATION_ITERATIONS = 100


@WorkflowStrategy.register("dynamic_access_iterative")
class DynamicAccessIterativeStrategy(WorkflowStrategy):
    """Iterative add-new-library workflow guided by dynamic-access coverage."""

    PROGRESS_DIVIDER = "=" * 83

    REQUIRED_PROMPTS = ["dynamic-access-iteration"]
    REQUIRED_PARAMS = [
        "max-iterations",
        "max-class-test-iterations",
    ]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.library = self.context["library"]
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.keep_tests_without_dynamic_access = bool(self.context.get("keep_tests_without_dynamic_access", False))
        self._last_dynamic_access_report_issue = "not_run"
        self.group, self.artifact, self.version = self.library.split(":")
        self.package = self.group
        self.max_class_iterations = self.parameters["max-iterations"]
        self.max_class_test_iterations = self.parameters["max-class-test-iterations"]
        self.max_native_test_verification_iterations = self._parameter_int(
            "max-native-test-verification-iterations",
            DEFAULT_MAX_NATIVE_TEST_VERIFICATION_ITERATIONS,
        )
        self.dynamic_access_report_path = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.version,
            "build",
            "reports",
            "dynamic-access",
            "dynamic-access-coverage.json",
        )

    def run(self, agent, checkpoint_commit_hash):
        initial_report = self._generate_dynamic_access_report()
        if self._should_fallback_to_basic_flow(initial_report):
            self._print_dynamic_access_message(
                "Falling back to basic iterative metadata flow: "
                f"cause={self._dynamic_access_fallback_cause()} "
                f"previous_report={self._current_dynamic_access_status()} "
                f"coordinate={self.library}"
            )
            return self._run_basic_iterative_fallback(agent, checkpoint_commit_hash)

        global_iterations = 0
        phase_ok, extra_iterations = self._run_dynamic_access_phase(agent, initial_report)
        global_iterations += extra_iterations
        if not phase_ok:
            subprocess.run(["git", "reset", "--hard", checkpoint_commit_hash], check=False)
            return RUN_STATUS_FAILURE, global_iterations, 0

        return RUN_STATUS_SUCCESS, global_iterations, 1

    def _run_basic_iterative_fallback(self, agent, checkpoint_commit_hash):
        """Instantiate a BasicIterativeStrategy and delegate to it."""
        from ai_workflows.workflow_strategies.basic_iterative_strategy import BasicIterativeStrategy
        fallback_obj = load_strategy_by_name(FALLBACK_STRATEGY_NAME)
        if fallback_obj is None:
            raise ValueError(f"Fallback strategy '{FALLBACK_STRATEGY_NAME}' not found in predefined strategies")
        fallback = BasicIterativeStrategy(fallback_obj, **self.context)
        return fallback.run(agent, checkpoint_commit_hash)

    def _run_dynamic_access_phase(self, agent, current_report=None) -> tuple[bool, int]:
        if current_report is None:
            current_report = self._generate_dynamic_access_report()
        if self._should_fallback_to_basic_flow(current_report):
            return True, 0

        exhausted_classes: set[str] = set()
        previous_report = None
        prompt_iterations = 0
        successful_classes = 0
        initial_test_change_signature = self._library_test_change_signature()
        initial_class_positions = {
            class_coverage.class_name: index
            for index, class_coverage in enumerate(current_report.classes, start=1)
        }
        initial_class_count = len(current_report.classes)

        while True:
            active_class = current_report.next_uncovered_class(exhausted_classes)
            if active_class is None:
                if (
                        successful_classes == 0
                        and self.keep_tests_without_dynamic_access
                        and prompt_iterations > 0
                        and self._library_test_change_signature() != initial_test_change_signature
                ):
                    self._print_dynamic_access_message("Coverage not improved. Keeping generated tests by request.")
                    return True, prompt_iterations
                return successful_classes > 0, prompt_iterations

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
                self._print_dynamic_access_detail("agent: running dynamic-access prompt", indent_level=2)
                agent.send_prompt(dynamic_prompt)
                self._print_dynamic_access_detail("agent: complete", indent_level=2)
                prompt_iterations += 1
                class_attempts += 1

                reached_native_test = False
                last_test_output = ""
                last_failed_task = None
                for test_iteration in range(self.max_class_test_iterations):
                    self._print_dynamic_access_detail(
                        "test {current}/{maximum}: running ./gradlew test -Pcoordinates={library}".format(
                            current=test_iteration + 1,
                            maximum=self.max_class_test_iterations,
                            library=self.library,
                        ),
                        indent_level=2,
                    )
                    test_output = agent.run_test_command(f"./gradlew test -Pcoordinates={self.library}")
                    failed_task = self._get_first_failed_task(test_output)
                    last_test_output = test_output
                    last_failed_task = failed_task
                    self._print_dynamic_access_detail(
                        "test: complete (failed task: {failed_task})".format(
                            failed_task=failed_task or "none",
                        ),
                        indent_level=2,
                    )
                    if failed_task in {"nativeTest", None}:
                        reached_native_test = True
                        break
                    self._print_dynamic_access_detail(
                        "agent: test failed before nativeTest; sending failure output back to agent",
                        indent_level=2,
                    )
                    agent.send_prompt(
                        "When `./gradlew test -Pcoordinates={library}` is ran this is the error:\n{error_output}".format(
                            library=self.library,
                            error_output=test_output,
                        )
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
                    class_failed = True
                    break

                previous_report = current_report
                current_report = self._generate_dynamic_access_report(indent_level=2)
                if current_report is None:
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
                    return False, prompt_iterations

                updated_class = current_report.get_class(class_name)
                if updated_class is None:
                    if current_report.covered_calls > previous_report.covered_calls:
                        self._print_dynamic_access_detail("result: resolved", indent_level=2)
                        self._commit_test_sources(
                            f"Dynamic-access coverage for {class_name} "
                            f"({current_report.covered_calls}/{current_report.total_calls})"
                        )
                        successful_classes += 1
                        if not self._run_native_test_verification_gate(class_name):
                            gate_failed = True
                            exhausted_classes.add(class_name)
                            break
                        current_report = self._refresh_report_after_gate(class_name) or current_report
                    else:
                        self._print_dynamic_access_detail(
                            "result: class disappeared without coverage gain, skipping",
                            indent_level=2,
                        )
                    exhausted_classes.add(class_name)
                    break
                if updated_class.uncovered_calls == 0:
                    self._print_dynamic_access_detail("result: resolved", indent_level=2)
                    self._commit_test_sources(
                        f"Dynamic-access coverage for {class_name} "
                        f"({current_report.covered_calls}/{current_report.total_calls})"
                    )
                    successful_classes += 1
                    exhausted_classes.add(class_name)
                    if not self._run_native_test_verification_gate(class_name):
                        gate_failed = True
                        break
                    current_report = self._refresh_report_after_gate(class_name) or current_report
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
                    self._commit_test_sources(
                        f"Partial dynamic-access coverage for {class_name} "
                        f"({updated_class.covered_calls}/{updated_class.total_calls})"
                    )
                    class_committed = True
                    class_checkpoint = subprocess.check_output(
                        ["git", "rev-parse", "HEAD"], text=True,
                    ).strip()
                    if not self._run_native_test_verification_gate(class_name):
                        gate_failed = True
                        break
                    refreshed = self._refresh_report_after_gate(class_name)
                    if refreshed is not None:
                        current_report = refreshed
                        updated_class = current_report.get_class(class_name) or updated_class
                else:
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
                self._print_class_completion_progress(
                    class_name,
                    self._completed_class_count(current_report, exhausted_classes),
                    self._class_completion_total(initial_class_count, current_report, exhausted_classes),
                    current_report,
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
            self._print_class_completion_progress(
                class_name,
                self._completed_class_count(current_report, exhausted_classes),
                self._class_completion_total(initial_class_count, current_report, exhausted_classes),
                current_report,
            )

    def _run_native_test_verification_gate(self, class_name: str) -> bool:
        """Run the per-class native-test verification gate; return True if PASSED."""
        output_dir = per_class_output_dir(
            self.reachability_repo_path, self.group, self.artifact, self.version, class_name,
        )
        self._print_dynamic_access_detail(
            f"native-test gate: starting class={class_name} output_dir={output_dir} "
            f"budget={self.max_native_test_verification_iterations}",
            indent_level=2,
        )
        result = verify_native_test_passes(
            reachability_repo_path=self.reachability_repo_path,
            coordinate=self.library,
            output_dir=output_dir,
            max_iterations=self.max_native_test_verification_iterations,
        )
        if result.status == NATIVE_TEST_GATE_FAILED:
            log_path = result.last_native_test_log_path or "(none)"
            self._print_dynamic_access_message(
                f"native-test gate FAILED for {class_name} after {result.iterations_used} cycles "
                f"(last log: {log_path})"
            )
            return False
        self._print_dynamic_access_detail(
            f"native-test gate {result.status} for {class_name} after {result.iterations_used} cycles",
            indent_level=2,
        )
        return True

    def _refresh_report_after_gate(self, class_name: str):
        """Regenerate the dynamic-access report after the gate to reflect new coverage."""
        refreshed = self._generate_dynamic_access_report(indent_level=2)
        if refreshed is None:
            self._print_dynamic_access_detail(
                f"native-test gate: post-gate report refresh failed for {class_name}",
                indent_level=2,
            )
        return refreshed

    @staticmethod
    def _resolve_class_progress(
            class_name: str,
            current_report,
            initial_class_positions: dict[str, int],
            initial_class_count: int,
    ) -> tuple[int, int] | None:
        """Resolve class progress using the initial report order when available."""
        initial_position = initial_class_positions.get(class_name)
        if initial_position is not None:
            return initial_position, initial_class_count
        return current_report.class_progress(class_name)

    @classmethod
    def _print_class_completion_progress(
            cls,
            class_name: str,
            completed_class_count: int,
            total_class_count: int,
            current_report,
    ) -> None:
        remaining_calls = max(current_report.total_calls - current_report.covered_calls, 0)
        cls._print_dynamic_access_message(cls.PROGRESS_DIVIDER)
        cls._print_dynamic_access_message(
            "Progress after {class_name}: classes {completed}/{total} complete; "
            "overall coverage {covered}/{call_total} covered ({remaining} remaining)".format(
                class_name=class_name,
                completed=completed_class_count,
                total=total_class_count,
                covered=current_report.covered_calls,
                call_total=current_report.total_calls,
                remaining=remaining_calls,
            )
        )
        cls._print_dynamic_access_message(cls.PROGRESS_DIVIDER)

    @staticmethod
    def _class_completion_total(initial_class_count: int, current_report, known_class_names: set[str]) -> int:
        return max(initial_class_count, len(current_report.classes), len(known_class_names))

    @staticmethod
    def _completed_class_count(current_report, exhausted_classes: set[str]) -> int:
        completed_class_names = set(exhausted_classes)
        completed_class_names.update(
            class_coverage.class_name
            for class_coverage in current_report.classes
            if class_coverage.uncovered_calls == 0
        )
        return len(completed_class_names)

    def _library_test_change_signature(self) -> str:
        """Capture tracked and untracked changes under the generated library test tree."""
        test_dir = os.path.join("tests", "src", self.group, self.artifact, self.version)
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

    def _commit_test_sources(self, message: str) -> None:
        """Stage and commit test sources so the next class has a clean checkpoint."""
        tests_dir = os.path.join(
            self.reachability_repo_path, "tests", "src",
            self.group, self.artifact, self.version,
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

    def _generate_dynamic_access_report(self, indent_level: int = 0):
        current_status = self._current_dynamic_access_status()
        self._print_dynamic_access_detail(
            "report: refreshing {library} (previous report: {status})".format(
                library=self.library,
                status=self._display_dynamic_access_status(current_status),
            ),
            indent_level=indent_level,
        )
        result = self._run_gradle_command_with_output([
            "./gradlew",
            "generateDynamicAccessCoverageReport",
            f"-Pcoordinates={self.library}",
        ])
        if result.returncode != 0:
            self._last_dynamic_access_report_issue = "gradle_task_failed"
            self._print_dynamic_access_detail(
                "Coverage report refresh failed:",
                indent_level=indent_level,
            )
            self._print_dynamic_access_detail(
                "cause=gradle_task_failed task=generateDynamicAccessCoverageReport exit_code={exit_code}".format(
                    exit_code=result.returncode,
                ),
                indent_level=indent_level,
            )
            self._print_failure_analysis(
                "generateDynamicAccessCoverageReport",
                issue=self._summarize_gradle_issue(result.stdout),
                indent_level=indent_level,
                exit_code=result.returncode,
            )
            print(result.stdout)
            return None
        try:
            report = load_dynamic_access_coverage_report(
                self.dynamic_access_report_path,
                source_context_files=self.context.get("source_context_files") or [],
            )
        except FileNotFoundError:
            self._last_dynamic_access_report_issue = "report_file_missing"
            self._print_dynamic_access_detail(
                "Coverage report unavailable after refresh:",
                indent_level=indent_level,
            )
            self._print_dynamic_access_detail(
                "cause=report_file_missing path={path}".format(
                    path=os.path.relpath(self.dynamic_access_report_path, self.reachability_repo_path),
                ),
                indent_level=indent_level,
            )
            self._print_failure_analysis(
                "generateDynamicAccessCoverageReport",
                issue="gradle_task_succeeded_but_report_file_was_not_written",
                indent_level=indent_level,
                path=self.dynamic_access_report_path,
            )
            return None
        self._print_dynamic_access_detail(
            "report: {library} -> {covered}/{total} covered".format(
                library=self.library,
                covered=report.covered_calls,
                total=report.total_calls,
            ),
            indent_level=indent_level,
        )
        if not report.has_dynamic_access or report.total_calls == 0:
            self._last_dynamic_access_report_issue = "no_dynamic_access"
            self._print_dynamic_access_detail(
                "Coverage report loaded but contains no usable dynamic-access guidance:",
                indent_level=indent_level,
            )
            self._print_dynamic_access_detail(
                "cause=no_dynamic_access hasDynamicAccess={has_dynamic_access} totalCalls={total_calls} "
                "coveredCalls={covered_calls} classes={class_count}".format(
                    has_dynamic_access=str(report.has_dynamic_access).lower(),
                    total_calls=report.total_calls,
                    covered_calls=report.covered_calls,
                    class_count=len(report.classes),
                ),
                indent_level=indent_level,
            )
            self._print_failure_analysis(
                "generateDynamicAccessCoverageReport",
                issue="report_loaded_without_dynamic_access_call_sites",
                indent_level=indent_level,
                has_dynamic_access=str(report.has_dynamic_access).lower(),
                total_calls=report.total_calls,
                covered_calls=report.covered_calls,
                classes=len(report.classes),
            )
        else:
            self._last_dynamic_access_report_issue = "ok"
        return report

    def _current_dynamic_access_status(self) -> str:
        try:
            current_report = load_dynamic_access_coverage_report(
                self.dynamic_access_report_path,
                source_context_files=self.context.get("source_context_files") or [],
            )
        except FileNotFoundError:
            return "missing"
        if current_report.total_calls == 0:
            return "0/0"
        return "{covered}/{total}".format(
            covered=current_report.covered_calls,
            total=current_report.total_calls,
        )

    @staticmethod
    def _display_dynamic_access_status(status: str) -> str:
        if status == "missing":
            return "missing"
        return status

    def _dynamic_access_fallback_cause(self) -> str:
        return self._last_dynamic_access_report_issue

    @staticmethod
    def _should_fallback_to_basic_flow(report) -> bool:
        return report is None or not report.has_dynamic_access or report.total_calls == 0

    @staticmethod
    def _format_progress(delta, class_attempt: int) -> str:
        lines = [f"- Attempts used for this class so far: {class_attempt}"]
        if delta.newly_covered:
            lines.append("- Newly covered call sites:")
            lines.append(format_call_sites(delta.newly_covered))
        else:
            lines.append("- Newly covered call sites: none")
        if delta.still_uncovered:
            lines.append(f"- Remaining uncovered call sites after the last report: {len(delta.still_uncovered)}")
        else:
            lines.append("- Remaining uncovered call sites after the last report: 0")
        return "\n".join(lines)

    @staticmethod
    def _call_label(count: int) -> str:
        if count == 1:
            return "call is"
        return "calls are"

    @staticmethod
    def _print_dynamic_access_message(message: str) -> None:
        log_stage("dynamic-access", message)

    @classmethod
    def _print_dynamic_access_detail(cls, message: str, indent_level: int = 1) -> None:
        log_stage("dynamic-access", message, indent_level=indent_level)

    @classmethod
    def _print_failure_analysis(cls, stage: str, issue: str, indent_level: int = 1, **details) -> None:
        cls._print_dynamic_access_detail(
            "analysis: stage={stage} issue={issue}".format(
                stage=stage,
                issue=issue,
            ),
            indent_level=indent_level,
        )
        for key, value in details.items():
            cls._print_dynamic_access_detail(
                "{key}={value}".format(
                    key=key,
                    value=value,
                ),
                indent_level=indent_level,
            )

    def _summarize_gradle_issue(self, output: str) -> str:
        failed_task = self._get_first_failed_task(output)
        if failed_task is not None:
            return f"task_{failed_task}_failed"

        patterns = [
            "Execution failed for task",
            "* What went wrong:",
            "Caused by:",
            "Exception is:",
            "BUILD FAILED",
            "FAILURE: Build failed with an exception.",
        ]
        for raw_line in output.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            for pattern in patterns:
                if pattern in line:
                    return line.replace(" ", "_")
            if line.startswith("ERROR:") or line.startswith("error:"):
                return line.replace(" ", "_")
        return "gradle_command_failed_without_specific_summary"
