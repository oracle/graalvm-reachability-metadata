# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared finalization status handling for workflow drivers."""

from ai_workflows.workflow_strategies.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
    WorkflowStrategy,
)

FINALIZABLE_DYNAMIC_ACCESS_STATUSES: frozenset[str] = frozenset({
    RUN_STATUS_SUCCESS,
    RUN_STATUS_CHUNK_READY,
})
PR_ELIGIBLE_FINALIZATION_STATUSES: frozenset[str] = frozenset({
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
})


def finalize_dynamic_access_driver_status(
        strategy_obj: WorkflowStrategy,
        workflow_status: str,
        base_commit: str,
) -> str:
    """Run shared finalization and preserve chunk-ready as the public status."""
    if workflow_status not in FINALIZABLE_DYNAMIC_ACCESS_STATUSES:
        return workflow_status

    finalization_status, _checkpoint_commit = strategy_obj._finalize_successful_iteration(
        base_commit=base_commit,
    )
    if (
            workflow_status == RUN_STATUS_CHUNK_READY
            and finalization_status in PR_ELIGIBLE_FINALIZATION_STATUSES
    ):
        return RUN_STATUS_CHUNK_READY
    return finalization_status
