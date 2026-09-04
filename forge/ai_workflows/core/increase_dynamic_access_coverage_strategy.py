# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from ai_workflows.core.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    WorkflowStrategy,
)
from utility_scripts.continuation_marker import PHASE_EXPLORE, PHASE_FIX, save_phase_update
from utility_scripts.dynamic_access_report import (
    BulkDynamicAccessProgress,
    DynamicAccessCoverageReport,
    remaining_uncovered_classes,
)
from utility_scripts.java_fix_coverage_follow_up import uncovered_dynamic_access_class_count
from utility_scripts.run_location import RunLocation, STEP_GENERATE_TESTS, record_step_failure
from utility_scripts.stage_logger import log_detail


@WorkflowStrategy.register("increase_dynamic_access_coverage")
class IncreaseDynamicAccessCoverageStrategy(WorkflowStrategy):
    """Composite strategy that runs a primary workflow then improves dynamic-access coverage.

    Implements the composite engine of §AR-dynamic-access-composite.
    """

    REQUIRED_PROMPTS = []
    REQUIRED_PARAMS = []

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.primary_workflow_name: str | None = strategy_obj.get("primary-workflow")
        self.chunk_class_count: int = int(self.context.get("chunk_class_count") or 0)
        self.bulk_min_uncovered_classes: int = int(
            self.parameters.get("bulk-min-uncovered-classes") or 0
        )
        if self.bulk_min_uncovered_classes < 0:
            raise ValueError("bulk-min-uncovered-classes must be non-negative")
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.library = self.context.get("library") or self.context.get("updated_library")
        self.group, self.artifact, self.version = self.library.split(":")
        self.dynamic_access_class_threshold = int(
            self.context.get("dynamic_access_class_threshold") or 0
        )
        if self.dynamic_access_class_threshold < 0:
            raise ValueError("dynamic_access_class_threshold must be non-negative")
        if self.primary_workflow_name:
            PrimaryClass = WorkflowStrategy.get_class(self.primary_workflow_name)
            primary_context: dict = dict(context)
            if self.primary_workflow_name == "bulk_dynamic_access":
                primary_context["defer_dynamic_access_chunk_decision"] = True
            self.primary = PrimaryClass(strategy_obj, **primary_context)
        else:
            self.primary = None

    @staticmethod
    def _print_message(message: str) -> None:
        log_detail("composition-workflow", message)

    def _record_dynamic_access_failure(self) -> None:
        """Record exploration only after the composite makes it terminal.

        §FS-forge-run-location-reporting.3
        """
        record_step_failure(
            location=RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS, self.library),
        )

    def _precheck_optimistic_bulk(
            self,
    ) -> tuple[DynamicAccessCoverageReport | None, int | None]:
        """Return the reusable report and a small-report iterative budget.

        The budget is the remainder iterative exploration can still take, on
        the same basis as the post-bulk boundary: a class an earlier phase
        already processed is not work either phase can repeat
        (§AR-dynamic-access-composite).
        """
        if (
                self.primary_workflow_name != "bulk_dynamic_access"
                or self.bulk_min_uncovered_classes <= 0
        ):
            return None, None

        report = self.primary._generate_dynamic_access_report()
        if report is None or not report.has_dynamic_access or report.total_calls == 0:
            return report, None

        uncovered_class_count: int = uncovered_dynamic_access_class_count(report)
        if uncovered_class_count == 0:
            self._print_message("skipping bulk dynamic-access primary: no uncovered classes remain")
            return report, 0
        if uncovered_class_count >= self.bulk_min_uncovered_classes:
            return report, None

        remaining_class_count: int = len(remaining_uncovered_classes(
            report,
            self.primary.processed_dynamic_access_classes(),
        ))
        if remaining_class_count == 0:
            # Bulk is the only phase that still prompts on a class iterative
            # exploration exhausted, so a fully processed remainder stays with it.
            self._print_message(
                "keeping bulk dynamic-access primary: "
                "uncovered_classes={uncovered} are already processed".format(
                    uncovered=uncovered_class_count,
                )
            )
            return report, None

        self._print_message(
            "skipping bulk dynamic-access primary: "
            "uncovered_classes={uncovered} remaining={remaining} minimum={minimum}".format(
                uncovered=uncovered_class_count,
                remaining=remaining_class_count,
                minimum=self.bulk_min_uncovered_classes,
            )
        )
        return report, remaining_class_count

    def run(self, agent, **kwargs):
        current_report: DynamicAccessCoverageReport | None = None
        iterative_chunk_count: int | None = None
        preceding_covered_call_gain: int = 0
        required_iterative_chunk_phase: bool = False
        skip_dynamic_access_phase: bool = False
        small_report_iterative_count: int | None = None
        if self.primary is not None:
            current_report, small_report_iterative_count = self._precheck_optimistic_bulk()

        if self.primary is None:
            self._print_message("no primary workflow configured, skipping to dynamic-access coverage phase")
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_skipped_if_pending(PHASE_FIX),
            )
            status = RUN_STATUS_SUCCESS
            iterations = 0
        elif small_report_iterative_count is not None:
            # The skipped primary still owns its phase transition, so the
            # composite releases the fix phase bulk would have released
            # (§FS-forge-run-continuation.1).
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_skipped_if_pending(PHASE_FIX),
            )
            result: tuple[str, int, int] = (RUN_STATUS_SUCCESS, 0, 1)
            status = RUN_STATUS_SUCCESS
            iterations = 0
            iterative_chunk_count = small_report_iterative_count
            skip_dynamic_access_phase = small_report_iterative_count == 0
            required_iterative_chunk_phase = small_report_iterative_count > 0
            agent.clear_context()
        else:
            self._print_message("starting primary workflow")
            primary_kwargs: dict[str, object] = dict(kwargs)
            if current_report is not None:
                primary_kwargs["initial_report"] = current_report
            result = self.primary.run(agent, **primary_kwargs)
            status = result[0]
            iterations = result[1]
            self._print_message(f"primary workflow completed with status: {status}")

            if status != RUN_STATUS_SUCCESS:
                self._print_message(
                    "skipping dynamic-access coverage phase because primary workflow did not succeed"
                )
                self.post_generation_intervention = self.primary.post_generation_intervention
                return result

            agent.clear_context()
            if self.primary_workflow_name == "bulk_dynamic_access":
                current_report, iterative_chunk_count, chunk_ready = self._route_after_bulk()
                bulk_progress: BulkDynamicAccessProgress | None = getattr(
                    self.primary,
                    "bulk_chunk_progress",
                    None,
                )
                if bulk_progress is not None:
                    preceding_covered_call_gain = bulk_progress.covered_call_gain
                if chunk_ready:
                    save_phase_update(
                        self.continuation_marker_path,
                        lambda marker: marker.mark_phase_completed(PHASE_EXPLORE),
                    )
                    status = RUN_STATUS_CHUNK_READY
                    self._print_message(
                        "bulk filled the dynamic-access chunk boundary; skipping iterative exploration"
                    )
                    if len(result) == 2:
                        return status, iterations
                    return (status, iterations) + result[2:]
                if iterative_chunk_count is not None:
                    self._print_message(
                        "iterative dynamic-access budget after bulk: "
                        "classes={budget}".format(budget=iterative_chunk_count)
                    )
                    skip_dynamic_access_phase = iterative_chunk_count == 0
                    required_iterative_chunk_phase = iterative_chunk_count > 0

        library = self.context.get("library") or self.context.get("updated_library")
        da_context = dict(self.context)
        da_context["library"] = library
        da_context["preceding_dynamic_access_covered_call_gain"] = preceding_covered_call_gain
        if iterative_chunk_count is not None:
            da_context["chunk_class_count"] = iterative_chunk_count

        da = DynamicAccessIterativeStrategy(self.strategy_obj, **da_context)
        if self.dynamic_access_class_threshold > 0:
            if current_report is None:
                current_report = da._generate_dynamic_access_report()
            uncovered_class_count = uncovered_dynamic_access_class_count(current_report)
            if uncovered_class_count > self.dynamic_access_class_threshold:
                self._print_message(
                    "skipping dynamic-access exploration: "
                    "uncovered_classes={uncovered} threshold={threshold}".format(
                        uncovered=uncovered_class_count,
                        threshold=self.dynamic_access_class_threshold,
                    )
                )
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: marker.defer_dynamic_access_coverage(
                        uncovered_class_count,
                        self.dynamic_access_class_threshold,
                    ),
                )
                if self.primary is None or len(result) == 2:
                    return status, iterations
                return (status, iterations) + result[2:]

        if skip_dynamic_access_phase:
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_completed(PHASE_EXPLORE),
            )
            phase_ok, da_iterations = True, 0
            self._print_message("all dynamic-access classes are covered")
        else:
            self._print_message("starting dynamic-access coverage phase")
            if current_report is None:
                phase_ok, da_iterations = da._run_dynamic_access_phase(agent)
            else:
                phase_ok, da_iterations = da._run_dynamic_access_phase(agent, current_report)
        iterations += da_iterations
        self._print_message(
            "dynamic-access coverage phase completed with phase_ok={phase_ok}, iterations_added={iterations}".format(
                phase_ok=phase_ok,
                iterations=da_iterations,
            )
        )

        has_issue_requested_metadata = da.has_issue_requested_metadata_context()
        if not phase_ok:
            if required_iterative_chunk_phase:
                self._print_message(
                    "required iterative dynamic-access chunk phase did not succeed"
                )
                status = RUN_STATUS_FAILURE
                self._record_dynamic_access_failure()
            elif self.primary is None and not has_issue_requested_metadata:
                self._print_message(
                    "dynamic-access coverage phase did not succeed and no reporter-requested metadata phase is available"
                )
                status = RUN_STATUS_FAILURE
                self._record_dynamic_access_failure()
            else:
                self._print_message(
                    "continuing with existing workflow result because dynamic-access coverage phase did not succeed"
                )
        elif da._last_phase_status == RUN_STATUS_CHUNK_READY:
            status = RUN_STATUS_CHUNK_READY
            self._print_message(
                "dynamic-access chunk boundary reached; deferring reporter-requested metadata phase"
            )
            if self.primary is None:
                return status, iterations
            if len(result) == 2:
                return status, iterations
            return (status, iterations) + result[2:]

        if has_issue_requested_metadata:
            self._print_message("starting reporter-requested metadata phase")
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_running(PHASE_EXPLORE),
            )
            issue_phase_ok, issue_iterations = da._run_issue_requested_metadata_phase(agent)
            iterations += issue_iterations
            self._print_message(
                "reporter-requested metadata phase completed with phase_ok={phase_ok}, iterations_added={iterations}"
                .format(
                    phase_ok=issue_phase_ok,
                    iterations=issue_iterations,
                )
            )
            if not issue_phase_ok:
                status = RUN_STATUS_FAILURE
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: marker.mark_phase_pending(PHASE_EXPLORE, iteration=iterations),
                )
                if self.primary is None:
                    return status, iterations
                if len(result) == 2:
                    return status, iterations
                return (status, iterations) + result[2:]
            if status not in {RUN_STATUS_CHUNK_READY, RUN_STATUS_FAILURE}:
                status = RUN_STATUS_SUCCESS
            if status == RUN_STATUS_FAILURE:
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: marker.mark_phase_pending(PHASE_EXPLORE, iteration=iterations),
                )
            else:
                save_phase_update(
                    self.continuation_marker_path,
                    lambda marker: marker.mark_phase_completed(PHASE_EXPLORE, iteration=iterations),
                )

        if self.primary is None:
            return status, iterations

        if len(result) == 2:
            return status, iterations
        return (status, iterations) + result[2:]

    def _route_after_bulk(
            self,
    ) -> tuple[DynamicAccessCoverageReport | None, int | None, bool]:
        """Choose the iterative shortfall or publish after the bulk phase.

        Bulk completion contributes to the invocation-wide class boundary; the
        iterative phase receives only what is still needed to fill it
        (§AR-dynamic-access-composite).
        """
        progress: BulkDynamicAccessProgress | None = getattr(
            self.primary,
            "bulk_chunk_progress",
            None,
        )
        if progress is None:
            return None, None, False
        if self.chunk_class_count <= 0:
            return progress.final_report, None, False

        completed_class_count: int = len(progress.completed_classes)
        remaining_class_count: int = len(progress.remaining_classes)
        if remaining_class_count == 0:
            iterative_chunk_count: int = 0
        elif remaining_class_count <= self.chunk_class_count:
            iterative_chunk_count = remaining_class_count
        elif completed_class_count >= self.chunk_class_count:
            self.primary.save_bulk_chunk_state()
            self._print_message(
                "bulk chunk boundary reached: "
                "completed={completed} remaining={remaining} boundary={boundary}".format(
                    completed=completed_class_count,
                    remaining=remaining_class_count,
                    boundary=self.chunk_class_count,
                )
            )
            return progress.final_report, 0, True
        else:
            iterative_chunk_count = self.chunk_class_count - completed_class_count

        self._print_message(
            "bulk chunk boundary continues to iterative exploration: "
            "completed={completed} remaining={remaining} iterative_budget={budget}".format(
                completed=completed_class_count,
                remaining=remaining_class_count,
                budget=iterative_chunk_count,
            )
        )
        return progress.final_report, iterative_chunk_count, False
