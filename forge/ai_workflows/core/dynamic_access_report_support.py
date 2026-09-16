# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Report generation and progress narration for the dynamic-access strategy.

Refreshing and loading the per-class coverage report, fallback detection,
and the strategy's progress/failure logging vocabulary
(§AR-dynamic-access-iterative), kept out of the class-loop flow.
"""

import os

from utility_scripts.dynamic_access_report import (
    DynamicAccessCoverageReport,
    format_call_sites,
    load_dynamic_access_coverage_report,
)
from utility_scripts.run_location import (
    PHASE_EXPLORE as RUN_PHASE_EXPLORE,
    STEP_GENERATE_TESTS,
    log_step_progress,
)
from utility_scripts.stage_logger import log_detail


class DynamicAccessReportSupport:
    """Mixin owning coverage-report access and progress narration."""

    PROGRESS_DIVIDER = "=" * 83

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
        log_step_progress(
            RUN_PHASE_EXPLORE,
            STEP_GENERATE_TESTS,
            "Finished class {class_name}: classes {completed}/{total} processed; "
            "coverage {covered}/{call_total} ({remaining} remaining)".format(
                class_name=class_name,
                completed=completed_class_count,
                total=total_class_count,
                covered=current_report.covered_calls,
                call_total=current_report.total_calls,
                remaining=remaining_calls,
            ),
        )
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

    @staticmethod
    def _uncovered_class_count(current_report: DynamicAccessCoverageReport) -> int:
        return sum(1 for class_coverage in current_report.classes if class_coverage.uncovered_calls > 0)

    @staticmethod
    def _uncovered_call_count(current_report: DynamicAccessCoverageReport) -> int:
        return max(current_report.total_calls - current_report.covered_calls, 0)

    def _generate_dynamic_access_report(self, indent_level: int = 0):
        """Generate and load the dynamic-access report used as strategy guidance.

        Missing guidance at the start allows fallback, but report loss after
        entering the class loop is a hard failure
        (§AR-dynamic-access-fallback-and-failure).
        """
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
        log_detail("dynamic-access", message)

    @classmethod
    def _print_dynamic_access_detail(cls, message: str, indent_level: int = 1) -> None:
        log_detail("dynamic-access", message, indent_level=indent_level)

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
