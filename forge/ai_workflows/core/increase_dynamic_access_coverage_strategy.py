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
from utility_scripts.dynamic_access_report import BulkDynamicAccessProgress, DynamicAccessCoverageReport
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

    def run(self, agent, **kwargs):
        current_report: DynamicAccessCoverageReport | None = None
        iterative_chunk_count: int | None = None
        required_iterative_chunk_phase: bool = False
        skip_dynamic_access_phase: bool = False

        if self.primary is None:
            self._print_message("no primary workflow configured, skipping to dynamic-access coverage phase")
            save_phase_update(
                self.continuation_marker_path,
                lambda marker: marker.mark_phase_skipped_if_pending(PHASE_FIX),
            )
            status = RUN_STATUS_SUCCESS
            iterations = 0
        else:
            self._print_message("starting primary workflow")
            result = self.primary.run(agent, **kwargs)
            status = result[0]
            iterations = result[1]
            self._print_message(f"primary workflow completed with status: {status}")

            if status != RUN_STATUS_SUCCESS:
                self._print_message("skipping dynamic-access coverage phase because primary workflow did not succeed")
                self.post_generation_intervention = self.primary.post_generation_intervention
                return result

            agent.clear_context()
            if self.primary_workflow_name == "bulk_dynamic_access":
                current_report, iterative_chunk_count, chunk_ready = self._route_after_bulk()
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
            self._print_message("all dynamic-access classes are covered after bulk")
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
