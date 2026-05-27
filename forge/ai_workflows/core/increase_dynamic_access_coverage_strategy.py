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


@WorkflowStrategy.register("increase_dynamic_access_coverage")
class IncreaseDynamicAccessCoverageStrategy(WorkflowStrategy):
    """Composite strategy that runs a primary workflow then improves dynamic-access coverage.

    Implements the composite engine of §WF-dynamic-access-composite-strategy.
    """

    REQUIRED_PROMPTS = []
    REQUIRED_PARAMS = []

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        primary_workflow_name = strategy_obj.get("primary-workflow")
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.library = self.context.get("library") or self.context.get("updated_library")
        self.group, self.artifact, self.version = self.library.split(":")
        if primary_workflow_name:
            PrimaryClass = WorkflowStrategy.get_class(primary_workflow_name)
            self.primary = PrimaryClass(strategy_obj, **context)
        else:
            self.primary = None

    @staticmethod
    def _print_message(message: str) -> None:
        print(f"[composition-workflow] {message}")

    def run(self, agent, **kwargs):
        if self.primary is None:
            self._print_message("no primary workflow configured, skipping to dynamic-access coverage phase")
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

        library = self.context.get("library") or self.context.get("updated_library")
        da_context = dict(self.context)
        da_context["library"] = library

        da = DynamicAccessIterativeStrategy(self.strategy_obj, **da_context)
        self._print_message("starting dynamic-access coverage phase")
        phase_ok, da_iterations = da._run_dynamic_access_phase(agent)
        iterations += da_iterations
        self._print_message(
            "dynamic-access coverage phase completed with phase_ok={phase_ok}, iterations_added={iterations}".format(
                phase_ok=phase_ok,
                iterations=da_iterations,
            )
        )

        has_issue_requested_metadata = da.has_issue_requested_metadata_context()
        if not phase_ok:
            if self.primary is None and not has_issue_requested_metadata:
                self._print_message(
                    "dynamic-access coverage phase did not succeed and no reporter-requested metadata phase is available"
                )
                status = RUN_STATUS_FAILURE
            else:
                self._print_message(
                    "continuing with existing workflow result because dynamic-access coverage phase did not succeed"
                )
        elif da._last_phase_status == RUN_STATUS_CHUNK_READY:
            status = RUN_STATUS_CHUNK_READY

        if has_issue_requested_metadata:
            self._print_message("starting reporter-requested metadata phase")
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
                if self.primary is None:
                    return status, iterations
                if len(result) == 2:
                    return status, iterations
                return (status, iterations) + result[2:]
            if status != RUN_STATUS_CHUNK_READY:
                status = RUN_STATUS_SUCCESS

        if self.primary is None:
            return status, iterations

        if len(result) == 2:
            return status, iterations
        return (status, iterations) + result[2:]
