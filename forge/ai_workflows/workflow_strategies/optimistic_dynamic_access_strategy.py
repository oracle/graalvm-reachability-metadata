# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess

from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_FAILURE, RUN_STATUS_SUCCESS, WorkflowStrategy
from utility_scripts.dynamic_access_report import format_full_report, load_dynamic_access_coverage_report
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import load_strategy_by_name


FALLBACK_STRATEGY_NAME = "basic_iterative_pi_gpt-5.4"


@WorkflowStrategy.register("optimistic_dynamic_access")
class OptimisticDynamicAccessStrategy(WorkflowStrategy):
    """Bulk dynamic-access coverage strategy that shows the full report to the agent."""

    REQUIRED_PROMPTS = ["optimistic-dynamic-access-iteration"]
    REQUIRED_PARAMS = ["max-optimistic-iterations", "max-test-iterations"]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.library = self.context["library"]
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.group, self.artifact, self.version = self.library.split(":")
        self.max_optimistic_iterations = self.parameters["max-optimistic-iterations"]
        self.max_test_iterations = self.parameters["max-test-iterations"]
        self._last_dynamic_access_report_issue = "not_run"
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

    def run(self, agent, **kwargs):
        initial_report = self._generate_dynamic_access_report()
        if initial_report is None or not initial_report.has_dynamic_access or initial_report.total_calls == 0:
            self._print_message(
                "Falling back to basic iterative metadata flow: "
                "cause={cause} coordinate={library}".format(
                    cause=self._last_dynamic_access_report_issue,
                    library=self.library,
                )
            )
            return self._run_basic_iterative_fallback(agent, **kwargs)

        checkpoint = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        successful_iterations = 0
        prompt_iterations = 0
        current_report = initial_report

        for iteration in range(self.max_optimistic_iterations):
            agent.clear_context()
            self._print_message(
                "iteration {current}/{max} — {uncovered} uncovered across {classes} classes".format(
                    current=iteration + 1,
                    max=self.max_optimistic_iterations,
                    uncovered=current_report.total_calls - current_report.covered_calls,
                    classes=sum(1 for c in current_report.classes if c.uncovered_calls > 0),
                )
            )

            full_report_text = format_full_report(current_report)
            iteration_progress = "- Iteration: {current}/{max}\n- Overall coverage: {covered}/{total}".format(
                current=iteration + 1,
                max=self.max_optimistic_iterations,
                covered=current_report.covered_calls,
                total=current_report.total_calls,
            )

            prompt = self._render_prompt(
                "optimistic-dynamic-access-iteration",
                dynamic_access_full_report=full_report_text,
                iteration_progress=iteration_progress,
            )
            self._print_detail("agent: sending optimistic prompt")
            agent.send_prompt(prompt)
            self._print_detail("agent: complete")
            prompt_iterations += 1

            # Inner loop: test and fix
            reached_native_test = False
            last_test_output = ""
            last_failed_task = None
            for test_iteration in range(self.max_test_iterations):
                self._print_detail(
                    "test {current}/{max}: running ./gradlew test -Pcoordinates={library}".format(
                        current=test_iteration + 1,
                        max=self.max_test_iterations,
                        library=self.library,
                    ),
                    indent_level=2,
                )
                test_output = agent.run_test_command(f"./gradlew test -Pcoordinates={self.library}")
                failed_task = self._get_first_failed_task(test_output)
                last_test_output = test_output
                last_failed_task = failed_task
                self._print_detail(
                    "test: complete (failed task: {failed_task})".format(
                        failed_task=failed_task or "none",
                    ),
                    indent_level=2,
                )
                if failed_task in {"nativeTest", None}:
                    reached_native_test = True
                    break
                self._print_detail("agent: test failed; sending failure output back", indent_level=2)
                agent.send_prompt(
                    "When `./gradlew test -Pcoordinates={library}` is ran this is the error:\n{error_output}".format(
                        library=self.library,
                        error_output=test_output,
                    )
                )
                self._print_detail("agent: complete", indent_level=2)
                prompt_iterations += 1

            if not reached_native_test:
                self._print_detail("result: failed, reverting to checkpoint")
                self._print_failure_analysis(
                    "gradle_test_failed",
                    issue=self._summarize_gradle_issue(last_test_output),
                    failed_task=last_failed_task or "unknown",
                )
                subprocess.run(["git", "reset", "--hard", checkpoint], check=False)
                continue

            # Regenerate report, commit, advance checkpoint
            current_report = self._generate_dynamic_access_report()
            if current_report is None:
                self._print_detail("result: dynamic-access report unavailable after test run")
                break

            self._commit_test_sources(
                "Optimistic dynamic-access coverage ({covered}/{total})".format(
                    covered=current_report.covered_calls,
                    total=current_report.total_calls,
                )
            )
            checkpoint = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
            successful_iterations += 1

            if current_report.total_calls == current_report.covered_calls:
                self._print_message("all call sites covered")
                break

        if successful_iterations > 0:
            return RUN_STATUS_SUCCESS, prompt_iterations, successful_iterations
        return RUN_STATUS_FAILURE, prompt_iterations, 0

    def _run_basic_iterative_fallback(self, agent, **kwargs):
        """Instantiate a BasicIterativeStrategy and delegate to it."""
        from ai_workflows.workflow_strategies.basic_iterative_strategy import BasicIterativeStrategy
        fallback_obj = load_strategy_by_name(FALLBACK_STRATEGY_NAME)
        if fallback_obj is None:
            raise ValueError(f"Fallback strategy '{FALLBACK_STRATEGY_NAME}' not found in predefined strategies")
        fallback = BasicIterativeStrategy(fallback_obj, **self.context)
        return fallback.run(agent, **kwargs)

    def _generate_dynamic_access_report(self, indent_level: int = 0):
        """Run the gradle task to generate a fresh DA coverage report."""
        self._print_detail(
            "report: refreshing {library}".format(library=self.library),
            indent_level=indent_level,
        )
        result = self._run_gradle_command_with_output([
            "./gradlew",
            "generateDynamicAccessCoverageReport",
            f"-Pcoordinates={self.library}",
        ])
        if result.returncode != 0:
            self._last_dynamic_access_report_issue = "gradle_task_failed"
            self._print_detail("report: refresh failed (exit_code={code})".format(code=result.returncode))
            self._print_failure_analysis(
                "generateDynamicAccessCoverageReport",
                issue=self._summarize_gradle_issue(result.stdout),
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
            self._print_detail(
                "report: file missing at {path}".format(
                    path=os.path.relpath(self.dynamic_access_report_path, self.reachability_repo_path),
                )
            )
            return None
        self._print_detail(
            "report: {covered}/{total} covered".format(
                covered=report.covered_calls,
                total=report.total_calls,
            ),
            indent_level=indent_level,
        )
        if not report.has_dynamic_access or report.total_calls == 0:
            self._last_dynamic_access_report_issue = "no_dynamic_access"
        else:
            self._last_dynamic_access_report_issue = "ok"
        return report

    def _commit_test_sources(self, message: str) -> None:
        """Stage and commit test sources."""
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

    def _summarize_gradle_issue(self, output: str) -> str:
        """Extract a short summary from gradle output for diagnostics."""
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

    @staticmethod
    def _print_message(message: str) -> None:
        log_stage("optimistic-da", message)

    @classmethod
    def _print_detail(cls, message: str, indent_level: int = 1) -> None:
        log_stage("optimistic-da", message, indent_level=indent_level)

    @classmethod
    def _print_failure_analysis(cls, stage: str, issue: str, indent_level: int = 1, **details) -> None:
        cls._print_detail(
            "analysis: stage={stage} issue={issue}".format(stage=stage, issue=issue),
            indent_level=indent_level,
        )
        for key, value in details.items():
            cls._print_detail("{key}={value}".format(key=key, value=value), indent_level=indent_level)
