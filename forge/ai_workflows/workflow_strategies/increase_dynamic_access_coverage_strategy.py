# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.workflow_strategies.workflow_strategy import RUN_STATUS_SUCCESS, WorkflowStrategy


@WorkflowStrategy.register("increase_dynamic_access_coverage")
class IncreaseDynamicAccessCoverageStrategy(WorkflowStrategy):
    """Composite strategy that runs a primary workflow then improves dynamic-access coverage."""

    REQUIRED_PROMPTS = []
    REQUIRED_PARAMS = []

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        primary_workflow_name = strategy_obj.get("primary-workflow")
        if not primary_workflow_name:
            raise ValueError("increase_dynamic_access_coverage strategy requires 'primary-workflow' in strategy config")
        self.reachability_repo_path = self.context["reachability_repo_path"]
        self.library = self.context.get("library") or self.context.get("updated_library")
        self.group, self.artifact, self.version = self.library.split(":")
        PrimaryClass = WorkflowStrategy.get_class(primary_workflow_name)
        self.primary = PrimaryClass(strategy_obj, **context)

    @staticmethod
    def _print_message(message: str) -> None:
        print(f"[composition-workflow] {message}")

    def run(self, agent, **kwargs):
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
        self._print_message("starting dynamic-access coverage phase")
        library = self.context.get("library") or self.context.get("updated_library")
        da_context = dict(self.context)
        da_context["library"] = library

        da = DynamicAccessIterativeStrategy(self.strategy_obj, **da_context)
        phase_ok, da_iterations = da._run_dynamic_access_phase(agent)
        iterations += da_iterations
        self._print_message(
            "dynamic-access coverage phase completed with phase_ok={phase_ok}, iterations_added={iterations}".format(
                phase_ok=phase_ok,
                iterations=da_iterations,
            )
        )

        if not phase_ok:
            self._print_message("keeping primary workflow result because dynamic-access coverage phase did not succeed")

        if len(result) == 2:
            return status, iterations
        return (status, iterations) + result[2:]
